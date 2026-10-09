package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable

/** An item stack. [damage] and [maxDamage] are set only for damageable items; [enchantments] read `<id> <level>`; [lore] holds the lore lines. */
@Serializable
data class ItemStackInfo(
    val id: String,
    val name: String,
    val count: Int,
    val damage: Int? = null,
    val maxDamage: Int? = null,
    val enchantments: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
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
    private const val PLAYER_MENU_GRID_START = 1
    private const val PLAYER_MENU_ARMOR_START = 5
    private const val PLAYER_MENU_MAIN_START = 9
    private const val PLAYER_MENU_HOTBAR_START = 36
    private const val PLAYER_MENU_OFFHAND = 45
    private val armorParts = listOf("boots", "leggings", "chestplate", "helmet")

    /** Label of an index of `Inventory`: 0-8 hotbar, 9-35 main, 36-39 armor (boots first), 40 offhand. */
    fun forInventoryIndex(index: Int): SlotLabel = when {
        index < HOTBAR_END -> SlotLabel("hotbar", null)
        index < MAIN_END -> SlotLabel("main", null)
        index < ARMOR_END -> SlotLabel("armor", armorParts[index - MAIN_END])
        else -> SlotLabel("offhand", null)
    }

    /**
     * Label of a slot of the player's own menu (`InventoryMenu`): 0 crafting result, 1-4 crafting grid, 5-8 armor (helmet first),
     * 9-35 main, 36-44 hotbar, 45 offhand. The creative inventory tab wraps these slots, so it reports them by this index.
     */
    fun forPlayerMenuIndex(index: Int): SlotLabel = when {
        index < PLAYER_MENU_GRID_START -> SlotLabel("result", null)
        index < PLAYER_MENU_ARMOR_START -> SlotLabel("grid", null)
        index < PLAYER_MENU_MAIN_START -> SlotLabel("armor", armorParts[PLAYER_MENU_MAIN_START - 1 - index])
        index < PLAYER_MENU_HOTBAR_START -> SlotLabel("main", null)
        index < PLAYER_MENU_OFFHAND -> SlotLabel("hotbar", null)
        else -> SlotLabel("offhand", null)
    }

    /** Drops empty slots unless [includeEmpty] is set. */
    fun filter(slots: List<SlotInfo>, includeEmpty: Boolean): List<SlotInfo> = if (includeEmpty) slots else slots.filter { it.item != null }
}

/** Names the slots of a menu that are not the player's own, so a furnace reads `input`, `fuel`, `output` instead of one block of `container`. */
object MenuSlotGroups {
    /** Group of unlabelled menu slots; chests, hoppers and menus this object does not know. */
    const val DEFAULT = "container"

    /** Group of every non-player slot of the creative inventory screen (`ItemPickerMenu`): the item grid, and the trash slot. */
    const val CREATIVE = "creative"

    private const val CRAFTING_GRID = 9
    private const val PLAYER_GRID = 4
    private val furnace = listOf("input", "fuel", "output")
    private val anvil = listOf("input", "input", "output")
    private val layouts: Map<String, List<String>> = mapOf(
        "minecraft:furnace" to furnace,
        "minecraft:blast_furnace" to furnace,
        "minecraft:smoker" to furnace,
        "minecraft:brewing_stand" to listOf("bottle", "bottle", "bottle", "ingredient", "fuel"),
        "minecraft:crafting" to listOf("result") + List(CRAFTING_GRID) { "grid" },
        "InventoryMenu" to listOf("result") + List(PLAYER_GRID) { "grid" },
        "minecraft:anvil" to anvil,
        "minecraft:grindstone" to anvil,
        "minecraft:enchantment" to listOf("item", "lapis"),
        "minecraft:smithing" to listOf("template", "base", "addition", "output"),
        "minecraft:stonecutter" to listOf("input", "output"),
    )

    /** Group of the slot at [menuIndex] in the menu named [menuType] (a registry id, or the class name when it has none). */
    fun forMenuSlot(menuType: String, menuIndex: Int): String {
        if (menuType == "ItemPickerMenu") return CREATIVE
        return layouts[menuType]?.getOrNull(menuIndex) ?: DEFAULT
    }
}

/** Group and optional armor part of a slot. */
data class SlotLabel(val group: String, val part: String?)
