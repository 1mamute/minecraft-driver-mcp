# Testing

## Unit tests

`./gradlew build` runs the unit tests and lint. Unit tests cover code that does not need
Minecraft: port selection, the instance registry, and anything else in `server/` and
`tools/` that can run on its own. Code in `client/` needs the running game.

## Runtime check

Run it for any change to `tools/`, `client/`, the mixin or the packaging, and say in the
PR when it was not done.

```bash
./gradlew :1.21.1:runClient
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
while its window is not in front.

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
  example a mod's battle menu) need the screen class inspected; the tool lists them
  separately when it can.
- **Replies are asynchronous.** A command's chat reply arrives after the command returns.
  Send with `mc_send_chat`, then poll `mc_read_messages` with the last `latest` as `since`.
  `mc_wait_for` with `message` and `since` set to the last `latest` replaces that manual polling.
- **Confirmation dialogs.** Actions such as forfeiting open a second screen; click its
  confirm button after it appears.
- **Screenshots** come from the game's framebuffer, never from the desktop, so they work
  with the window behind other windows.
- **Inventory and containers.** Switch a creative world to survival with `mc_send_chat` (`/gamemode survival`), fill slots with `/give` and `/item replace`, then call `mc_read_inventory`. For a container, place a chest with `/setblock`, aim with `mc_look_at` and open it with `mc_use`; a villager (`/summon villager ... {NoAI:1b}`) works the same way and fills `offers`. Slot numbers in `mc_read_container` are menu indexes, so the player's hotbar is not 0-8 there; `mc_read_inventory` uses inventory indexes. The world list needs a click on the entry (a point click) before `Play Selected World` is enabled.
- **Screen text** is captured while the screen renders, so call `mc_read_screen_text` after the
  screen has been open for a frame. A tooltip shows only while the real
  cursor hovers its target, so the tool cannot reach one the agent cannot hover.

## Debugging

`./gradlew :1.21.1:runClient --debug-jvm` attaches a JDWP debugger. Break in a tool in
`tools/` or an object in `client/` to follow a call from the HTTP request to the game.
