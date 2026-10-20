# Local gateway

The `minecraft-driver-mcp` gateway is the recommended connection method for Claude
Code, Codex and other coding agents. It exposes built-in tools before Minecraft
starts and keeps one stdio MCP connection available across client launches and
restarts. It discovers clients through the registry and forwards calls to the
selected client's HTTP endpoint once that client is running with the mod.

The gateway does not launch or stop games. Direct HTTP access remains an
[alternative](getting-started.md#direct-http-alternative) for specialized setups,
extension tools and endpoint debugging.

The gateway requires Node.js 22 or later. Minecraft still requires Java 21 and the
Fabric mod. The gateway and mod must use matching releases; the Minecraft version
suffix on a mod jar does not affect the match.

## Build and install

For a released mod, use `npx -y minecraft-driver-mcp@<matching-release>` as the stdio
command, or install it with `npm install --global minecraft-driver-mcp@<matching-release>`.
The gateway and mod releases must match.

For a source build, build the mod, then compile and test the gateway. Built-in definitions are maintained
in `gateway/catalogue.json` and bundled into both distributions:

```bash
./gradlew build
cd gateway
npm ci
npm test
npm pack
```

Install the resulting local `.tgz` package with your package manager.
Keep the gateway and
mod on the same release; a Minecraft version suffix such as `+1.21.1` is ignored when
matching releases.

For example, from `gateway/`, install the archive with
`npm install --global ./minecraft-driver-mcp-<matching-release>.tgz` and register
`minecraft-driver-mcp` as the stdio command in the host. This uses the packaged
executable without requiring a published npm release or a Java runtime for the gateway.

## Configure instances and tokens

The gateway reads `~/.minecraft-driver-mcp/gateway.json` by default. Pass
`--config <path>` to use another file. If `registryDirectory` is omitted, the gateway
uses `~/.minecraft-driver-mcp/instances`; otherwise provide the same registry directory
used by the mod. Paths in the config are resolved relative to the config file.

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

`tokens` maps each unique instance name to the name of an environment variable that
contains that client's bearer token. Put token values in the gateway process
environment, never in this JSON file. Token mappings must use distinct names. If
multiple registry records share a name, select by PID; authentication mapping still
requires unique names.
The connect timeout defaults to 5000 ms and the request timeout to 70000 ms.

After installing the local archive, register its executable in Claude Code:

```bash
claude mcp add --transport stdio minecraft-driver -- minecraft-driver-mcp
```

Or add it to Codex:

```bash
codex mcp add minecraft-driver -- minecraft-driver-mcp
```

The gateway's forwarded request timeout is 70000 ms. Set the host timeout above it:
Set `MCP_TOOL_TIMEOUT=75000` in the environment that launches Claude Code, or set
`"timeout": 75000` on its server entry in `.mcp.json`. Passing this variable to the
gateway child process does not configure Claude Code's timeout. Codex uses
`tool_timeout_sec` in seconds. For Codex, add this to `~/.codex/config.toml`:

```toml
[mcp_servers.minecraft-driver]
command = "minecraft-driver-mcp"
tool_timeout_sec = 75
```

For an npm release, `npx -y minecraft-driver-mcp@<matching-release>` can replace the
installed executable as the stdio command. See the hosts' official guides for [Claude Code
MCP](https://code.claude.com/docs/en/mcp) and [Codex MCP](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

## Select a client

The gateway adds `mc_wait_for_instance` and replaces the mod's `mc_list_instances` with
a local-registry view:

- `mc_list_instances` returns registry records only. Set `check_ready:true` to add
  endpoint health, whether authentication is required, and the available Minecraft
  and gateway version status. Omit it for the basic registry listing.
- `mc_wait_for_instance` waits for one selected client to be ready. Select by its
  stable `instance_name` or current `instance_pid`. `ready` defaults to `true`, and
  `timeout_ms` defaults to 10000, with a maximum of 60000. Set `ready:false` for
  discovery without waiting for endpoint readiness. The result includes the selector.

Discovery accepts canonical `<pid>.json` files whose positive PID matches the record.
It skips invalid records without deleting files. When a record includes
`processStartEpochSeconds`, discovery compares it with the operating system's process
start time and hides confirmed mismatches caused by PID reuse. Older records with a
missing or `null` start time, and failed or unavailable process metadata, use PID
liveness. The gateway never deletes registry files. Readiness failures preserve
registrations and do not resolve duplicate names.

Game tools accept optional `instance_name` and `instance_pid` selectors. Omission
works with exactly one live registered instance. With multiple live instances,
explicit selection is required even if only one endpoint is ready. If both selectors
are provided, they must match the same registry record. The gateway strips these
selector fields before forwarding arguments to the mod. Selection is exact: if the
chosen endpoint is unavailable or its health check fails, the request returns an
error and is never rerouted to a different client. Instance names remain stable
across restarts when configured that way; process
IDs change. `mc_wait_for_instance`
only checks endpoint readiness. Use the mod's existing `mc_wait_for` to wait for a
screen or in-game message after connecting to a world.

The gateway forwards built-in tools from the shared JSON catalogue. Tools contributed
by extension mods are not dynamically discovered or forwarded.

## Limits and local trust

The gateway talks to `127.0.0.1` and uses the mod's registry; it is intended for one
trusted machine. Anyone who can access the configured stdio process can control the
selected player's game. It does not launch Minecraft, stop a client, or install
extensions. The gateway does not retry calls, and canceling a request does not undo a
game action that has already reached the client.

Both Claude Code and Codex have passed no-game startup, two-client token routing and
restart checks without reconnecting the gateway. See [Known issues](known-issues.md)
for package availability.
