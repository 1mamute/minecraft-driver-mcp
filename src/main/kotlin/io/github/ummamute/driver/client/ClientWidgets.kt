package io.github.ummamute.driver.client

import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import java.util.Collections
import java.util.IdentityHashMap

internal data class WidgetTarget(
    val listener: GuiEventListener,
    val path: String,
    val ancestors: List<ContainerEventHandler>,
) {
    fun receivesPoint(x: Double, y: Double): Boolean {
        val route = ancestors.drop(1) + listener
        return ancestors.zip(route).all { (parent, child) -> parent.getChildAt(x, y).orElse(null) === child }
    }
}

/** Traverses the game's public event tree without inspecting mod-private fields. */
internal object ClientWidgets {
    private const val MAX_WIDGETS = 2048
    private const val MAX_DEPTH = 32

    fun targets(screen: Screen): List<WidgetTarget> {
        val roots = screen.children().mapIndexed { index, child -> WidgetTarget(child, index.toString(), listOf(screen)) }
        require(roots.size <= MAX_WIDGETS) { "The screen has too many widgets to inspect safely. Use explicit GUI coordinates" }
        val result = roots.toMutableList()
        val visited = Collections.newSetFromMap(IdentityHashMap<GuiEventListener, Boolean>())
        roots.forEach { visited.add(it.listener) }
        roots.forEach { appendChildren(it, result, visited) }
        return result
    }

    private fun appendChildren(target: WidgetTarget, result: MutableList<WidgetTarget>, visited: MutableSet<GuiEventListener>) {
        val container = target.listener as? ContainerEventHandler ?: return
        val ancestors = target.ancestors + container
        require(ancestors.size <= MAX_DEPTH) { "The widget tree is too deep to inspect safely. Use explicit GUI coordinates" }
        container.children().forEachIndexed { index, child ->
            if (visited.add(child)) {
                require(result.size < MAX_WIDGETS) { "The screen has too many widgets to inspect safely. Use explicit GUI coordinates" }
                val nested = WidgetTarget(child, "${target.path}/$index", ancestors)
                result.add(nested)
                appendChildren(nested, result, visited)
            }
        }
    }
}
