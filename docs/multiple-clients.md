# Running several clients

Testing multiplayer needs two or more clients. Each client starts its own MCP server, so the ports must not collide.
The mod is built for this: nothing is global to the machine except a small registry of running instances.
Register the [gateway](gateway.md) once to reach all clients through one agent connection.

## Ports

- **Default.** A client takes the first free port from 25890. Two clients get 25890 and 25891. If two clients start in
  the same instant and pick the same port, the loser retries on the next one.
- **Fixed port.** `-Ddriver.port=25901` uses exactly that port. If it is taken, the mod logs an error and leaves the
  driver off, and the game still starts.

## Names

`-Ddriver.name=Alice` labels the instance. The name appears in the MCP server name and in its instructions, so an
assistant connected to several clients can tell which one it is talking to. Without it, the name is the player name.

## Discovery

Each running client writes `~/.minecraft-driver-mcp/instances/<pid>.json` and removes it on exit. Call
`mc_list_instances` through the gateway to list live clients and their selectors, or
`mc_wait_for_instance` to wait for one to become ready. Game calls accept `instance_name`
or `instance_pid`; multiple live clients require explicit selection even if only one
endpoint is ready. A crashed client leaves its file behind, but it is ignored because
its process no longer exists.

## Game directories

Minecraft requires a separate run directory for each client. Give each one its own, and a distinct account name.

## Stable setups

Give each client a unique `driver.name` and leave ports automatic. The gateway
discovers the selected client's endpoint on each call, so name-based selection
continues working after a restart. PIDs identify a single process and change on restart.

## Direct HTTP alternative

For specialized setups, extension tools or endpoint debugging, connect directly to
each client's HTTP endpoint. Fixed ports can be useful for these registrations:

```bash
claude mcp add --transport http alice http://127.0.0.1:25901/mcp
claude mcp add --transport http bob   http://127.0.0.1:25902/mcp
```

## Notes

- In a Gradle development environment, two clients can share `logs/latest.log`, and the second one cannot open it.
  `mc_read_log` is not affected, because each client captures its own process. See
  [Known issues](known-issues.md#shared-log-file-in-development).
- Start clients one after another. Two Gradle builds at once can corrupt the Kotlin build cache.
