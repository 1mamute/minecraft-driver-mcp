---
name: minecraft
description: Resolve Minecraft Java client API, mapping, screen, input, entity, and world questions using version-matched sources. Use when answering vanilla Minecraft questions or changing net.minecraft integrations in client/.
---

# Minecraft Java client APIs

Read [repository cache guidance](../references/repository-cache.md) for upstream
source lookup. Obtain vanilla Minecraft source through Loom and search the
resolved sources locally.

## Resolve the question

1. Resolve Minecraft and Java versions from `gradle.properties`,
   `versions/<mc>/gradle.properties`, `build.gradle.kts` and `fabric.mod.json`.
   Identify the mapping configuration (official Mojang names with Parchment) before
   naming APIs.
2. Search the relevant `net.minecraft` imports and usages in `client/`. Inspect the
   mapped Minecraft sources produced by the project's Loom setup for the exact
   implementation, method overloads, and execution context.
3. If sources are missing, inspect available Gradle tasks for source generation
   with the wrapper (`tasks --all`; `genSources`) and use the task supported by this
   Loom version. For external examples, use documentation for the same game version
   and translate names to this build's Mojang/Parchment namespace. Yarn-named
   examples need translating before use.
4. Support the answer with a local source path or upstream link, specifying the
   version and mapping namespace. Treat a proposed signature as unverified until
   found in matching sources or confirmed by compilation.

## Sources

- Resolved, mapped Minecraft sources are the authority for this build's vanilla
  behavior. Parchment enriches official mappings with parameter names and docs;
  it is not a separate Minecraft API.
- [Fabric's versioned Minecraft development docs](https://docs.fabricmc.net/1.21.1/)
  explain screens, entities, text, rendering and networking in a mod context. Use
  the version selector when the repository's version changes.
- [Parchment project](https://parchmentmc.org/) explains mapping artifacts and
  version selection.
- [Mojang's Brigadier source](https://github.com/Mojang/brigadier) documents command
  parsing, relevant when the driver sends a `/command` as the player.

## Integration checks

- Game state belongs to the render thread: screens, the player, the level, input and
  the framebuffer. Route every access through `RenderThread.call`, and account for the
  player or level being `null` (title screen, loading, disconnect) when the work runs.
- Keep `net.minecraft` imports in `client/`. When a signature differs between Minecraft
  versions, isolate the difference with a Stonecutter conditional or a per-version class
  inside `client/`, so `tools/` and `server/` stay unchanged.
- The driver acts through the game's own APIs (`Screen.mouseClicked`, `KeyMapping`,
  `LocalPlayer`), never through the operating system's mouse or keyboard. Widgets
  react to hover, which comes from the real cursor, so mark the target hovered first.
- A screen's buttons are not always in `children()`; some screens draw clickable
  tiles themselves. Inspect the screen class before assuming a widget is listed.
- Chat and system messages arrive on the client's network thread; record them through
  `MessageLog`, not by reading the chat GUI.
- Use the Fabric skill for loader hooks and the Mixin skill for injection.
