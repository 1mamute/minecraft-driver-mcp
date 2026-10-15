package io.github.ummamute.driver.catalogue

/** Shared argument values and limits used by both the definitions and the client handlers. */
object ToolContract {
    const val DEFAULT_LOG_LIMIT = 100
    const val MAX_LOG_LIMIT = 500
    const val DEFAULT_WAIT_MILLIS = 10_000L
    const val MAX_WAIT_MILLIS = 60_000L
    const val MAX_HOLD_TICKS = 200
    const val MAX_TYPED_CHARS = 1000
    const val MAX_PRESSES = 256

    const val PICK_UP = "pick_up"
    const val QUICK_MOVE = "quick_move"
    const val SWAP = "swap"
    const val THROW = "throw"
    const val CLONE = "clone"
    const val PICK_UP_ALL = "pick_up_all"

    val LOG_LEVELS: List<String> = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
    val SLOT_ACTIONS: List<String> = listOf(PICK_UP, QUICK_MOVE, SWAP, THROW, CLONE, PICK_UP_ALL)
    val MOVEMENT_KEYS: List<String> = listOf("forward", "back", "left", "right", "jump", "sneak", "inventory")
    val SCREEN_KEYS: List<String> = listOf(
        "enter", "escape", "tab", "backspace", "delete", "left", "right", "up", "down", "home", "end", "page_up", "page_down",
    )
}
