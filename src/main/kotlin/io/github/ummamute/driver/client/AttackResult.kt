package io.github.ummamute.driver.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An attack attempt and the client observations made before releasing its input. */
@Serializable
data class AttackResult(
    val target: CrosshairTargetSnapshot,
    val requestedHoldTicks: Int,
    val elapsedTicks: Int,
    val outcome: AttackOutcome,
    /** Whether the original block state changed locally; null for non-block targets or a replaced world. */
    val blockChanged: Boolean?,
    /** Client prediction and sent attack packets never establish server acceptance. */
    val serverConfirmed: Boolean = false,
)

/** Why an attack released its input. None of these outcomes guarantees server-side damage or breaking. */
@Serializable
enum class AttackOutcome {
    @SerialName("pressed")
    PRESSED,

    @SerialName("hold_complete")
    HOLD_COMPLETE,

    @SerialName("block_changed")
    BLOCK_CHANGED,

    @SerialName("target_changed")
    TARGET_CHANGED,

    @SerialName("screen_opened")
    SCREEN_OPENED,

    @SerialName("disconnected")
    DISCONNECTED,

    @SerialName("hands_busy")
    HANDS_BUSY,

    @SerialName("input_released")
    INPUT_RELEASED,

    @SerialName("timed_out")
    TIMED_OUT,

    @SerialName("cancelled")
    CANCELLED,
}
