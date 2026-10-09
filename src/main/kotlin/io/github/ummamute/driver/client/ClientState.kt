package io.github.ummamute.driver.client

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
)

/** Reads client state. Call on the render thread. */
object ClientState {
    private const val ENTITY_RANGE = 64.0
    private const val ENTITY_LIMIT = 100
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

    /** Entities within [ENTITY_RANGE] blocks, nearest first, at most [ENTITY_LIMIT]. */
    fun entitiesNearby(): List<EntitySummary> {
        val player = mc.player ?: error("Not in a world")
        val level = mc.level ?: error("Not in a world")
        return level.entitiesForRendering()
            .filter { it !== player }
            .map { summarize(it, it.distanceTo(player).toDouble()) }
            .filter { it.distance < ENTITY_RANGE }
            .sortedBy { it.distance }
            .take(ENTITY_LIMIT)
    }

    private fun summarize(entity: Entity, distance: Double) = EntitySummary(
        id = entity.id,
        type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
        name = entity.name.string,
        x = entity.x,
        y = entity.y,
        z = entity.z,
        distance = distance,
    )
}
