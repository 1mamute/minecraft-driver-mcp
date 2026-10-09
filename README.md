# minecraft-driver-mcp

Let AI drive. A Fabric **client** mod that starts an [MCP](https://modelcontextprotocol.io) server inside a real, rendering Minecraft client, so an AI assistant can look at screens, click buttons, move, chat and take screenshots while you develop a mod.

Unlike a protocol bot (mineflayer), this is the actual modded client: it sees your mod's screens, HUD and rendering, and joins modded servers without handshake tricks. It is a development tool, not a gameplay bot.

## Use

1. Put the jar for your Minecraft version in the client's `mods/` folder (or add it to your dev run, see below). It needs Fabric API; Kotlin and the MCP server libraries are bundled in the jar.
2. Start the client. The log prints the endpoint: `Minecraft Driver MCP "Player123" listening on http://127.0.0.1:25890/mcp`.
3. Point your assistant at it:

   ```bash
   claude mcp add --transport http minecraft http://127.0.0.1:25890/mcp
   ```

A fresh game directory opens Minecraft's accessibility onboarding screen first. Close it once (`mc_click` it away) or set `onboardAccessibility:false` in `options.txt`; this repo's `runClient` does that for you.

### In a mod's dev environment

Add the jar to your Loom run, for example `modLocalRuntime(files("libs/minecraft-driver-mcp-0.1.0+1.21.1.jar"))`, and run `gradlew runClient`.

## Tools

| Tool | Does |
| --- | --- |
| `mc_get_state` | Open screen, connection, window focus, player position |
| `mc_list_widgets` | Widgets of the open screen with index, label and bounds |
| `mc_read_screen_text` | Text the open screen drew (title, labels, body, tooltip) with coordinates |
| `mc_read_inventory` | The player's hotbar, main, armor and offhand slots with item id, name, count, durability and enchantments, plus the cursor stack |
| `mc_read_container` | The open container screen (chest, furnace, crafting table, villager trades) with every slot and, for merchants, the offers |
| `mc_click` | Click a widget by label or index, or a point |
| `mc_click_slot` | Click a slot of the open container: pick up, quick move, swap with a hotbar key, throw, clone, gather, or drop the cursor stack |
| `mc_close_screen` | Close the open screen |
| `mc_join_server` | Connect to a multiplayer server by `host` or `host:port` (opens the connect screen; login finishes later) |
| `mc_join_world` | Load a singleplayer world by its save folder name |
| `mc_disconnect` | Leave the current world or server and return to the title screen |
| `mc_set_key` | Hold or release forward, back, left, right, jump, sneak |
| `mc_look_at` | Face a world position |
| `mc_use` | Right click what is under the crosshair |
| `mc_send_chat` | Send chat or a `/command` as the player |
| `mc_read_messages` | Chat and system messages received since a sequence number |
| `mc_read_log` | The game's log lines (level, logger, thread, message, throwable) since a sequence number, filtered by level and text |
| `mc_wait_for` | Block until a message or screen appears, or time out |
| `mc_list_entities` | Entities within 64 blocks |
| `mc_screenshot` | PNG of the game framebuffer |
| `mc_list_instances` | Every running client with this mod, to find the others |

Everything runs on the render thread through the game's own screen and player APIs. The operating system's mouse and keyboard are never used.

## Several clients at once

Testing multiplayer needs two or more clients. Each one starts its own server, so ports must not collide:

- **Default:** a client takes the first free port from `25890`. Start two clients and they get `25890` and `25891`.
- **Fixed port:** `-Ddriver.port=25901` uses exactly that port and fails loudly if it is taken, because your MCP config points at it.
- **Names:** `-Ddriver.name=Alice` labels the instance (default: the player name). The name appears in the MCP server name and instructions, so an assistant connected to several clients can tell them apart.
- **Discovery:** each running client writes `~/.minecraft-driver-mcp/instances/<pid>.json` and removes it on exit. `mc_list_instances` (or the directory) lists the live ones with their URLs; files of crashed clients are ignored.
- **Game directories:** give each client its own run directory, as Minecraft requires.

For stable setups, fix the ports and register each client once:

```bash
claude mcp add --transport http alice http://127.0.0.1:25901/mcp
claude mcp add --transport http bob   http://127.0.0.1:25902/mcp
```

## Properties

| Property | Default | Meaning |
| --- | --- | --- |
| `driver.port` | first free from 25890 | Fixed port |
| `driver.host` | `127.0.0.1` | Bind address. Keep it on localhost: the tools control the player |
| `driver.name` | player name | Instance name |
| `driver.registry` | `~/.minecraft-driver-mcp/instances` | Directory of running instances |
| `driver.unfocused` | `false` (`true` in this repo's `runClient`) | Create the window without taking focus |

## Build

JDK 21 and the Gradle wrapper. [Stonecutter](https://stonecutter.kikugie.dev) builds one jar per Minecraft version from one source tree; version-specific code is marked with Stonecutter comments.

```bash
./gradlew build          # jars in versions/<mc>/build/libs
./gradlew lint           # ktlint + detekt
./gradlew :1.21.1:runClient
```

Supported: Minecraft 1.21.1. Contributors: see [AGENTS.md](AGENTS.md) and the [documentation](docs/README.md).

## Status

Early scaffold. Open risks and unverified behavior are tracked in [docs/known-issues.md](docs/known-issues.md). Planned: inventory and container contents, joining and leaving servers, log tail, and extension points for other mods.
