# Contributor guide

This section is for people who change the mod itself. For installing and using it, see the
[user documentation](../README.md).

## Orientation

Minecraft Driver MCP is a Fabric client mod written in Kotlin with thin Java hooks. It runs an MCP server (Ktor and the
MCP Kotlin SDK) inside the Minecraft client. The source is in three layers, and the dependency direction is strict:

| Package | Role | May import Minecraft |
| --- | --- | --- |
| `client/` | One object per concern of the game client; every call runs on the render thread | Yes |
| `tools/` | MCP tool definitions: names, schemas, annotations, result JSON | No |
| `server/` | Ktor and MCP SDK endpoint, port choice, instance registry | No |
| `api/` | Public, Minecraft-free API for other mods | No |

Java is used only for the entrypoint (`DriverMod`) and the mixins in `mixin/`. Because only `client/` and the mixins know
Minecraft, supporting a new Minecraft version changes those and nothing else.

## Pages

| Page | Contents |
| --- | --- |
| [Architecture](architecture.md) | Layers, lifecycle, threading, transport, several clients, packaging, the build |
| [Extending the mod](extending.md) | Step-by-step: add a tool, add a client concern, add a mixin, support a Minecraft version |
| [Testing](testing.md) | Unit tests, the runtime check, driving a client by hand, debugging |
| [Code style](code-style.md) | Naming, readability limits, tool conventions, linting |
| [Dependencies](dependencies.md) | Where versions live, upgrading, adding a Minecraft version |
| [Verification status](known-issues.md) | What has been checked against a running client, what has not, design limits |

## Quick start

Requires JDK 21 and the Gradle wrapper.

```bash
./gradlew build                  # compile, unit tests, lint, jar in versions/<mc>/build/libs
./gradlew lint                   # ktlint and detekt
./gradlew :1.21.1:runClient      # start a client with the mod and the MCP endpoint
```

Then connect an MCP client to the endpoint printed in the log and call `tools/list`.

## Ground rules

- Keep Minecraft imports inside `client/` and the mixins.
- Run every game call through `RenderThread.call`; never touch screens, the player or the level from a Ktor thread.
- Bind to `127.0.0.1` only.
- Several clients must coexist: no fixed ports unless requested, no shared mutable files except the instance registry.
- Do not depend on Fabric Language Kotlin; Kotlin and the MCP libraries ship inside the jar.
- Version differences use Stonecutter conditionals; extract a class per version when a difference grows past a few lines.
- Changes to tools, system properties, packaging, ports or the build update the matching documentation, the tables in
  `README.md`, and `llms.txt`, in the same change.

## Commits and releases

Commit subjects follow Conventional Commits without scopes: `feat: add wait_for tool`, `fix: close the registry file on
shutdown`. Types are lowercase: `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`. Describe a
breaking change in the commit body. Branches are `<type>/<short-description>` in lowercase kebab case.

`mod_version` in `gradle.properties` follows Semantic Versioning, and for `0.x` a breaking change advances the minor
version. Supported contracts are tool names and schemas, system properties, the instance registry format and the endpoint
path. Ordinary changes do not bump the version. The jar version is `<mod_version>+<minecraft_version>`.
