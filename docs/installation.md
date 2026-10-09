# Installation

## Requirements

- Minecraft Java Edition 1.21.1 with [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer.
- [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1.
- Java 21 or newer.
- An MCP client that supports the Streamable HTTP transport, for example Claude Code or the MCP Inspector.

Kotlin, the MCP SDK and the HTTP server are bundled in the jar. Nothing else needs to be installed.

## Install in a normal client

1. Take the jar named `minecraft-driver-mcp-<version>+1.21.1.jar` from a release.
2. Copy it into the `mods/` folder of the Minecraft instance, next to Fabric API.
3. Start the game.

When the client starts, the log prints the endpoint:

```text
Minecraft Driver MCP "Player123" listening on http://127.0.0.1:25890/mcp
```

Use that URL to [connect an assistant](getting-started.md).

## Install in a mod's development environment

The usual case is testing a mod under development with `gradlew runClient`.

1. Copy the driver jar into the project's `run/mods/` folder.
2. Run `gradlew runClient`.

Fabric Loader remaps the jar to the project's mappings (Yarn or Mojang) at launch.

> **Do not add the jar with `modLocalRuntime(files(...))`.** Loom drops the list of bundled libraries from a remapped
> file dependency, and the game crashes with `NoClassDefFoundError: kotlin/...`. Copying the jar into `run/mods/`
> works. See [Known issues](known-issues.md#modlocalruntime-crashes-the-client).

To build against the driver's [extension API](extension-api.md), use `modCompileOnly(files(...))` instead; that does
not run the jar.

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

The jar is written to `versions/<minecraft version>/build/libs/`. Use the jar without the `-sources` suffix.

## Verify the installation

With the client running, send an MCP `initialize` request or run the MCP Inspector against the endpoint, then call
`tools/list`. The tools named `mc_*` should be listed. `mc_get_state` returns the open screen and the connection state
even on the title screen.
