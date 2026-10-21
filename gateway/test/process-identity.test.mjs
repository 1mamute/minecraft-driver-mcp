import assert from "node:assert/strict";
import childProcess from "node:child_process";
import { syncBuiltinESMExports } from "node:module";
import { promisify } from "node:util";
import { test } from "node:test";

let inspect;
const originalExecFile = childProcess.execFile;
const execFile = () => {
    throw new Error("Use the asynchronous process inspection boundary");
};
execFile[promisify.custom] = (...args) => inspect(...args);
childProcess.execFile = execFile;
syncBuiltinESMExports();
const { processStartTimes } = await import("../dist/process-identity.js");
childProcess.execFile = originalExecFile;
syncBuiltinESMExports();

const windowsOnly = { skip: process.platform !== "win32" };

test("Windows lookup prefers PowerShell 7 without starting legacy PowerShell", windowsOnly, async () => {
    const shells = [];
    inspect = async (shell) => {
        shells.push(shell);
        return { stdout: "123 1000\r\n" };
    };
    assert.deepEqual(await processStartTimes([123]), new Map([[123, 1000]]));
    assert.deepEqual(shells, ["pwsh.exe"]);
});

for (const code of ["ENOENT", "EACCES", "EPERM"]) {
    test(`Windows lookup falls back when PowerShell 7 cannot start (${code})`, windowsOnly, async () => {
        const shells = [];
        inspect = async (shell) => {
            shells.push(shell);
            if (shell === "pwsh.exe") throw Object.assign(new Error("Cannot start"), { code });
            return { stdout: "123 1000\r\n" };
        };
        assert.deepEqual(await processStartTimes([123]), new Map([[123, 1000]]));
        assert.deepEqual(shells, ["pwsh.exe", "powershell.exe"]);
    });
}

test("Windows lookup does not retry a started shell that failed or timed out", windowsOnly, async () => {
    for (const code of [1, "ETIMEDOUT"]) {
        const shells = [];
        inspect = async (shell) => {
            shells.push(shell);
            throw Object.assign(new Error("Inspection failed"), { code });
        };
        assert.deepEqual(await processStartTimes([123]), new Map());
        assert.deepEqual(shells, ["pwsh.exe"]);
    }
});
