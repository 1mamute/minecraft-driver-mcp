# Feature ideas

Ideas that are not bugs and not needed for 0.1.0. Untracked on purpose, like `plan.md`; turn an idea into an issue when it
is picked up. Tool names follow `mc_<verb>_<noun>`. Each idea says why it helps an agent and what is unclear.

## New tools

| Idea | Why | Open points |
| --- | --- | --- |
| `mc_select_hotbar_slot` | `mc_use` and `mc_click_slot` act on the held item; today an agent can only change it through the inventory screen | Slot 0-8 argument; also scroll-wheel semantics? |
| `mc_attack` / `mc_break_block` | Combat and mining are the other half of `mc_use`; hold a key for N ticks like `hold_ticks` | Needs a hold, since breaking takes several ticks; creative breaks instantly; report the block and the result |
| `mc_scroll` | Scrolls lists (world select, resource packs, server list) and the creative item grid | Mouse position or widget index; amount |
| `mc_hover` | Tooltips of inventory items need the real cursor over a slot, which no tool can move (see "Screen text capture" in known-issues) | Move the virtual cursor, then `mc_read_screen_text` |
| Cancel a pending join | `mc_join_world` / `mc_join_server` return at once; a stuck connect screen can only be left through `mc_close_screen` | May already work through the connect screen's Cancel button; check first |
| Drag / quick craft in `mc_click_slot` | The one container click type not covered | Needs a sequence of slots; see known-issues |
| Key rebinding awareness | `mc_set_key` and `mc_press_key` use fixed keys; a player may have rebound them | Read the key mapping from `Options` |

## Existing tools

- **Screenshot `max_width`.** Smaller images cost fewer tokens; screenshots are the fallback to text anyway.
- **Widget categories instead of class names** (`button`, `text_field`, `slider`, `checkbox`, `list`), with the raw class name in
  an optional field: same output in every environment and version. See
  [#39](https://github.com/1mamute/minecraft-driver-mcp/issues/39).
- **Logger names in `mc_read_log`** readable in a normal install; build-time name generation, blocked on the mappings license
  question in #39.
- **`mc_wait_for` conditions** beyond messages, screens and state, for example "inventory contains item", "entity near", "health
  below"; today an agent polls.

## Server and protocol

- **Streaming / notifications.** `mcpStatelessStreamableHttp` keeps no session, so the server cannot push; `mc_wait_for` blocks
  inside one call (cap 60 s). `mcpStreamableHttp` would allow progress and log notifications at the cost of sessions.
- **TLS or a documented tunnel** before any remote use; today localhost plus an optional bearer token only.
- **Per-instance log file** (custom log4j config) so two dev clients do not share `latest.log`.
- **Registry cleanup** of stale files left by forced kills, for example on the next start.

## Build and distribution

- **Publish to a Maven repository with a POM** that lists the bundled libraries, so a consumer can use `modLocalRuntime` instead of
  copying the jar into `run/mods/` (known-issues: "No `modLocalRuntime` in a consumer's dev run").
- **A second Minecraft version.** The real test of the Stonecutter setup (`ClassNames`, mixin targets and `client/` are the
  parts that change).
- **Release automation:** build on tag, attach the jar, draft release notes from Conventional Commits.
- **A smoke-test script** that starts a client and runs `initialize`, `tools/list` and `mc_get_state` (the manual check in
  `docs/testing.md`, scripted), usable locally and in CI where a display is available.

## Extension API

- Java and Kotlin example mods in the repo, built in CI against the jar.
- Late registration (tools are collected once at startup, so a mod that registers late is not seen).
- Typed list and object properties in `ToolDefinition` (today `get` reads lists and objects without a declared schema).
