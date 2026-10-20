package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientStateSnapshot
import io.github.ummamute.driver.client.LoggedMessage
import io.github.ummamute.driver.client.MessagePage
import io.github.ummamute.driver.client.PlayerPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionWaiterLifecycleTest {
    private val messages = mutableListOf<LoggedMessage>()

    @Test
    fun `no world never satisfies zero count or zero health predicates`() = runBlocking {
        val result = waiter(snapshot(inWorld = false)).await(
            WaitCondition(
                inventory = InventoryPredicate("minecraft:stone", 0, 0),
                entity = EntityPredicate("minecraft:cow", minCount = 0, maxCount = 0), health = HealthPredicate(max = 0.0),
            ),
            timeoutMillis = 40,
        )

        assertFalse(result.matched)
        assertEquals(listOf(WaitObservation("inventory", false), WaitObservation("entity", false), WaitObservation("health", false)), result.observations)
        assertTrue(assertNotNull(result.nextStep).contains("mc_join_world"))
    }

    @Test
    fun `disconnect clears earlier numeric observations`() = runBlocking {
        val result = waiter(snapshot(inventory = 1, entities = 1, health = 20.0), snapshot(inWorld = false)).await(
            numericCondition(), timeoutMillis = 80,
        )

        assertFalse(result.matched)
        assertEquals(listOf(WaitObservation("inventory", false), WaitObservation("entity", false), WaitObservation("health", false)), result.observations)
        assertNull(result.state?.player)
    }

    @Test
    fun `joining a world allows pending numeric predicates to match`() = runBlocking<Unit> {
        val result = waiter(snapshot(inWorld = false), snapshot(inventory = 4, entities = 3, health = 4.0)).await(
            numericCondition().copy(mode = WaitMode.ALL), timeoutMillis = 2000,
        )

        assertTrue(result.matched)
        assertNotNull(result.state?.player)
    }

    @Test
    fun `timeout includes current values state latest and an actionable next step`() = runBlocking {
        messages += LoggedMessage(7, "unrelated")
        val last = snapshot(inventory = 3, entities = 2, health = 6.0)
        val result = waiter(last).await(numericCondition(), timeoutMillis = 40)

        assertFalse(result.matched)
        assertEquals("timeout", result.reason)
        assertEquals(7L, result.latest)
        assertEquals(last.state, result.state)
        assertEquals(
            listOf(WaitObservation("inventory", false, 3.0), WaitObservation("entity", false, 2.0), WaitObservation("health", false, 6.0)),
            result.observations,
        )
        assertTrue(assertNotNull(result.nextStep).contains("mc_read_inventory"))
    }

    @Test
    fun `timeout retains the last completed snapshot when a later read hangs`() = runBlocking {
        val last = snapshot(inventory = 3)
        var reads = 0
        val waiter = ConditionWaiter(
            readSnapshot = { if (reads++ == 0) last else awaitCancellation() }, readMessages = ::readMessages, pollMillis = 1,
        )

        val result = withTimeout(2000) { waiter.await(WaitCondition(inventory = InventoryPredicate("minecraft:stone", 4)), timeoutMillis = 80) }

        assertFalse(result.matched)
        assertEquals(last.state, result.state)
        assertEquals(listOf(WaitObservation("inventory", false, 3.0)), result.observations)
    }

    @Test
    fun `a hung first render read still returns a bounded timeout`() = runBlocking {
        val waiter = ConditionWaiter(readSnapshot = { awaitCancellation() }, readMessages = ::readMessages)
        val result = withTimeout(2000) { waiter.await(WaitCondition(inventory = InventoryPredicate("minecraft:stone")), timeoutMillis = 40) }

        assertFalse(result.matched)
        assertNull(result.state)
        assertEquals(listOf(WaitObservation("inventory", false)), result.observations)
        assertTrue(assertNotNull(result.nextStep).contains("mc_get_state"))
    }

    @Test
    fun `message timeout bounds its final render snapshot`() = runBlocking {
        val waiter = ConditionWaiter(readSnapshot = { awaitCancellation() }, readMessages = ::readMessages)
        val result = withTimeout(2000) { waiter.await(WaitCondition(messageContains = "never"), timeoutMillis = 40) }

        assertFalse(result.matched)
        assertNull(result.state)
        assertTrue(assertNotNull(result.nextStep).contains("mc_get_state"))
    }

    @Test
    fun `parent cancellation propagates during polling`() = runBlocking<Unit> {
        val polled = CompletableDeferred<Unit>()
        val waiter = ConditionWaiter(
            readSnapshot = { snapshot() },
            readMessages = {
                polled.complete(Unit)
                readMessages(it)
            },
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) { waiter.await(WaitCondition(messageContains = "never"), timeoutMillis = 10000) }
        polled.await()

        pending.cancel()

        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun `parent cancellation propagates during a render read`() = runBlocking<Unit> {
        val reading = CompletableDeferred<Unit>()
        val waiter = ConditionWaiter(
            readSnapshot = {
                reading.complete(Unit)
                awaitCancellation()
            },
            readMessages = ::readMessages,
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) { waiter.await(WaitCondition(screenContains = "pause"), timeoutMillis = 10000) }
        reading.await()

        pending.cancel()

        assertFailsWith<CancellationException> { pending.await() }
    }

    private fun waiter(vararg snapshots: WaitSnapshot): ConditionWaiter {
        val values = snapshots.toList().ifEmpty { listOf(snapshot()) }
        var index = 0
        return ConditionWaiter(
            readSnapshot = { values[(index++).coerceAtMost(values.lastIndex)] }, readMessages = ::readMessages, pollMillis = 1,
        )
    }

    private fun readMessages(since: Long) = MessagePage(messages.filter { it.seq > since }, messages.lastOrNull()?.seq ?: 0L, truncated = false)

    private fun numericCondition() = WaitCondition(
        inventory = InventoryPredicate("minecraft:stone", 4), entity = EntityPredicate("minecraft:cow", minCount = 3), health = HealthPredicate(max = 4.0),
    )

    private fun snapshot(screen: String? = null, inventory: Int? = null, entities: Int? = null, health: Double? = null, inWorld: Boolean = true) = WaitSnapshot(
        state = ClientStateSnapshot(
            screen = screen, connected = inWorld, windowFocused = true,
            player = if (inWorld) PlayerPosition(0.0, 64.0, 0.0, 0f, 0f) else null,
        ),
        inventoryCount = inventory,
        entityCount = entities,
        health = health,
    )
}
