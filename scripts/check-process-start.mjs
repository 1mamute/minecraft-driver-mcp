import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execute = promisify(execFile);
if (process.platform !== "win32") process.exit(0);

async function inspect(shell, operation, command) {
    const started = performance.now();
    try {
        const { stdout } = await execute(shell, ["-NoProfile", "-NonInteractive", "-Command", command], {
            timeout: 20000,
            windowsHide: true,
        });
        console.log(JSON.stringify({ shell, operation, elapsedMs: Math.round(performance.now() - started), output: stdout.trim() }));
    } catch (error) {
        console.log(JSON.stringify({ shell, operation, elapsedMs: Math.round(performance.now() - started), code: error.code, killed: error.killed }));
    }
}

const shells = ["powershell.exe", "pwsh.exe"];
await Promise.all(shells.map((shell) => inspect(shell, "startup", '"ready"')));
const command =
    "$PSModuleAutoLoadingPreference = 'None'; " +
    `$p = [System.Diagnostics.Process]::GetProcessById(${process.pid}); ` +
    '"{0} {1}" -f $p.Id, ([DateTimeOffset]$p.StartTime.ToUniversalTime()).ToUnixTimeSeconds(); $p.Dispose(); exit 0';
await Promise.all(shells.map((shell) => inspect(shell, "process start", command)));
