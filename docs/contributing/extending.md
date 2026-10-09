# Extending the mod

How to add features to the mod itself. To add tools from a separate mod instead, see the
[Extension API](../extension-api.md).

Read [Architecture](architecture.md) and [Code style](code-style.md) first. Every step below follows the rule that
`tools/` and `server/` never import Minecraft.

## Add a tool

A tool that reads or changes the game needs two parts: a method in `client/` that touches Minecraft, and a registration
in `tools/` that defines what the assistant sees.

### 1. Put the game access in `client/`

Find the object that owns the concern (`ClientScreens`, `ClientInput`, `ClientInventory`, `ClientChat`,
`ClientConnection`, `ClientState`) or add a `Client<Concern>` object. Write a function that:

- is called on the render thread, so it may touch screens, the player and the level;
- checks for `null` (title screen, loading, disconnect) with guard clauses, and fails with a message that says what to do
  next: `error("Not in a world. Join or create one with mc_join_world or mc_join_server, then call again")`;
- returns a `@Serializable` data class, with KDoc on properties whose meaning is not obvious.

Keep logic that does not need Minecraft (validation, planning, formatting) in a separate pure class, like
`SlotClickPlanner` or `TextFrame`, so it can be unit tested.

### 2. Register the tool in `tools/`

Add a `register<Name>` function to the matching group (`ObservationTools` for read-only tools, `ActionTools` for tools
that change the game, `ConnectionTools`, `InstanceTools`) and call it from the group's `register`.

An illustrative example (`ClientState.health` is hypothetical):

```kotlin
private fun registerHealth(server: Server) {
    server.addGuardedTool(
        name = "mc_get_health",
        description = "Get the player's health and food level. Fails outside a world.",
        toolAnnotations = readOnly,
    ) { _ -> onRenderThread(ClientState::health) { jsonResult(it) } }
}
```

- Name: `mc_<verb>_<noun>`, snake case.
- Annotations: set all four (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`). Reuse the shared
  `readOnly` and `action` values and `copy` them for differences.
- Arguments: declare them with `ToolSupport.schema(Property(...))` and read them with the `ToolSupport` helpers
  (`string`, `int`, `long`, `double`, `boolean`, `require*`).
- Register with `addGuardedTool`, not `addTool`, so exceptions become plain `isError` results.
- Run game access through `onRenderThread`. Waiting tools poll with short render-thread reads and `delay`, and never
  hold the render thread.
- Keep the description to what the assistant needs to decide to call the tool and how to call it.

### 3. Test and document

- Unit test the pure parts under `src/test/kotlin`, in the same package, with backtick test names.
- Run the [runtime check](testing.md#runtime-check): `tools/list` shows the tool with its annotations; call it on the
  title screen and in a world; call it with a missing argument.
- Update, in the same change: the tool table in `README.md`, [../tools.md](../tools.md), `llms.txt`, and
  [../known-issues.md](../known-issues.md) if the tool has limits.

## Add a client concern

When a new area of the game does not fit an existing object, add `Client<Concern>` in `client/`: an `object` when it
is stateless, a class constructed in `DriverBootstrap` when it holds state. State that outlives a request, such as a
buffer fed from another thread (`MessageLog`, `LogBuffer`), is bounded and returns copies. Anything fed from a non-render
thread must be safe for that thread and must not touch game state.

## Add a mixin

Use a mixin only when no API reaches what is needed (screen text capture is an example).

1. Add a Java class in `src/main/java/io/github/ummamute/driver/mixin/` that calls a Kotlin object; keep the hook thin.
2. List it in `minecraft-driver-mcp.mixins.json`.
3. Prefer an accessor (`AbstractContainerScreenAccessor`) for a private field or method over an injection.
4. Read the matching Minecraft source for the target and its descriptor. Names are official Mojang mappings with
   Parchment parameter names.
5. Run the runtime check; a broken mixin shows as an application failure in the log at startup.

## Support a new Minecraft version

See [Dependencies](dependencies.md#adding-a-minecraft-version). In short: add `versions/<mc>/gradle.properties`, add the
version to `settings.gradle.kts`, build, and fix what the compiler reports in `client/` and the mixins with Stonecutter
conditionals. Update `ClassNames`, which maps vanilla classes to readable names, if class names changed.

## Add a system property

Read it in `DriverBootstrap`, pass it to the component that needs it, and document it in
[../configuration.md](../configuration.md), the property table in `README.md`, and `llms.txt`. A property is part of the
supported contract: renaming it is a breaking change.

## Change the extension API

`api/` is the public surface other mods compile against. Keep it free of Minecraft types and stable. Document changes in
[../extension-api.md](../extension-api.md) and flag a breaking change in the commit body.
