# Extension API

A Fabric mod can add its own MCP tools to the endpoint of the running client, for example a tool that reads the state of the mod's own screen. An assistant sees those tools next to the [built-in ones](tools.md). The API lives in `io.github.ummamute.driver.api`. It has no Minecraft types, and it is the only package a mod should depend on.

**Stability.** The project is at 0.x. The API can change in any MINOR release; a change is flagged in the release notes and the commit body.

## Add the dependency

Compile against the driver jar and keep it out of your own jar. In a Loom project:

```kotlin
dependencies {
    modCompileOnly(files("libs/minecraft-driver-mcp-0.1.0+1.21.1.jar"))
}
```

To run with the driver, copy the same jar into `run/mods/`. Do not add it with `modLocalRuntime(files(...))`: Loom drops the bundled Kotlin and MCP libraries from a remapped file dependency, and the client crashes at startup (see [known issues](known-issues.md#modlocalruntime-crashes-the-client)).

Do not list the driver in `depends`: your mod keeps working without it, and the entrypoint is simply never called.

## Declare the entrypoint

In `fabric.mod.json`, under the custom key `minecraft-driver-mcp`:

```json
"entrypoints": {
    "minecraft-driver-mcp": ["com.example.mymod.MyDriverExtension"]
}
```

The class implements `DriverExtension`. Because the entrypoint class names `io.github.ummamute.driver.api` types, the game only loads it when the driver is installed.

## Register a tool

```java
public class MyDriverExtension implements DriverExtension {
    @Override
    public void registerTools(ToolRegistrar registrar) {
        registrar.register(ToolDefinition.builder("mymod_read_score", "Reads the score of one player. Returns JSON {\"score\": n}.")
            .property("player", PropertyType.STRING, "Player name", true)
            .readOnly(true).destructive(false).idempotent(true).openWorld(false)
            .handler(args -> {
                int score = MyMod.scoreOf(args.requireString("player"));
                return ToolResult.text("{\"score\": " + score + "}");
            })
            .build());
    }
}
```

Kotlin works the same way; `DriverExtension` and `ToolHandler` are `fun interface`s.

`registerTools` runs once, while the driver starts. `register` returns `false` and logs a warning when it rejects a tool.

## Rules

| Rule | Why |
| --- | --- |
| The name is `<modid>_<verb>_<noun>`: lowercase snake case, at most 64 characters, starting with your mod id (dashes become underscores). | The `mc_` prefix belongs to the built-in tools, and the mod id keeps two mods from claiming the same name. A mod called `mc` is rejected. |
| A name already registered is rejected and the first tool keeps it. | Registration order is the order Fabric loads the entrypoints; the winner must not depend on a guess. |
| Set all four annotations. The builder defaults to the MCP defaults (not read-only, destructive, not idempotent, open world), so say what is true. | Agents use them to decide which calls need confirmation. |
| The handler runs on the render thread unless you call `onRenderThread(false)`. | Screens, the player and the level may only be touched there. Keep the handler short; use `false` only for work that does not touch the game, and it then runs on an endpoint worker thread. |
| Return `ToolResult.text` with a JSON string, or `ToolResult.error` with a message that says what to do next. | Same convention as the built-in tools. |
| Throwing is allowed but reported as a generic error. | An exception in a handler, or in `registerTools`, is logged and turned into an error result. The endpoint and the other tools keep working. |

`ToolArguments` gives typed reads (`getString`, `getInt`, `getDouble`, `getBoolean`, `get` for lists and objects) that return `null` when the argument is missing, and `require*` reads that throw with a message the agent can read.

## Check it

Start a client with your mod and the driver, then call `tools/list`: your tools appear after the built-in ones, with the annotations you set. The log shows `Mod "mymod" registered MCP tool mymod_read_score`, or the reason a tool was ignored.
