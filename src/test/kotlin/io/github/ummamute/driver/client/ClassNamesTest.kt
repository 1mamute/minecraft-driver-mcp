package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassNamesTest {
    @Test
    fun `a vanilla widget uses the listed name`() {
        assertEquals("Button", ClassNames.widgetName("net.minecraft.class_4185", "Button"))
    }

    @Test
    fun `a vanilla widget outside the table is a component`() {
        assertEquals("Component", ClassNames.widgetName("net.minecraft.class_9999\$class_1", null))
    }

    @Test
    fun `a widget from another mod keeps its class name`() {
        assertEquals("com.example.FancyButton", ClassNames.widgetName("com.example.FancyButton", "Button"))
    }
}
