package io.github.ummamute.driver.client

import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextGeometryTest {
    @Test
    fun `translated and scaled text has measured GUI bounds`() {
        val matrix = Matrix4f().translate(100f, 50f, 0f).scale(2f, 3f, 1f)

        assertEquals(
            TextBounds(x = 120.0, y = 110.0, width = 60.0, height = 27.0),
            TextGeometry.transform(matrix, x = 10, y = 20, width = 30, height = 9),
        )
    }

    @Test
    fun `rotation reflection shear and invalid dimensions have no supported bounds`() {
        val unsupported = listOf(
            Matrix4f().rotateZ(0.5f),
            Matrix4f().scale(-1f, 1f, 1f),
            Matrix4f().m10(0.5f),
            Matrix4f().m03(0.5f),
            Matrix4f().m00(Float.NaN),
        )
        for (matrix in unsupported) assertNull(TextGeometry.transform(matrix, x = 1, y = 2, width = 30, height = 9))
        assertNull(TextGeometry.transform(Matrix4f(), x = 1, y = 2, width = 0, height = 9))
    }
}
