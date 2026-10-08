# Code style

How code in this repository is written. `./gradlew lint` enforces the mechanical
parts; the rest is checked in review.

The goal is **readable** code: a contributor who knows Minecraft modding can open a
file, follow an MCP tool call through ordinary method calls, and put a breakpoint on
each step.

## Language

- Write Kotlin. Use Java only where Fabric needs it: the mod entrypoint
  (`DriverMod`) and Mixin classes in `src/main/java/.../mixin/`. Keep each of them a
  thin hook that calls a Kotlin object.
- Do not depend on Fabric Language Kotlin. Kotlin and the MCP libraries are bundled in
  the jar (see [architecture.md](architecture.md#packaging)).

## Packages and naming

Group code by layer, so a Minecraft version change touches one package:

```text
io.github.ummamute.driver/
    DriverBootstrap.kt      # reads properties, wires and starts everything, stops on shutdown
    client/                 # the only code that imports net.minecraft (plus mixins)
        RenderThread.kt     # runs game calls on the render thread
        ClientState.kt      # one object per concern: state, screens, input, chat, screenshots
        MessageLog.kt
    tools/                  # MCP tool definitions; call client/, never Minecraft
        ToolSupport.kt      # schema builder, argument helpers, result helpers
        ObservationTools.kt # read-only tools
        ActionTools.kt      # tools that change the game
        InstanceTools.kt    # discovery of other running clients
    server/                 # Ktor + MCP SDK endpoint, port choice, instance registry
```

| Role | Name | Example |
| --- | --- | --- |
| One concern of the game client | `Client<Concern>` object | `ClientScreens`, `ClientInput`, `ClientChat` |
| A group of MCP tools | `<Group>Tools` object with a `register` function | `ObservationTools`, `ActionTools` |
| Starts, holds or finds something running | plain noun | `McpEndpoint`, `InstanceRegistry` |
| Pure calculation | plain noun or `object` | `PortSelector` |
| Value returned to the agent | `@Serializable` data class | `ScreenSummary`, `WidgetSummary` |

Add an interface only when a second implementation exists or a test needs a fake; a
concrete class is the default. Avoid `Manager`, `Provider`, `Factory` and similar
layers when nothing varies.

Name MCP tools `mc_<verb>_<noun>` in snake case (`mc_get_state`, `mc_send_chat`).
Name functions after the action: `click`, `lookAt`, `readSince`. Name booleans as
questions: `isConnected`, `hasScreen`.

## Classes and state

- Inject collaborators through the constructor and wire them in `DriverBootstrap`.
  Use `object` for stateless helpers.
- Use `val` and immutable collections. Keep mutable state `private` inside the class
  that owns it, and return copies or immutable views.
- Declare the logger first in the class body:

  ```kotlin
  private val logger = LoggerFactory.getLogger(McpEndpoint::class.java)
  ```

- Log with parameterized messages and pass the exception last when logging a
  failure: `logger.error("Failed to start MCP server on port {}", port, e)`.

## Functions

- One statement per line. One job per function.
- Use guard clauses at the top and return early:

  ```kotlin
  val player = minecraft.player ?: return ToolSupport.failure("Not in a world")
  ```

- When a function grows past the limits below, extract a named private function per
  step so the caller reads like a list of steps.
- Use named arguments when calling constructors or functions with three or more
  parameters of similar types.
- Use `when` over a sealed class with one branch per case and no `else` branch, so the
  compiler reports new cases.
- Use an expression body only for one-line functions.

## Readability limits

`./gradlew lint` enforces these per function:

| Measure | Limit |
| --- | --- |
| Cognitive complexity | below 15 |
| Cyclomatic complexity | below 15 |
| Length | below 60 lines |
| Nesting depth | below 4 blocks |
| Conditions in one `if` | below 4 |
| Parameters | 6 for functions, 10 for constructors |
| Line length | 160 characters (ktlint) |

A finding means the function needs splitting into named steps. Change a threshold or
add `@Suppress` only with the maintainer's agreement, and write the reason on the line
above.

## Threading and asynchronous work

- Game state (screens, the player, the level, input, the framebuffer) belongs to the
  render thread. Wrap every access in `RenderThread.call` (or `ToolSupport.onRenderThread`
  from a tool). Never touch it from a Ktor thread.
- Do the work that does not need the game (parsing arguments, encoding JSON, writing the
  registry file) outside the render-thread block, so the game loop is held as briefly as
  possible.
- Use coroutines and `suspend` functions for asynchronous code. Do not block a Ktor
  worker waiting for the game; suspend instead.
- The player and level can be `null` (title screen, loading, disconnect) or change
  between two calls. Check inside the same render-thread block that uses them.

## Tools

- Annotate every tool: `readOnlyHint`, `destructiveHint`, `idempotentHint`,
  `openWorldHint`. Observation tools are read-only and idempotent.
- Return JSON text built from `@Serializable` data classes, not hand-built strings.
- Prefer text over images. A screenshot is for when text cannot answer the question.
- Report a failure as an `isError` result with a message that says what to do next
  ("No screen is open; call mc_get_state"), not an exception message.
- Keep tool descriptions to what the agent needs to decide to call it and how.
- Adding or changing a tool updates the table in `README.md`.

## Types and data

- Use `@Serializable` data classes for everything that crosses the MCP boundary, with
  KDoc on properties whose meaning is not obvious from the name.
- Use `kotlinx.serialization` types for structured data. Read and write JSON through
  data classes, not string keys, except for tool arguments through `ToolSupport`.
- Name numbers with constants. Inline numbers are fine for obvious values such as `0`,
  `1` and `100`.

## Comments and documentation

- Write KDoc on public classes and functions: one summary line, then `@param`,
  `@return` and `@throws` where they add information.
- Write implementation comments for the reason or constraint the code cannot show.
- Mark known gaps with `// TODO:` or `// FIXME:` and a short explanation.

## Imports and formatting

`./gradlew ktlintFormat` applies formatting. The rules live in `.editorconfig`:

- IntelliJ IDEA Kotlin style, 4-space indentation, LF line endings.
- Explicit imports, sorted, no wildcard imports, no fully qualified names inline.
- Trailing commas are allowed and not required.

## Tests

- Put tests in `src/test/kotlin` under the same package as the code.
- Name tests with backtick sentences describing the behavior:
  ``fun `ignores clients whose process is gone`()``.
- Test through public operations and assert observable results. Use fakes for time and
  the file system (a temporary directory); avoid asserting private state.
- Code in `client/` needs a running game and is covered by the runtime check in
  [testing.md](testing.md), not by unit tests. Keep logic that does not need Minecraft
  in `server/` or `tools/` so it can be unit tested.

## Linting

| Command | Use |
| --- | --- |
| `./gradlew lint` | Runs ktlint and detekt. Part of `./gradlew check` and `build`. |
| `./gradlew ktlintFormat` | Fixes formatting in place. |
| `./gradlew detekt` | Code smells and complexity; HTML report in `build/reports/detekt/`. |
| `./gradlew detektBaseline` | Rewrites the detekt baseline. Run only when the maintainer agrees. |

`config/detekt/baseline.xml` and `config/ktlint/baseline.xml` list accepted findings.
New and rewritten code passes without adding entries to either baseline.
