package io.github.ummamute.driver.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
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
    /** Keys that stay down until released, then `inventory`, which is a single press that opens or closes the inventory screen. */
    val keyNames = listOf("forward", "back", "left", "right", "jump", "sneak", INVENTORY_KEY)

    private const val INVENTORY_KEY = "inventory"

    private val mc: Minecraft get() = Minecraft.getInstance()

    fun setKey(name: String, down: Boolean): Boolean {
        if (name == INVENTORY_KEY) return pressInventory(down)
        val binding = keyBinding(name)
        binding.setDown(down)
        return binding.isDown
    }

    /** Queues one press of the inventory key; the game handles it on its next tick, as for a real key press. */
    private fun pressInventory(down: Boolean): Boolean {
        if (down) KeyMapping.click(InputConstants.getKey(mc.options.keyInventory.saveString()))
        return down
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
        val result = when (val hit = mc.hitResult) {
            is EntityHitResult -> interactWithEntity(player, hit)
            is BlockHitResult -> useOnBlock(player, hit)
            else -> error("Nothing under the crosshair")
        }
        return result.toString()
    }

    /** A block hit result also exists for a miss, which the game reports as a block hit of type MISS; treat it as nothing there. */
    private fun useOnBlock(player: LocalPlayer, hit: BlockHitResult): InteractionResult {
        if (hit.type == HitResult.Type.MISS) error("Nothing under the crosshair. Aim with mc_look_at, within reach, and check the block is not behind another")
        val gameMode = mc.gameMode ?: error("No game mode")
        return gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
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
