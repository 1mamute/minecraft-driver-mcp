# Minecraft Driver MCP gateway

This gateway is made to be used with the
[`minecraft-driver-mcp` Fabric client mod](https://github.com/1mamute/minecraft-driver-mcp).
Install the mod in each Minecraft client you want to control, using a matching release.

The gateway is the recommended connection method for coding agents using the mod.
It advertises built-in tools before Minecraft starts and stays connected across
game restarts. It uses the mod's instance registry to discover local clients and
forwards calls to the selected client's HTTP endpoint. Clients must be running with
the mod to execute game calls. The gateway does not start or stop games, and it does
not dynamically load tools contributed by extension mods.

The gateway requires Node.js 22 or later. The Minecraft mod still requires Java 21.
Use matching gateway and mod releases; a Minecraft suffix such as `+1.21.1` is ignored
when comparing the versions.

## Build and test from the repository

For released versions, run `npx -y minecraft-driver-mcp@<matching-release>` as the
stdio command in your MCP host, or install the executable globally with
`npm install --global minecraft-driver-mcp@<matching-release>`.

The built-in definitions are maintained in this package's `catalogue.json`. The mod
build bundles the same file as a resource. To build and test the gateway:

```bash
cd gateway
npm ci
npm test
npm pack
```

`npm pack` creates a local `.tgz` package for testing source builds before publication.
Install it with `npm install --global ./minecraft-driver-mcp-<matching-release>.tgz`
and register `minecraft-driver-mcp` as the stdio command in your MCP host.

## Configuration

The default config file is `~/.minecraft-driver-mcp/gateway.json`; pass `--config
<path>` to select another. If `registryDirectory` is omitted, the gateway uses the
mod's default registry directory. Configured paths resolve relative to the config file.

```json
{
  "tokens": {
    "builder": "MINECRAFT_BUILDER_TOKEN",
    "tester": "MINECRAFT_TESTER_TOKEN"
  },
  "connectTimeoutMs": 5000,
  "requestTimeoutMs": 70000
}
```

The `tokens` mapping connects unique instance names to environment variable names.
Store bearer token values in the gateway process environment, not in the JSON file.
Token mapping names must be unique. If multiple registry records share a name, select
by PID; the token mapping still cannot distinguish those duplicate names.

## Routing

Call `mc_list_instances` to inspect registry records. Its `check_ready` argument is
optional; set it to `true` to check endpoint health and return authentication and
version status. Call `mc_wait_for_instance` to wait for a selected record to become
ready. It accepts an `instance_name` or `instance_pid`; `ready` defaults to `true`,
`timeout_ms` defaults to 10000 and is capped at 60000, and `ready:false` performs
registry discovery without waiting for endpoint readiness.

Forwarded game tools accept optional `instance_name` and `instance_pid`. Without
selectors, exactly one live registered client must exist. Multiple live clients
require explicit selection even if only one is ready. Supplying both selectors
requires them to resolve to the same record. The gateway removes selector fields
before forwarding the tool arguments.
If the selected endpoint is unavailable, it returns an error without rerouting the
call to another client. Names can remain stable across restarts; PIDs change. Endpoint
readiness does not indicate that a world is loaded; use the mod's `mc_wait_for` for
in-game screens and messages.

The gateway uses localhost and controls a player's game. Trust the local MCP host and
machine. It does not retry forwarded calls, and canceling a request does not undo an
action already received by the client.

## MCP host timeouts

Forwarded requests can take up to 70000 ms. Set the host timeout above that value.
Claude Code uses the `MCP_TOOL_TIMEOUT` environment variable in milliseconds; Codex
uses `tool_timeout_sec` in seconds. See the [full gateway guide](https://1mamute.github.io/minecraft-driver-mcp/gateway/) for registration examples.
