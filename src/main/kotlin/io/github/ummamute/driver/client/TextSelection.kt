package io.github.ummamute.driver.client

/** A single-use selection from a caller's captured frame, independent of Minecraft. */
class TextSelection(
    val frameId: Long,
    val revision: Long,
    private val texts: List<DrawnText>,
    private val capturedNanos: Long,
) {
    /** Resolves exact, case-sensitive text; an index is required when the label occurs more than once. */
    fun resolve(frameId: Long, revision: Long, text: String, textIndex: Int?, nowNanos: Long): DrawnText {
        require(this.frameId == frameId && this.revision == revision && nowNanos - capturedNanos <= MAX_AGE_NANOS) {
            "The captured frame is stale. Call mc_read_screen_text and select text from its new frameId"
        }
        require(text.isNotBlank()) { "Give nonblank exact text from mc_read_screen_text" }
        val matches = texts.withIndex().filter { it.value.text == text }
        require(matches.isNotEmpty()) { "No captured text matches \"$text\". Call mc_read_screen_text for current text" }
        val selected = if (textIndex != null) {
            matches.firstOrNull { it.index == textIndex }
                ?: error("text_index does not match this text. Use its zero-based index in mc_read_screen_text texts")
        } else {
            require(matches.size == 1) {
                "Text is ambiguous at indexes ${matches.map { it.index }}. Give text_index from mc_read_screen_text"
            }
            matches.single()
        }
        require(selected.value.bounds != null) {
            "Text bounds are unsupported or clipped. Inspect mc_screenshot and use mc_click with GUI coordinates"
        }
        return selected.value
    }

    private companion object {
        const val MAX_AGE_NANOS = 5_000_000_000L
    }
}
