# Architecture

## Layers

```text
agent ──HTTP──▶ server/McpEndpoint ──▶ tools/*Tools ──▶ client/Client* ──▶ Minecraft
                  (Ktor + MCP SDK)     (schemas, JSON)    (render thread)
```

- `server/` knows Ktor, the MCP SDK, ports and the instance registry. It does not know
  Minecraft.
- `tools/` defines what the agent sees: names, input schemas, annotations and result
  JSON. It calls `client/` and does not import `net.minecraft`.
- `client/` is the only code that touches the game. Each object owns one concern.
- `DriverBootstrap` reads the system properties, builds the pieces and hooks the client
  lifecycle. The Java entrypoint `DriverMod` only calls it.

Three mixins in `mixin/` capture screen text for `client/ScreenText`: `ScreenMixin` marks the
start and end of `Screen.renderWithTooltip`, `GuiGraphicsMixin` records the two
`GuiGraphics.drawString` sinks, and `ClientTextTooltipMixin` records tooltip lines. The pure
collector `TextFrame` turns one frame's text into the data `mc_read_screen_text` returns.

`ClientInventory` maps the player's `Inventory`, the open `AbstractContainerMenu` and merchant offers into the data classes of `InventoryModel.kt`; `SlotLabels` and `MenuSlotGroups` (no Minecraft types) decide each slot's group: hotbar, main, armor and offhand for the player's slots, and `input`, `fuel`, `output`, `bottle`, `ingredient`, `result`, `grid` and similar for furnaces, brewing stands, crafting tables, anvils and a few more menus (`creative` for the creative item grid, `trash` for its delete slot; other menus stay `container`). A slot's number is its place in the menu's slot list, because the creative inventory tab wraps slots whose own `index` is 0. `mc_read_inventory` and `mc_read_container` return them. Item stacks are sprites, so `mc_read_screen_text` never sees them. `ClientSlots` sends a slot click through the open screen's `slotClicked` (reached by `AbstractContainerScreenAccessor`) for `mc_click_slot`, so screens that override the click, such as the creative inventory, behave as under the mouse; `SlotClickPlanner` (no Minecraft types) validates the arguments.

Other mods add tools through the `minecraft-driver-mcp` Fabric entrypoint ([extending.md](extending.md)). `api/` is the public, Minecraft-free surface (`DriverExtension`, `ToolRegistrar`, `ToolDefinition`, `ToolArguments`, `ToolResult`). `ExtensionLoader` calls each extension once in `DriverBootstrap.start`, catching failures per extension; `ExtensionRegistry` validates names with `ToolNames` and rejects duplicates with a warning; `ExtensionTools` adds the accepted tools to each per-request `Server`, runs handlers on the render thread by default and turns any exception into an error result.

Because of this split, supporting a new Minecraft version means changing `client/` (and
the mixin), not the tools or the server.

## Lifecycle

`DriverMod.onInitializeClient` calls `DriverBootstrap.start()`, which:

1. registers `MessageLog` so received chat and system messages are recorded;
2. picks the host and port ([several clients](#several-clients));
3. starts `McpEndpoint` on a Ktor CIO engine without waiting;
4. writes the instance file to the registry.

On `CLIENT_STOPPING` the endpoint stops and the instance file is removed. A crashed
client leaves its file behind; `InstanceRegistry.list` ignores files whose process is
gone.

## Threading

Ktor handles each request on its own worker. The game only allows screens, the player,
the level, input and the framebuffer to be used from the render thread, so a tool runs:

```text
Ktor worker: parse arguments ─▶ RenderThread.call { touch the game } ─▶ encode JSON
```

`RenderThread.call` suspends the coroutine, queues the block with `Minecraft.execute`
and resumes with its result or exception. The worker is never blocked, and the game loop
only runs the short block. Tools that wait for something (`mc_wait_for`) must poll
by suspending between short render-thread reads, never by holding the render thread.

Chat and system messages arrive on the client's network thread and are appended to
`MessageLog`, a bounded buffer with a sequence number. `mc_read_messages(since)` returns
only newer ones, so an agent can read a reply that arrives asynchronously.

The game's own log goes the same way: `LogCapture` attaches a Log4j2 appender to the root logger at start
and detaches it on shutdown. Lines land in `LogBuffer` (1000 lines, messages cut at 2000 characters), and
`mc_read_log` filters by `since`, minimum level and text. The appender runs on whichever thread logs, only
copies strings under a short lock and ignores events it causes itself. Capture is per process, so two
clients never see each other's lines even though they share `logs/latest.log`.

## Transport

The endpoint is `mcpStatelessStreamableHttp` at `/mcp` on `127.0.0.1`. Stateless means
each request is independent: restarting the agent or the client needs no session
handshake. The cost is that the server cannot push notifications or stream progress
(see [known-issues.md](known-issues.md)).

The MCP SDK enables DNS-rebinding protection by default, so a web page cannot reach the
local endpoint through a hostile hostname. Keep the bind address on localhost: the tools
control the player.

`-Ddriver.token=<value>` turns on a shared bearer token. `TokenAuthenticator` (pure, in `server/`)
hashes the configured token with SHA-256 and compares the hash of the presented
`Authorization: Bearer ...` value with `MessageDigest.isEqual`, so the comparison takes the same
time for any input length. `McpEndpoint` installs it as a Ktor pipeline interceptor ahead of the
MCP routes: a missing or wrong header gets `401` with `WWW-Authenticate: Bearer` and the request
ends there, so no tool runs. An unset or empty property keeps the endpoint open. The token is
never logged. The instance registry records only `authRequired` (default `false`; files without
the field still read as open), so `mc_list_instances` shows which clients need a token without
exposing it. Configure the agent with the header, for example
`claude mcp add --transport http alice http://127.0.0.1:25901/mcp --header "Authorization: Bearer <token>"`.

## Several clients

Developers test multiplayer with two or more clients, so nothing is global to the
machine except the registry:

- **Port.** `PortSelector` takes `driver.port` strictly when set (a fixed port a
  configured agent points at, and it fails if taken), otherwise the first free port from
  25890, scanning 100 ports.
- **Name.** `driver.name` (default: the player name) goes into the MCP server name and
  instructions, so an agent connected to several clients can tell them apart.
- **Discovery.** `InstanceRegistry` writes `<pid>.json` into
  `~/.minecraft-driver-mcp/instances` (`driver.registry` overrides it). `mc_list_instances`
  returns the live ones with their URLs.
- **Game directories.** Each client needs its own run directory; Minecraft requires it.

## Packaging

Everything ships in one jar and does not depend on Fabric Language Kotlin:

- The `bundled` configuration holds the Kotlin stdlib, the MCP SDK and Ktor. The
  `afterEvaluate` block in `build.gradle.kts` resolves it and adds each module to Loom's
  `include`, which nests the jars (jar-in-jar). Fabric Loader loads nested jars itself.
- slf4j and the JetBrains annotations are not bundled; Minecraft provides them.
- `implementation` extends `bundled`, so the code compiles against the same libraries.

A new library goes into `bundled`. Check the jar size and that it does not clash with a
library Minecraft or another mod already provides.

## Build and Minecraft versions

Stonecutter builds one jar per Minecraft version from one source tree.

- `settings.gradle.kts` lists the versions; `stonecutter.gradle.kts` names the active
  one. Each version has `versions/<mc>/gradle.properties` with its Minecraft, Fabric API
  and Parchment versions.
- Shared versions (Loader, Kotlin, Loom, libraries) are in the root `gradle.properties`.
- Version-specific source is marked with Stonecutter comments (`//? if >=1.21.5 {`).
  Past a few lines, move the code into one class per version instead.
- The root project must not apply `java` or `base`; Stonecutter rejects a buildable root.

See [dependencies.md](dependencies.md) for how to move versions forward.
