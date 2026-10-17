package io.github.ummamute.driver.tools

import io.github.ummamute.driver.tools.ToolSupport.double
import io.github.ummamute.driver.tools.ToolSupport.int
import io.github.ummamute.driver.tools.ToolSupport.string
import kotlinx.serialization.json.JsonObject

/** A complete coordinate target or an entity to resolve when aiming runs. */
internal sealed interface LookAtTarget {
    data class Position(val x: Double, val y: Double, val z: Double) : LookAtTarget

    data class Entity(val id: Int, val aimPoint: EntityAimPoint) : LookAtTarget

    companion object {
        fun from(args: JsonObject): LookAtTarget {
            val id = args.int("entity_id")
            val aimPoint = args.string("aim_point")
            val x = args.double("x")
            val y = args.double("y")
            val z = args.double("z")
            if (id != null) {
                require(x == null && y == null && z == null) {
                    "Choose entity_id or x, y, z for mc_look_at; do not combine them"
                }
                val point = when (aimPoint) {
                    null, "eye" -> EntityAimPoint.EYE
                    "center" -> EntityAimPoint.CENTER
                    else -> throw IllegalArgumentException("Argument \"aim_point\" must be eye or center. Pass entity_id and a supported aim_point")
                }
                return Entity(id, point)
            }
            require(aimPoint == null) { "Argument \"aim_point\" needs entity_id. Pass an ID from mc_list_entities" }
            require(x != null && y != null && z != null) { "Pass all of x, y, z or an entity_id from mc_list_entities to mc_look_at" }
            require(x.isFinite() && y.isFinite() && z.isFinite()) { "Pass finite x, y, z coordinates to mc_look_at" }
            return Position(x = x, y = y, z = z)
        }
    }
}

/** Current entity geometry used as the aim point. */
internal enum class EntityAimPoint { EYE, CENTER }
