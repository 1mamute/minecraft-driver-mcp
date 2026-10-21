# Installation

## Requirements

- Minecraft Java Edition 1.21.1 with [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer.
- [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1.
- Java 21 or newer.
- Node.js 22 or later for the gateway.
- A coding agent with stdio MCP support, such as Claude Code or Codex.

Kotlin, the MCP SDK and the HTTP server are bundled in the mod jar. Install the
matching gateway as described in [Build and install](gateway.md#build-and-install)
and [connect the coding agent](getting-started.md). Direct HTTP access is an
[alternative](getting-started.md#direct-http-alternative) for specialized setups and debugging.

## Install in a mod's development environment

**Recommended:** add the driver as a Maven Central dependency in the Loom project's `build.gradle.kts`.
Each new release is published there:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    modLocalRuntime("io.github.1mamute:minecraft-driver-mcp:<version>+1.21.1")
}
```

Replace `<version>` with the driver release version, keep Fabric API in the project's dependencies, then run
`gradlew runClient`. Loom remaps the driver to the project's mappings (Yarn or Mojang). Gradle supplies Kotlin, MCP
and Ktor through the published dependency metadata; no manual jar download or copy is needed.

This is a development runtime dependency. Do not bundle the driver in the mod being tested. Remove any manually
copied driver jar from `run/mods/` when switching to the dependency.

To build against the driver's [extension API](extension-api.md), also add `modCompileOnly` with the same coordinate.

When the client starts, the log prints the endpoint:

```text
Minecraft Driver MCP "Player123" listening on http://127.0.0.1:25890/mcp
```

The gateway discovers this endpoint automatically. Register the gateway once using
[Getting started](getting-started.md); no per-client URL registration is needed.

## Install in a normal client

For a client launched outside a Gradle development environment, install the jar manually:

1. Take the jar named `minecraft-driver-mcp-<version>+1.21.1.jar` from a release.
2. Copy it into the `mods/` folder of the Minecraft instance, next to Fabric API.
3. Start the game.

Kotlin, MCP and Ktor are bundled in the standalone jar.

## Use a local jar in a development environment

For a local build or an older release available only as a jar, copy the driver into `run/mods/` and run
`gradlew runClient`. Use this as a fallback to the Maven Central dependency.

Do not use `modLocalRuntime(files(...))`: Loom removes the nested library list, and the client crashes with missing
Kotlin classes. See [Known issues](known-issues.md#modlocalruntime-crashes-the-client).

## Options for a driven client

A fresh game directory has defaults that get in an assistant's way. Put these lines in the client's `options.txt`:

```properties
onboardAccessibility:false
pauseOnLostFocus:false
soundCategory_master:0.0
```

- `onboardAccessibility:false` skips the accessibility screen shown on first launch, which would otherwise absorb the
  first click. Without the option, close it once with `mc_click`.
- `pauseOnLostFocus:false` keeps the game running while its window is behind other windows. The system property
  `-Ddriver.unfocused=true` has the same effect without editing the file; see [Configuration](configuration.md).
- `soundCategory_master:0.0` mutes the game.

## Build from source

Requires JDK 21 and the Gradle wrapper.

```bash
./gradlew build
```

The jar is written to `versions/<minecraft version>/build/libs/`. Use the jar without a `-sources` or `-javadoc` suffix.
The tool catalogue is bundled as a JSON resource inside the mod jar; no additional file is needed.

## Verify the installation

With the client running, send an MCP `initialize` request or run the MCP Inspector against the endpoint, then call
`tools/list`. The tools named `mc_*` should be listed. `mc_get_state` returns the open screen and the connection state
even on the title screen.
