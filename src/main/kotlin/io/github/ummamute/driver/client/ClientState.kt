package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft

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
data class EntitySummary(val id: Int, val type: String, val name: String, val x: Double, val y: Double, val z: Double)

/** Reads client state. Call on the render thread. */
object ClientState {
    private const val ENTITY_RANGE = 64.0
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

    fun entitiesNearby(): List<EntitySummary> {
        val player = mc.player ?: error("Not in a world")
        val level = mc.level ?: error("Not in a world")
        return level.entitiesForRendering()
            .filter { it !== player && it.distanceTo(player) < ENTITY_RANGE }
            .map { EntitySummary(it.id, it.type.descriptionId, it.name.string, it.x, it.eyeY, it.z) }
    }
}
