package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientStateSnapshot
import io.github.ummamute.driver.client.LoggedMessage
import io.github.ummamute.driver.client.MessagePage
import io.github.ummamute.driver.client.PlayerPosition
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionWaiterTest {
    private val messages = mutableListOf<LoggedMessage>()

    @Test
    fun `matches a message that is already buffered after since`() = runBlocking {
        messages += LoggedMessage(1, "old news")
        messages += LoggedMessage(2, "Set the time to 1000")

        val result = waiter().await(WaitCondition(messageContains = "set the TIME", sinceSeq = 1), timeoutMillis = 2000)

        assertTrue(result.matched)
        assertEquals("message", result.reason)
        assertEquals(2L, result.message?.seq)
        assertEquals(2L, result.latest)
    }

    @Test
    fun `ignores messages at or before since`() = runBlocking {
        messages += LoggedMessage(1, "ready")

        val result = waiter().await(WaitCondition(messageContains = "ready", sinceSeq = 1), timeoutMillis = 40)

        assertFalse(result.matched)
        assertEquals("timeout", result.reason)
    }

    @Test
    fun `matches a screen class name and reports the state`() = runBlocking {
        val screen = "net.minecraft.client.gui.screens.PauseScreen"
        val result = waiter(snapshot(screen = screen)).await(WaitCondition(screenContains = "pausescreen"), timeoutMillis = 2000)

        assertTrue(result.matched)
        assertEquals("screen", result.reason)
        assertEquals(screen, result.state?.screen)
    }

    @Test
    fun `waits for the screen to close when asked for none`() = runBlocking {
        val result = waiter(snapshot(screen = "PauseScreen"), snapshot()).await(
            WaitCondition(screenContains = WaitCondition.NO_SCREEN), timeoutMillis = 2000,
        )

        assertTrue(result.matched)
        assertNull(result.state?.screen)
    }

    @Test
    fun `message success never waits for a render snapshot`() = runBlocking {
        messages += LoggedMessage(1, "ready")
        val waiter = ConditionWaiter(readSnapshot = { awaitCancellation() }, readMessages = ::readMessages)

        val result = withTimeout(2000) { waiter.await(WaitCondition(messageContains = "ready"), timeoutMillis = 500) }

        assertTrue(result.matched)
        assertEquals("message", result.reason)
        assertNull(result.state)
    }

    @Test
    fun `any matching message succeeds even when another predicate needs a stalled render thread`() = runBlocking {
        messages += LoggedMessage(1, "ready")
        val waiter = ConditionWaiter(readSnapshot = { awaitCancellation() }, readMessages = ::readMessages)

        val result = withTimeout(2000) {
            waiter.await(WaitCondition(messageContains = "ready", screenContains = "pause"), timeoutMillis = 500)
        }

        assertTrue(result.matched)
        assertEquals("message", result.reason)
        assertEquals(1L, result.message?.seq)
        assertNull(result.state)
        assertEquals(listOf(WaitObservation("message", true)), result.observations)
    }

    @Test
    fun `reads the state only once at timeout when waiting for a message alone`() = runBlocking<Unit> {
        var reads = 0
        val waiter = ConditionWaiter(
            readSnapshot = {
                reads++
                snapshot()
            },
            readMessages = ::readMessages,
            pollMillis = 1,
        )

        val result = waiter.await(WaitCondition(messageContains = "never"), timeoutMillis = 40)

        assertFalse(result.matched)
        assertEquals(1, reads)
        assertNotNull(result.state)
    }

    @Test
    fun `times out with the latest sequence number`() = runBlocking {
        messages += LoggedMessage(7, "unrelated")

        val result = waiter().await(WaitCondition(messageContains = "never"), timeoutMillis = 40)

        assertFalse(result.matched)
        assertEquals(7L, result.latest)
    }

    @Test
    fun `inventory and entity counts match inclusive bounds and absence`() = runBlocking {
        for (count in listOf(0, 2, 4)) {
            val min = if (count == 0) 0 else 2
            val max = if (count == 0) 0 else 4
            val result = waiter(snapshot(inventory = count, entities = count)).await(
                countCondition(min, max), timeoutMillis = 2000,
            )
            assertTrue(result.matched)
            assertEquals(
                listOf(WaitObservation("inventory", true, count.toDouble()), WaitObservation("entity", true, count.toDouble())),
                result.observations,
            )
        }
    }

    @Test
    fun `count predicates wait through values outside both bounds`() = runBlocking {
        val result = waiter(
            snapshot(inventory = 1, entities = 5), snapshot(inventory = 5, entities = 1), snapshot(inventory = 3, entities = 3),
        ).await(countCondition(2, 4), timeoutMillis = 2000)

        assertTrue(result.matched)
        assertEquals(listOf(WaitObservation("inventory", true, 3.0), WaitObservation("entity", true, 3.0)), result.observations)
    }

    @Test
    fun `health matches inclusive lower and upper bounds`() = runBlocking {
        for (health in listOf(4.0, 8.0)) {
            val result = waiter(snapshot(health = health)).await(WaitCondition(health = HealthPredicate(min = 4.0, max = 8.0)), timeoutMillis = 2000)
            assertTrue(result.matched)
            assertEquals(listOf(WaitObservation("health", true, health)), result.observations)
        }
        val result = waiter(snapshot(health = 9.0), snapshot(health = 3.0), snapshot(health = 6.0)).await(
            WaitCondition(health = HealthPredicate(min = 4.0, max = 8.0)), timeoutMillis = 2000,
        )
        assertEquals(listOf(WaitObservation("health", true, 6.0)), result.observations)
    }

    @Test
    fun `health supports a minimum alone and a maximum alone`() = runBlocking {
        val healed = waiter(snapshot(health = 19.0), snapshot(health = 20.0)).await(WaitCondition(health = HealthPredicate(min = 20.0)), 2000)
        val hurt = waiter(snapshot(health = 5.0), snapshot(health = 4.0)).await(WaitCondition(health = HealthPredicate(max = 4.0)), 2000)

        assertTrue(healed.matched)
        assertTrue(hurt.matched)
        assertEquals(20.0, healed.observations.single().value)
        assertEquals(4.0, hurt.observations.single().value)
    }

    @Test
    fun `all requires the predicates to hold in the same snapshot`() = runBlocking {
        val result = waiter(
            snapshot(inventory = 1, entities = 0), snapshot(inventory = 0, entities = 1), snapshot(inventory = 2, entities = 2),
        ).await(countCondition(1), timeoutMillis = 2000)

        assertTrue(result.matched)
        assertEquals("all", result.reason)
        assertEquals(listOf(WaitObservation("inventory", true, 2.0), WaitObservation("entity", true, 2.0)), result.observations)
    }

    @Test
    fun `buffered messages can join state predicates in all mode`() = runBlocking {
        messages += LoggedMessage(2, "ready")
        val result = waiter(snapshot(inventory = 0), snapshot(inventory = 1)).await(
            WaitCondition(messageContains = "READY", sinceSeq = 1, inventory = InventoryPredicate("minecraft:stone"), mode = WaitMode.ALL),
            timeoutMillis = 2000,
        )

        assertEquals("all", result.reason)
        assertEquals(2L, result.message?.seq)
        assertEquals(listOf(WaitObservation("message", true), WaitObservation("inventory", true, 1.0)), result.observations)
    }

    @Test
    fun `any reports the first matching predicate while observing every supplied predicate`() = runBlocking {
        val result = waiter(snapshot(screen = "PauseScreen", inventory = 1, entities = 1, health = 20.0)).await(
            WaitCondition(
                messageContains = "ready", screenContains = "pause", inventory = InventoryPredicate("minecraft:stone"),
                entity = EntityPredicate("minecraft:cow"), health = HealthPredicate(min = 20.0),
            ),
            timeoutMillis = 2000,
        )

        assertEquals("screen", result.reason)
        assertEquals(listOf("message", "screen", "inventory", "entity", "health"), result.observations.map { it.condition })
        assertFalse(result.observations.first().matched)
        assertTrue(result.observations.drop(1).all { it.matched })
    }

    @Test
    fun `any succeeds when only a later predicate matches`() = runBlocking {
        val result = waiter(snapshot(inventory = 0, health = 4.0)).await(
            WaitCondition(inventory = InventoryPredicate("minecraft:stone"), health = HealthPredicate(max = 4.0)), timeoutMillis = 2000,
        )

        assertTrue(result.matched)
        assertEquals("health", result.reason)
        assertEquals(listOf(WaitObservation("inventory", false, 0.0), WaitObservation("health", true, 4.0)), result.observations)
    }

    @Test
    fun `widget waits until a disabled button becomes active`() = runBlocking {
        val result = waiter(
            snapshot(screen = "DeathScreen", widget = respawn(active = false)),
            snapshot(screen = "DeathScreen", widget = respawn(active = true)),
        ).await(WaitCondition(widget = WidgetPredicate(label = "respawn")), timeoutMillis = 2000)

        assertTrue(result.matched)
        assertEquals("widget", result.reason)
        assertEquals(listOf(WaitObservation("widget", true, widget = respawn(active = true))), result.observations)
    }

    @Test
    fun `widget that stays disabled times out with its last flags and a widget next step`() = runBlocking {
        val result = waiter(snapshot(screen = "DeathScreen", widget = respawn(active = false), inWorld = false))
            .await(WaitCondition(widget = WidgetPredicate(path = "0")), timeoutMillis = 40)

        assertFalse(result.matched)
        assertEquals("timeout", result.reason)
        assertEquals(listOf(WaitObservation("widget", false, widget = respawn(active = false))), result.observations)
        assertTrue(result.nextStep.orEmpty().contains("mc_list_widgets"))
        assertFalse(result.nextStep.orEmpty().contains("No world"))
    }

    @Test
    fun `widget is unmet after its screen closes`() = runBlocking {
        val result = waiter(snapshot(screen = "DeathScreen", widget = respawn(active = false)), snapshot())
            .await(WaitCondition(widget = WidgetPredicate(label = "respawn")), timeoutMillis = 40)

        assertFalse(result.matched)
        assertEquals(listOf(WaitObservation("widget", false)), result.observations)
    }

    @Test
    fun `widget flags must both match and entries without flags never match`() = runBlocking {
        val hidden = WidgetObservation("0", "Respawn", active = true, visible = false)
        val noFlags = WidgetObservation("0", "Server list", active = null, visible = null)

        assertFalse(WidgetPredicate(label = "respawn").matches(hidden))
        assertTrue(WidgetPredicate(label = "respawn", visible = false).matches(hidden))
        assertFalse(WidgetPredicate(index = 0, active = false).matches(respawn(active = true)))
        assertTrue(WidgetPredicate(index = 0, active = false).matches(respawn(active = false)))
        assertFalse(WidgetPredicate(label = "server").matches(noFlags))
        assertFalse(WidgetPredicate(label = "respawn").matches(null))
    }

    private fun respawn(active: Boolean) = WidgetObservation("0", "Respawn", active = active, visible = true)

    private fun waiter(vararg snapshots: WaitSnapshot): ConditionWaiter {
        val values = snapshots.toList().ifEmpty { listOf(snapshot()) }
        var index = 0
        return ConditionWaiter(
            readSnapshot = { values[(index++).coerceAtMost(values.lastIndex)] }, readMessages = ::readMessages, pollMillis = 1,
        )
    }

    private fun readMessages(since: Long) = MessagePage(messages.filter { it.seq > since }, messages.lastOrNull()?.seq ?: 0L, truncated = false)

    private fun countCondition(min: Int, max: Int? = null) = WaitCondition(
        inventory = InventoryPredicate("minecraft:stone", min, max),
        entity = EntityPredicate("minecraft:cow", minCount = min, maxCount = max),
        mode = WaitMode.ALL,
    )

    private fun snapshot(
        screen: String? = null,
        inventory: Int? = null,
        entities: Int? = null,
        health: Double? = null,
        widget: WidgetObservation? = null,
        inWorld: Boolean = true,
    ) = WaitSnapshot(
        state = ClientStateSnapshot(
            screen = screen, connected = inWorld, windowFocused = true,
            player = if (inWorld) PlayerPosition(0.0, 64.0, 0.0, 0f, 0f) else null,
        ),
        inventoryCount = inventory,
        entityCount = entities,
        health = health,
        widget = widget,
    )
}
