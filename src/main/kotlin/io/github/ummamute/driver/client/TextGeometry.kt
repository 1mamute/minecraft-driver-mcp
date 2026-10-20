package io.github.ummamute.driver.client

import net.minecraft.client.gui.GuiGraphics
import org.joml.Matrix4f
import kotlin.math.abs

/** Converts measured font layout bounds through supported GUI transforms. Call on the render thread. */
object TextGeometry {
    /** Rotation, shear, reflection and perspective cannot provide a supported text click rectangle. */
    fun measure(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int): TextBounds? {
        val bounds = transform(graphics.pose().last().pose(), x, y, width, height) ?: return null
        val right = bounds.x + bounds.width
        val bottom = bounds.y + bounds.height
        val visible = graphics.containsPointInScissor(bounds.x.toInt(), bounds.y.toInt()) &&
            graphics.containsPointInScissor(kotlin.math.ceil(right).toInt() - 1, kotlin.math.ceil(bottom).toInt() - 1)
        return bounds.takeIf { visible }
    }

    /** Positive axis-aligned scale and translation preserve a rectangular text layout. */
    fun transform(matrix: Matrix4f, x: Int, y: Int, width: Int, height: Int): TextBounds? {
        if (!matrix.isFinite || width <= 0 || height <= 0) return null
        if (!isAxisAligned(matrix)) return null
        return TextBounds(
            x = matrix.m00().toDouble() * x + matrix.m30(),
            y = matrix.m11().toDouble() * y + matrix.m31(),
            width = matrix.m00().toDouble() * width,
            height = matrix.m11().toDouble() * height,
        )
    }

    private fun isAxisAligned(matrix: Matrix4f): Boolean {
        val mixed = listOf(matrix.m01(), matrix.m02(), matrix.m03(), matrix.m10(), matrix.m12(), matrix.m13(), matrix.m23())
        return matrix.m00() > 0 && matrix.m11() > 0 && mixed.all { abs(it) < EPSILON } && abs(matrix.m33() - 1) < EPSILON
    }

    private const val EPSILON = 0.00001f
}
