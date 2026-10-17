package io.github.ummamute.driver.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One position in the current client dimension; an unloaded chunk has no [block] observation. */
@Serializable
data class BlockSnapshot(
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String,
    val loaded: Boolean,
    val block: BlockSummary?,
)

/** Client-visible registry identifier, state properties and optional block entity. */
@Serializable
data class BlockSummary(val id: String, val properties: Map<String, String>, val blockEntity: BlockEntitySummary?)

/** A client block entity's update tag, never its full saved or server-side NBT. */
@Serializable
data class BlockEntitySummary(
    val type: String?,
    val status: BlockEntityDataStatus,
    /** SNBT capped at 16,384 characters; null when unavailable or too large, and "{}" for an empty update tag. */
    val updateSnbt: String?,
)

/** Why client update-tag data is present or omitted; none of these states imply complete server data. */
@Serializable
enum class BlockEntityDataStatus {
    @SerialName("available")
    AVAILABLE,

    @SerialName("empty_update_tag")
    EMPTY_UPDATE_TAG,

    @SerialName("not_available")
    NOT_AVAILABLE,

    @SerialName("too_large")
    TOO_LARGE,
}
