package io.github.ummamute.driver.client

import io.github.ummamute.driver.mixin.MinecraftAttackAccessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/** Owns one bounded attack at a time, leaving block-breaking progression to vanilla input handling. */
object ClientAttack {
    /** Ten seconds at the normal client tick rate. */
    const val MAX_HOLD_TICKS = 200
    private const val MAX_WALL_TIME_MS = 15_000L
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var active: Operation? = null

    /** Watches the target before vanilla handles input and observes progress after it. */
    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { checkHold() }
        ClientTickEvents.END_CLIENT_TICK.register { endTick() }
    }

    /** Sends one attack press and waits outside the render thread until any block hold releases. */
    suspend fun attack(holdTicks: Int): AttackResult {
        require(holdTicks in 0..MAX_HOLD_TICKS) { "hold_ticks must be between 0 and $MAX_HOLD_TICKS; call mc_attack again within that range" }
        val owner = Any()
        try {
            val completion = RenderThread.call { begin(owner, holdTicks) }
            val result = withTimeoutOrNull(MAX_WALL_TIME_MS) { completion.await() }
            if (result != null) return result
            RenderThread.call { finishOwned(owner, AttackOutcome.TIMED_OUT) }
            return completion.await()
        } finally {
            withContext(NonCancellable) {
                RenderThread.call { finishOwned(owner, AttackOutcome.CANCELLED) }
            }
        }
    }

    private fun begin(owner: Any, holdTicks: Int): CompletableDeferred<AttackResult> {
        check(active == null) { "An mc_attack hold is already active. Wait for it to finish before calling mc_attack again" }
        val player = mc.player ?: error("Not in a world. Join one with mc_join_world or mc_join_server, then call mc_attack again")
        val level = mc.level ?: error("No client level is available. Wait for the world to load, then call mc_attack again")
        val gameMode = mc.gameMode ?: error("No game mode is available. Wait for the world to load, then call mc_attack again")
        check(mc.screen == null && mc.overlay == null) { "A screen or overlay is open. Close it with mc_close_screen or wait, then call mc_attack again" }
        check(!player.isHandsBusy && !player.isUsingItem) { "The player's hands are busy. Release the using item or wait, then call mc_attack again" }
        check(!player.isSpectator) { "Spectators cannot attack. Change game mode, then call mc_attack again" }
        check(!mc.options.keyAttack.isDown) { "The attack key is already down. Release it, then call mc_attack again" }
        check(player.mainHandItem.isItemEnabled(level.enabledFeatures())) { "The held item is disabled. Select an enabled item, then call mc_attack again" }
        val pos = (mc.hitResult as? BlockHitResult)?.takeIf { it.type == HitResult.Type.BLOCK }?.blockPos?.immutable()
        val operation = Operation(owner, ClientTarget.snapshot(), holdTicks, player, level, gameMode, pos, pos?.let(level::getBlockState))
        active = operation
        (mc as MinecraftAttackAccessor).`driver$startAttack`()
        if (active !== operation) return operation.completion
        when {
            blockChanged(operation) == true -> finish(AttackOutcome.BLOCK_CHANGED)
            pos == null || holdTicks == 0 -> finish(AttackOutcome.PRESSED)
            else -> mc.options.keyAttack.setDown(true)
        }
        return operation.completion
    }

    private fun checkHold() {
        val operation = active ?: return
        val reason = when {
            mc.level !== operation.level || mc.player !== operation.player || mc.gameMode !== operation.gameMode -> AttackOutcome.DISCONNECTED
            mc.screen != null || mc.overlay != null -> AttackOutcome.SCREEN_OPENED
            operation.player.isHandsBusy || operation.player.isUsingItem -> AttackOutcome.HANDS_BUSY
            !mc.options.keyAttack.isDown -> AttackOutcome.INPUT_RELEASED
            blockChanged(operation) == true -> AttackOutcome.BLOCK_CHANGED
            !hasOriginalTarget(operation) -> AttackOutcome.TARGET_CHANGED
            else -> null
        }
        if (reason != null) finish(reason)
    }

    /** Checks vanilla's refreshed pick and supplies owned input even when its mouse is not captured. */
    @JvmStatic
    fun continuationInput(leftClick: Boolean): Boolean {
        val operation = active ?: return leftClick
        checkHold()
        return active === operation
    }

    private fun endTick() {
        val operation = active ?: return
        operation.elapsedTicks++
        checkHold()
        if (active === operation && operation.elapsedTicks >= operation.holdTicks) finish(AttackOutcome.HOLD_COMPLETE)
    }

    private fun hasOriginalTarget(operation: Operation): Boolean {
        val hit = mc.hitResult as? BlockHitResult ?: return false
        return hit.type == HitResult.Type.BLOCK && hit.blockPos == operation.pos
    }

    private fun blockChanged(operation: Operation): Boolean? {
        val pos = operation.pos ?: return null
        if (mc.level !== operation.level) return null
        return operation.level.getBlockState(pos) != operation.blockState
    }

    private fun finishOwned(owner: Any, reason: AttackOutcome) {
        if (active?.owner === owner) finish(reason)
    }

    private fun finish(reason: AttackOutcome) {
        val operation = active ?: return
        active = null
        mc.options.keyAttack.setDown(false)
        try {
            if (mc.level === operation.level && mc.player === operation.player && mc.gameMode === operation.gameMode) {
                operation.gameMode.stopDestroyBlock()
            }
        } finally {
            operation.completion.complete(
                AttackResult(
                    target = operation.target,
                    requestedHoldTicks = operation.holdTicks,
                    elapsedTicks = operation.elapsedTicks,
                    outcome = reason,
                    blockChanged = blockChanged(operation),
                ),
            )
        }
    }

    /** Releases before a screen or overlay can pause ticks, including one closed again before the next tick. */
    @JvmStatic
    fun onScreenOpened() {
        finish(AttackOutcome.SCREEN_OPENED)
    }

    /** Releases while the original player and level are still available for vanilla's abort packet. */
    @JvmStatic
    fun onDisconnect() {
        finish(AttackOutcome.DISCONNECTED)
    }

    private class Operation(
        val owner: Any,
        val target: CrosshairTargetSnapshot,
        val holdTicks: Int,
        val player: LocalPlayer,
        val level: ClientLevel,
        val gameMode: MultiPlayerGameMode,
        val pos: BlockPos?,
        val blockState: BlockState?,
    ) {
        val completion = CompletableDeferred<AttackResult>()
        var elapsedTicks = 0
    }
}
