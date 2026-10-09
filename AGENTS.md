# minecraft-driver-mcp agent guide

A Fabric client mod, written in Kotlin with thin Java hooks, that runs an MCP server inside a Minecraft
client so AI assistants can drive it. It is a development tool for mod authors. Resolve versions from
`gradle.properties` and `versions/<mc>/gradle.properties`, mappings and dependencies from `build.gradle.kts`, and
the entrypoint from `src/main/resources/fabric.mod.json`.

## Project context

- Code: `src/main/kotlin/io/github/ummamute/driver/`; Java only for the entrypoint (`DriverMod`) and mixins in
  `src/main/java/io/github/ummamute/driver/mixin/`.
- Packages: `client/` talks to Minecraft (one object per concern, all on the render thread), `tools/` defines MCP
  tools and calls `client/`, `server/` is the Ktor + MCP SDK endpoint, port choice and instance registry.
- `DriverBootstrap` wires everything at client start. [docs/contributing/architecture.md](docs/contributing/architecture.md) explains the
  layers, threading, several-client support and packaging.
- Build: Stonecutter (`stonecutter.gradle.kts`, `settings.gradle.kts`), Fabric Loom, official Mojang mappings
  layered with Parchment. Translate Yarn-named examples against the resolved Minecraft sources before using them.
- Everything ships in one jar: Kotlin stdlib and the MCP/Ktor libraries are jar-in-jar (see `bundled` in
  `build.gradle.kts`). Do not add a dependency on Fabric Language Kotlin.

## Rules

- Keep Minecraft imports inside `client/` (and mixins). `tools/` and `server/` stay version-independent so a new
  Minecraft version only touches `client/`.
- Version differences use Stonecutter conditionals (`//? if >=1.21.5 {`). When a difference grows beyond a few
  lines, extract that piece to a class per version rather than stacking conditionals.
- Run every game call through `RenderThread.call`; never touch screens, the player or the level from a Ktor thread.
- Tools: name `mc_<verb>_<noun>`, snake case, annotate `readOnlyHint`/`destructiveHint`/`idempotentHint`/
  `openWorldHint`, return JSON text built from `@Serializable` data classes, and return `isError` results with a
  message that says what to do next. Prefer text over images; screenshots are for when text cannot answer.
- Bind to `127.0.0.1` only. The tools control the player; never expose them on a network interface by default.
- Several clients must coexist: no fixed ports without an opt-in, no shared mutable files except the instance
  registry.

## Code style

Write **readable** Kotlin. Read [the code style guide](docs/contributing/code-style.md) before writing or reviewing code; it
covers naming, packages, threading, tools, limits and tests. In short: one statement per line, one job per
function, guard clauses, constructor injection, `object` for stateless helpers, parameterized logging, KDoc on
public types, backtick test names that describe behavior. `./gradlew lint` (ktlint and detekt) enforces the limits
(complexity below 15, functions below 60 lines, nesting below 4, 160 columns). A finding means splitting the
function into named steps; do not add baseline entries or `@Suppress` without the maintainer's agreement.

## Documentation

Three audiences, one source of truth each. `docs/` is user-facing (installation, usage, [tool reference](docs/tools.md),
configuration, [known issues](docs/known-issues.md)); `docs/contributing/` is developer-facing (architecture, extending,
testing, code style); [llms.txt](llms.txt) is the self-contained brief for LLM agents. [docs/README.md](docs/README.md)
indexes them, and a VitePress site on GitHub Pages is built from `docs/` with `README.md` as its home page (see
[the contributor guide](docs/contributing/README.md#documentation-site)). Every change to tools, system properties,
packaging, ports or the build updates the matching doc, the tool and property tables in `README.md` and `docs/`, and
`llms.txt` in the same PR; new docs are added to the index and to the site sidebar in `docs/.vitepress/config.mts`. Public documents are impersonal: no names, machines, paths or launchers from a
maintainer's own setup. Write for readers: what it does, how to use it, why a choice was made; leave out what the code
already states. Unverified behavior and risks go in
[docs/contributing/known-issues.md](docs/contributing/known-issues.md); limits a user can hit go in
[docs/known-issues.md](docs/known-issues.md).

## Dependency questions

Load the relevant skill when answering questions or changing integrations:

| Topic | Skill | Use for |
| --- | --- | --- |
| Fabric | `.agents/skills/fabric/SKILL.md` | Loader, Loom, Stonecutter, lifecycle events, networking |
| Mixin | `.agents/skills/mixin/SKILL.md` | Injection points, descriptors, remapping, MixinExtras, application failures |
| Minecraft | `.agents/skills/minecraft/SKILL.md` | Vanilla client APIs, mappings, screens, input, world state |

Codex discovers `.agents/skills/`. Claude Code discovers `.claude/skills/`, which links to `.agents/skills/`
(`../.agents/skills`; on Windows without symlink rights, create a directory junction with
`mklink /J .claude\skills .agents\skills`). Edit skills only in `.agents/skills/`. The `.claude/settings.json`
enables the Kotlin language server plugin (`kotlin-lsp@claude-plugins-official`).

For upstream repository source, follow [repository cache guidance](.agents/skills/references/repository-cache.md):
reuse shallow clones under `~/.cache/minecraft-driver-mcp/repos/`, creating missing clones as needed. Search local
files and read focused excerpts instead of fetching remote repository pages. Select the matching revision before
relying on source behavior.

For API answers, identify the relevant version and mapping namespace, inspect local usages, then confirm uncertain
signatures or behavior in matching upstream documentation or source. Cite the page or source file that supports the
answer. If the matching source is unavailable, state that gap and distinguish inference from verified behavior.
Load only the skills relevant to the question.

## Commits and versioning

Use Conventional Commits with the subject format `<type>: <description>` only, for example
`feat: add wait_for tool` or `fix: close the registry file on shutdown`. Use lowercase types such as `feat`, `fix`,
`docs`, `refactor`, `perf`, `test`, `build`, `ci`, and `chore`. Omit scopes and the `!` marker; describe breaking
changes in the commit body when applicable. Create commits only when requested.

Name branches `<type>/<short-description>` with the same types, in lowercase kebab case, for example
`feat/wait-for` or `fix/registry-cleanup`. Describe the change, never the tool or agent that made it: a branch name
never contains `claude` or another agent name. Rename an agent-assigned branch before pushing.

For every commit agents create, use the user's Git identity for both author and committer. Verify the effective
identity before committing; if it is missing or uncertain, ask the user rather than substituting an agent, bot, or
other identity. Never add an agent, AI tool, or provider as a co-author, including through `Co-authored-by`
trailers or automatic attribution.

Create and update issues, pull requests, and their comments through the user's account. Verify the authenticated
account before posting; if it is missing or uncertain, ask the user. Keep titles, descriptions, and comments
attributed exclusively to the user; never add agent, AI tool, or provider credits or signatures.

Use Semantic Versioning for `mod_version` in `gradle.properties`: MAJOR for incompatible changes to supported public
contracts, MINOR for backward-compatible features, PATCH for backward-compatible fixes. Supported contracts include
tool names and schemas, system properties, the instance registry format and the endpoint path. For `0.x` releases,
flag breaking changes explicitly and advance MINOR for them. Version changes belong to requested releases; ordinary
edits do not bump the version. The jar version is `<mod_version>+<minecraft_version>`; the build reads
`mod_version` and expands `${version}` in `fabric.mod.json`, so keep that single source of truth.

## Validation

Use JDK 21 and the Gradle wrapper. `./gradlew build` runs the tests and lint with the jar packaging. For changes to
tools, `client/`, the mixin or packaging, also run the runtime check in [docs/contributing/testing.md](docs/contributing/testing.md)
(`./gradlew :1.21.1:runClient`, then `initialize`, `tools/list` and the changed tool); report when that check was not
done. Test with two clients when touching ports or the registry. Documentation-only changes need link checks rather
than a build.
