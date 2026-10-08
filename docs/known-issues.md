# Known issues and watch list

Open questions and risks found while scaffolding. Check them when a related change lands, and remove an entry when it is resolved.

## Unverified

| Item | What we know | How to close it |
| --- | --- | --- |
| Two clients at once | Port choice and the instance registry have unit tests. Two real clients were never started together. | Start two clients with separate run directories; confirm ports 25890 and 25891, both entries in `mc_list_instances`, and cleanup on exit. |
| Unfocused window | `WindowMixin` sets the GLFW hints when `-Ddriver.unfocused=true`. In the one smoke test `mc_get_state` still reported `windowFocused: true`. | Launch with the property and check focus while another app is active. |
| Bundled library clashes | Ktor, kotlinx and Kotlin ship as jar-in-jar. Another mod bundling different versions of the same libraries may conflict. | Run next to other mods that bundle Kotlin or Ktor; watch for `NoSuchMethodError` or duplicate-class warnings. |
| Jar size | About 13.7 MB. `kotlin-reflect` (3.6 MB) and `config` come in transitively through Ktor and the MCP SDK. | Try excluding `kotlin-reflect` and `config` from `bundled`, then re-run the smoke test. |

## Design limits

- **Stateless HTTP.** `mcpStatelessStreamableHttp` keeps no session, so the server cannot push notifications to the agent. Tools such as `wait_for` must block inside one call. Revisit if streaming progress is needed (`mcpStreamableHttp`).
- **Localhost only, no auth.** The tools control the player. Binding to a non-loopback `driver.host` exposes them to the network. Add an optional token before documenting any remote use.
- **Single Minecraft version.** Only 1.21.1 exists, so Stonecutter's conditionals are untested. Adding a second version is the real test of the approach.

## Tooling

- **Fabric Loom 1.18 needs JDK 25.** The project pins Loom 1.17.21 to stay on JDK 21. Moving to Loom 1.18 means moving the build JDK; watch for the first Minecraft version that requires it.
- **Deprecation warning** in `build.gradle.kts` for `RunConfigSettings.property` (Loom). Switch to the replacement API when Loom documents one.
- **Stonecutter 0.9.x** is maintained mainly by one developer. Pin the version and read its changelog before upgrading.
- **Upstream SDK.** `io.modelcontextprotocol:kotlin-sdk` is pre-1.0 (0.15.0), so its API can change between minor versions. Upgrade deliberately and re-run the smoke test (`initialize`, `tools/list`, one tool call).

## Missing features

`wait_for` (message or screen), screen text and tooltips, inventory and container contents, joining and leaving servers, client log tail, optional auth token, extension points for other mods.
