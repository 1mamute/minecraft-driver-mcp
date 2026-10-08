package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
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
        val slots = menu.slots.map { slotInfo(it, player.inventory) }
        return ContainerSnapshot(
            menuType = menuType(menu),
            title = screen.title.string,
            containerId = menu.containerId,
            carried = stackInfo(menu.carried),
            slots = SlotLabels.filter(slots, includeEmpty),
            offers = (menu as? MerchantMenu)?.offers?.map(::tradeOffer),
        )
    }

    private fun slotInfo(slot: Slot, inventory: Inventory): SlotInfo {
        val item = stackInfo(slot.item)
        if (slot.container !== inventory) return SlotInfo("container", slot.index, null, item)
        val label = SlotLabels.forInventoryIndex(slot.containerSlot)
        return SlotInfo(label.group, slot.index, label.part, item)
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

    private fun stackInfo(stack: ItemStack): ItemStackInfo? {
        if (stack.isEmpty) return null
        return ItemStackInfo(
            id = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
            name = stack.hoverName.string,
            count = stack.count,
            damage = stack.damageValue.takeIf { stack.isDamageableItem },
            maxDamage = stack.maxDamage.takeIf { stack.isDamageableItem },
            enchantments = enchantments(stack),
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
