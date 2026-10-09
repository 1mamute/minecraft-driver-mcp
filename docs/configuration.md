# Configuration

The mod is configured with Java system properties, passed to the game as JVM arguments (`-Ddriver.port=25901`). In a
Gradle development environment, add them to the `runClient` JVM arguments. Launchers differ in whether they pass `-D`
arguments through; see [Known issues](known-issues.md#launchers-and-jvm-arguments).

## Properties

| Property | Default | Meaning |
| --- | --- | --- |
| `driver.port` | first free port from 25890 | A fixed port |
| `driver.host` | `127.0.0.1` | Bind address |
| `driver.name` | the player name | Instance name |
| `driver.token` | unset (no authentication) | Shared access token |
| `driver.registry` | `~/.minecraft-driver-mcp/instances` | Directory of running instances |
| `driver.unfocused` | `false` | Create the window without focus and keep the game running while it is behind other windows |

### Port

By default a client takes the first free port from 25890, scanning 100 ports, so two clients get 25890 and 25891 even
when they start at the same moment.

`driver.port` fixes the port. If it is taken, the mod logs an error and leaves the driver off; the game still starts.
Use a fixed port when an MCP client is configured to point at it.

### Host

The server binds to `127.0.0.1`. Keep it there: the tools control the player. A specific address is also accepted as
the request's `Host` header; a wildcard bind (`0.0.0.0`) accepts only localhost names. Remote use is not supported:
there is no TLS, and over a non-loopback address the token travels in clear text.

### Name

The instance name appears in the MCP server name and instructions, so an assistant connected to several clients can
tell them apart. It defaults to the player name.

### Access token

When `driver.token` is set, every request must carry `Authorization: Bearer <token>`. Requests without it, or with a
wrong token, get `401` with `WWW-Authenticate: Bearer`, and no tool runs. An empty value leaves the endpoint open.

The token is never logged and never written to the registry; the registry only records `authRequired`. A `-D` argument
is visible to other local users in the process list, so the token protects against other web pages and other
processes that do not know it, not against other local accounts.

### Registry

Each running client writes `<pid>.json` into the registry directory and removes it on exit. `mc_list_instances` reads
it. Files of crashed clients are ignored because their process is gone.

### Unfocused window

Vanilla opens the pause menu every frame while the window is inactive if "pause on lost focus" is on. For an assistant
working while the developer uses other windows, that would undo `mc_close_screen`. With `-Ddriver.unfocused=true` the
window is created without taking focus and the game ignores the option. The option itself and `options.txt` are not
changed. Without the flag, vanilla behavior stays, and `mc_get_state` reports `windowFocused: false`.

## Game directory options

For a driven client, `options.txt` should contain `onboardAccessibility:false` and, unless `driver.unfocused` is used,
`pauseOnLostFocus:false`. See [Installation](installation.md#options-for-a-driven-client).
