import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve, join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { Gateway } from "../gateway/dist/gateway.js";
import { listInstances, isAlive } from "../gateway/dist/registry.js";

const [directoryArgument] = process.argv.slice(2);
assert.ok(directoryArgument, "Usage: node scripts/check-registry.mjs <isolated-registry-directory>");
const directory = resolve(directoryArgument);
const catalogue = JSON.parse(await readFile(new URL("../gateway/catalogue.json", import.meta.url), "utf8"));
const gateway = new Gateway(
    { registryDirectory: directory, tokens: {}, connectTimeoutMs: 5000, requestTimeoutMs: 10000 },
    catalogue,
);
const instances = await listInstances(directory);
assert.equal(instances.length, 2, "Start exactly two disposable, unauthenticated clients in this isolated registry");
assert.equal(new Set(instances.map((instance) => instance.name)).size, 2, "Use distinct instance names");
assert.ok(instances.every((instance) => !instance.authRequired && instance.processStartEpochSeconds > 0));
const [first, second] = instances;
const secondFile = join(directory, `${second.pid}.json`);
const original = await readFile(secondFile, "utf8");
let requestId = 0;

async function rpc(instance, method, params) {
    const response = await fetch(instance.url, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json, text/event-stream" },
        body: JSON.stringify({ jsonrpc: "2.0", id: ++requestId, method, params }),
        signal: AbortSignal.timeout(10000),
    });
    assert.equal(response.status, 200, `${method}: HTTP ${response.status}`);
    const body = await response.text();
    const message = response.headers.get("content-type")?.includes("text/event-stream")
        ? JSON.parse(
              body
                  .split("\n")
                  .find((line) => line.startsWith("data: "))
                  .slice(6),
          )
        : JSON.parse(body);
    assert.equal(message.error, undefined, JSON.stringify(message.error));
    return message.result;
}

async function direct(instance, name, args = {}) {
    const result = await rpc(instance, "tools/call", { name, arguments: args });
    assert.ok(!result.isError, JSON.stringify(result));
    return JSON.parse(result.content[0].text);
}

async function routed(name, args = {}) {
    return gateway.call(name, args, AbortSignal.timeout(15000));
}

async function compare(expected) {
    const pids = (records) => records.map((instance) => instance.pid).sort((a, b) => a - b);
    for (const instance of instances) {
        assert.deepEqual(pids(await direct(instance, "mc_list_instances")), pids(expected));
    }
    const result = await routed("mc_list_instances");
    assert.ok(!result.isError, JSON.stringify(result));
    assert.deepEqual(pids(JSON.parse(result.content[0].text).instances), pids(expected));
}

for (const instance of instances) {
    await rpc(instance, "initialize", {
        protocolVersion: "2025-03-26",
        capabilities: {},
        clientInfo: { name: "registry-check", version: "1.0.0" },
    });
    assert.ok((await rpc(instance, "tools/list", {})).tools.some((tool) => tool.name === "mc_list_instances"));
    const widgets = await direct(instance, "mc_list_widgets");
    assert.ok(
        widgets.widgets.some((widget) => widget.label === "Quit Game"),
        "Use the English title screen",
    );
    assert.ok(!(await routed("mc_get_state", { instance_pid: instance.pid })).isError);
}

try {
    await compare(instances);
    assert.equal((await routed("mc_get_state")).isError, true);
    console.log("PASS: both readers retain both clients, require selection and route by PID");

    await writeFile(secondFile, JSON.stringify({ ...second, processStartEpochSeconds: 1 }));
    await compare([first]);
    const stale = await routed("mc_get_state", { instance_pid: second.pid });
    assert.equal(stale.isError, true);
    assert.match(stale.content[0].text, /No matching/);
    assert.equal(JSON.parse(await readFile(secondFile, "utf8")).processStartEpochSeconds, 1);
    console.log("PASS: confirmed PID reuse is hidden and explicit routing fails without deleting on read");

    const { processStartEpochSeconds, ...legacy } = second;
    await writeFile(secondFile, JSON.stringify(legacy));
    await compare(instances);
    console.log("PASS: older records remain discoverable by PID");

    await writeFile(secondFile, JSON.stringify({ ...second, port: 1, url: "http://127.0.0.1:1/mcp" }));
    await compare(instances);
    const listed = await routed("mc_list_instances", { check_ready: true });
    assert.ok(!listed.isError, JSON.stringify(listed));
    const unavailable = JSON.parse(listed.content[0].text).instances.find((instance) => instance.pid === second.pid);
    assert.equal(unavailable.status, "connection_failed");
    assert.equal((await routed("mc_get_state")).isError, true);
    assert.equal(JSON.parse(await readFile(secondFile, "utf8")).port, 1);
    console.log("PASS: endpoint failure retains live records and ambiguity");
} finally {
    await writeFile(secondFile, original);
}

await compare(instances);
await rpc(second, "tools/call", { name: "mc_click", arguments: { label: "Quit Game" } }).catch(() => {});
const deadline = Date.now() + 15000;
while (isAlive(second.pid) && Date.now() < deadline) await delay(100);
assert.equal(isAlive(second.pid), false, "Second client must exit after Quit Game");
assert.deepEqual(
    (await listInstances(directory)).map((instance) => instance.pid),
    [first.pid],
);
await assert.rejects(readFile(secondFile), { code: "ENOENT" });
assert.deepEqual(
    (await direct(first, "mc_list_instances")).map((instance) => instance.pid),
    [first.pid],
);
console.log("PASS: graceful client shutdown removes its registration from both readers");
