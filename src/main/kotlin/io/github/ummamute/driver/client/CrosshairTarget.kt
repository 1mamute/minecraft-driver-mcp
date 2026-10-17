package io.github.ummamute.driver.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The renderer's latest target alongside the current camera origin and player interaction ranges. */
@Serializable
data class CrosshairTargetSnapshot(
    val target: CrosshairTarget,
    /** Current camera eye position; the cached target may come from an earlier rendered frame. */
    val origin: TargetPosition,
    val blockReach: Double,
    val entityReach: Double,
)

/** A point in world coordinates, measured in blocks. */
@Serializable
data class TargetPosition(val x: Double, val y: Double, val z: Double)

/** The mutually exclusive outcomes of the client's crosshair pick, without any server confirmation. */
@Serializable
sealed interface CrosshairTarget {
    /** A selected block and the exact hit point on its outline. */
    @Serializable
    @SerialName("block")
    data class Block(val location: TargetPosition, val distance: Double, val block: TargetBlock) : CrosshairTarget

    /** A selected entity and the hit point on its pick bounds. */
    @Serializable
    @SerialName("entity")
    data class Entity(val location: TargetPosition, val distance: Double, val entity: TargetEntity) : CrosshairTarget

    /** No selected target. Location is the ray endpoint or a rejected hit, not a successful interaction. */
    @Serializable
    @SerialName("miss")
    data class Miss(val location: TargetPosition, val distance: Double) : CrosshairTarget
}

/** Block registry identifier, integer world position and lowercase hit face. */
@Serializable
data class TargetBlock(val id: String, val x: Int, val y: Int, val z: Int, val face: String)

/** Entity identifiers; [id] is the client network ID and [type] is the namespaced registry identifier. */
@Serializable
data class TargetEntity(val id: Int, val uuid: String, val type: String)
