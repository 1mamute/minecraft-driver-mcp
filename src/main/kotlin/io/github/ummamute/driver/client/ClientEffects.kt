package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffectInstance

/** The player's active effects, bounded independently of the installed effect registry. */
@Serializable
data class PlayerEffectsSnapshot(
    val effects: List<ActiveEffectSummary>,
    /** Number of active effects before the response limit is applied. */
    val total: Int,
    val truncated: Boolean,
)

/** One currently active effect; hidden effects waiting to replace it are not included. */
@Serializable
data class ActiveEffectSummary(
    /** Registry identifier, including the namespace, such as `minecraft:speed`. */
    val id: String,
    /** Zero-based strength: amplifier 0 is level I. */
    val amplifier: Int,
    /** Remaining game ticks; -1 means infinite duration. */
    val remainingTicks: Int,
)

/** Reads the local player's status effects. Call on the render thread. */
object ClientEffects {
    private const val EFFECT_LIMIT = 100
    private const val INFINITE_DURATION = -1

    /** Returns effects ordered by registry identifier, or fails when no world is joined. */
    fun snapshot(): PlayerEffectsSnapshot {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")
        check(minecraft.level != null) { "Not in a world. Join or create one with mc_join_world or mc_join_server, then call again" }
        val activeEffects = player.activeEffects
        val effects = activeEffects.sortedBy(::effectId).take(EFFECT_LIMIT).map(::summarize)
        return PlayerEffectsSnapshot(effects = effects, total = activeEffects.size, truncated = activeEffects.size > EFFECT_LIMIT)
    }

    private fun effectId(effect: MobEffectInstance): String = BuiltInRegistries.MOB_EFFECT.getKey(effect.effect.value()).toString()

    private fun summarize(effect: MobEffectInstance) = ActiveEffectSummary(
        id = effectId(effect),
        amplifier = effect.amplifier,
        remainingTicks = if (effect.isInfiniteDuration) INFINITE_DURATION else effect.duration,
    )
}
