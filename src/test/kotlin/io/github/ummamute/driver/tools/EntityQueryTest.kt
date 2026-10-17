package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.EntitySummary
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntityQueryTest {
    @Test
    fun `filters before limiting and returns matching entities nearest first`() {
        val entities = sequenceOf(
            entity(1, "minecraft:cow", "Decoy", 1.0),
            entity(2, "cobblemon:pokemon", "Rotom Drone", 12.0),
            entity(3, "cobblemon:pokemon", "ROTOM [Scout]", 8.0),
            entity(4, "cobblemon:pokemon", "Pikachu", 2.0),
        )

        val result = EntityQuery(limit = 1, typePrefix = "cobblemon:", nameContains = "rotom").select(entities)

        assertEquals(listOf(3), result.entities.map(EntitySummary::id))
        assertEquals(EntityQueryMetadata(total = 2, truncated = true, radius = 64.0, limit = 1), result.metadata)
    }

    @Test
    fun `type matching is case sensitive and exact while prefixes are literal`() {
        val entities = listOf(
            entity(1, "minecraft:zombie", "Zombie", 1.0),
            entity(2, "minecraft:zombie_villager", "Zombie Villager", 2.0),
        )
        assertEquals(listOf(1), EntityQuery(type = "minecraft:zombie").select(entities.asSequence()).entities.map(EntitySummary::id))
        assertEquals(listOf(1, 2), EntityQuery(typePrefix = "minecraft:zombie").select(entities.asSequence()).entities.map(EntitySummary::id))
        for (type in listOf("zombie", "Minecraft:zombie", "minecraft:zomb", "minecraft:*", "unknown:entity")) {
            assertTrue(EntityQuery(type = type).select(entities.asSequence()).entities.isEmpty())
        }
        assertTrue(EntityQuery(typePrefix = "Minecraft:").select(entities.asSequence()).entities.isEmpty())
        assertTrue(EntityQuery(type = "minecraft:zombie", typePrefix = "cobblemon:").select(entities.asSequence()).entities.isEmpty())
    }

    @Test
    fun `names use case insensitive literal substrings and empty strings impose no restriction`() {
        val entities = sequenceOf(entity(1, "minecraft:armor_stand", "Rotom [Scout]", 1.0))
        assertEquals(1, EntityQuery(nameContains = "[sCoUt]").select(entities).entities.size)
        assertTrue(EntityQuery(nameContains = "Rotom.*").select(entities).entities.isEmpty())
        assertEquals(1, EntityQuery(typePrefix = "", nameContains = "").select(entities).entities.size)
        assertTrue(EntityQuery(type = "").select(entities).entities.isEmpty())
    }

    @Test
    fun `radius excludes the boundary and truncation counts only matches`() {
        val entities = sequenceOf(
            entity(1, "minecraft:cow", "Cow", 63.0),
            entity(2, "minecraft:cow", "Cow", 64.0),
            entity(3, "minecraft:cow", "Cow", 65.0),
        )
        val defaults = EntityQuery().select(entities)
        assertEquals(listOf(1), defaults.entities.map(EntitySummary::id))
        assertEquals(1, defaults.metadata.total)
        assertFalse(defaults.metadata.truncated)
        assertEquals(3, EntityQuery(radius = 256.0, limit = 1000).select(entities).entities.size)
        val empty = EntityQuery(radius = 1.0).select(entities)
        assertEquals(0, empty.metadata.total)
        assertFalse(empty.metadata.truncated)
    }

    @Test
    fun `defaults retain the first hundred nearest entities and report overflow`() {
        val entities = (101 downTo 1).map { entity(it, "minecraft:cow", "Cow", it / 10.0) }.asSequence()
        val result = EntityQuery().select(entities)
        assertEquals((1..100).toList(), result.entities.map(EntitySummary::id))
        assertEquals(EntityQueryMetadata(total = 101, truncated = true, radius = 64.0, limit = 100), result.metadata)
    }

    @Test
    fun `invalid query bounds fail with actionable argument errors`() {
        for (radius in listOf(0.0, -1.0, 256.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(assertFailsWith<IllegalArgumentException> { EntityQuery(radius = radius) }.message.orEmpty().contains("radius"))
        }
        for (limit in listOf(-1, 0, 1001, Int.MAX_VALUE)) {
            assertTrue(assertFailsWith<IllegalArgumentException> { EntityQuery(limit = limit) }.message.orEmpty().contains("limit"))
        }
    }

    @Test
    fun `arguments accept omitted and null defaults but reject wrong types`() {
        assertEquals(EntityQuery(), EntityQuery.from(JsonObject(emptyMap())))
        assertEquals(EntityQuery(), EntityQuery.from(args("""{"radius":null,"limit":null,"type":null,"type_prefix":null,"name_contains":null}""")))
        assertEquals(
            EntityQuery(radius = 12.5, limit = 2, type = "minecraft:zombie", typePrefix = "minecraft:", nameContains = "Bob"),
            EntityQuery.from(args("""{"radius":12.5,"limit":2,"type":"minecraft:zombie","type_prefix":"minecraft:","name_contains":"Bob"}""")),
        )
        for (input in listOf("""{"radius":"64"}""", """{"radius":true}""", """{"radius":[]}""", """{"limit":1.5}""", """{"limit":"2"}""")) {
            assertFailsWith<IllegalArgumentException> { EntityQuery.from(args(input)) }
        }
        for (name in listOf("type", "type_prefix", "name_contains")) {
            assertFailsWith<IllegalArgumentException> { EntityQuery.from(args("""{"$name":123}""")) }
        }
    }

    @Test
    fun `response keeps the original entity array first and appends matching metadata`() {
        val entity = entity(7, "minecraft:cow", "Cow", 2.0)
        val queryResult = EntityQuery().select(sequenceOf(entity))
        val result = queryResult.toToolResult()

        assertEquals(2, result.content.size)
        val array = assertIs<TextContent>(result.content[0]).text
        val metadata = assertIs<TextContent>(result.content[1]).text
        assertEquals(listOf(entity), ToolSupport.json.decodeFromString<List<EntitySummary>>(array))
        assertEquals(queryResult.metadata, ToolSupport.json.decodeFromString<EntityQueryMetadata>(metadata))
    }

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun entity(id: Int, type: String, name: String, distance: Double) =
        EntitySummary(id = id, type = type, name = name, x = distance, y = 0.0, z = 0.0, distance = distance)
}
