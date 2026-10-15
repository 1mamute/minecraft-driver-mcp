import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

/** Public registry metadata. Authentication secrets are never registry data. */
export interface Instance {
    name: string;
    pid: number;
    url: string;
    port: number;
    minecraftVersion: string;
    gameDirectory: string;
    authRequired: boolean;
}

export function isAlive(pid: number): boolean {
    try {
        process.kill(pid, 0);
        return true;
    } catch (error) {
        return (error as NodeJS.ErrnoException).code === "EPERM";
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
export async function listInstances(directory: string): Promise<Instance[]> {
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
                        !value.name ||
                        !Number.isSafeInteger(value.pid) ||
                        value.pid < 1
                    )
                        return;
                    if (String(value.pid) + ".json" !== file || !isAlive(value.pid)) return;
                    if (typeof value.url !== "string" || !Number.isSafeInteger(value.port)) return;
                    if (typeof value.minecraftVersion !== "string" || typeof value.gameDirectory !== "string") return;
                    if (value.authRequired !== undefined && typeof value.authRequired !== "boolean") return;
                    const instance: Instance = {
                        name: value.name,
                        pid: value.pid,
                        url: value.url,
                        port: value.port,
                        minecraftVersion: value.minecraftVersion,
                        gameDirectory: value.gameDirectory,
                        authRequired: value.authRequired ?? false,
                    };
                    return instance;
                } catch {
                    return;
                }
            }),
    );
    return instances.filter((instance): instance is Instance => instance !== undefined).sort((a, b) => a.port - b.port);
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
