package io.github.ummamute.driver.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LookAtTargetTest {
    @Test
    fun `coordinate aiming preserves the requested world position`() {
        assertEquals(LookAtTarget.Position(x = -1.5, y = 72.0, z = 0.0), parse("""{"x":-1.5,"y":72,"z":0}"""))
    }

    @Test
    fun `entity aiming defaults to the eyes and allows the box center`() {
        assertEquals(LookAtTarget.Entity(42, EntityAimPoint.EYE), parse("""{"entity_id":42}"""))
        assertEquals(LookAtTarget.Entity(42, EntityAimPoint.EYE), parse("""{"entity_id":42,"aim_point":"eye"}"""))
        assertEquals(LookAtTarget.Entity(42, EntityAimPoint.CENTER), parse("""{"entity_id":42,"aim_point":"center"}"""))
    }

    @Test
    fun `null optional values are treated as omitted`() {
        assertEquals(LookAtTarget.Entity(42, EntityAimPoint.EYE), parse("""{"entity_id":42,"aim_point":null,"x":null}"""))
        assertEquals(
            LookAtTarget.Position(x = 1.0, y = 2.0, z = 3.0),
            parse("""{"entity_id":null,"aim_point":null,"x":1,"y":2,"z":3}"""),
        )
    }

    @Test
    fun `incomplete or ambiguous targets explain how to retry`() {
        val invalid = listOf(
            "{}", """{"x":1,"y":2}""", """{"entity_id":42,"x":0}""",
            """{"entity_id":42,"x":1,"y":2,"z":3}""", """{"x":1,"y":2,"z":3,"aim_point":"eye"}""",
            """{"aim_point":"center"}""", """{"entity_id":42,"aim_point":"feet"}""",
        )
        invalid.forEach { input -> assertFailsWith<IllegalArgumentException>(input) { parse(input) } }
    }

    @Test
    fun `entity IDs and aim points reject incorrect types`() {
        for (id in listOf("1.5", "\"42\"", "true", "[]", "2147483648")) {
            assertFailsWith<IllegalArgumentException> { parse("""{"entity_id":$id}""") }
        }
        assertFailsWith<IllegalArgumentException> { parse("""{"entity_id":42,"aim_point":1}""") }
    }

    @Test
    fun `coordinates must be finite numbers`() {
        for (value in listOf("1e999", "true", "[]", "\"NaN\"", "\"Infinity\"")) {
            assertFailsWith<IllegalArgumentException> { parse("""{"x":$value,"y":2,"z":3}""") }
        }
    }

    private fun parse(input: String): LookAtTarget = LookAtTarget.from(args(input))

    private fun args(input: String): JsonObject = Json.parseToJsonElement(input).jsonObject
}
