package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientStateSnapshot
import io.github.ummamute.driver.client.LoggedMessage
import io.github.ummamute.driver.client.MessagePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** Predicates evaluated afresh on every poll; messages remain eligible while buffered after [sinceSeq]. */
data class WaitCondition(
    /** Text a message received after [sinceSeq] must contain, ignoring case. */
    val messageContains: String? = null,
    /** Text the open screen's class name must contain, ignoring case, or [NO_SCREEN] for no open screen. */
    val screenContains: String? = null,
    /** Messages with a sequence number above this count. */
    val sinceSeq: Long = 0L,
    val inventory: InventoryPredicate? = null,
    val entity: EntityPredicate? = null,
    val health: HealthPredicate? = null,
    val mode: WaitMode = WaitMode.ANY,
) {
    init {
        require(listOfNotNull(messageContains, screenContains, inventory, entity, health).isNotEmpty()) {
            "Give message, screen, inventory, entity or health. Example: {\"inventory\":{\"item\":\"minecraft:stone\"}}"
        }
    }

    val needsSnapshot: Boolean get() = screenContains != null || inventory != null || entity != null || health != null

    companion object {
        const val NO_SCREEN = "none"
    }
}

/** One short, coherent render-thread read. A null numeric value means no player/world is available. */
data class WaitSnapshot(
    val state: ClientStateSnapshot,
    val inventoryCount: Int? = null,
    val entityCount: Int? = null,
    val health: Double? = null,
)

/** Latest observed count or health for a supplied condition; messages and screens have no numeric value. */
@Serializable
data class WaitObservation(val condition: String, val matched: Boolean, val value: Double? = null)

/** Outcome of a bounded wait, including observations that explain an unmet predicate. */
@Serializable
data class WaitResult(
    val matched: Boolean,
    val reason: String,
    val message: LoggedMessage? = null,
    /** Sequence number to pass as `since` to read only messages after this wait. */
    val latest: Long = 0L,
    val state: ClientStateSnapshot? = null,
    val observations: List<WaitObservation> = emptyList(),
    val nextStep: String? = null,
)

/** Polls the client until a [WaitCondition] holds or the time runs out. Has no Minecraft dependency, so it can be tested with fakes. */
internal class ConditionWaiter(
    private val readSnapshot: suspend (WaitCondition) -> WaitSnapshot,
    private val readMessages: (Long) -> MessagePage,
    private val pollMillis: Long = POLL_MILLIS,
) {
    suspend fun await(condition: WaitCondition, timeoutMillis: Long): WaitResult {
        var last: WaitResult? = null
        val matched = withTimeoutOrNull(timeoutMillis) {
            var result: WaitResult
            do {
                result = check(condition)
                last = result
                if (!result.matched) delay(pollMillis)
            } while (!result.matched)
            result
        }
        if (matched != null) return matched
        return timeout(condition, last)
    }

    private suspend fun timeout(condition: WaitCondition, last: WaitResult?): WaitResult {
        // Message-only polling avoids the render thread; its timeout snapshot must also be bounded.
        val state = last?.state ?: if (!condition.needsSnapshot) {
            withTimeoutOrNull(SNAPSHOT_GRACE_MILLIS) { readSnapshot(condition).state }
        } else {
            null
        }
        val nextStep = when {
            state == null -> "No render-thread snapshot completed. Call mc_get_state to check client responsiveness, then retry"
            state.player == null -> "No world is loaded. Join with mc_join_world or mc_join_server, then retry or adjust the conditions"
            else -> "Inspect observations and state. Use mc_read_inventory, mc_list_entities or mc_get_state, then adjust the conditions or timeout_ms"
        }
        return (last ?: WaitResult(false, "timeout", observations = observations(condition, null, null))).copy(
            matched = false,
            reason = "timeout",
            latest = readMessages(condition.sinceSeq).latest,
            state = state,
            nextStep = nextStep,
        )
    }

    private suspend fun check(condition: WaitCondition): WaitResult {
        val page = readMessages(condition.sinceSeq)
        val message = condition.messageContains?.let { text -> page.messages.firstOrNull { it.text.contains(text, ignoreCase = true) } }
        if (condition.mode == WaitMode.ANY && message != null) {
            return WaitResult(
                matched = true,
                reason = "message",
                message = message,
                latest = page.latest,
                observations = listOf(WaitObservation("message", true)),
            )
        }
        val snapshot = if (condition.needsSnapshot) readSnapshot(condition) else null
        val observations = observations(condition, snapshot, message)
        val matched = when (condition.mode) {
            WaitMode.ANY -> observations.any { it.matched }
            WaitMode.ALL -> observations.all { it.matched }
        }
        val reason = when {
            !matched -> "pending"
            condition.mode == WaitMode.ALL && observations.size > 1 -> "all"
            else -> observations.first { it.matched }.condition
        }
        return WaitResult(matched, reason, message, page.latest, snapshot?.state, observations)
    }

    private fun observations(condition: WaitCondition, snapshot: WaitSnapshot?, message: LoggedMessage?): List<WaitObservation> = buildList {
        condition.messageContains?.let { add(WaitObservation("message", message != null)) }
        condition.screenContains?.let { expected ->
            val matches = snapshot != null && screenMatches(expected, snapshot.state.screen)
            add(WaitObservation("screen", matches))
        }
        condition.inventory?.let { predicate ->
            val count = snapshot?.inventoryCount
            add(WaitObservation("inventory", count?.let(predicate::matches) == true, count?.toDouble()))
        }
        condition.entity?.let { predicate ->
            val count = snapshot?.entityCount
            add(WaitObservation("entity", count?.let(predicate::matches) == true, count?.toDouble()))
        }
        condition.health?.let { predicate ->
            val health = snapshot?.health
            add(WaitObservation("health", health?.let(predicate::matches) == true, health))
        }
    }

    private fun screenMatches(expected: String, actual: String?): Boolean = when {
        expected == WaitCondition.NO_SCREEN -> actual == null
        else -> actual?.contains(expected, ignoreCase = true) == true
    }

    private companion object {
        const val POLL_MILLIS = 50L
        const val SNAPSHOT_GRACE_MILLIS = 250L
    }
}
