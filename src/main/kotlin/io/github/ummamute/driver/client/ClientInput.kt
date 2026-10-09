package io.github.ummamute.driver.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

/** Movement, aiming and interaction, applied through the player and game mode. Call on the render thread. */
object ClientInput {
    /** Keys that stay held until released (while no screen is open), then `inventory`, which is a single press that opens the inventory, or closes it when a container screen is open. */
    val keyNames = listOf("forward", "back", "left", "right", "jump", "sneak", INVENTORY_KEY)

    private const val INVENTORY_KEY = "inventory"

    /** Longest `mc_use` hold: ten seconds of ticks, enough for any item's use time. */
    const val MAX_HOLD_TICKS = 200

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Keys the agent holds. Render thread only. */
    private val heldKeys = mutableSetOf<String>()

    /** Ticks the use key stays down after [use] started an item. Render thread only. */
    private var useTicksLeft = 0

    /** Whether [use] still holds the use key down. Render thread only. */
    val isHoldingUse: Boolean get() = useTicksLeft > 0

    /**
     * Applies the held keys at the end of every client tick: down while no screen is open, up while one is. Vanilla sends
     * the keyboard to an open screen and releases every key (`Minecraft.setScreen` calls `KeyMapping.releaseAll`), and
     * grabbing the mouse again restores the physical state. Re-applying them after a screen closes makes a held key resume
     * the way a physically held key does.
     */
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { applyHeldKeys() }
    }

    fun setKey(name: String, down: Boolean): Boolean {
        if (name == INVENTORY_KEY) return pressInventory(down)
        val binding = keyBinding(name)
        if (down) heldKeys.add(name) else heldKeys.remove(name)
        binding.setDown(down && mc.screen == null)
        return down
    }

    private fun applyHeldKeys() {
        val active = mc.screen == null
        heldKeys.forEach { keyBinding(it).setDown(active) }
        tickUseHold(active)
    }

    /** Vanilla releases a using item on any tick the use key is up, so the key stays down for the whole hold. */
    private fun tickUseHold(active: Boolean) {
        if (useTicksLeft == 0) return
        useTicksLeft--
        mc.options.keyUse.setDown(active && useTicksLeft > 0)
    }

    /**
     * Toggles the inventory. With no screen it queues one press of the inventory key, which the game handles on its next tick.
     * A queued press is consumed only while no screen is open, so with a container screen open it closes that screen directly.
     */
    private fun pressInventory(down: Boolean): Boolean {
        if (!down) return false
        val screen = mc.screen
        when {
            screen == null -> KeyMapping.click(InputConstants.getKey(mc.options.keyInventory.saveString()))
            screen is AbstractContainerScreen<*> -> screen.onClose()
            else -> error("A ${ClassNames.of(screen)} is open, not an inventory. Call mc_close_screen first, then press inventory again")
        }
        return true
    }

    fun lookAt(x: Double, y: Double, z: Double) {
        val player = mc.player ?: error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")
        val delta = Vec3(x, y, z).subtract(player.eyePosition)
        val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z)
        player.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        player.xRot = Math.toDegrees(-atan2(delta.y, horizontal)).toFloat()
    }

    /**
     * Uses what is under the crosshair, like the use key: each hand in turn tries the entity or block under the crosshair, then its item.
     * Mirrors `Minecraft.startUseItem`, so food, potions, bows, pearls and shields work while aiming at the sky or at stone.
     * With [holdTicks] above zero and an item now in use, the use key stays down for that many ticks, as when a player holds right click.
     */
    fun use(holdTicks: Int = 0): String {
        require(holdTicks in 0..MAX_HOLD_TICKS) { "hold_ticks must be between 0 and $MAX_HOLD_TICKS, got $holdTicks" }
        val player = mc.player ?: error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")
        val gameMode = mc.gameMode ?: error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")
        if (player.isHandsBusy) error("The player's hands are busy (using an item or riding). Wait, then call mc_use again")
        val hit = mc.hitResult
        var lastResult = InteractionResult.PASS
        for (hand in InteractionHand.values()) {
            val result = useHand(player, gameMode, hand, hit)
            if (result.consumesAction()) return startHold(player, holdTicks, result)
            lastResult = result
            if (result == InteractionResult.FAIL) break
        }
        if (isNothingTargeted(hit)) {
            error("Nothing was used: no target under the crosshair, and neither hand holds an item that can be used now (food needs hunger). Aim with mc_look_at, within reach, or change the held item")
        }
        return lastResult.toString()
    }

    private fun startHold(player: LocalPlayer, holdTicks: Int, result: InteractionResult): String {
        if (holdTicks > 0 && player.isUsingItem) {
            useTicksLeft = holdTicks
            mc.options.keyUse.setDown(true)
        }
        return result.toString()
    }

    private fun isNothingTargeted(hit: HitResult?): Boolean = hit == null || hit.type == HitResult.Type.MISS

    /** One iteration of the vanilla use flow: the targeted entity or block first, then the item in [hand]. */
    private fun useHand(player: LocalPlayer, gameMode: MultiPlayerGameMode, hand: InteractionHand, hit: HitResult?): InteractionResult {
        val targetResult = when {
            hit is EntityHitResult -> interactWithEntity(player, gameMode, hit, hand)
            hit is BlockHitResult && hit.type != HitResult.Type.MISS -> gameMode.useItemOn(player, hand, hit)
            else -> InteractionResult.PASS
        }
        if (targetResult.consumesAction() || targetResult == InteractionResult.FAIL) return swingIfNeeded(player, hand, targetResult)
        if (player.getItemInHand(hand).isEmpty) return targetResult
        val itemResult = gameMode.useItem(player, hand)
        return if (itemResult.consumesAction()) swingIfNeeded(player, hand, itemResult) else targetResult
    }

    private fun swingIfNeeded(player: LocalPlayer, hand: InteractionHand, result: InteractionResult): InteractionResult {
        if (result.consumesAction() && result.shouldSwing()) player.swing(hand)
        return result
    }

    /** Mirrors the vanilla use-key flow: interact-at first, then a plain interact when it did not consume. */
    private fun interactWithEntity(player: LocalPlayer, gameMode: MultiPlayerGameMode, hit: EntityHitResult, hand: InteractionHand): InteractionResult {
        val atResult = gameMode.interactAt(player, hit.entity, hit, hand)
        if (atResult.consumesAction()) return atResult
        return gameMode.interact(player, hit.entity, hand)
    }

    private fun keyBinding(name: String): KeyMapping = when (name) {
        "forward" -> mc.options.keyUp
        "back" -> mc.options.keyDown
        "left" -> mc.options.keyLeft
        "right" -> mc.options.keyRight
        "jump" -> mc.options.keyJump
        "sneak" -> mc.options.keyShift
        else -> error("Unknown key \"$name\". Use one of $keyNames")
    }
}
