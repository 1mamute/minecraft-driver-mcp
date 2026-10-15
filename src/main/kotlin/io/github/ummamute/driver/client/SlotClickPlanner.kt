package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable

/** The ways to click a slot, named as the agent passes them. */
enum class SlotAction(val label: String) {
    PICK_UP("pick_up"),
    QUICK_MOVE("quick_move"),
    SWAP("swap"),
    THROW("throw"),
    CLONE("clone"),
    PICK_UP_ALL("pick_up_all"),
}

/** The raw tool arguments of a slot click. */
data class SlotClickArgs(
    val action: String? = null,
    val slot: Int? = null,
    val outside: Boolean = false,
    val button: Int? = null,
    val hotbar: Int? = null,
)

/** A validated click: [slot] is a menu index or [SlotClickPlanner.OUTSIDE], [button] is the protocol button for [action]. */
data class SlotClick(val action: SlotAction, val slot: Int, val button: Int)

/** Turns tool arguments into a [SlotClick]. Has no Minecraft types so it can be tested on its own. */
object SlotClickPlanner {
    /** The slot id the game uses for a click outside the window, which drops the cursor stack. */
    const val OUTSIDE = -999
    const val OFFHAND_HOTBAR = 40
    private const val HOTBAR_SIZE = 9
    private const val MIDDLE_BUTTON = 2

    val actionNames: List<String> = SlotAction.entries.map { it.label }

    /** Validates the arguments against a menu with [slotCount] slots; throws [IllegalArgumentException] with advice on bad input. */
    fun plan(args: SlotClickArgs, slotCount: Int): SlotClick {
        val kind = SlotAction.entries.firstOrNull { it.label == (args.action ?: SlotAction.PICK_UP.label) }
            ?: throw IllegalArgumentException("Unknown action \"${args.action}\". Use one of $actionNames")
        val target = target(kind, args.slot, args.outside, slotCount)
        return SlotClick(kind, target, buttonFor(kind, args.button, args.hotbar))
    }

    private fun target(kind: SlotAction, slot: Int?, outside: Boolean, slotCount: Int): Int {
        if (outside) {
            require(kind == SlotAction.PICK_UP) { "outside=true only works with action pick_up, which drops the cursor stack" }
            require(slot == null) { "Pass either slot or outside=true, not both" }
            return OUTSIDE
        }
        val index = slot ?: throw IllegalArgumentException("Pass slot (a menu index from mc_read_container) or outside=true")
        require(index in 0 until slotCount) { "Slot $index does not exist. Menu slots are 0 to ${slotCount - 1}; call mc_read_container" }
        return index
    }

    private fun buttonFor(kind: SlotAction, button: Int?, hotbar: Int?): Int = when (kind) {
        SlotAction.PICK_UP, SlotAction.THROW -> sideButton(button)
        SlotAction.SWAP -> swapButton(hotbar)
        SlotAction.CLONE -> MIDDLE_BUTTON
        SlotAction.QUICK_MOVE, SlotAction.PICK_UP_ALL -> 0
    }

    private fun sideButton(button: Int?): Int {
        val side = button ?: 0
        require(side == 0 || side == 1) { "button must be 0 (left, or one item for throw) or 1 (right, or the whole stack for throw)" }
        return side
    }

    private fun swapButton(hotbar: Int?): Int {
        val key = hotbar ?: throw IllegalArgumentException("action swap needs hotbar: 0-8, or $OFFHAND_HOTBAR for the offhand")
        require(key in 0 until HOTBAR_SIZE || key == OFFHAND_HOTBAR) { "hotbar must be 0-8, or $OFFHAND_HOTBAR for the offhand" }
        return key
    }
}

/** State after a click. [slotItem] is null for an empty slot or an outside click; [synced] is always false because the server answers later. */
@Serializable
data class SlotClickResult(
    val action: String,
    val slot: Int,
    val slotItem: ItemStackInfo?,
    val carried: ItemStackInfo?,
    val containerId: Int,
    val synced: Boolean,
    val note: String,
)
