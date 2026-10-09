# Introduction

## What it is

Minecraft Driver MCP is a Fabric **client** mod. When the Minecraft client starts, the mod starts an MCP server on the
local machine. Any MCP-capable AI assistant can connect to it and operate the running game: look at the open screen,
click buttons, type, walk, chat, open containers, read the game log and take screenshots.

It is a development tool for mod authors and testers. It is not a gameplay bot and it is not meant for use on public
servers.

## Why it exists

Debugging a Fabric mod is mostly a loop: change code, launch the game, navigate to the feature, look at what happened,
read the log, repeat. An AI assistant can edit the code and run the build, but it cannot see or touch the game. The
loop breaks at the step where somebody has to click through menus and describe the result.

This mod closes that loop. The assistant starts a client, joins a world, opens the screen that is being developed,
reads its text and widgets, triggers the action under test, and reads the game log for the exception that followed.
Because the mod runs inside the real client, it sees exactly what a player sees, including the screens, HUD and
rendering of other mods.

## How it differs from a protocol bot

Tools such as Mineflayer speak the Minecraft network protocol and run no game client. That is fast and light, but such a
bot has no screens, no rendering, no client-side mod code and often cannot join a modded server. Minecraft Driver MCP is
the opposite trade: it is the real, rendering, modded client, so it can test client-side behavior and join any server a
normal client can.

## Features

- **Screens.** Name the open screen, list its widgets with type, label and bounds, read the text it drew (title,
  labels, body, tooltip) with coordinates.
- **Input.** Click widgets by label or index or at a point, type into text fields, press keys such as enter and tab,
  hold movement keys, face a position, use the item or block in front of the player.
- **Inventories and containers.** Read the player inventory and any open container (chests, furnaces, crafting tables,
  villager trades) with item ids, counts, durability, enchantments and lore, and click slots.
- **Chat and commands.** Send chat or `/commands` and read the replies as they arrive.
- **Game log.** Read the client's log with level, logger, thread, message and exception frames, filtered by level and
  text.
- **Waiting.** Block until a message or a screen appears instead of polling.
- **World and server.** Join a singleplayer world or a server, disconnect, list nearby entities.
- **Screenshots.** A PNG of the game framebuffer, for when text is not enough.
- **Several clients.** Each client gets its own port and name, and discovers the others, so multiplayer tests can run
  two or more at once.
- **Extensible.** Other mods can add their own tools through a Fabric entrypoint.

Everything runs through the game's own screen and player APIs on the render thread. The operating system's mouse and
keyboard are never used, so the window can stay behind other windows.

## Design principles

- **Text first.** Tools return JSON text. A screenshot is the fallback, because text is cheaper for an assistant to
  read and reliable to compare.
- **Local only.** The server binds to `127.0.0.1`. The tools control the player, so they are never exposed on a network
  interface by default.
- **Many clients.** No fixed ports unless requested, and no shared mutable state except a small instance registry.
- **Self-contained.** One jar carries Kotlin and the MCP and HTTP libraries. It does not need Fabric Language Kotlin.

## Supported versions

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | required |
| Java | 21 or newer |

The project is at version 0.x. Tool names and schemas can change between minor versions; changes are called out in the
[release notes](release-notes.md). See [Known issues](known-issues.md) for what has been verified.

## License

MIT.

The jar bundles these libraries, each under its own license:

| Library | License |
| --- | --- |
| [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) | MIT |
| [Ktor](https://ktor.io) | Apache 2.0 |
| [Kotlin standard library](https://kotlinlang.org) and kotlinx libraries (coroutines, serialization, io, collections) | Apache 2.0 |
| [kotlin-logging](https://github.com/oshai/kotlin-logging) and [Typesafe Config](https://github.com/lightbend/config) | Apache 2.0 |
