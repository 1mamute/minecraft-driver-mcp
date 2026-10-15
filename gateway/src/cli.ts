#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { loadConfig } from "./config.js";
import { Gateway } from "./gateway.js";

async function main(): Promise<void> {
    const args = process.argv.slice(2);
    if (args.includes("--help")) {
        process.stdout.write(
            "Usage: minecraft-driver-mcp [--config PATH]\nRuns a stdio MCP gateway. Configuration defaults to ~/.minecraft-driver-mcp/gateway.json.\n",
        );
        return;
    }
    if (args.length && (args.length !== 2 || args[0] !== "--config"))
        throw new Error("Usage: minecraft-driver-mcp [--config PATH]");
    const catalogue = JSON.parse(await readFile(new URL("../catalogue.json", import.meta.url), "utf8"));
    const gateway = new Gateway(await loadConfig(args[1]), catalogue);
    const server = new Server(
        { name: "minecraft-driver-mcp", version: catalogue.version },
        { capabilities: { tools: {} } },
    );
    server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: gateway.tools }));
    server.setRequestHandler(CallToolRequestSchema, async (request, extra) =>
        gateway.call(request.params.name, request.params.arguments ?? {}, extra.signal),
    );
    const close = (): void => {
        void server.close();
    };
    process.stdin.once("end", close);
    process.stdin.once("close", close);
    server.onclose = () => {
        process.stdin.off("end", close);
        process.stdin.off("close", close);
    };
    await server.connect(new StdioServerTransport());
}
main().catch(() => {
    process.stderr.write(
        "Gateway could not start. Check --config PATH, its JSON values, and the installed package catalogue.\n",
    );
    process.exitCode = 1;
});
