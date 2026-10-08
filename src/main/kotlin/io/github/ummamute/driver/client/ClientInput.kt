package io.github.ummamute.driver.client

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

/** Movement, aiming and interaction, applied through the player and game mode. Call on the render thread. */
object ClientInput {
    val keyNames = listOf("forward", "back", "left", "right", "jump", "sneak")

    private val mc: Minecraft get() = Minecraft.getInstance()

    fun setKey(name: String, down: Boolean): Boolean {
        val binding = keyBinding(name)
        binding.setDown(down)
        return binding.isDown
    }

    fun lookAt(x: Double, y: Double, z: Double) {
        val player = mc.player ?: error("Not in a world")
        val delta = Vec3(x, y, z).subtract(player.eyePosition)
        val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z)
        player.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        player.xRot = Math.toDegrees(-atan2(delta.y, horizontal)).toFloat()
    }

    /** Uses what is under the crosshair, like the use key. */
    fun use(): String {
        val player = mc.player ?: error("Not in a world")
        val gameMode = mc.gameMode ?: error("No game mode")
        val result = when (val hit = mc.hitResult) {
            is EntityHitResult -> interactWithEntity(player, hit)
            is BlockHitResult -> gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
            else -> error("Nothing under the crosshair")
        }
        return result.toString()
    }

    /** Mirrors the vanilla use-key flow: interact-at first, then a plain interact when it did not consume. */
    private fun interactWithEntity(player: LocalPlayer, hit: EntityHitResult): InteractionResult {
        val gameMode = mc.gameMode ?: error("No game mode")
        val atResult = gameMode.interactAt(player, hit.entity, hit, InteractionHand.MAIN_HAND)
        if (atResult.consumesAction()) return atResult
        return gameMode.interact(player, hit.entity, InteractionHand.MAIN_HAND)
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
