import assert from "node:assert/strict";
import { test } from "node:test";
import { createServer } from "node:http";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { Gateway } from "../dist/gateway.js";
import { listInstances } from "../dist/registry.js";
import { loadConfig } from "../dist/config.js";

const catalogue = {
    version: "0.1.0",
    tools: [{ name: "mc_get_state", description: "Observe", inputSchema: { type: "object", properties: {} } }],
};
const signal = () => new AbortController().signal;
const parsed = (result) => JSON.parse(result.content[0].text);

async function setup(t) {
    const directory = await mkdtemp(join(tmpdir(), "driver-gateway-"));
    t.after(() => rm(directory, { recursive: true, force: true }));
    const config = { registryDirectory: directory, tokens: {}, connectTimeoutMs: 300, requestTimeoutMs: 300 };
    return { directory, config, gateway: new Gateway(config, catalogue) };
}

async function mock(t, options = {}) {
    const calls = [];
    const server = createServer(async (request, response) => {
        let text = "";
        for await (const chunk of request) text += chunk;
        const body = text ? JSON.parse(text) : {};
        calls.push({
            body,
            authorization: request.headers.authorization,
            protocol: request.headers["mcp-protocol-version"],
        });
        if (options.token && request.headers.authorization !== `Bearer ${options.token}`) {
            response.writeHead(401).end("secret must never appear");
            return;
        }
        if (options.redirect) {
            response.writeHead(307, { location: options.redirect }).end();
            return;
        }
        if (options.handshakeDelay && (body.method === "initialize" || body.method === "notifications/initialized")) {
            await new Promise((resolve) => setTimeout(resolve, options.handshakeDelay));
        }
        if (body.method === "tools/call") options.onTool?.();
        if (!("id" in body)) {
            response.writeHead(202).end();
            return;
        }
        if (options.delay && body.method === "tools/call") {
            await new Promise((resolve) => setTimeout(resolve, options.delay));
        }
        if (options.failCall && body.method === "tools/call") {
            response.writeHead(404).end("secret must never appear");
            return;
        }
        const result =
            body.method === "initialize"
                ? {
                      protocolVersion: body.params.protocolVersion,
                      capabilities: { tools: {} },
                      serverInfo: { name: "mock", version: options.version ?? "0.1.0+1.21.1" },
                  }
                : (options.result ?? { content: [{ type: "text", text: '{"world":null}' }] });
        if (options.sse && body.method === "tools/call") {
            response.writeHead(200, { "content-type": "text/event-stream" });
            response.end(`event: message\ndata: ${JSON.stringify({ jsonrpc: "2.0", id: body.id, result })}\n\n`);
        } else {
            response.writeHead(200, { "content-type": "application/json" });
            response.end(JSON.stringify({ jsonrpc: "2.0", id: body.id, result }));
        }
    });
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    t.after(
        () =>
            new Promise((resolve) => {
                server.closeAllConnections();
                server.close(resolve);
            }),
    );
    const port = server.address().port;
    return { port, url: `http://127.0.0.1:${port}/mcp`, calls };
}

async function record(directory, endpoint, overrides = {}) {
    const value = {
        name: "client",
        pid: process.pid,
        ...endpoint,
        minecraftVersion: "1.21.1",
        gameDirectory: directory,
        authRequired: false,
        ...overrides,
    };
    delete value.calls;
    await writeFile(join(directory, `${value.pid}.json`), JSON.stringify(value));
    return value;
}

async function child(t) {
    const process = spawn(globalThis.process.execPath, ["-e", "setInterval(()=>{},1000)"], { stdio: "ignore" });
    t.after(() => process.kill());
    return process;
}

test("lists no clients and advertises tools without a game", async (t) => {
    const { gateway } = await setup(t);
    assert.deepEqual(parsed(await gateway.call("mc_list_instances", {}, signal())), { instances: [] });
    assert.deepEqual(
        gateway.tools.map((tool) => tool.name),
        ["mc_list_instances", "mc_wait_for_instance", "mc_get_state"],
    );
    assert.ok(gateway.tools[2].inputSchema.properties.instance_pid);
    assert.equal((await gateway.call("mc_get_state", {}, signal())).isError, true);
});

test("ignores stale and malformed entries but retains unsafe live endpoints for selection", async (t) => {
    const { directory } = await setup(t);
    await writeFile(join(directory, "77.json"), "{");
    await record(directory, { port: 1234, url: "http://127.0.0.1:1234/mcp" }, { pid: 999999999 });
    for (const url of [
        "http://localhost:1234/mcp",
        "http://2130706433:1234/mcp",
        "http://127.0.0.1:1234/mcp?token=x",
    ]) {
        await record(directory, { port: 1234, url });
        const instances = await listInstances(directory);
        assert.equal(instances.length, 1);
        assert.equal(instances[0].url, url);
    }
});

test("initializes each operation, strips selectors, and preserves text images and errors", async (t) => {
    const { gateway, directory } = await setup(t);
    const expected = {
        isError: true,
        content: [
            { type: "text", text: "No world" },
            { type: "image", data: "YQ==", mimeType: "image/png" },
        ],
    };
    const endpoint = await mock(t, { result: expected, sse: true });
    await record(directory, endpoint);
    assert.deepEqual(
        await gateway.call(
            "mc_get_state",
            { instance_name: "client", instance_pid: process.pid, arbitrary: 1 },
            signal(),
        ),
        expected,
    );
    assert.deepEqual(
        endpoint.calls.map((call) => call.body.method),
        ["initialize", "notifications/initialized", "tools/call"],
    );
    assert.deepEqual(endpoint.calls[2].body.params.arguments, { arbitrary: 1 });
    assert.ok(endpoint.calls[2].protocol);
});

test("requires selection among all live clients, including an endpoint that is not ready", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t);
    const other = await child(t);
    await record(directory, endpoint);
    await record(directory, { port: 1, url: "http://127.0.0.1:1/mcp" }, { pid: other.pid, name: "loading" });
    assert.match((await gateway.call("mc_get_state", {}, signal())).content[0].text, /Multiple/);
    assert.equal((await gateway.call("mc_get_state", { instance_name: "client" }, signal())).isError, undefined);
});

test("routes two differently authenticated instances and refuses duplicate token names", async (t) => {
    const { config, directory } = await setup(t);
    const first = await mock(t, { token: "one-secret" });
    const second = await mock(t, { token: "two-secret" });
    const other = await child(t);
    process.env.GATEWAY_TEST_ONE = "one-secret";
    process.env.GATEWAY_TEST_TWO = "two-secret";
    t.after(() => {
        delete process.env.GATEWAY_TEST_ONE;
        delete process.env.GATEWAY_TEST_TWO;
    });
    config.tokens = { first: "GATEWAY_TEST_ONE", second: "GATEWAY_TEST_TWO" };
    const gateway = new Gateway(config, catalogue);
    await record(directory, first, { name: "first", authRequired: true });
    await record(directory, second, { pid: other.pid, name: "second", authRequired: true });
    const listed = parsed(await gateway.call("mc_list_instances", { check_ready: true }, signal()));
    assert.ok(listed.instances.every((instance) => instance.ready));
    assert.equal((await gateway.call("mc_get_state", { instance_name: "second" }, signal())).isError, undefined);
    assert.equal(second.calls.at(-1).authorization, "Bearer two-secret");
    await record(directory, second, { pid: other.pid, name: "first", authRequired: true });
    const duplicate = await gateway.call("mc_get_state", { instance_pid: process.pid }, signal());
    assert.match(duplicate.content[0].text, /duplicate instance name/);
    assert.doesNotMatch(JSON.stringify(duplicate), /one-secret|two-secret/);
});

test("stable name follows a restarted client while a stale PID never reroutes", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t);
    const old = await child(t);
    await record(directory, endpoint, { pid: old.pid });
    const oldPid = old.pid;
    old.kill();
    await new Promise((resolve) => old.once("exit", resolve));
    await record(directory, endpoint);
    assert.equal((await gateway.call("mc_get_state", { instance_name: "client" }, signal())).isError, undefined);
    assert.match(
        (await gateway.call("mc_get_state", { instance_pid: oldPid }, signal())).content[0].text,
        /No matching/,
    );
});

test("a confirmed reused PID cannot route to an endpoint or a different live client", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t);
    const other = await child(t);
    await record(directory, endpoint, { processStartEpochSeconds: 1 });
    await record(directory, endpoint, { pid: other.pid, name: "other" });
    assert.match(
        (await gateway.call("mc_get_state", { instance_pid: process.pid }, signal())).content[0].text,
        /No matching/,
    );
    assert.equal(endpoint.calls.length, 0);
    assert.equal(parsed(await gateway.call("mc_list_instances", {}, signal())).instances.length, 1);
});

test("readiness failure retains live identity records and ambiguity without deleting files", async (t) => {
    const { gateway, directory } = await setup(t);
    const { processStartTimes } = await import("../dist/process-identity.js");
    const { readFile } = await import("node:fs/promises");
    const endpoint = await mock(t);
    const other = await child(t);
    const starts = await processStartTimes([process.pid, other.pid]);
    await record(directory, endpoint, { processStartEpochSeconds: starts.get(process.pid) });
    const unavailable = await record(
        directory,
        { port: 1, url: "http://127.0.0.1:1/mcp" },
        { pid: other.pid, processStartEpochSeconds: starts.get(other.pid) },
    );
    const listed = parsed(await gateway.call("mc_list_instances", { check_ready: true }, signal()));
    assert.equal(listed.instances.length, 2);
    assert.equal(listed.instances.find((instance) => instance.pid === other.pid).status, "connection_failed");
    assert.deepEqual(JSON.parse(await readFile(join(directory, `${other.pid}.json`), "utf8")), unavailable);
    assert.match((await gateway.call("mc_get_state", {}, signal())).content[0].text, /Multiple/);
    assert.equal(endpoint.calls.filter((call) => call.body.method === "tools/call").length, 0);
});

test("discovery without readiness still respects a positive wait deadline", async (t) => {
    const { gateway, directory } = await setup(t);
    const { processStartTimes } = await import("../dist/process-identity.js");
    const starts = await processStartTimes([process.pid]);
    await record(directory, { port: 1, url: "http://127.0.0.1:1/mcp" }, {
        processStartEpochSeconds: starts.get(process.pid) ?? 1,
    });
    const result = await gateway.call("mc_wait_for_instance", { ready: false, timeout_ms: 1 }, signal());
    assert.equal(result.isError, true);
    assert.match(result.content[0].text, /Timed out/);
});

test("readiness requires authentication and matching release but not a world", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t, { version: "0.2.0+1.21.1" });
    await record(directory, endpoint);
    const list = parsed(await gateway.call("mc_list_instances", { check_ready: true }, signal()));
    assert.equal(list.instances[0].ready, false);
    assert.match(list.instances[0].reason, /matching/);
    assert.equal(
        (await gateway.call("mc_wait_for_instance", { ready: false, timeout_ms: 0 }, signal())).isError,
        undefined,
    );
    assert.equal((await gateway.call("mc_wait_for_instance", { timeout_ms: 1 }, signal())).isError, true);
});

test("wait discovers clients that appear later and cancellation stops waiting", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t);
    const timer = setTimeout(() => void record(directory, endpoint), 50);
    t.after(() => clearTimeout(timer));
    assert.equal((await gateway.call("mc_wait_for_instance", { timeout_ms: 1000 }, signal())).isError, undefined);
    const controller = new AbortController();
    setTimeout(() => controller.abort(), 20);
    const start = Date.now();
    assert.equal(
        (await gateway.call("mc_wait_for_instance", { instance_name: "absent", timeout_ms: 60000 }, controller.signal))
            .isError,
        true,
    );
    assert.ok(Date.now() - start < 1000);
});

test("does not replay failed tool calls and reports unknown outcome without HTTP secrets", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t, { failCall: true });
    await record(directory, endpoint);
    const result = await gateway.call("mc_get_state", {}, signal());
    assert.match(result.content[0].text, /outcome is unknown/);
    assert.doesNotMatch(JSON.stringify(result), /secret must/);
    assert.equal(endpoint.calls.filter((call) => call.body.method === "tools/call").length, 1);
});

test("refuses redirects without contacting the target", async (t) => {
    const { gateway, directory } = await setup(t);
    const target = await mock(t);
    const redirect = await mock(t, { redirect: target.url });
    await record(directory, redirect);
    assert.equal((await gateway.call("mc_get_state", {}, signal())).isError, true);
    assert.equal(target.calls.length, 0);
});

test("tool timeout and cancellation stop requests without replay", async (t) => {
    const { config, directory } = await setup(t);
    config.requestTimeoutMs = 30;
    const gateway = new Gateway(config, catalogue);
    const options = { delay: 200, onTool: undefined };
    const endpoint = await mock(t, options);
    await record(directory, endpoint);
    assert.match((await gateway.call("mc_get_state", {}, signal())).content[0].text, /outcome is unknown/);
    const controller = new AbortController();
    options.onTool = () => controller.abort();
    config.requestTimeoutMs = 300;
    assert.match((await gateway.call("mc_get_state", {}, controller.signal)).content[0].text, /cancelled/);
    assert.equal(endpoint.calls.filter((call) => call.body.method === "tools/call").length, 2);
});

test("loads relative registry config, validates token variable names and timeout bounds", async (t) => {
    const { directory } = await setup(t);
    const file = join(directory, "gateway.json");
    await writeFile(file, JSON.stringify({ registryDirectory: "registry", tokens: { client: "TOKEN_ENV" } }));
    assert.equal((await loadConfig(file)).registryDirectory, join(directory, "registry"));
    await writeFile(file, JSON.stringify({ tokens: { client: "actual token with spaces" } }));
    await assert.rejects(loadConfig(file), /environment variable names/);
    await writeFile(file, JSON.stringify({ requestTimeoutMs: -1 }));
    await assert.rejects(loadConfig(file), /Timeouts/);
});

test("unsafe live client prevents implicit routing and reports unsafe readiness", async (t) => {
    const { gateway, directory } = await setup(t);
    const endpoint = await mock(t);
    const other = await child(t);
    await record(directory, endpoint);
    await record(directory, { port: 1234, url: "http://localhost:1234/mcp" }, { pid: other.pid });
    assert.match((await gateway.call("mc_get_state", {}, signal())).content[0].text, /Multiple/);
    const listed = parsed(await gateway.call("mc_list_instances", { check_ready: true }, signal()));
    assert.equal(listed.instances.find((instance) => instance.pid === other.pid).status, "unsafe_endpoint");
    assert.equal(endpoint.calls.filter((call) => call.body.method === "tools/call").length, 0);
});

test("explicit missing config fails and readiness distinguishes missing and rejected authentication", async (t) => {
    const { config, directory } = await setup(t);
    await assert.rejects(loadConfig(join(directory, "missing.json")), /Cannot read/);
    const endpoint = await mock(t, { token: "expected" });
    await record(directory, endpoint, { authRequired: true });
    let gateway = new Gateway(config, catalogue);
    let listed = parsed(await gateway.call("mc_list_instances", { check_ready: true }, signal()));
    assert.equal(listed.instances[0].status, "authentication_missing");
    config.tokens = { client: "GATEWAY_WRONG_TOKEN" };
    process.env.GATEWAY_WRONG_TOKEN = "wrong-secret";
    t.after(() => delete process.env.GATEWAY_WRONG_TOKEN);
    gateway = new Gateway(config, catalogue);
    listed = parsed(await gateway.call("mc_list_instances", { check_ready: true }, signal()));
    assert.equal(listed.instances[0].status, "authentication_failed");
    assert.doesNotMatch(JSON.stringify(listed), /wrong-secret|expected|secret must/);
});

test("stdio initializes with the complete bundled catalogue without a running game", async (t) => {
    const { directory } = await setup(t);
    const { readFile } = await import("node:fs/promises");
    const { Client } = await import("@modelcontextprotocol/sdk/client/index.js");
    const { StdioClientTransport } = await import("@modelcontextprotocol/sdk/client/stdio.js");
    const configPath = join(directory, "gateway.json");
    await writeFile(configPath, JSON.stringify({ registryDirectory: directory }));
    const transport = new StdioClientTransport({
        command: process.execPath,
        args: [
            fileURLToPath(new URL("../dist/cli.js", import.meta.url)),
            "--config",
            configPath,
        ],
        stderr: "pipe",
    });
    const client = new Client({ name: "gateway-test", version: "1.0.0" });
    t.after(() => client.close());
    await client.connect(transport);
    const bundled = JSON.parse(await readFile(new URL("../catalogue.json", import.meta.url), "utf8"));
    const pkg = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
    assert.equal(client.getServerVersion().version, bundled.version);
    assert.equal(pkg.version, bundled.version);
    const tools = (await client.listTools()).tools;
    for (const expected of bundled.tools.filter((tool) => tool.name !== "mc_list_instances")) {
        const actual = tools.find((tool) => tool.name === expected.name);
        assert.deepEqual(actual, {
            ...expected,
            inputSchema: {
                ...expected.inputSchema,
                properties: {
                    ...expected.inputSchema.properties,
                    instance_name: actual.inputSchema.properties.instance_name,
                    instance_pid: actual.inputSchema.properties.instance_pid,
                },
            },
        });
    }
    assert.deepEqual(parsed(await client.callTool({ name: "mc_list_instances", arguments: {} })), { instances: [] });
});

test("closing host stdin aborts an in-flight wait and exits promptly", async (t) => {
    const { directory } = await setup(t);
    const configPath = join(directory, "gateway.json");
    await writeFile(configPath, JSON.stringify({ registryDirectory: directory }));
    const child = spawn(process.execPath, [
        fileURLToPath(new URL("../dist/cli.js", import.meta.url)),
        "--config",
        configPath,
    ]);
    t.after(() => child.kill());
    child.stderr.resume();
    const exit = new Promise((resolve) => child.once("exit", resolve));
    const initialized = new Promise((resolve) => child.stdout.once("data", resolve));
    child.stdin.write(
        JSON.stringify({
            jsonrpc: "2.0",
            id: 1,
            method: "initialize",
            params: {
                protocolVersion: "2025-03-26",
                capabilities: {},
                clientInfo: { name: "eof-test", version: "1" },
            },
        }) + "\n",
    );
    await initialized;
    child.stdout.resume();
    child.stdin.write(JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }) + "\n");
    child.stdin.write(
        JSON.stringify({
            jsonrpc: "2.0",
            id: 2,
            method: "tools/call",
            params: {
                name: "mc_wait_for_instance",
                arguments: { timeout_ms: 60000 },
            },
        }) + "\n",
    );
    await new Promise((resolve) => setTimeout(resolve, 50));
    child.stdin.end();
    const timer = setTimeout(() => child.kill(), 1500);
    t.after(() => clearTimeout(timer));
    assert.equal(await exit, 0);
});

test("connection deadline covers initialize plus initialized notification", async (t) => {
    const { config, directory } = await setup(t);
    config.connectTimeoutMs = 100;
    config.requestTimeoutMs = 1000;
    const endpoint = await mock(t, { handshakeDelay: 70 });
    await record(directory, endpoint);
    const result = await new Gateway(config, catalogue).call("mc_get_state", {}, signal());
    assert.equal(result.isError, true);
    assert.equal(endpoint.calls.filter((call) => call.body.method === "tools/call").length, 0);
});

test("request deadline includes handshake time before the tool starts", async (t) => {
    const { config, directory } = await setup(t);
    config.connectTimeoutMs = 500;
    config.requestTimeoutMs = 180;
    const endpoint = await mock(t, { handshakeDelay: 60, delay: 100 });
    await record(directory, endpoint);
    const result = await new Gateway(config, catalogue).call("mc_get_state", {}, signal());
    assert.match(result.content[0].text, /outcome is unknown/);
    assert.equal(endpoint.calls.filter((call) => call.body.method === "tools/call").length, 1);
});
