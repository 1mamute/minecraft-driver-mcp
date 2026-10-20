import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import { processStartTimes } from "./process-identity.js";

/** Public registry metadata. Authentication secrets are never registry data. */
export interface Instance {
    name: string;
    pid: number;
    url: string;
    port: number;
    minecraftVersion: string;
    gameDirectory: string;
    authRequired: boolean;
    /** Optional whole Unix seconds; absent or null in older registrations. */
    processStartEpochSeconds?: number | null;
}

export function isAlive(pid: number): boolean {
    try {
        process.kill(pid, 0);
        return true;
    } catch (error) {
        return (error as NodeJS.ErrnoException).code !== "ESRCH";
    }
}

export function isEndpoint(instance: Instance): boolean {
    if (!/^http:\/\/127\.0\.0\.1:[1-9][0-9]*\/mcp$/.test(instance.url)) return false;
    try {
        const url = new URL(instance.url);
        return Number(url.port) === instance.port && instance.port <= 65535;
    } catch {
        return false;
    }
}

/** Read valid live records, retaining unsafe endpoints so selection cannot silently change. */
export async function listInstances(
    directory: string,
    signal?: AbortSignal,
    inspectStarts = processStartTimes,
): Promise<Instance[]> {
    signal?.throwIfAborted();
    let files: string[];
    try {
        files = await readdir(directory);
    } catch {
        return [];
    }
    const instances = await Promise.all(
        files
            .filter((file) => /^[0-9]+\.json$/.test(file))
            .map(async (file) => {
                try {
                    const value = JSON.parse(await readFile(join(directory, file), "utf8"));
                    if (
                        !value ||
                        typeof value.name !== "string" ||
                        !value.name.trim() ||
                        !Number.isSafeInteger(value.pid) ||
                        value.pid < 1
                    )
                        return;
                    if (String(value.pid) + ".json" !== file || !isAlive(value.pid)) return;
                    if (typeof value.url !== "string" || !Number.isSafeInteger(value.port)) return;
                    if (typeof value.minecraftVersion !== "string" || typeof value.gameDirectory !== "string") return;
                    if (value.authRequired !== undefined && typeof value.authRequired !== "boolean") return;
                    if (
                        value.processStartEpochSeconds != null &&
                        (!Number.isSafeInteger(value.processStartEpochSeconds) || value.processStartEpochSeconds < 1)
                    )
                        return;
                    const instance: Instance = {
                        name: value.name,
                        pid: value.pid,
                        url: value.url,
                        port: value.port,
                        minecraftVersion: value.minecraftVersion,
                        gameDirectory: value.gameDirectory,
                        authRequired: value.authRequired ?? false,
                        ...(value.processStartEpochSeconds == null
                            ? {}
                            : { processStartEpochSeconds: value.processStartEpochSeconds }),
                    };
                    return instance;
                } catch {
                    return;
                }
            }),
    );
    const live = instances.filter((instance): instance is Instance => instance !== undefined);
    const starts = await inspectStarts(
        live.filter((instance) => instance.processStartEpochSeconds != null).map((instance) => instance.pid),
        signal,
    );
    signal?.throwIfAborted();
    return live
        .filter((instance) => {
            const actual = starts.get(instance.pid);
            return (
                isAlive(instance.pid) &&
                (instance.processStartEpochSeconds == null ||
                    actual === undefined ||
                    actual === instance.processStartEpochSeconds)
            );
        })
        .sort((a, b) => a.port - b.port);
}

export function selectInstance(instances: Instance[], name?: string, pid?: number): Instance {
    const matches = instances.filter(
        (instance) => (name === undefined || instance.name === name) && (pid === undefined || instance.pid === pid),
    );
    if (matches.length === 0)
        throw new Error("No matching live instance. Call mc_list_instances or mc_wait_for_instance.");
    if (matches.length !== 1)
        throw new Error("Multiple live instances match. Call mc_list_instances and specify instance_pid.");
    return matches[0];
}
