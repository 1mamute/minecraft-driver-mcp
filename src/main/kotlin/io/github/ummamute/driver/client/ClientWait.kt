package io.github.ummamute.driver.client

import io.github.ummamute.driver.tools.WaitCondition
import io.github.ummamute.driver.tools.WaitSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory

/** Reads only the values required by a wait, without serializing stacks or entity lists. Call on the render thread. */
object ClientWait {
    /** Returns the requested counts, health and widget from the same frame; player values are null without a world. */
    fun snapshot(condition: WaitCondition): WaitSnapshot {
        val minecraft = Minecraft.getInstance()
        val state = ClientState.snapshot()
        val widget = condition.widget?.let(ClientScreens::observe)
        val player = minecraft.player ?: return WaitSnapshot(state, widget = widget)
        val level = minecraft.level ?: return WaitSnapshot(state, widget = widget)
        return WaitSnapshot(
            state = state,
            widget = widget,
            inventoryCount = condition.inventory?.let { countItems(player.inventory, it.item) },
            entityCount = condition.entity?.let { predicate ->
                level.entitiesForRendering().count { entity ->
                    entity !== player &&
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() == predicate.type &&
                        player.distanceToSqr(entity.x, entity.y, entity.z) < predicate.radius * predicate.radius
                }
            },
            health = condition.health?.let { player.health.toDouble() },
        )
    }

    private fun countItems(inventory: Inventory, item: String): Int = (0 until inventory.containerSize).sumOf { index ->
        val stack = inventory.getItem(index)
        if (!stack.isEmpty && BuiltInRegistries.ITEM.getKey(stack.item).toString() == item) stack.count else 0
    }
}
