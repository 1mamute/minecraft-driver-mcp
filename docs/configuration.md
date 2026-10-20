# Configuration

Use the [gateway](gateway.md) for coding-agent connections; it discovers game ports
automatically. Its settings are described under [Gateway configuration](#gateway-configuration).

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
| `driver.screenshotDirectory` | unset (saving disabled) | Opt-in directory for screenshot PNG + JSON evidence |
| `driver.unfocused` | `false` | Create the window without focus and keep the game running while it is behind other windows |

### Screenshot evidence

Set `-Ddriver.screenshotDirectory=<directory>` to enable `mc_screenshot`'s optional `save_as` argument.
Relative directories resolve against the game's working directory. Missing directories are created when saving.
Use a separate directory per client to keep evidence independent. The default returns inline images without writing files.

`save_as` accepts a simple filename such as `inventory-1.png`; its stem may contain letters, digits, underscores and
hyphens. The tool writes that PNG and a matching `inventory-1.json` capture-metadata sidecar, and returns their absolute
paths in `evidence`. Nested/absolute paths and symbolic links in the directory or targets are rejected.
Existing PNG or JSON files are never overwritten; choose a fresh filename for each save. See [mc_screenshot](tools.md#mc_screenshot).

### Port

By default a client takes the first free port from 25890, scanning 100 ports, so two clients get 25890 and 25891 even
when they start at the same moment.

`driver.port` fixes the port. If it is taken, the mod logs an error and leaves the driver off; the game still starts.
Use a fixed port for a direct HTTP client configured to point at it. The gateway
discovers automatic ports through the registry.

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

For gateway connections, map the instance name to a token environment variable in
[gateway configuration](gateway.md#configure-instances-and-tokens). Direct HTTP
clients supply the header themselves.

The token is never logged and never written to the registry; the registry only records `authRequired`. A `-D` argument
is visible to other local users in the process list, so the token protects against other web pages and other
processes that do not know it, not against other local accounts.

### Registry

Each running client writes `<pid>.json` into the registry directory and removes it on exit. `mc_list_instances` reads
it. The filename must contain a positive PID matching the record. Invalid or unreadable files are skipped and kept.
Files of crashed clients are ignored because their process is gone.

New records include `processStartEpochSeconds` when the operating system supplies the process start time: a positive
whole number of seconds since the Unix epoch. A confirmed start-time mismatch hides a stale record after PID reuse;
the mod removes confirmed stale records during the next registration. Missing or `null` start times in older records,
and unavailable operating-system metadata, retain PID-only liveness checks. The gateway never deletes registry files.
Endpoint readiness is a separate, optional check: a timeout, authentication error or connection failure does not
delete a registration or redirect a call to another client. Duplicate names still require selection by PID.

### Unfocused window

Vanilla opens the pause menu every frame while the window is inactive if "pause on lost focus" is on. For an assistant
working while the developer uses other windows, that would undo `mc_close_screen`. With `-Ddriver.unfocused=true` the
window is created without taking focus and the game ignores the option. The option itself and `options.txt` are not
changed. Without the flag, vanilla behavior stays, and `mc_get_state` reports `windowFocused: false`.

## Game directory options

For a driven client, `options.txt` should contain `onboardAccessibility:false` and, unless `driver.unfocused` is used,
`pauseOnLostFocus:false`. See [Installation](installation.md#options-for-a-driven-client).

## Gateway configuration

The gateway is the recommended connection method for coding agents. It routes tools
from one stdio MCP connection to several modded clients. It uses the same registry
directory as the mod and a separate configuration
file at `~/.minecraft-driver-mcp/gateway.json` by default; `--config <path>` overrides
the file. The `tokens` object maps unique instance names to environment variable
names containing bearer token values. It does not store token values in the config.

| Gateway setting | Default | Meaning |
| --- | --- | --- |
| `registryDirectory` | `~/.minecraft-driver-mcp/instances` | Registry directory used by the clients |
| `tokens` | `{}` | Instance-name to token environment-variable-name mapping |
| `connectTimeoutMs` | `5000` | Timeout for establishing a client connection |
| `requestTimeoutMs` | `70000` | Timeout for a forwarded tool request |

See [Local gateway](gateway.md) for installation, host registration and routing.
