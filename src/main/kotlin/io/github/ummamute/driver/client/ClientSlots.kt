package io.github.ummamute.driver.client

import io.github.ummamute.driver.mixin.AbstractContainerScreenAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ClickType

/** Clicks slots of the open container screen. Call on the render thread. */
object ClientSlots {
    private val mc: Minecraft get() = Minecraft.getInstance()

    private const val SYNC_NOTE = "Result is the client's prediction; the server may correct it. Call mc_read_container to confirm."

    fun click(args: SlotClickArgs): SlotClickResult {
        if (mc.player == null) error("Not in a world. Join or create one with mc_click on the menus, then call again")
        val screen = mc.screen as? AbstractContainerScreen<*>
            ?: error("No container is open. Open one in game (mc_look_at, then mc_use on a chest or villager), then call again")
        val menu = screen.menu
        val click = SlotClickPlanner.plan(args, menu.slots.size)
        clickThroughScreen(screen, click)
        val slotItem = menu.slots.getOrNull(click.slot)?.let { ClientInventory.stackInfo(it.item) }
        return SlotClickResult(click.action.label, click.slot, slotItem, ClientInventory.stackInfo(menu.carried), menu.containerId, false, SYNC_NOTE)
    }

    /**
     * Clicks through the screen, as a mouse click does. Screens such as the creative inventory override the click to send
     * their own packets, so a plain `handleInventoryMouseClick` would hit the wrong slot of the server's menu.
     */
    private fun clickThroughScreen(screen: AbstractContainerScreen<*>, click: SlotClick) {
        val slot = screen.menu.slots.getOrNull(click.slot)
        (screen as AbstractContainerScreenAccessor).`driver$slotClicked`(slot, click.slot, click.button, clickType(click.action))
    }

    private fun clickType(action: SlotAction): ClickType = when (action) {
        SlotAction.PICK_UP -> ClickType.PICKUP
        SlotAction.QUICK_MOVE -> ClickType.QUICK_MOVE
        SlotAction.SWAP -> ClickType.SWAP
        SlotAction.THROW -> ClickType.THROW
        SlotAction.CLONE -> ClickType.CLONE
        SlotAction.PICK_UP_ALL -> ClickType.PICKUP_ALL
    }
}
