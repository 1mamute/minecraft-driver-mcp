package io.github.ummamute.driver.client

import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.client.Minecraft
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs game calls on the render thread, the only thread allowed to touch screens, the player and the level. */
object RenderThread {
    suspend fun <T> call(action: () -> T): T = suspendCancellableCoroutine { continuation ->
        Minecraft.getInstance().execute {
            try {
                continuation.resume(action())
            } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
                continuation.resumeWithException(exception)
            }
        }
    }
}
