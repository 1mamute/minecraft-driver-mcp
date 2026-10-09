# Verification status

What has been checked against a running client, what has not, and the limits that come from the design. Check an entry
when a related change lands, and remove it when it is resolved. Limits that matter to users are repeated in the
user-facing [known issues](../known-issues.md).

All checks below were made on Minecraft 1.21.1, in a development environment unless stated.

## Verified

| Area | Checked |
| --- | --- |
| Two clients | Two real clients on 25890 and 25891 both answered `initialize`, `tools/list` and `mc_get_state`; both appeared in `mc_list_instances`; closing one removed only its registry file. A fixed, taken `driver.port` logs an error and leaves the driver off while the game starts. |
| Port race | Simultaneous launches got distinct ports. The `EndpointBinder` retry after a lost race is covered by unit tests only, because the window between probing and binding is a few milliseconds. |
| Auth token | Without a token all calls work. With one, a missing header, a wrong token, a token one character short and a plain GET return 401 with `WWW-Authenticate: Bearer`; the correct header returns 200. The token is absent from the registry file and the log. |
| Window focus | With `driver.unfocused=true` the window never took the foreground in repeated launches, and the game answered `mc_get_state` and `mc_screenshot` behind other windows. With the flag and `pauseOnLostFocus:true`, a closed screen stays closed; without it the pause screen returns. |
| Inventory and containers | `mc_read_inventory` (custom names, enchantments, durability, lore, armor and offhand), `mc_read_container` for a chest, a merchant, a furnace, a brewing stand, a crafting table and both creative screens, and the error results. |
| `mc_click_slot` | Pick up, put down, quick move in both directions, swap with a hotbar key, throw, pick up all, drop outside, merchant trades, in survival and in creative (item grid, inventory tab), furnace, brewing stand and crafting table. |
| `mc_use` | Throwing a snowball, eating with `hold_ticks`, drawing a bow, error cases. A screen opened mid-hold releases the key. |
| `mc_set_key` | Held keys follow vanilla: released while any screen is open and resumed when it closes. `inventory` opens and closes the inventory and container screens, and errors with a pointer to `mc_close_screen` on other screens. |
| Joining and leaving | `mc_disconnect` and `mc_join_world` with a singleplayer world; `mc_join_server` up to the connect screen and the failure path (a disconnect screen); error results for a bad port, a missing address, an unknown folder and being already in a world. |
| Log capture | WARN and ERROR lines from the game, `min_level`, `contains`, `since`, the bounded buffer, the unknown-level error. A clean shutdown ends with no errors and removes the registry file. |
| Extension API | An extension declared in the mod's own `fabric.mod.json` and an extension in a separate Kotlin mod jar compiled against the driver jar and loaded next to Fabric Language Kotlin and other mods. Checked: schema and annotations in `tools/list`, a handler on the render thread, error results for a missing argument and a throwing handler, a duplicate name and a name without the mod-id prefix ignored with a warning, an extension throwing in `registerTools` logged without stopping the endpoint. |
| Errors on the render thread | A `NoSuchMethodError` and an `AssertionError` thrown inside `RenderThread.call` came back as `isError` results in milliseconds and the client kept running. |
| Production install | Screen names and widget types show no `class_NNNN` for the title, options, pause and creative inventory screens in a launcher-managed Fabric install with other mods. A real MCP client completed a session without a token. |
| Library clashes | Loading next to Fabric Language Kotlin 1.14.1 (Kotlin 2.4) and 1.10.20 (Kotlin 1.9) caused no duplicate-class or linkage errors. |
| Jar size | About 9 MB. `kotlin-reflect` (pulled in by `ktor-server-core`) is excluded and every tool still works. |

## Not verified

| Area | Gap | How to close it |
| --- | --- | --- |
| Auth token with a real client | A real MCP client sending the header; two clients with different tokens; timing side channels beyond the SHA-256 plus `MessageDigest.isEqual` comparison. | Connect a client with the header and call a tool. |
| `-D` arguments in launchers | Some launchers do not pass them, so token and flag behavior in a production install is unchecked. | Test with a launcher that passes JVM arguments. |
| `driver.unfocused` on other systems | Measured on Windows only. | Compare `mc_get_state` with the OS foreground window on macOS and Linux. |
| Separate run directories | Loom ignores the `workingDir` override of `runClient`; `--gameDir` moves saves, options and crash reports, but the log4j file appenders still write `logs/latest.log` under the default run directory, so two development clients share it. | Point the log file at the game directory with a custom log4j configuration, or keep documenting it. |
| Remaining menus | Anvil, enchanting table, smithing, stonecutter, grindstone and modded menus: slot names come from a table in `MenuSlotGroups`, unchecked against the game. Also item components other than damage, enchantments and lore. | Open each menu in a world and compare with the screen. |
| Quick craft and modded slots | Dragging in `mc_click_slot`, modded menus, and whether the server rejects a click the client predicted. | Click in a modded menu and compare with `mc_read_container` after a second. |
| Screen text | Text from direct `Font.drawInBatch` calls and in-world text is not captured. Item tooltips need the real cursor. | Hover an item and read; check a mod screen that draws directly. |
| Log capture | A throwable from the running game (the provoked errors carried none); running next to a mod that reconfigures Log4j2. | Trigger a mixin failure or a mod exception and read it. |
| Joining | A successful login to a dedicated server or a LAN world; resource-pack prompts; unsupported protocols; `mc_disconnect` on a remote server; saves needing an upgrade. | Join a dedicated server and a LAN world from a second client. |
| Extension API | A Java extension, `onRenderThread(false)`, and coexistence with a mod bundling another Kotlin runtime. Tools are collected once at startup, so late registration is not seen. | Build a small Java mod against the jar. |
| Bundled libraries | Ktor or kotlinx bundled by other mods. | Run next to such mods and watch for `NoSuchMethodError` and duplicate-class warnings. |
| Held-use items | A shield and a potion with `hold_ticks`. | Try both. |

## Design limits

- **Stateless HTTP.** `mcpStatelessStreamableHttp` keeps no session, so the server cannot push notifications.
  `mc_wait_for` blocks inside one call (default 10 s, cap 60 s). Revisit if streaming progress is needed
  (`mcpStreamableHttp`).
- **Localhost and an optional token only.** There are no users, scopes or TLS. Over a non-loopback `driver.host` the
  token travels in clear text. Do not document remote use until TLS or a tunnel is part of the setup. The SDK's
  DNS-rebinding guard accepts only localhost names and a specific `driver.host` address in the `Host` header
  (`HostAllowLists`).
- **One Minecraft version.** Only 1.21.1 exists, so the Stonecutter conditionals are untested. A second version is the
  real test of the approach.
- **Logger names.** In a normal install, logger names in `mc_read_log` are intermediary names and cannot be fixed with a
  lookup table. Alternatives are tracked in [issue 39](https://github.com/1mamute/minecraft-driver-mcp/issues/39).
- **Class-name tables.** `ClassNames` maps vanilla screens and widgets to official simple names because reflection
  returns intermediary names in a normal install. Add a class to the table when `mc_list_widgets` shows `Component` for a
  vanilla widget that matters.
- **No `modLocalRuntime`.** Loom drops the `jars` list from `fabric.mod.json` when it remaps a file dependency. Publishing
  the jar to a Maven repository with a POM listing the bundled libraries would fix it.

## Tooling

- **Fabric Loom 1.18 needs JDK 25.** The project pins Loom 1.17.x to stay on JDK 21. Watch for the first Minecraft
  version that requires a newer JDK.
- **Deprecation warning** in `build.gradle.kts` for `RunConfigSettings.property` (Loom). Switch when Loom documents a
  replacement.
- **Stonecutter 0.9.x** is maintained mainly by one developer. Pin the version and read its changelog before upgrading.
- **MCP Kotlin SDK** is pre-1.0 (0.15.0); its API can change between minor versions. Upgrade deliberately and re-run the
  smoke test (`initialize`, `tools/list`, one tool call).
