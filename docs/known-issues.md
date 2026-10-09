# Known issues

Limits a user can run into, with workarounds. Internal verification notes are in
[Verification status](contributing/known-issues.md).

## Supported scope

- Only Minecraft **1.21.1** is built and tested. Other versions need a new build.
- The mod is a client mod for development and testing. It is not meant for gameplay automation on public servers.
- The project is at 0.x. Tool names, schemas and the extension API can change in any minor version.

## modLocalRuntime crashes the client

Adding the jar to a mod's development run with `modLocalRuntime(files(...))` crashes the client with
`NoClassDefFoundError: kotlin/collections/ArrayDeque`. Loom removes the list of bundled libraries from the remapped
copy. **Workaround:** copy the jar into the project's `run/mods/` folder; Fabric Loader remaps it at launch.

## Launchers and JVM arguments

Some launchers do not pass `-D` system properties to the game. The properties in [Configuration](configuration.md)
(`driver.port`, `driver.token`, `driver.unfocused`) then have no effect, and the endpoint stays on its defaults and open
to local processes. Check the log line printed at startup, and set `pauseOnLostFocus:false` in `options.txt` instead of
`driver.unfocused`.

## Remote access

The server is for the local machine. The access token is a shared secret without TLS, users or scopes. Over a
non-loopback address it travels in clear text. A wildcard bind accepts only localhost host names, so a request to the
machine's LAN address is answered with `403 Invalid Host`.

## Stateless HTTP

The server keeps no session, so it cannot push notifications to the assistant. `mc_wait_for` blocks inside a single
call, with a default timeout of 10 seconds and a cap of 60 seconds. Keep it below the MCP client's request timeout.

## Screens and widgets

- **Tooltips need a hovering cursor.** The tools do not move the real cursor, so a tooltip appears only if the real
  cursor is over its target. Item tooltips in containers cannot be read this way; `mc_read_inventory` and
  `mc_read_container` give the item data instead.
- **Text drawn directly by a mod.** `mc_read_screen_text` captures text drawn through the standard `GuiGraphics` and
  tooltip paths. Text drawn by calling `Font.drawInBatch` directly, or text drawn in the world, is not captured.
  `mc_screenshot` is the fallback.
- **Custom tiles.** A screen that draws its own clickable tiles without registering widgets shows nothing in
  `mc_list_widgets`. Click by point.
- **Class names outside a development environment.** Vanilla screen and widget names are mapped to readable names
  through built-in tables. A vanilla screen that is not in the table reads `class_NNNN` in a normal install. A mod's
  own screens keep their class names.
- **Modifier keys.** Shift and control cannot be sent; shortcuts such as select-all do not work.
- **First launch.** A fresh game directory opens the accessibility onboarding screen. Close it once with `mc_click`
  or set `onboardAccessibility:false`.

## Window focus

`windowFocused` in `mc_get_state` reports `true` at launch whatever the operating system's foreground window is, and
turns `false` only after the window had focus and lost it. Without `-Ddriver.unfocused=true`, vanilla pauses the game
when the window is inactive and "pause on lost focus" is on, so `mc_close_screen` can be undone at once. The flag was
checked on Windows; macOS and Linux were not checked.

## Log capture

`mc_read_log` captures through a Log4j2 appender on the root logger. It sees what Log4j2 routes there at the levels the
game's configuration allows (normally `INFO` and up), so `DEBUG` lines never arrive. Lines logged before the mod started
and output written straight to `System.out` or `System.err` are not captured. Logger names in a normal install are
intermediary names such as `net.minecraft.class_7766`. A mod that reconfigures Log4j2 may change what is captured.

## Shared log file in development

In a Gradle development environment, two clients write to the same `logs/latest.log` even with separate game
directories. The second client cannot open it and logs file-lock errors, but its console output and `mc_read_log` work.

## Inventories and containers

- Slot groups for menus other than the player's inventory, chests, furnaces, brewing stands, crafting tables, merchants
  and the creative screens come from a table and may be wrong; unknown menus read `container`.
- Item data includes damage, enchantments, lore and the name. Other components such as potion contents, trims and
  custom data are not reported.
- `mc_click_slot` does not support dragging (quick craft). In creative mode, `outside` with a cursor stack drops one
  item per click.
- The returned slot state is the client's prediction. Read the container again to see what the server kept.

## Joining and leaving

A successful login to a real dedicated server, servers that prompt for a resource pack, and saves that need an upgrade
have not been checked. `mc_join_world` and `mc_join_server` return at once; follow them with `mc_wait_for`.

## Compatibility with other mods

Kotlin and the HTTP libraries are bundled as nested jars. Another mod that bundles different versions of the same
libraries could conflict. Loading next to Fabric Language Kotlin (two versions) caused no problems, but bundled Ktor or
kotlinx from other mods has not been checked. Report conflicts, with the log, as issues.

## Reporting a problem

Include the Minecraft and mod versions, the other mods installed, the failing call and its result, and the relevant
`mc_read_log` output or `logs/latest.log`.
