package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffer

/** Reads the player's inventory and the open container. Call on the render thread. */
object ClientInventory {
    private val mc: Minecraft get() = Minecraft.getInstance()

    fun inventory(includeEmpty: Boolean): InventorySnapshot {
        val player = mc.player ?: error("Not in a world. Join or create one with mc_click on the menus, then call again")
        val inventory = player.inventory
        val slots = (0 until inventory.containerSize).map { index ->
            val label = SlotLabels.forInventoryIndex(index)
            SlotInfo(label.group, index, label.part, stackInfo(inventory.getItem(index)))
        }
        return InventorySnapshot(inventory.selected, stackInfo(player.containerMenu.carried), SlotLabels.filter(slots, includeEmpty))
    }

    fun container(includeEmpty: Boolean): ContainerSnapshot {
        val player = mc.player ?: error("Not in a world. Join or create one with mc_click on the menus, then call again")
        val screen = mc.screen as? AbstractContainerScreen<*>
            ?: error("No container is open. Open one in game, or call mc_read_inventory for the player's own items")
        val menu = screen.menu
        val type = menuType(menu)
        val view = MenuView(type, player.inventory, (screen as? CreativeModeInventoryScreen)?.isInventoryOpen() == true)
        val slots = menu.slots.mapIndexed { position, slot -> slotInfo(slot, position, view, position == menu.slots.lastIndex) }
        return ContainerSnapshot(
            menuType = type,
            title = screen.title.string,
            containerId = menu.containerId,
            carried = stackInfo(menu.carried),
            slots = SlotLabels.filter(slots, includeEmpty),
            offers = (menu as? MerchantMenu)?.offers?.map(::tradeOffer),
        )
    }

    /** What decides a slot's label: the menu, the player's inventory, and whether the creative screen shows its inventory tab. */
    private class MenuView(val menuType: String, val inventory: Inventory, val creativeInventoryTab: Boolean)

    /** [position] is the slot's place in the menu's slot list, which is what `mc_click_slot` takes; a wrapped slot's own `index` is not reliable. */
    private fun slotInfo(slot: Slot, position: Int, view: MenuView, isLast: Boolean): SlotInfo {
        val label = slotLabel(slot, position, view, isLast)
        return SlotInfo(label.group, position, label.part, stackInfo(slot.item))
    }

    private fun slotLabel(slot: Slot, position: Int, view: MenuView, isLast: Boolean): SlotLabel = when {
        // The creative inventory tab wraps the player menu's slots, numbering them by menu index, and ends with the trash slot.
        view.creativeInventoryTab -> if (isLast) SlotLabel("trash", null) else SlotLabels.forPlayerMenuIndex(slot.containerSlot)
        slot.container === view.inventory -> SlotLabels.forInventoryIndex(slot.containerSlot)
        else -> SlotLabel(MenuSlotGroups.forMenuSlot(view.menuType, position), null)
    }

    private fun menuType(menu: AbstractContainerMenu): String {
        val type = runCatching { menu.type }.getOrNull()
        return type?.let { BuiltInRegistries.MENU.getKey(it)?.toString() } ?: menu.javaClass.simpleName
    }

    private fun tradeOffer(offer: MerchantOffer) = TradeOffer(
        buy = requireNotNull(stackInfo(offer.costA)),
        buyB = stackInfo(offer.costB),
        sell = requireNotNull(stackInfo(offer.result)),
        uses = offer.uses,
        maxUses = offer.maxUses,
        outOfStock = offer.isOutOfStock,
    )

    internal fun stackInfo(stack: ItemStack): ItemStackInfo? {
        if (stack.isEmpty) return null
        return ItemStackInfo(
            id = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
            name = stack.hoverName.string,
            count = stack.count,
            damage = stack.damageValue.takeIf { stack.isDamageableItem },
            maxDamage = stack.maxDamage.takeIf { stack.isDamageableItem },
            enchantments = enchantments(stack),
            lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty(),
        )
    }

    /** Enchantments on the item, plus those an enchanted book stores. */
    private fun enchantments(stack: ItemStack): List<String> {
        val applied = stack.get(DataComponents.ENCHANTMENTS)
        val stored = stack.get(DataComponents.STORED_ENCHANTMENTS)
        return listOfNotNull(applied, stored).flatMap { enchantments ->
            enchantments.entrySet().map { entry ->
                val id = entry.key.unwrapKey().map { it.location().toString() }.orElse("unknown")
                "$id ${entry.intValue}"
            }
        }
    }
}
