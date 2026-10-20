package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable

/** Result of dispatching a point click at measured text; handling does not prove the intended action occurred. */
@Serializable
data class TextClickResult(
    val text: String,
    val x: Double,
    val y: Double,
    val handled: Boolean,
    val warning: String = "Text bounds do not prove a clickable area. Re-read the screen to verify the intended action",
)
