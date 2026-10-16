package io.github.ummamute.driver.client

import io.github.ummamute.driver.mixin.MultiPlayerGameModeAccessor
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand

/** The client's predicted result of dropping from the selected hotbar slot. */
@Serializable
data class DropItemResult(
    val dropped: Boolean,
    val count: Int,
    val remaining: Int,
)

/** Drops the held item through the normal player action. Call on the render thread. */
object ClientDrops {
    /** Drops one item, or the entire selected stack when [fullStack] is true; an empty hand is a no-op. */
    fun drop(fullStack: Boolean): DropItemResult {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: error("Not in a world. Join with mc_join_world or mc_join_server, then call mc_drop_item again")
        check(minecraft.level != null && minecraft.gameMode != null) { "World is still loading. Call mc_get_state, then retry mc_drop_item" }
        check(minecraft.screen == null) { "A screen is open. Call mc_close_screen first, or use mc_click_slot with action throw in a container" }
        check(!player.isSpectator) { "Spectators cannot drop items. Switch to survival or creative mode, then call mc_drop_item again" }
        val before = player.mainHandItem.count
        if (before == 0) return DropItemResult(dropped = false, count = 0, remaining = 0)
        // Keep vanilla's cached slot consistent when selection and dropping happen before the next game-mode tick.
        (minecraft.gameMode as MultiPlayerGameModeAccessor).`driver$ensureHasSentCarriedItem`()
        val dropped = player.drop(fullStack)
        if (dropped) player.swing(InteractionHand.MAIN_HAND)
        val remaining = player.mainHandItem.count
        return DropItemResult(dropped = dropped, count = before - remaining, remaining = remaining)
    }
}
