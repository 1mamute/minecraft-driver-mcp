# Publication verification

This directory contains a standalone Fabric mod project that consumes the driver as a Maven dependency, just as
another mod's development environment would. It checks that publication works for consumers, beyond building the
driver's own jar.

Loom removes nested libraries when remapping dependencies for development. A published driver therefore needs
dependency metadata that supplies Kotlin, Ktor and MCP on the consumer's runtime classpath.

## What it checks

The [`maven-consumer`](maven-consumer/) fixture uses `modCompileOnly` for the extension API and `modLocalRuntime`
to load the driver. Its checks verify that:

- A Java mod can compile against the published extension API.
- Driver, Kotlin, Ktor and MCP classes are available at runtime.
- Both Gradle module metadata and Maven POM-only dependency resolution work.
- The consumer's jar contains no driver classes or nested library jars.

The fixture is a separate Gradle build. It resolves the driver from `build/maven-verification/` at the repository
root, populated by the local publication task. These checks require no Central credentials or uploads, although
Gradle may download build tools and third-party dependencies. Fixture sources are tracked; generated builds,
Gradle caches and the client run directory are ignored.

## Run the checks

Use JDK 21, Node.js 22 or later, and the repository's Gradle wrapper. Run the commands in
[Verify Maven publication](../docs/contributing/README.md#verify-maven-publication) from the repository root.
CI runs the artifact checks and both consumer resolution modes on Linux and Windows.

For a game-level check, launch `./gradlew -p verification/maven-consumer runClient`, then verify `initialize`,
`tools/list` and `mc_get_state` against the endpoint printed in the log. This launches the driver through its
repository dependency, without copying a jar into `run/mods/`. CI does not launch the game.
