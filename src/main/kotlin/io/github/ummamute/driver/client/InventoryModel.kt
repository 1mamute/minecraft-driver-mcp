package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable

/** An item stack. [damage] and [maxDamage] are set only for damageable items; [enchantments] read `<id> <level>`. */
@Serializable
data class ItemStackInfo(
    val id: String,
    val name: String,
    val count: Int,
    val damage: Int? = null,
    val maxDamage: Int? = null,
    val enchantments: List<String> = emptyList(),
)

/** One slot. [item] is null for an empty slot; [part] names the armor piece of an armor slot. */
@Serializable
data class SlotInfo(val group: String, val slot: Int, val part: String?, val item: ItemStackInfo?)

/** The player's own inventory. */
@Serializable
data class InventorySnapshot(val selectedHotbarSlot: Int, val carried: ItemStackInfo?, val slots: List<SlotInfo>)

/** One trade of a villager or wandering trader. */
@Serializable
data class TradeOffer(
    val buy: ItemStackInfo,
    val buyB: ItemStackInfo?,
    val sell: ItemStackInfo,
    val uses: Int,
    val maxUses: Int,
    val outOfStock: Boolean,
)

/** The open container screen. [offers] is set only for merchant menus and stays empty until the server sends the trades. */
@Serializable
data class ContainerSnapshot(
    val menuType: String,
    val title: String,
    val containerId: Int,
    val carried: ItemStackInfo?,
    val slots: List<SlotInfo>,
    val offers: List<TradeOffer>? = null,
)

/** Where a player inventory index belongs. Has no Minecraft types so it can be tested on its own. */
object SlotLabels {
    private const val HOTBAR_END = 9
    private const val MAIN_END = 36
    private const val ARMOR_END = 40
    private val armorParts = listOf("boots", "leggings", "chestplate", "helmet")

    /** Label of an index of `Inventory`: 0-8 hotbar, 9-35 main, 36-39 armor (boots first), 40 offhand. */
    fun forInventoryIndex(index: Int): SlotLabel = when {
        index < HOTBAR_END -> SlotLabel("hotbar", null)
        index < MAIN_END -> SlotLabel("main", null)
        index < ARMOR_END -> SlotLabel("armor", armorParts[index - MAIN_END])
        else -> SlotLabel("offhand", null)
    }

    /** Drops empty slots unless [includeEmpty] is set. */
    fun filter(slots: List<SlotInfo>, includeEmpty: Boolean): List<SlotInfo> = if (includeEmpty) slots else slots.filter { it.item != null }
}

/** Group and optional armor part of a slot. */
data class SlotLabel(val group: String, val part: String?)
