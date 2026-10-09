# Minecraft Driver MCP documentation

Minecraft Driver MCP is a Fabric client mod that runs a [Model Context Protocol](https://modelcontextprotocol.io) (MCP)
server inside a real Minecraft client. An AI assistant connects to it and can read screens, click widgets, move, chat,
inspect inventories, read the game log and take screenshots, which makes it a practical tool for testing and debugging
Fabric mods.

This documentation has three parts, one for each kind of reader.

## For users

Read these to install the mod and drive a game with an assistant.

| Page | Contents |
| --- | --- |
| [Introduction](introduction.md) | What the mod is, why it exists, what it can do and what it is not |
| [Installation](installation.md) | Requirements, installing the jar, using it in a mod's development environment |
| [Getting started](getting-started.md) | Connecting an assistant, first calls, the first-launch screen |
| [Debugging mods with an assistant](debugging-mods.md) | Workflows for testing screens, commands, containers and crashes |
| [Tool reference](tools.md) | Every MCP tool with its arguments and results |
| [Configuration](configuration.md) | System properties, ports, names, the optional access token |
| [Several clients](multiple-clients.md) | Running two or more clients for multiplayer tests |
| [Extension API](extension-api.md) | Adding tools from another mod |
| [Known issues](known-issues.md) | Limits, troubleshooting and unverified areas |
| [Release notes](release-notes.md) | What each version contains and its known limits |

## For contributors

The [contributor guide](contributing/README.md) covers how the mod is built and changed.

| Page | Contents |
| --- | --- |
| [Architecture](contributing/architecture.md) | Layers, threading, transport, several clients, packaging |
| [Extending the mod](contributing/extending.md) | Adding a tool, a client concern or a mixin; supporting a Minecraft version |
| [Testing](contributing/testing.md) | Unit tests, the runtime check, driving a client by hand |
| [Code style](contributing/code-style.md) | Naming, readability limits, tool conventions, linting |
| [Dependencies](contributing/dependencies.md) | Where versions live, upgrading, adding a Minecraft version |
| [Verification status](contributing/known-issues.md) | What has been verified, what has not, and design limits |

## For LLM agents

[llms.txt](../llms.txt) is a single self-contained brief for an agent: how to connect, every tool, debugging recipes,
and how to change the mod itself.
