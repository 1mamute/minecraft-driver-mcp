# Architecture

## Layers

```text
agent --stdio--> gateway --HTTP--> server/McpEndpoint --> tools/*Tools --> client/Client* --> Minecraft
```

- `server/` knows Ktor, the MCP SDK, ports and the instance registry. It does not know
  Minecraft.
- `gateway/catalogue.json` holds the built-in names, descriptions, input schemas and
  all four MCP annotations. The TypeScript gateway reads it directly; Gradle bundles
  the same file into the mod as a resource.
- `tools/` attaches handlers to catalogue definitions and builds result JSON. It calls
  `client/` and does not import `net.minecraft`.
- `client/` is the only code that touches the game. Each object owns one concern.
- `DriverBootstrap` reads the system properties, builds the pieces and hooks the client
  lifecycle. The Java entrypoint `DriverMod` only calls it.

Three mixins in `mixin/` capture screen text for `client/ScreenText`: `ScreenMixin` marks the
start and end of `Screen.renderWithTooltip`, `GuiGraphicsMixin` records the two
`GuiGraphics.drawString` sinks, and `ClientTextTooltipMixin` records tooltip lines. The pure
collector `TextFrame` turns one frame's text into the data `mc_read_screen_text` returns.

Two mixins serve `-Ddriver.unfocused=true`, for a developer who keeps working in other windows. `WindowMixin` creates the window without focus. `GameRendererMixin` makes `GameRenderer.render` read the "pause on lost focus" option as off: vanilla opens the pause menu every frame the window is inactive while that option is on, so a screen closed by `mc_close_screen` would come back at once and `mc_set_key` `inventory` would fail. The option itself and `options.txt` stay untouched. Without the flag the vanilla behavior stays, and `mc_get_state` reports `windowFocused: false`.

`ClientInventory` maps the player's `Inventory`, the open `AbstractContainerMenu` and merchant offers into the data classes of `InventoryModel.kt`; `SlotLabels` and `MenuSlotGroups` (no Minecraft types) decide each slot's group: hotbar, main, armor and offhand for the player's slots, and `input`, `fuel`, `output`, `bottle`, `ingredient`, `result`, `grid` and similar for furnaces, brewing stands, crafting tables, anvils and a few more menus (`creative` for the creative item grid, `trash` for its delete slot; other menus stay `container`). A slot's number is its place in the menu's slot list, because the creative inventory tab wraps slots whose own `index` is 0. `mc_read_inventory` and `mc_read_container` return them. Item stacks are sprites, so `mc_read_screen_text` never sees them. `ClientSlots` sends a slot click through the open screen's `slotClicked` (reached by `AbstractContainerScreenAccessor`) for `mc_click_slot`, so screens that override the click, such as the creative inventory, behave as under the mouse; `SlotClickPlanner` (no Minecraft types) validates the arguments.

Other mods add tools through the `minecraft-driver-mcp` Fabric entrypoint ([extension-api.md](../extension-api.md)). `api/` is the public, Minecraft-free surface (`DriverExtension`, `ToolRegistrar`, `ToolDefinition`, `ToolArguments`, `ToolResult`). `ExtensionLoader` calls each extension once in `DriverBootstrap.start`, catching failures per extension; `ExtensionRegistry` validates names with `ToolNames` and rejects duplicates with a warning; `ExtensionTools` adds the accepted tools to each per-request `Server`, runs handlers on the render thread by default and turns any exception into an error result.

Because of this split, supporting a new Minecraft version means changing `client/` (and
the mixin), not the tools or the server.

### Shared tool catalogue

`gateway/catalogue.json` is the authoritative built-in catalogue, maintained inside
the gateway package. It contains a release version and MCP tool definitions. It has
no separate project, build task or distribution artifact.

Gradle's `processResources` copies it to `minecraft-driver-mcp/catalogue.json` inside
the mod jar. `tools/BuiltInTools` deserializes that resource into SDK `Tool` values
and attaches the existing handlers through `ToolSupport.addGuardedTool`. No gateway
process or Node.js installation is needed to build or run the mod.

Handler limits and supported argument values remain with the game handlers. Tests
compare the JSON enums with the game handlers and preserve the direct endpoint's
definitions through a compatibility fixture. Dynamic extension definitions come
from running clients and stay outside the built-in catalogue.

## Local gateway

The Node.js gateway in `gateway/` is the recommended connection method for coding
agents. It starts as an MCP stdio server with the agent session, advertises built-in
tools before Minecraft starts, then forwards calls to the
existing per-client HTTP `/mcp` endpoints bound to `127.0.0.1`. It reads the built-in
definitions from its own `catalogue.json`, which is also bundled into the mod. This
keeps names, schemas and annotations in one source of truth.

Direct HTTP access to the mod remains available for specialized setups, extension
tools and endpoint debugging.

The gateway replaces `mc_list_instances` with a local registry view; the optional
`check_ready:true` adds endpoint health and auth/version metadata. It adds
`mc_wait_for_instance` to wait for a selected registry record to become ready.
Ordinary tool calls take optional `instance_name` or
`instance_pid` routing fields; when both are supplied they must refer to the same
record. The gateway removes those routing fields before forwarding the original tool
arguments. It resolves the selected endpoint once and never retries a game action on
another client. Registry names are intended to remain stable across restarts; process
PIDs are temporary. Without a selector there must be exactly one live registry record;
multiple live records require explicit selection, including when only one endpoint
is ready. Multiple records with the same name must be selected by PID. Token
mappings have unique instance names, so duplicate-name records cannot be authenticated
by name. The gateway does not start or stop Minecraft clients and does not
load tools added dynamically by extension mods.

The config file defaults to `~/.minecraft-driver-mcp/gateway.json`, with `--config`
to select another path. If `registryDirectory` is omitted, the gateway uses the mod's
default registry directory. Other configured paths are resolved relative to the config
file. Its `tokens` object maps unique instance names to environment variable names;
token values stay in the process environment and are not stored in this file.
Connect and request timeouts default to 5000 and 70000 milliseconds. Calls are not
retried; canceling an MCP request does not undo an action already sent to the game.
Each operation opens a fresh upstream MCP client, initializes and checks the mod
release, forwards at most one tool call, then closes the transport. No notification
stream or persistent game selection is kept. The connection deadline covers the
whole handshake, and the request deadline includes that handshake. Host disconnects
cancel outstanding work.
Both processes are local tools with access to the player's game, so their stdio host
and the local machine must be trusted.

## Lifecycle

`DriverMod.onInitializeClient` calls `DriverBootstrap.start()`, which:

1. registers `MessageLog` so received chat and system messages are recorded;
2. picks the host and port ([several clients](#several-clients));
3. starts `McpEndpoint` on a Ktor CIO engine without waiting;
4. writes the instance file to the registry.

On `CLIENT_STOPPING` the endpoint stops and the instance file is removed. A crashed
client leaves its file behind; `InstanceRegistry.list` ignores files whose process is
gone or whose available process start time confirms a reused PID.

## Threading

Ktor handles each request on its own worker. The game only allows screens, the player,
the level, input and the framebuffer to be used from the render thread, so a tool runs:

```text
Ktor worker: parse arguments ─▶ RenderThread.call { touch the game } ─▶ encode JSON
```

`RenderThread.call` suspends the coroutine, queues the block with `Minecraft.execute`
and resumes with its result, exception or error (a `NoSuchMethodError` from a version mismatch fails the request instead of
hanging it; only a `VirtualMachineError` is rethrown). The worker is never blocked, and the game loop
only runs the short block. Tools that wait for something (`mc_wait_for`) must poll
by suspending between short render-thread reads, never by holding the render thread.

Held movement keys live in `ClientInput`, which applies them at the end of every client tick. As in vanilla, an
open screen takes the keyboard and releases all keys, so a key set with `mc_set_key` is up while a screen is open and
resumes when it closes, like a physically held key.

Chat and system messages arrive on the client's network thread and are appended to
`MessageLog`, a bounded buffer with a sequence number. `mc_read_messages(since)` returns
only newer ones, so an agent can read a reply that arrives asynchronously. Action-bar (overlay) text is skipped so it
cannot evict chat, and the result has `truncated: true` when messages after `since` were already dropped from the
200-entry buffer.

The game's own log goes the same way: `LogCapture` attaches a Log4j2 appender to the root logger at start
and detaches it on shutdown. Lines land in `LogBuffer` (1000 lines, messages cut at 2000 characters), and
`mc_read_log` filters by `since`, minimum level and text. The appender runs on whichever thread logs, only
copies strings under a short lock and ignores events it causes itself. Capture is per process, so two
clients never see each other's lines even though they share `logs/latest.log`.

## Transport

The endpoint is `mcpStatelessStreamableHttp` at `/mcp` on `127.0.0.1`. Stateless means
each request is independent: restarting the agent or the client needs no session
handshake. The cost is that the server cannot push notifications or stream progress
(see [known-issues.md](known-issues.md)). `McpEndpoint` builds one MCP `Server` with all
tools at the first request and reuses it; the SDK opens a session for each request and
closes it when the response is sent.

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
  25890, scanning 100 ports. The check and the bind are two steps, so clients started
  together can pick the same port; `EndpointBinder` retries on the next port after a
  `BindException` when no port is fixed, and fails with the same message as the check
  when one is. `DriverBootstrap.start` logs any startup failure and leaves the driver off,
  so the game keeps running.
- **Name.** `driver.name` (default: the player name) goes into the MCP server name and
  instructions, so an agent connected to several clients can tell them apart.
- **Discovery.** `InstanceRegistry` writes `<pid>.json` into
  `~/.minecraft-driver-mcp/instances` (`driver.registry` overrides it). `mc_list_instances`
  returns the live ones with their URLs. Files are written to a temporary file and moved
  into place atomically. Readers accept only canonical filenames with a positive PID matching
  the payload. Registration includes optional `processStartEpochSeconds`, a positive whole
  Unix second from `ProcessHandle.info().startInstant()`. Readers hide confirmed start-time
  mismatches; registration cleans up valid records with a confirmed dead or reused PID.
  Missing or `null` identity and unavailable process metadata preserve PID-only checks.
  Invalid and unreadable files are skipped and kept. The gateway never deletes files.
  It batches asynchronous OS lookups (`ps` with `LC_ALL=C` and `TZ=UTC0` on Linux/macOS,
  PowerShell calling .NET `Process.GetProcessById` on Windows), bounds lookup to five seconds and respects cancellation.
  The Windows lookup disables module autoloading so inspecting a PID does not trigger PowerShell module discovery.
  It prefers `pwsh.exe` to avoid legacy PowerShell's cold startup cost, falling back to `powershell.exe` only when
  the preferred executable is missing or cannot be started. An inspection failure or timeout does not launch a second shell.
  Unsupported systems and lookup failures retain PID liveness. Optional readiness checks
  never supply stale-process evidence or change selection; duplicate names remain ambiguous.
- **Game directories.** Each client needs its own run directory; Minecraft requires it.

## Packaging

The mod ships in one jar and does not depend on Fabric Language Kotlin:

- The `bundled` configuration holds the Kotlin stdlib, the MCP SDK and Ktor. The
  `afterEvaluate` block in `build.gradle.kts` resolves it and adds each module to Loom's
  `include`, which nests the jars (jar-in-jar). Fabric Loader loads nested jars itself.
- slf4j and the JetBrains annotations are not bundled; Minecraft provides them.
- `implementation` extends `bundled`, so the code compiles against the same libraries.
- `gateway/catalogue.json` is copied into the mod as a resource, with no additional jar.

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
- `gateway/` uses Node.js 22 or later and is built and tested separately with
  `npm --prefix gateway test`. The npm package is named `minecraft-driver-mcp`;
  publishing a release is a separate step.

See [dependencies.md](dependencies.md) for how to move versions forward.
