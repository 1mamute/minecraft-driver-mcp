import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execute = promisify(execFile);
const inspectionTimeoutMs = 5000;

/** Best-effort OS start times, in whole Unix seconds like Java's Instant.epochSecond. */
export async function processStartTimes(pids: number[], signal?: AbortSignal): Promise<Map<number, number>> {
    const starts = new Map<number, number>();
    if (!pids.length) return starts;
    if (pids.some((pid) => !Number.isSafeInteger(pid) || pid < 1)) return starts;
    const options = { timeout: inspectionTimeoutMs, maxBuffer: 1024 * 1024, windowsHide: true, signal };
    try {
        if (process.platform === "win32") {
            const command =
                `Get-Process -Id ${pids.join(",")} -ErrorAction SilentlyContinue | ForEach-Object { ` +
                'try { "{0} {1}" -f $_.Id, ([DateTimeOffset]$_.StartTime.ToUniversalTime()).ToUnixTimeSeconds() } catch {} }';
            const { stdout } = await execute(
                "powershell.exe",
                ["-NoProfile", "-NonInteractive", "-Command", command],
                options,
            );
            for (const line of stdout.trim().split(/\r?\n/)) {
                const match = /^(\d+)\s+(\d+)$/.exec(line.trim());
                if (!match) continue;
                const seconds = Number(match[2]);
                if (Number.isSafeInteger(seconds) && seconds > 0) starts.set(Number(match[1]), seconds);
            }
        } else if (process.platform === "linux" || process.platform === "darwin") {
            const { stdout } = await execute("ps", ["-p", pids.join(","), "-o", "pid=", "-o", "lstart="], {
                ...options,
                env: { ...process.env, LC_ALL: "C", TZ: "UTC0" },
            });
            for (const line of stdout.trim().split(/\r?\n/)) {
                const match =
                    /^\s*(\d+)\s+([A-Z][a-z]{2}\s+[A-Z][a-z]{2}\s+\d{1,2}\s+\d{2}:\d{2}:\d{2}\s+\d{4})\s*$/.exec(line);
                if (!match) continue;
                const seconds = Date.parse(`${match[2]} UTC`) / 1000;
                if (Number.isSafeInteger(seconds) && seconds > 0) starts.set(Number(match[1]), seconds);
            }
        }
    } catch {
        signal?.throwIfAborted();
        // Access restrictions, missing OS tools and timeouts cannot establish a stale process.
    }
    return starts;
}
