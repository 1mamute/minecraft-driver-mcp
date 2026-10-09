package io.github.ummamute.driver.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ToolNamesTest {
    @Test
    fun `accepts a mod id prefix followed by verb and noun`() {
        assertNull(ToolNames.problem("mymod", "mymod_read_score"))
    }

    @Test
    fun `turns dashes in the mod id into underscores`() {
        assertEquals("my_mod", ToolNames.prefixFor("my-mod"))
        assertNull(ToolNames.problem("my-mod", "my_mod_read_score"))
    }

    @Test
    fun `rejects a name without the mod id prefix`() {
        assertNotNull(ToolNames.problem("mymod", "othermod_read_score"))
        assertNotNull(ToolNames.problem("mymod", "read_score"))
    }

    @Test
    fun `rejects the built-in mc prefix and a mod called mc`() {
        assertNotNull(ToolNames.problem("mymod", "mc_read_score"))
        assertNotNull(ToolNames.problem("mc", "mc_read_score"))
    }

    @Test
    fun `rejects a name that is missing the noun`() {
        assertNotNull(ToolNames.problem("mymod", "mymod_score"))
        assertNotNull(ToolNames.problem("mymod", "mymod_"))
    }

    @Test
    fun `rejects names that are not snake case`() {
        listOf("mymod_Read_score", "mymod-read-score", "mymod__read_score", "mymod_read_score_", "mymod_read score").forEach { name ->
            assertNotNull(ToolNames.problem("mymod", name), name)
        }
    }

    @Test
    fun `rejects a name longer than 64 characters`() {
        assertNotNull(ToolNames.problem("mymod", "mymod_read_" + "a".repeat(60)))
    }
}
