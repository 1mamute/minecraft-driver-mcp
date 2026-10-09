[![Build](https://github.com/1mamute/minecraft-driver-mcp/actions/workflows/build.yml/badge.svg)](https://github.com/1mamute/minecraft-driver-mcp/actions/workflows/build.yml)

<!-- #region site-top -->
# Minecraft Driver MCP

**Let an AI assistant drive a real Minecraft client, so it can test and debug your Fabric mod.**

Minecraft Driver MCP is a Fabric client mod that starts a [Model Context Protocol](https://modelcontextprotocol.io)
server inside the running game. An assistant connects to it and can read the open screen, click buttons, type, walk,
chat, open chests, read the game log and take screenshots, all through the game's own APIs.

## Why it exists

Debugging a mod is a loop of editing, launching, clicking through menus to the feature, reading what happened and
digging through the log. An AI assistant can edit code and run builds, but it cannot see or touch the game, so the loop
stalls at the point where a person has to describe the screen.

This mod removes that step. The assistant can start a client, join a world, open the screen under development, read its
text and widgets, trigger the action, and read the exception that followed in the log. It runs inside the real client,
so it sees the screens, HUD and rendering of your mod, and it joins modded servers like any player. That is the
difference from a protocol bot such as Mineflayer, which has no client to render or run client-side mod code.

It is a development tool, not a gameplay bot.

## What it can do

- Name the open screen, list its widgets, and read the text it drew, with coordinates.
- Click widgets by label, index or point; type text; press keys; hold movement keys; face a position; use items.
- Read the player's inventory and any open container, including villager trades, and click slots.
- Send chat and commands and read the replies; wait for a message or screen instead of polling.
- Read the game's log with level, logger and exception frames, filtered by level and text.
- Join a world or a server, leave it, list nearby entities, take a screenshot.
- Run several clients at once, each with its own port, for multiplayer tests.
- Let other mods add their own tools.

Everything runs on the render thread through screen and player APIs. The operating system's mouse and keyboard are never
used, so the window can stay behind other windows. The server binds to `127.0.0.1` only.

## Quick start

1. Put the jar for your Minecraft version in the client's `mods/` folder, next to Fabric API. Kotlin and the server
   libraries are bundled. Requires Fabric Loader 0.19.5+ and Java 21+.
2. Start the client. The log prints the endpoint:

   ```text
   Minecraft Driver MCP "Player123" listening on http://127.0.0.1:25890/mcp
   ```

3. Point an MCP client at it:

   ```bash
   claude mcp add --transport http minecraft http://127.0.0.1:25890/mcp
   ```

4. Ask the assistant to call `mc_get_state`, then describe what to test.

For a mod's development environment, copy the jar into `run/mods/` and run `gradlew runClient`. Do not add it with
`modLocalRuntime(files(...))`: the client crashes with `NoClassDefFoundError: kotlin/...`. A fresh game directory opens
the accessibility screen first; close it once with `mc_click` or set `onboardAccessibility:false` in `options.txt`.
See [Installation](docs/installation.md) and [Getting started](docs/getting-started.md).

## Tools

| Tool | Does |
| --- | --- |
| `mc_get_state` | Open screen, connection, window focus, player position |
| `mc_list_widgets` | Widgets of the open screen with index, type, label and bounds |
| `mc_read_screen_text` | Text the open screen drew, with coordinates |
| `mc_read_inventory` | The player's slots with item id, name, count, durability, enchantments and lore, plus the cursor stack |
| `mc_read_container` | The open container screen with every slot and, for merchants, the offers |
| `mc_click` | Click a widget by label or index, or a point |
| `mc_click_slot` | Click a container slot: pick up, quick move, swap, throw, clone, gather, or drop the cursor stack |
| `mc_close_screen` | Close the open screen as Escape does |
| `mc_join_server` | Connect to a multiplayer server |
| `mc_join_world` | Load a singleplayer world by save folder name |
| `mc_disconnect` | Leave the current world or server |
| `mc_set_key` | Hold or release a movement key; press `inventory` |
| `mc_look_at` | Face a world position |
| `mc_use` | Right click what is under the crosshair, then the held item; `hold_ticks` finishes eating, drinking or drawing a bow |
| `mc_send_chat` | Send chat or a `/command` as the player |
| `mc_type_text` | Type into the focused widget |
| `mc_press_key` | Press enter, escape, tab, backspace, arrows and similar on the open screen |
| `mc_read_messages` | Chat and system messages since a sequence number |
| `mc_read_log` | The game's log lines since a sequence number, filtered by level and text |
| `mc_wait_for` | Block until a message or screen appears, or time out |
| `mc_list_entities` | Entities within 64 blocks, nearest first |
| `mc_screenshot` | PNG of the game framebuffer |
| `mc_list_instances` | Every running client with this mod |

Arguments and results are in the [tool reference](docs/tools.md). Other mods add tools named
`<modid>_<verb>_<noun>` through the [extension API](docs/extension-api.md).

## Several clients

A client takes the first free port from 25890, so two clients get 25890 and 25891. Fix a port with `-Ddriver.port`, name
an instance with `-Ddriver.name`, and find the others with `mc_list_instances`. See
[Running several clients](docs/multiple-clients.md).

## Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `driver.port` | first free from 25890 | Fixed port |
| `driver.host` | `127.0.0.1` | Bind address. Keep it on localhost: the tools control the player |
| `driver.name` | player name | Instance name |
| `driver.token` | unset (no authentication) | When set, every request needs `Authorization: Bearer <token>` |
| `driver.registry` | `~/.minecraft-driver-mcp/instances` | Directory of running instances |
| `driver.unfocused` | `false` | Create the window without focus and keep the game running while it is behind other windows |

Details are in [Configuration](docs/configuration.md).

<!-- #endregion site-top -->

## Documentation

The documentation is published at [1mamute.github.io/minecraft-driver-mcp](https://1mamute.github.io/minecraft-driver-mcp/)
and lives in [docs/](docs/README.md):

- [User documentation](docs/README.md): installation, usage, [debugging mods with an assistant](docs/debugging-mods.md),
  tool reference, configuration, [known issues](docs/known-issues.md).
- [Contributor guide](docs/contributing/README.md): architecture, extending the mod, testing, code style.
- [llms.txt](llms.txt): a single self-contained brief for LLM agents.

<!-- #region site-bottom -->
## Build

JDK 21 and the Gradle wrapper. [Stonecutter](https://stonecutter.kikugie.dev) builds one jar per Minecraft version from
one source tree.

```bash
./gradlew build          # jars in versions/<mc>/build/libs
./gradlew lint           # ktlint and detekt
./gradlew :1.21.1:runClient
```

## Status

Pre-release (0.1.0) for Minecraft 1.21.1. The tools above are checked against a running client, both in a development
environment and in a regular launcher-managed install with other mods, before each change merges. Other Minecraft
versions and operating systems other than Windows have not been tested, and tool names and schemas can change between
minor versions. Limits and unverified areas are listed in [Known issues](docs/known-issues.md).

## License

MIT
<!-- #endregion site-bottom -->
