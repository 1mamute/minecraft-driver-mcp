package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Reads the same cached crosshair target used by client interactions. Call on the render thread. */
object ClientTarget {
    /** Reads the latest rendered pick without running a separate raycast or changing the client's target. */
    fun snapshot(): CrosshairTargetSnapshot {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: error("Not in a world. Join one with mc_join_world or mc_join_server, then call mc_get_target again")
        val level = mc.level ?: error("No client level is available. Wait for the world to load, then call mc_get_target again")
        val camera = mc.cameraEntity ?: error("No camera is available. Wait for a rendered frame, then call mc_get_target again")
        val hit = mc.hitResult ?: error("No crosshair target is available yet. Wait for a rendered frame, then call mc_get_target again")
        val origin = camera.eyePosition
        val location = position(hit.location)
        val distance = origin.distanceTo(hit.location)
        val target = when (hit.type) {
            HitResult.Type.BLOCK -> {
                val blockHit = hit as BlockHitResult
                val pos = blockHit.blockPos
                val block = TargetBlock(
                    id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).toString(),
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    face = blockHit.direction.getName(),
                )
                CrosshairTarget.Block(location = location, distance = distance, block = block)
            }
            HitResult.Type.ENTITY -> {
                val entity = (hit as EntityHitResult).entity
                val summary = TargetEntity(
                    id = entity.id,
                    uuid = entity.uuid.toString(),
                    type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
                )
                CrosshairTarget.Entity(location = location, distance = distance, entity = summary)
            }
            HitResult.Type.MISS -> CrosshairTarget.Miss(location = location, distance = distance)
        }
        return CrosshairTargetSnapshot(
            target = target,
            origin = position(origin),
            blockReach = player.blockInteractionRange(),
            entityReach = player.entityInteractionRange(),
        )
    }

    private fun position(vector: Vec3) = TargetPosition(x = vector.x, y = vector.y, z = vector.z)
}
