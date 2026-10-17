package io.github.ummamute.driver.client

import io.github.ummamute.driver.tools.EntityQuery
import io.github.ummamute.driver.tools.EntityQueryResult
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity

/** What the client is showing and doing right now. */
@Serializable
data class ClientStateSnapshot(
    val screen: String?,
    val connected: Boolean,
    val windowFocused: Boolean,
    val player: PlayerPosition?,
)

@Serializable
data class PlayerPosition(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

/** A nearby entity. */
@Serializable
data class EntitySummary(
    val id: Int,
    /** Registry id, such as `minecraft:zombie`. */
    val type: String,
    val name: String,
    /** Feet position, the same point as the player's position in `mc_get_state`. */
    val x: Double,
    val y: Double,
    val z: Double,
    /** Distance in blocks from the player's feet. */
    val distance: Double,
    /** Current eye position in world coordinates, including pose and size effects. */
    val eyePosition: TargetPosition,
    /** Current collision bounds in world coordinates, not rendered model extents or inflated pick bounds. */
    val boundingBox: EntityBounds,
)

/** Axis-aligned entity bounds in world coordinates, measured in blocks. */
@Serializable
data class EntityBounds(val minX: Double, val minY: Double, val minZ: Double, val maxX: Double, val maxY: Double, val maxZ: Double)

/** Reads client state. Call on the render thread. */
object ClientState {
    private val mc: Minecraft get() = Minecraft.getInstance()

    fun snapshot(): ClientStateSnapshot {
        val player = mc.player
        return ClientStateSnapshot(
            screen = mc.screen?.let(ClassNames::of),
            connected = mc.connection != null,
            windowFocused = mc.isWindowActive,
            player = player?.let { PlayerPosition(it.x, it.y, it.z, it.yRot, it.xRot) },
        )
    }

    /** Client-visible entities matching [query], nearest first, excluding the local player. */
    fun entitiesNearby(query: EntityQuery): EntityQueryResult {
        val player = mc.player ?: error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")
        val level = mc.level ?: error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")
        val entities = level.entitiesForRendering().asSequence()
            .filter { it !== player }
            .map { summarize(it, it.distanceTo(player).toDouble()) }
        return query.select(entities)
    }

    private fun summarize(entity: Entity, distance: Double) = EntitySummary(
        id = entity.id,
        type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
        name = entity.name.string,
        x = entity.x,
        y = entity.y,
        z = entity.z,
        distance = distance,
        eyePosition = entity.eyePosition.let { TargetPosition(x = it.x, y = it.y, z = it.z) },
        boundingBox = entity.boundingBox.let {
            EntityBounds(minX = it.minX, minY = it.minY, minZ = it.minZ, maxX = it.maxX, maxY = it.maxY, maxZ = it.maxZ)
        },
    )
}
