package io.github.ummamute.driver.server

/** Available operating-system process metadata; null fields mean inspection could not establish a value. */
data class ProcessIdentity(val isAlive: Boolean? = null, val startEpochSeconds: Long? = null)

internal fun inspectProcessIdentity(pid: Long): ProcessIdentity = try {
    val process = ProcessHandle.of(pid).orElse(null)
    if (process == null) {
        ProcessIdentity(isAlive = false)
    } else {
        ProcessIdentity(isAlive = process.isAlive, startEpochSeconds = processStart(process))
    }
} catch (_: UnsupportedOperationException) {
    ProcessIdentity()
} catch (_: SecurityException) {
    ProcessIdentity()
}

private fun processStart(process: ProcessHandle): Long? = try {
    process.info().startInstant().orElse(null)?.epochSecond?.takeIf { it > 0 }
} catch (_: UnsupportedOperationException) {
    null
} catch (_: SecurityException) {
    null
}
