# Release notes

Versions follow [Semantic Versioning](https://semver.org). While the project is at 0.x, tool names, schemas, system
properties, the instance registry format and the extension API can change in any minor version, and each change is
listed here. The jar is named `minecraft-driver-mcp-<version>+<minecraft version>.jar`.

## 0.1.0

First release, for Minecraft 1.21.1 with Fabric Loader 0.19.5 or newer, Fabric API and Java 21. See the
[tool reference](tools.md) for the tools and [Configuration](configuration.md) for the system properties.

### Known limits

- Only Minecraft 1.21.1 is built, and the checks were made on Windows.
- The MCP Kotlin SDK is pre-1.0 (0.15.0), so its API can change between its minor versions.
- In a regular install, logger names in `mc_read_log` are intermediary names.
- `modLocalRuntime` crashes a mod's development client; copy the jar into `run/mods/` instead.
- Two development clients share one `latest.log`.

The full list is in [Known issues](known-issues.md), and what has been verified is in
[Verification status](contributing/known-issues.md).

<!-- changelog:0.1.0 -->
### Features

- Scaffold the Minecraft driver MCP server
- Add `mc_wait_for` tool
- Add `mc_read_screen_text` tool
- Add inventory and container tools ([#3](https://github.com/1mamute/minecraft-driver-mcp/pull/3))
- Add `mc_read_log` tool ([#4](https://github.com/1mamute/minecraft-driver-mcp/pull/4))
- Add `mc_click_slot` tool ([#5](https://github.com/1mamute/minecraft-driver-mcp/pull/5))
- Add optional bearer token for the endpoint ([#6](https://github.com/1mamute/minecraft-driver-mcp/pull/6))
- Add extension points for other mods ([#7](https://github.com/1mamute/minecraft-driver-mcp/pull/7))
- Add server join and disconnect tools ([#8](https://github.com/1mamute/minecraft-driver-mcp/pull/8))
- Add `hold_ticks` to `mc_use` to finish eating, drinking and drawing a bow ([#36](https://github.com/1mamute/minecraft-driver-mcp/pull/36))
- Add `mc_type_text` and `mc_press_key` for text fields and screen keys ([#37](https://github.com/1mamute/minecraft-driver-mcp/pull/37))

### Fixes

- Fail with a clear message when the fixed port is taken
- Return a tool error when `mc_send_chat` has no text
- Click and label slots correctly in the creative inventory and named menus ([#9](https://github.com/1mamute/minecraft-driver-mcp/pull/9))
- Avoid name-based reflection that breaks outside the dev environment ([#10](https://github.com/1mamute/minecraft-driver-mcp/pull/10))
- Keep live registrations when clients start together ([#11](https://github.com/1mamute/minecraft-driver-mcp/pull/11))
- Retry on the next port when two clients race for one and keep the game running when startup fails ([#12](https://github.com/1mamute/minecraft-driver-mcp/pull/12))
- Use the held item in both hands when `mc_use` has no block or entity to act on ([#13](https://github.com/1mamute/minecraft-driver-mcp/pull/13))
- Validate and normalize chat text before sending ([#14](https://github.com/1mamute/minecraft-driver-mcp/pull/14))
- Keep keys held with `mc_set_key` down when a screen opens ([#15](https://github.com/1mamute/minecraft-driver-mcp/pull/15))
- Close an open container screen with the inventory key in `mc_set_key` ([#16](https://github.com/1mamute/minecraft-driver-mcp/pull/16))
- Skip action-bar text in the message log and report dropped messages ([#17](https://github.com/1mamute/minecraft-driver-mcp/pull/17))
- Return errors thrown on the render thread instead of hanging the request ([#18](https://github.com/1mamute/minecraft-driver-mcp/pull/18))
- Reject wrong-typed tool arguments and treat JSON null as absent ([#19](https://github.com/1mamute/minecraft-driver-mcp/pull/19))
- Report bad tool arguments as plain errors instead of SDK failures ([#21](https://github.com/1mamute/minecraft-driver-mcp/pull/21))
- Report registry ids, feet position and distance in `mc_list_entities` ([#23](https://github.com/1mamute/minecraft-driver-mcp/pull/23))
- Match the screen-text frame to the open screen instance ([#26](https://github.com/1mamute/minecraft-driver-mcp/pull/26))
- Close screens in `mc_close_screen` the way Escape does ([#28](https://github.com/1mamute/minecraft-driver-mcp/pull/28))
- Accept a specific `driver.host` address in the Host header ([#30](https://github.com/1mamute/minecraft-driver-mcp/pull/30))
- Report errors from tool handlers and keep cancellation intact ([#31](https://github.com/1mamute/minecraft-driver-mcp/pull/31))
- Say what to do next in click, chat and world error messages ([#32](https://github.com/1mamute/minecraft-driver-mcp/pull/32))
- Pause held keys while a screen is open, as vanilla does ([#35](https://github.com/1mamute/minecraft-driver-mcp/pull/35))
- Keep the game running when an unfocused window loses focus ([#38](https://github.com/1mamute/minecraft-driver-mcp/pull/38))
- Name widgets by vanilla class in `mc_list_widgets` on a normal install ([#40](https://github.com/1mamute/minecraft-driver-mcp/pull/40))

### Performance

- Skip state reads in `mc_wait_for` when only a message is awaited ([#24](https://github.com/1mamute/minecraft-driver-mcp/pull/24))
- Encode screenshots in memory instead of through a temp file ([#27](https://github.com/1mamute/minecraft-driver-mcp/pull/27))
- Build the MCP server once instead of on every request ([#29](https://github.com/1mamute/minecraft-driver-mcp/pull/29))

### Documentation

- Record the second pass of runtime verification ([#22](https://github.com/1mamute/minecraft-driver-mcp/pull/22))
- Run the driver from run/mods in a consumer dev environment ([#25](https://github.com/1mamute/minecraft-driver-mcp/pull/25))
- Describe the current status in the README ([#33](https://github.com/1mamute/minecraft-driver-mcp/pull/33))
- Record the runtime checks of keys, food and the port race ([#34](https://github.com/1mamute/minecraft-driver-mcp/pull/34))
- Record the held key check ([#35](https://github.com/1mamute/minecraft-driver-mcp/pull/35))
- Record the auth and logger-name deferrals and add feature ideas ([#41](https://github.com/1mamute/minecraft-driver-mcp/pull/41))
- Add user, contributor and LLM documentation and rewrite the readme
- Add a documentation site built from docs/ ([#43](https://github.com/1mamute/minecraft-driver-mcp/pull/43))
- Record the separate-jar extension check ([#45](https://github.com/1mamute/minecraft-driver-mcp/pull/45))

### Build

- Exclude kotlin-reflect from the bundled libraries
- Add build workflow for linux and windows ([#44](https://github.com/1mamute/minecraft-driver-mcp/pull/44))

### Chores

- Add release metadata, generated release notes and a release workflow ([#46](https://github.com/1mamute/minecraft-driver-mcp/pull/46))
<!-- /changelog:0.1.0 -->
