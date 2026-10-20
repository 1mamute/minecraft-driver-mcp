# Testing

## Unit tests

`./gradlew build` runs the unit tests and lint. Unit tests cover code that does not need
Minecraft: port selection, the instance registry, and anything else in `server/` and
`tools/` that can run on its own. Code in `client/` needs the running game.

The mod tests load the bundled `gateway/catalogue.json`, compare it with the
compatibility fixture under `src/test/resources`, check that each definition has a
handler, and compare declared enums with supported game values. Intentional contract
changes update the fixture and tool documentation together. Gateway builds validate
the JSON schemas, annotations, unique names and matching release versions.

## Continuous integration

The `Build` workflow (`.github/workflows/build.yml`) runs `./gradlew build` on Linux and
Windows for every pull request and every push to `main`, and uploads the mod jar as an
artifact of the Linux job. It does not launch the game, so it does not replace the runtime
check below. Changes that only touch documentation skip the build jobs; the `Documentation`
workflow builds the site for those and fails on a dead link. `main` requires the `Build result`
check, which passes when the build passed or was skipped for a documentation-only change.

## Gateway checks

The gateway uses Node.js 22 or later. Run its TypeScript build and unit tests with:

```bash
npm --prefix gateway test
```

For a manual host check, in `gateway/` run `npm ci`, `npm test` and `npm pack`, then
install the local package into Claude Code and Codex using their stdio MCP
configuration. Start each host session before either game and verify tools are
available without manually toggling `/mcp`. Then start two modded clients with
distinct names and tokens. Check
`tools/list`, calls routed by each name and PID, the mismatch and unavailable-endpoint
errors, readiness timeout, token selection, and restart behavior. After restarting a
client, verify that its stable name still selects it while its former PID no longer
does. Verify the mod's `mc_wait_for` separately inside each world;
`mc_wait_for_instance` checks endpoint readiness only.

The no-game startup, two-client token routing and restart checks passed in both hosts. See
[Verification status](known-issues.md) for gaps and current evidence. For a release,
repeat the host check with `npx minecraft-driver-mcp@<version>` after publishing the
matching mod and gateway. Confirm the npm archive's integrity matches the reviewed
package before testing it.

## Runtime check

Run it for any change to `tools/`, `client/`, the mixin or the packaging, and say in the
PR when it was not done.

```bash
./gradlew :1.21.1:runClient
```

After the title screen appears, run the vanilla-only screen check. It navigates vanilla screens and makes no world changes:

```bash
node scripts/check-screen.mjs http://127.0.0.1:25890/mcp
```

`runClient` seeds `run/options.txt` (once) with the settings below and sets
`-Ddriver.unfocused=true`. The log prints the endpoint, normally
`http://127.0.0.1:25890/mcp`.

To check that the window does not take focus, sample `GetForegroundWindow` (via P/Invoke in PowerShell)
every 100 ms from before the launch. Without the flag the Minecraft window becomes foreground about 25 s
in; with it the foreground stays on the previous window. Then confirm `mc_get_state` and `mc_screenshot`
still answer with the window behind.

Then call the endpoint. With the MCP Inspector (`npx @modelcontextprotocol/inspector`) or
any HTTP client:

1. `initialize`, then `tools/list`: the tool names, schemas and annotations appear.
2. The tool you changed, on the title screen and again inside a world. Check the result
   and the error result (a missing argument, no world joined).
3. `mc_screenshot` when rendering is involved.

### Screenshot metadata

Call `mc_screenshot` on the title screen and in a world. Confirm the first content block is an inline PNG and the
second is JSON text. Decode the PNG header and compare its dimensions with `framebuffer`; compare `instance` with
`mc_list_instances`, `screen` and `player` with `mc_get_state`, and the timestamp with the capture time. Without a world,
`player` and `worldTime` are null. In a world, confirm day-time ticks advance unless time is stopped.

Repeat with at least two GUI scales and after resizing to dimensions not divisible by the scale. Check `gui` against
the screen size and click a visible widget at a point converted using `px * gui.width / framebuffer.width` and
`py * gui.height / framebuffer.height`. Keep fractional results. Confirm the top-left origin and repeat after a resize;
GUI dimensions round up, so dividing by nominal `guiScale` can differ at the edges.

Call `mc_screenshot` with an in-bounds `x`, `y`, `width`, `height` crop. Compare decoded pixels against the same
region of a full capture of a stable screen; native pixels should match without resizing. Add `max_width` smaller
than the crop, then larger than the crop: verify the first shrinks and the second preserves native size. Check the
PNG dimensions against `image`, while `framebuffer` remains the original size and `crop` records the source region.
Check a height that rounds down and a narrow resize that produces height one. Click a visible widget using
`imageToGui.offsetX + px * imageToGui.scaleX` and the matching Y formula; test crop-only, resize-only and combined.
Verify `isError` with a missing crop member, negative origin, zero/negative dimensions, zero `max_width`, non-integers,
an out-of-bounds edge and an overflowing rectangle such as `x:1,width:2147483647`. A no-argument capture still returns
the full original PNG followed by metadata.
Combine a crop and `max_width` with a fresh `save_as` filename. Compare the saved PNG bytes with the inline image
and its dimensions with `image`; confirm the sidecar has the same crop and GUI transform as the inline metadata.

### Hotbar selection

Give the player distinct items in hotbar slots 0 and 8. Call `mc_select_hotbar_slot` for each, then
`mc_read_inventory`; verify `selectedHotbarSlot` and the returned item. Repeat the same slot and select an empty
slot; selection should stay stable and the empty slot should return `item: null`. Use the selected item with
`mc_use` and verify the server observes that item (for example, a placeable block in slot 8). Check `isError`
for a missing slot, a non-integer slot, -1, 9, and a call from the title screen. The error should explain how to retry.

### Crosshair target

Start the development client at the title screen, then run the repeatable check against a disposable saved world
with cheats enabled. It changes blocks near `(0, 200, 0)`, summons and removes a tagged entity, and changes the player's
position, game mode and interaction-range attributes. Use a dedicated test world, not a normal playthrough:

```bash
node scripts/check-target.mjs http://127.0.0.1:25890/mcp <disposable-world-folder>
```

The script checks the direct endpoint without authentication, prints each passing scenario, disconnects after the
in-world checks, and exits unsuccessfully on a failed assertion. It does not launch or close the client process.

For `mc_get_target`, check the title-screen error directs the caller to join a world. In a world, allow a rendered
frame after aiming or moving before each comparison:

1. Aim at a known nearby block. Check `type: "block"`, namespaced block ID, integer coordinates and lowercase face.
   Compare `location` with the face hit, `origin` with the current camera eye position, and `distance` with their
   Euclidean distance in blocks.
2. Aim at an entity. Check `type: "entity"`, integer runtime ID, UUID and namespaced registry type, with no `block` property.
3. Aim at the sky and at a target beyond the applicable interaction range. Check `type: "miss"` and that both `block`
   and `entity` are absent; the returned location and distance are not reported as a successful hit.
4. Repeat in survival and creative, then change each interaction-range attribute. Check `blockReach` and `entityReach`
   follow the player's current values and targeting follows the renderer's result at the changed ranges.
5. Aim through fluid at a solid block in range. Check that the solid target is returned rather than the fluid; compare
   a bucket's separate fluid action when relevant.
6. Read the target repeatedly and verify no inventory, world, rotation or input state changes. Move or aim, then wait
   a rendered frame and verify the updated cached result. Confirm `tools/list` advertises no arguments and the four
   annotations: read-only and idempotent true, destructive and open-world false.

### Block observations

Start the client at the title screen, then run the repeatable check against a disposable saved world with cheats enabled:

```bash
node scripts/check-block.mjs http://127.0.0.1:25890/mcp <disposable-world-folder>
```

The script changes blocks near `(0, 300, 0)`, including negative chunk coordinates, and changes the player's position
and game mode. It checks `initialize`, `tools/list`, title-screen and coordinate errors, loaded air versus unloaded
chunks, block IDs and properties, synchronized sign text, absence of server-only chest inventory data, oversized banner
update tags, subsequent block updates and unchanged player/inventory state during repeated reads. It disconnects after the world checks.
For modded block entities, also compare returned update tags with the mod's client synchronization contract. Verify
a block awaiting its client entity reports `not_available`.

### Condition waits

Start the development client at the title screen, then run against a disposable saved world with cheats enabled:

```bash
node scripts/check-wait.mjs http://127.0.0.1:25890/mcp <disposable-world-folder>
```

Use a dedicated test world. The script clears inventory, changes game mode and player position, disables natural
regeneration, applies and removes effects, damages and heals the player, and places a platform near
`(16000, 240, 16000)`. It summons a uniquely tagged armor stand, removes its fixtures and disconnects.
It does not restore the disposable world's inventory, blocks, game rules or player state. Keep the client running
without physical game input while the check runs. The endpoint must permit direct calls without authentication.

The check covers `initialize`, `tools/list`, nested schemas and annotations, invalid argument shapes and bounds,
title-screen matching, and no-world timeouts with null player observations even for zero-count predicates.
In the world it checks exact registry IDs, inclusive inventory/entity count bounds, absence, exclusive feet-distance
radius boundaries, health points excluding absorption, and changes while waits are active. Commands use message
markers with `since` to synchronize server execution. Combined checks exercise default and explicit `any`, ordered
success reasons, `all` with buffered messages and current player/screen conditions, and disconnect during a wait.
Timeouts must include an actionable `nextStep`; disconnect must clear numeric observations to null.

The script also checks conditions that become true at different times without overlapping. Unit tests provide
precise coverage of same-poll `all` behavior, stalled snapshot deadlines and coroutine cancellation; this script
does not establish MCP transport cancellation. For a separate cancellation check, cancel an active long wait through
an MCP client, verify other observations still respond, and confirm a fresh wait can complete. Record actual runtime
results in [Verification status](known-issues.md); the presence of this script does not establish a passing run.

### Nearby entities

Start the development client at the title screen, then run against a disposable saved world with cheats enabled:

```bash
node scripts/check-entities.mjs http://127.0.0.1:25890/mcp <disposable-world-folder>
```

The check changes blocks and the player's position/game mode near `(0, 260, 0)`, summons tagged armor stands,
removes its fixtures and disconnects. It verifies the handshake, schema/annotations, no-world and argument errors,
exact/prefix type and literal name semantics, combined filters before limiting, nearest-first ordering, radius
boundaries, default and maximum bounds, truncation metadata, and unchanged player/inventory state during reads.
For modded entities, also compare names and registry IDs with the mod's client synchronization contract.

### Entity aiming

Start the development client at the title screen, then run against a disposable saved world with cheats enabled:

```bash
node scripts/check-entity-aim.mjs http://127.0.0.1:25890/mcp <disposable-world-folder>
```

The check changes difficulty, time, game mode, player position and blocks near `(0, 260, 0)`, summons tagged adult
and baby zombies, temporarily force-loads one remote server chunk to keep a fixture available for cleanup while
unloading it from the client, removes fixtures and disconnects. It checks
`initialize`, `tools/list`, title-screen/disconnect errors, eye positions and world-space bounding boxes for different
sizes, default/explicit eye and center aiming, preserved coordinate aiming, repeated calls and updated geometry after
movement. It also checks mixed/incomplete inputs, invalid types and nonfinite coordinates, removed/unloaded IDs, and
successful aiming through a wall or beyond reach while the renderer still reports a block or miss.

For modded entities, compare eyes and bounds with their pose and size implementation; eye positions may lie outside
collision bounds. Also repeat with pose changes and dedicated-server entity tracking.

### Player effects

For `mc_read_effects`, check the title-screen error and an empty in-world list. With cheats enabled, run
`/effect give @s minecraft:speed 60 1`, verify its ID, amplifier `1` and decreasing `remainingTicks`, then run
`/effect give @s minecraft:night_vision infinite` and verify `remainingTicks: -1`. Remove each effect with
`/effect clear @s` and confirm the list becomes empty. For modded effects, verify namespaced IDs and, when more
than 100 are active, a sorted 100-entry list with `total` and `truncated: true`.

### Attacking

Start the client at the title screen, then run against a disposable saved world with cheats enabled:

```bash
node scripts/check-attack.mjs http://127.0.0.1:25890/mcp <disposable-world-folder>
```

Use a dedicated test world: the check changes blocks near `(0, 280, 0)`, game mode and player position,
clears inventory, and summons entities. It temporarily sets the tick rate to 1 and restores it to 20.
Keep the game window behind another application and avoid physical game input while the check runs, so mouse
movement cannot change the aimed targets between assertions.
The runtime check must cover `initialize`, `tools/list`, schema/annotations and the following scenarios. Record the
actual results in [Verification status](known-issues.md); these instructions alone do not establish a passing run.

1. Check no-world errors and integer bounds: omitted/default 0, 0, 200, -1, 201, fractional and wrong-type values.
2. In creative, press once at a nearby block, then test a positive hold. In survival, use a breakable block and an
   unbreakable block to distinguish `block_changed` from duration expiry. Compare the initial `target` with
   `mc_get_target`, check `requestedHoldTicks`, held `elapsedTicks` and outcome, and confirm `serverConfirmed:false`.
   Do not treat a client-predicted block change as server acceptance.
3. Aim at an entity and then the sky. For each, compare hold 0 and 200: only one press should occur, without an
   entity attack loop, and `blockChanged` must be null. Do not infer entity damage from the result.
4. During a long block hold, call observation tools and a second attack. Observations must continue; the second
   attack must fail busy without replacing the first. Change the aimed block or aim away and verify prompt release.
   Change the original block state separately and verify `block_changed` stops before a replacement is attacked.
5. Open a screen or overlay mid-hold, start item use or otherwise make hands busy, disconnect, and replace the
   player/world (for example through respawn or dimension travel). Verify the applicable stop outcome and no
   automatic resume after closing the screen, finishing item use or rejoining.
6. Cancel a pending request and exercise the wall-clock timeout with client ticks slowed or stalled. Verify release even if
   cancellation prevents result delivery. After every stop, wait longer than the requested hold and verify no
   continued block breaking or entity swings; start a fresh attack to check ownership was cleared.
7. Check refusal with an existing screen/overlay, busy hands, spectator mode, an already-down attack key and a
   disabled held item. Verify rejected calls do not acquire or release somebody else's attack input.

Repeat survival mining with the window behind another application and the mouse released. With
`driver.unfocused=true`, the owned hold must progress without requiring physical mouse capture. Change aim to
an instantly breakable block during a hold and verify that block remains intact: Minecraft refreshes its pick
inside the tick, so checking only at the start of the tick misses that change.

Also test dedicated-server rejection and mod interception separately; record those as unverified until exercised.

### Dropping held items

In survival and creative mode, put a known stack in the selected hotbar slot. Call `mc_drop_item`, then
`mc_read_inventory` after server updates: one item should be removed. Repeat with `full_stack: true` and
confirm the slot becomes empty. Empty hand should return a successful no-op. Check the title-screen,
open-screen, spectator and non-boolean argument errors. Select another slot immediately before dropping
and verify the server removes items from that slot. The container alternative is `mc_click_slot` with
`action: "throw"` and `button: 0` (one) or `1` (stack).

### Screenshot evidence

Run this when changing screenshot saving, after `initialize` and `tools/list`:

1. Without `driver.screenshotDirectory`, call `mc_screenshot` with no arguments: it must return inline PNG first and
   metadata second without writing files. Call `{"save_as":"disabled.png"}` and check for an actionable opt-in error.
2. Restart with `-Ddriver.screenshotDirectory=<temporary-directory>`. Call `{"save_as":"title-1.png"}` at the title screen.
   Check that `evidence.pngPath` and `evidence.metadataPath` name the new files in that directory. Decode the inline PNG
   and compare its bytes with the saved PNG. The JSON sidecar must match inline capture metadata except `evidence` is null.
3. Repeat the filename and verify an error leaves both files unchanged. Separately precreate only the PNG and only the JSON
   for two other names; each save must fail without changing that file or creating its counterpart.
4. Try `{"save_as":"../outside.png"}`, an absolute filename, and `{"save_as":123}`. Each must return an actionable error
   without creating files. Where symbolic links are available, check linked target files and directory ancestors too.
5. Join a world and save with a fresh name. Verify the sidecar contains the current instance, player/world state, dimensions
   and capture timestamp, and the saved PNG still matches the inline bytes. A subsequent capture without `save_as` writes
   no additional files.

### Auth token

Run this when you touch `McpEndpoint`, `TokenAuthenticator` or the registry format. Start the client twice
(use your own `--gameDir`, `--username`, `-Ddriver.port` and `-Ddriver.registry`):

1. Without `-Ddriver.token`: `initialize`, `tools/list` and `mc_get_state` work with no header.
2. With `-Ddriver.token=<value>`: no `Authorization` header, a wrong token and a plain GET return `401`
   (with `WWW-Authenticate: Bearer`); `Authorization: Bearer <value>` returns `200` for `initialize`,
   `tools/list` and `mc_get_state`.
3. The registry file has `"authRequired": true` and no token, and `logs/latest.log` does not contain the token.

### Options a driven client needs

A fresh game directory has defaults that block an agent or annoy the developer. Put these
lines in the client's `options.txt` (the `prepareClientRun` task does it for `run/`):

```properties
onboardAccessibility:false
pauseOnLostFocus:false
soundCategory_master:0.0
```

`onboardAccessibility:false` skips the accessibility screen that opens on first launch
and would otherwise eat the first click. `pauseOnLostFocus:false` keeps the game running
while its window is not in front. `-Ddriver.unfocused=true` does the same without touching
`options.txt` (`GameRendererMixin`), so `runClient` works in a fresh game directory too.

To check that, run a client with a game directory whose `options.txt` has `pauseOnLostFocus:true`
and `-Ddriver.unfocused=true`, join a world, give the window focus (`WScript.Shell.AppActivate`), then
start Notepad so the window loses it (`mc_get_state` shows `windowFocused: false`). `mc_close_screen`
and `mc_set_key` `inventory` must keep working. Without the flag the pause screen comes back after
every `mc_close_screen`, and `inventory` fails with a message about the window.

## Two clients

Run this check when you touch ports, the registry or names.

1. Give each client its own run directory and a distinct account name. Loom ignores the `runClient` `workingDir`, so pass `--gameDir <dir>` and `--username <name>` in the run's program arguments.
2. Start the clients one after the other. Two Gradle builds at once can corrupt the Kotlin
   cache, so wait until the first client's window is open.
3. Check that they listen on `25890` and `25891`, that each `mc_list_instances` shows both,
   and that closing one removes its file from `~/.minecraft-driver-mcp/instances`.
4. Repeat with fixed ports (`-Ddriver.port`), and check that starting a second client on a
   taken fixed port fails with a clear message.

## Behavior learned while driving the client

- **Widgets react to hover.** Hover comes from the real cursor, so `mc_click` marks the
  target hovered before it clicks.
- **Some buttons are not in `children()`.** Screens that draw their own tiles (for
  example a mod's battle menu) are not exposed by standard child traversal. Use captured text or a screenshot and
  explicit GUI coordinates; mod-specific inspection belongs in an adapter.
- **Replies are asynchronous.** A command's chat reply arrives after the command returns.
  Send with `mc_send_chat`, then poll `mc_read_messages` with the last `latest` as `since`.
  `mc_wait_for` with `message` and `since` set to the last `latest` replaces that manual polling.
- **Log lines.** `mc_read_log` shows what the game logs from the moment the mod starts (earlier lines are only in `logs/latest.log`). Provoke a line (a chat command with a bad argument, or a world load) and read with `min_level` `WARN` and `contains`. It defaults to `INFO` and up; pass `since` with the last `latest` to read only new lines.
- **Confirmation dialogs.** Actions such as forfeiting open a second screen; click its
  confirm button after it appears.
- **Screenshots** come from the game's framebuffer, never from the desktop, so they work
  with the window behind other windows.
- **Relative aiming.** After joining a world, record `mc_get_state` and call `mc_look {yaw:90,pitch:0}` twice: each
  result must turn right another 90 degrees, with yaw in `[-180,180)`. Call `{yaw:0,pitch:-180}` to look straight up
  (`pitch:-90`), then `{yaw:0,pitch:15}` to reach `-75`. Check the downward clamp with `pitch:180`, and a full yaw turn
  with `yaw:360`. `mc_look_at` must still face a supplied world position. After disconnecting, `mc_look` must return an
  actionable join-world error. Missing, wrong-type and non-finite deltas must return an error without changing the view.
- **Inventory and containers.** Switch a creative world to survival with `mc_send_chat` (`/gamemode survival`), fill slots with `/give` and `/item replace`, then call `mc_read_inventory`. For a container, place a chest with `/setblock`, aim with `mc_look_at` and open it with `mc_use`; a villager (`/summon villager ... {NoAI:1b}`) works the same way and fills `offers`. Slot numbers in `mc_read_container` are menu indexes, so the player's hotbar is not 0-8 there; `mc_read_inventory` uses inventory indexes. The world list needs a click on the entry (a point click) before `Play Selected World` is enabled.
- **Opening the inventory.** `mc_set_key` with `name` `inventory` opens the player's inventory and, on a second press, closes it or any other container screen (any other screen gives an error pointing to `mc_close_screen`). In creative mode it is the creative item screen. Its tabs are not widgets: click one with a point click (`x`, `y` in GUI units). In the creative item grid slots 0-44 are the visible items (group `creative`) and 45-53 the hotbar; on the inventory tab the slots follow the player menu (crafting, armor, main, hotbar, offhand) and the last one is the trash.
- **Clicking slots.** `mc_click_slot` takes the menu indexes `mc_read_container` shows (a chest is 0-26, then main 27-53 and hotbar 54-62; a merchant is 0-1 payment, 2 result, 3-29 main, 30-38 hotbar). A merchant needs its trade selected first: `mc_click` the trade button by `index` from `mc_list_widgets`, then `quick_move` slot 2 takes the result. The returned state is the client prediction; call `mc_read_container` to see what the server kept.
- **Typing.** `mc_type_text` sends characters to the focused widget of the open screen; `mc_click` the field first (the direct-connection box starts focused). It errors when no widget takes the text. `mc_press_key` sends one key press and release, `times` repeats it (`backspace` with a large `times` empties a field). Shift and control are not offered: widgets read them from the physical keyboard, so a modifier sent with the event does nothing (ctrl+a does not select all). `escape` with no screen in a world opens the pause screen; `mc_close_screen` or a second `escape` closes it.
- **Joining and leaving.** `mc_disconnect` closes the level's connection before `Minecraft.disconnect`, as the pause screen does; calling `disconnect` alone leaves the integrated server running and hangs the render thread. The call blocks while a singleplayer world saves. `mc_join_server` and `mc_join_world` return at once; follow with `mc_wait_for` or `mc_get_state`. A failed server connection leaves a disconnect screen that `mc_read_screen_text` can read.
- **Screen text** is captured while the screen renders, so call `mc_read_screen_text` after the
  screen has been open for a frame. A tooltip shows only while the real
  cursor hovers its target, so the tool cannot reach one the agent cannot hover.

### Screen text and widgets

Run `node scripts/check-screen.mjs <endpoint>` after the title screen appears. The check navigates vanilla screens only
and does not change a world. It checks measured text clicks, Controls to Key Binds nested paths, preserved root indexes,
inactive-widget refusal, repeated Reset text disambiguation, single-use capture, expired/replaced captures, returning
to a previous screen instance, and screenshots. Use the English language and default key bindings; the repeated Reset
fixture clicks the first disabled reset button. Both the development run and the packaged-driver run with Cobblemon
1.8.1 passed. Record observed results in [Verification status](known-issues.md).

For the original modded-screen reproduction, prepare a disposable two-player Cobblemon 1.8.1 battle with English
labels, keep the first client on its initial battle menu, then run:

```bash
node scripts/check-cobblemon-screen.mjs <endpoint> [evidence-directory]
```

The check opens Forfeit through measured text, verifies the confirmation's raw `(0, 0, 113, 45)` bounds are uncertain,
and saves a screenshot and JSON evidence (default `build/check-screen/`). It cancels, reopens through a fresh text
capture and accepts, ending the disposable battle. The explicit confirmation coordinates in this test fixture come
from Cobblemon 1.8.1's layout; they are not generic driver logic. This check passed against the packaged driver.
The private accept/decline fields are not `ContainerEventHandler.children`; generic traversal does not inspect them.

## Debugging

`./gradlew :1.21.1:runClient --debug-jvm` attaches a JDWP debugger. Break in a tool in
`tools/` or an object in `client/` to follow a call from the HTTP request to the game.
