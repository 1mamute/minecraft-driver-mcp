package io.github.ummamute.driver.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitArgumentsTest {
    @Test
    fun `parses all predicates with inclusive bounds and an explicit cursor`() {
        val condition = parse(
            """{
                "message":"ready", "screen":"inventory", "since":7, "mode":"all",
                "inventory":{"item":"minecraft:stone","min_count":0,"max_count":3},
                "entity":{"type":"minecraft:cow","radius":256,"min_count":2,"max_count":4},
                "health":{"min":4,"max":20}
            }""",
        )

        assertEquals("ready", condition.messageContains)
        assertEquals("inventory", condition.screenContains)
        assertEquals(7L, condition.sinceSeq)
        assertEquals(WaitMode.ALL, condition.mode)
        assertEquals(InventoryPredicate("minecraft:stone", 0, 3), condition.inventory)
        assertEquals(EntityPredicate("minecraft:cow", radius = 256.0, minCount = 2, maxCount = 4), condition.entity)
        assertEquals(HealthPredicate(min = 4.0, max = 20.0), condition.health)
    }

    @Test
    fun `defaults to any latest cursor one item one entity and default radius`() {
        val condition = parse("""{"inventory":{"item":"minecraft:stone"},"entity":{"type":"minecraft:cow"}}""")

        assertEquals(WaitMode.ANY, condition.mode)
        assertEquals(9L, condition.sinceSeq)
        assertEquals(InventoryPredicate("minecraft:stone", minCount = 1), condition.inventory)
        assertEquals(EntityPredicate("minecraft:cow", radius = 64.0, minCount = 1), condition.entity)
    }

    @Test
    fun `top level nulls are absent and preserve defaults`() {
        val condition = parse(
            """{"message":"ready","screen":null,"since":null,"mode":null,"inventory":null,"entity":null,"health":null}""",
        )

        assertEquals("ready", condition.messageContains)
        assertEquals(9L, condition.sinceSeq)
        assertEquals(WaitMode.ANY, condition.mode)
        assertNull(condition.screenContains)
        assertNull(condition.inventory)
        assertNull(condition.entity)
        assertNull(condition.health)
    }

    @Test
    fun `nullable nested bounds are absent while other bounds still apply`() {
        val condition = parse(
            """{"inventory":{"item":"minecraft:stone","max_count":null},
                "entity":{"type":"minecraft:cow","max_count":null},"health":{"min":null,"max":4}}""",
        )

        assertNull(condition.inventory?.maxCount)
        assertNull(condition.entity?.maxCount)
        assertEquals(HealthPredicate(max = 4.0), condition.health)
    }

    @Test
    fun `requires at least one condition`() {
        for (input in listOf("{}", """{"message":null,"screen":null,"inventory":null,"entity":null,"health":null}""")) {
            assertFails(input) { parse(input) }
        }
    }

    @Test
    fun `mode accepts only any and all strings`() {
        assertEquals(WaitMode.ANY, parse("""{"screen":"none","mode":"any"}""").mode)
        assertEquals(WaitMode.ALL, parse("""{"screen":"none","mode":"all"}""").mode)
        for (mode in listOf("\"ALL\"", "\"\"", "\"either\"", "1", "true", "[]", "{}")) {
            assertFails(mode) { parse("""{"screen":"none","mode":$mode}""") }
        }
    }

    @Test
    fun `rejects wrong top level argument types`() {
        for (input in listOf(
            """{"message":1}""", """{"screen":true}""", """{"message":"ready","since":true}""",
            """{"inventory":[]}""", """{"inventory":"minecraft:stone"}""", """{"entity":3}""", """{"health":true}""",
        )) {
            assertFails(input) { parse(input) }
        }
    }

    @Test
    fun `preserves the existing numeric string message cursor`() {
        assertEquals(1L, parse("""{"message":"ready","since":"1"}""").sinceSeq)
    }

    @Test
    fun `rejects missing required IDs and unknown nested fields`() {
        for (input in listOf(
            """{"inventory":{}}""", """{"entity":{}}""",
            """{"inventory":{"item":"minecraft:stone","count":2}}""",
            """{"entity":{"type":"minecraft:cow","radius_blocks":2}}""",
            """{"health":{"min":1,"minimum":2}}""",
        )) {
            assertFails(input) { parse(input) }
        }
    }

    @Test
    fun `registry IDs must be full lowercase exact IDs`() {
        for (id in listOf("", "stone", ":stone", "minecraft:", "Minecraft:stone", "minecraft:Stone", "minecraft:*", "minecraft:stone extra")) {
            assertFails(id) { parse("""{"inventory":{"item":"$id"}}""") }
            assertFails(id) { parse("""{"entity":{"type":"$id"}}""") }
        }
        assertEquals("example-mod:group/stone_2", parse("""{"inventory":{"item":"example-mod:group/stone_2"}}""").inventory?.item)
    }

    @Test
    fun `rejects nonstring and null registry IDs`() {
        for (id in listOf("1", "true", "null", "[]", "{}")) {
            assertFails(id) { parse("""{"inventory":{"item":$id}}""") }
            assertFails(id) { parse("""{"entity":{"type":$id}}""") }
        }
    }

    @Test
    fun `count bounds must be nonnegative and ordered`() {
        for (bounds in listOf(""""min_count":-1""", """"max_count":-1""", """"min_count":3,"max_count":2""")) {
            assertFails(bounds) { parse("""{"inventory":{"item":"minecraft:stone",$bounds}}""") }
            assertFails(bounds) { parse("""{"entity":{"type":"minecraft:cow",$bounds}}""") }
        }
        val absent = parse("""{"inventory":{"item":"minecraft:stone","min_count":0,"max_count":0}}""")
        assertEquals(InventoryPredicate("minecraft:stone", 0, 0), absent.inventory)
    }

    @Test
    fun `count fields reject quoted numbers fractions booleans and structured values`() {
        for (value in listOf("\"2\"", "1.5", "true", "[]", "{}")) {
            for (field in listOf("min_count", "max_count")) {
                assertFails("$field=$value") { parse("""{"inventory":{"item":"minecraft:stone","$field":$value}}""") }
                assertFails("$field=$value") { parse("""{"entity":{"type":"minecraft:cow","$field":$value}}""") }
            }
        }
    }

    @Test
    fun `nonnullable defaults reject explicit null`() {
        for (input in listOf(
            """{"inventory":{"item":"minecraft:stone","min_count":null}}""",
            """{"entity":{"type":"minecraft:cow","min_count":null}}""",
            """{"entity":{"type":"minecraft:cow","radius":null}}""",
        )) {
            assertFails(input) { parse(input) }
        }
    }

    @Test
    fun `radius must be finite positive and at most 256 blocks`() {
        for (radius in listOf("0", "-1", "256.01", "1e309", "\"NaN\"", "\"Infinity\"")) {
            assertFails(radius) { parse("""{"entity":{"type":"minecraft:cow","radius":$radius}}""") }
        }
        assertEquals(0.5, parse("""{"entity":{"type":"minecraft:cow","radius":0.5}}""").entity?.radius)
    }

    @Test
    fun `health needs a finite nonnegative bound in ascending order`() {
        for (bounds in listOf(
            "", """"min":null,"max":null""", """"min":-1""", """"max":-1""",
            """"min":5,"max":4""", """"min":1e309""", """"max":"NaN""", """"min":"Infinity""",
        )) {
            assertFails(bounds) { parse("""{"health":{$bounds}}""") }
        }
        assertEquals(HealthPredicate(min = 0.0, max = 0.0), parse("""{"health":{"min":0,"max":0}}""").health)
    }

    @Test
    fun `health and radius reject quoted numbers booleans and structured values`() {
        for (value in listOf("\"2\"", "true", "[]", "{}")) {
            assertFails(value) { parse("""{"entity":{"type":"minecraft:cow","radius":$value}}""") }
            assertFails(value) { parse("""{"health":{"min":$value}}""") }
            assertFails(value) { parse("""{"health":{"max":$value}}""") }
        }
    }

    @Test
    fun `invalid predicates report the argument to correct`() {
        val problem = assertFails { parse("""{"inventory":{"item":"minecraft:stone","min_count":-1}}""") }

        assertTrue(problem.message.orEmpty().contains("inventory"))
        assertTrue(problem.message.orEmpty().contains("min_count"))
    }

    private fun parse(input: String): WaitCondition {
        val args: JsonObject = Json.parseToJsonElement(input).jsonObject
        return WaitArguments.condition(args, latest = 9L)
    }
}
