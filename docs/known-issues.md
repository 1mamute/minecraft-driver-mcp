# Known issues and watch list

Open questions and risks found while scaffolding. Check them when a related change lands, and remove an entry when it is resolved.

## Unverified

| Item | What we know | How to close it |
| --- | --- | --- |
| Two clients at once | Verified on 1.21.1 with two real clients: AlphaBot on 25890 and BravoBot on 25891 both answered `initialize`, `tools/list` and `mc_get_state`, both appeared in `mc_list_instances`, and closing one removed only its registry file. A fixed `-Ddriver.port` that is already taken now fails at startup with `Port 25991 is already in use. Free it or set -Ddriver.port to another port ...` in the crash report (re-verified on 1.21.1 with a second client; the first client kept answering `mc_get_state`). The message is in the crash report, not the log, because the entrypoint error ends the game. | Confirm the message also appears in `logs/latest.log` when the run directories are separate. Note that a force-killed client leaves its registry file behind. |
| `windowFocused` in `mc_get_state` | Measured on 1.21.1 (Windows 11) with a Win32 foreground sampler: without `-Ddriver.unfocused=true` Minecraft took the foreground about 25 s after launch; with the flag, three consecutive launches never did, and the game rendered and answered `mc_get_state` and `mc_screenshot` while behind other windows. `windowFocused` (`mc.isWindowActive`) still reports `true` at launch in both cases, so it does not reflect OS foreground state. | Compare `mc_get_state` with `GetForegroundWindow` during a launch on macOS and Linux, which the sampler does not cover. |
| Separate run directories | Loom ignores the `workingDir` override on `runClient`. Minecraft kept `versions/1.21.1/run` as its game directory until `--gameDir` was passed in the program arguments. Two clients sharing that directory fight over `logs/latest.log`. Re-checked: with `--gameDir` the crash report went to the new directory, but log4j still wrote to `versions/1.21.1/run/logs/latest.log` and logged file-lock errors against client A's log. | Find a way to point the log file at the game directory, or document that the log is shared. |
| Bundled library clashes | Ktor, kotlinx and Kotlin ship as jar-in-jar. Another mod bundling different versions of the same libraries may conflict. Tested with Fabric Language Kotlin added as a runtime-only dependency on 1.21.1: FLK 1.14.1+kotlin.2.4.20 (same Kotlin as the bundled stdlib) and FLK 1.10.20+kotlin.1.9.24 (older Kotlin). Both clients loaded with the mod, answered `initialize`, `tools/list` and `mc_get_state`, and logged no duplicate-class, `LinkageError`, `NoClassDefFoundError`, `NoSuchMethodError` or `IncompatibleClassChangeError`. | Run next to other mods that bundle Kotlin or Ktor; watch for `NoSuchMethodError` or duplicate-class warnings. Still untested: Ktor or kotlinx bundled by other mods. |
| Screen text capture | Hooks `GuiGraphics.drawString` (String and FormattedCharSequence variants), `ClientTextTooltip.renderText` and `Screen.renderWithTooltip` on 1.21.1. Text drawn by other paths (direct `Font.drawInBatch`, item counts, entity or in-world text) is not captured. Title and options screens verified on 1.21.1; a tooltip on the Video Settings Graphics button was captured with the real cursor placed over it (the window must be in front of other windows to receive the cursor); inventory item tooltips are untested. | Check a mod screen that draws text with `Font.drawInBatch` directly. |
| Jar size | About 9.0 MB. `kotlin-reflect` (3.6 MB, pulled in by `ktor-server-core`) is excluded from `bundled`; the title-screen smoke test of all 12 tools and their error paths passed, and the tools also passed inside a freshly created world (`mc_list_entities`, `mc_look_at`, `mc_use` on a cow, `mc_set_key`, `mc_send_chat`, `mc_read_messages`, `mc_screenshot`, `mc_close_screen`), with no `LinkageError` or reflection failure in the log. `mc_click` on a widget was only exercised on menus. `config` still comes in transitively. | Try excluding `config` and re-run the smoke test. If a Ktor or MCP SDK upgrade needs reflection, remove the exclude in `build.gradle.kts`. |

## Design limits

- **Stateless HTTP.** `mcpStatelessStreamableHttp` keeps no session, so the server cannot push notifications to the agent. `mc_wait_for` blocks inside one call, with a timeout that defaults to 10 s and is capped at 60 s, so keep it below the MCP client's request timeout. Revisit if streaming progress is needed (`mcpStreamableHttp`).
- **Localhost only, no auth.** The tools control the player. Binding to a non-loopback `driver.host` exposes them to the network. Add an optional token before documenting any remote use.
- **Single Minecraft version.** Only 1.21.1 exists, so Stonecutter's conditionals are untested. Adding a second version is the real test of the approach.

## Tooling

- **Fabric Loom 1.18 needs JDK 25.** The project pins Loom 1.17.21 to stay on JDK 21. Moving to Loom 1.18 means moving the build JDK; watch for the first Minecraft version that requires it.
- **Deprecation warning** in `build.gradle.kts` for `RunConfigSettings.property` (Loom). Switch to the replacement API when Loom documents one.
- **Stonecutter 0.9.x** is maintained mainly by one developer. Pin the version and read its changelog before upgrading.
- **Upstream SDK.** `io.modelcontextprotocol:kotlin-sdk` is pre-1.0 (0.15.0), so its API can change between minor versions. Upgrade deliberately and re-run the smoke test (`initialize`, `tools/list`, one tool call).

## Missing features

Inventory and container contents, joining and leaving servers, client log tail, optional auth token, extension points for other mods.
