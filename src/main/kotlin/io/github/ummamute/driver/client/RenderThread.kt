package io.github.ummamute.driver.client

import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.client.Minecraft

/**
 * Runs game calls on the render thread, the only thread allowed to touch screens, the player and the level.
 * An `Error` from the action (`NoSuchMethodError`, `StackOverflowError`, `AssertionError`) reaches the caller like an exception,
 * so the request fails instead of hanging; only a [VirtualMachineError] is rethrown on the render thread after that.
 */
object RenderThread {
    suspend fun <T> call(action: () -> T): T = suspendCancellableCoroutine { continuation ->
        Minecraft.getInstance().execute {
            val outcome = runCatching(action)
            continuation.resumeWith(outcome)
            outcome.exceptionOrNull()?.let { if (it is VirtualMachineError) throw it }
        }
    }
}
