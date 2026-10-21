import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { listInstances } from "../dist/registry.js";
import { processStartTimes } from "../dist/process-identity.js";

async function fixture(t, overrides = {}) {
    const directory = await mkdtemp(join(tmpdir(), "driver-identity-"));
    t.after(() => rm(directory, { recursive: true, force: true }));
    const record = {
        name: "client",
        pid: process.pid,
        url: "http://127.0.0.1:25890/mcp",
        port: 25890,
        minecraftVersion: "1.21.1",
        gameDirectory: directory,
        processStartEpochSeconds: 1000,
        ...overrides,
    };
    const file = join(directory, `${record.pid}.json`);
    await writeFile(file, JSON.stringify(record));
    return { directory, file, record };
}

for (const [label, observed, expected] of [
    ["matching process start", 1000, 1],
    ["reused PID", 2000, 0],
    ["unavailable process start", undefined, 1],
]) {
    test(`registry handles ${label} without deleting the registration`, async (t) => {
        const { directory, file, record } = await fixture(t);
        const listed = await listInstances(directory, undefined, async () =>
            observed === undefined ? new Map() : new Map([[process.pid, observed]]),
        );
        assert.equal(listed.length, expected);
        assert.deepEqual(JSON.parse(await readFile(file, "utf8")), record);
    });
}

test("legacy records retain PID-only discovery when process start is absent or null", async (t) => {
    for (const processStartEpochSeconds of [undefined, null]) {
        const { directory } = await fixture(t, { processStartEpochSeconds });
        const listed = await listInstances(directory, undefined, async (pids) => {
            assert.deepEqual(pids, []);
            return new Map();
        });
        assert.equal(listed.length, 1);
        assert.equal(listed[0].authRequired, false);
    }
});

test("invalid process start metadata is ignored and retained on disk", async (t) => {
    for (const processStartEpochSeconds of [0, -1, 1.5, "1000", true]) {
        const { directory, file } = await fixture(t, { processStartEpochSeconds });
        assert.deepEqual(await listInstances(directory), []);
        assert.ok(await readFile(file, "utf8"));
    }
});

test("OS process-start lookup discovers the current process and rejects stale identity", async (t) => {
    if (!["win32", "linux", "darwin"].includes(process.platform)) return t.skip("OS inspection unavailable");
    const before = Math.floor(Date.now() / 1000 - process.uptime()) - 1;
    const starts = await processStartTimes([process.pid]);
    const actual = starts.get(process.pid);
    assert.ok(actual >= before && actual <= Math.floor(Date.now() / 1000), `Missing or invalid start time: ${actual}`);
    const { directory, file, record } = await fixture(t, { processStartEpochSeconds: actual });
    assert.equal((await listInstances(directory)).length, 1);
    await writeFile(file, JSON.stringify({ ...record, processStartEpochSeconds: 1 }));
    assert.deepEqual(await listInstances(directory), []);
});

test("Windows process-start lookup retains live results when another PID is unavailable", async (t) => {
    if (process.platform !== "win32") return t.skip("Windows process inspection");
    const before = Math.floor(Date.now() / 1000 - process.uptime()) - 1;
    const missingPid = 2_147_483_647;
    const starts = await processStartTimes([missingPid, process.pid, missingPid]);
    const actual = starts.get(process.pid);
    assert.equal(starts.size, 1);
    assert.ok(actual >= before && actual <= Math.floor(Date.now() / 1000), `Missing or invalid start time: ${actual}`);
});

test("Windows process-start lookup propagates cancellation while PowerShell is starting", async (t) => {
    if (process.platform !== "win32") return t.skip("Windows process inspection");
    const controller = new AbortController();
    const lookup = processStartTimes([process.pid], controller.signal);
    controller.abort();
    await assert.rejects(lookup, { name: "AbortError" });
});

test("cancelling process inspection does not turn unknown metadata into a successful listing", async (t) => {
    const { directory } = await fixture(t);
    const controller = new AbortController();
    await assert.rejects(
        listInstances(directory, controller.signal, async () => {
            controller.abort();
            return new Map();
        }),
        { name: "AbortError" },
    );
});
