import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, isAbsolute, join, resolve } from "node:path";

/** Local discovery, credential references, and operation deadlines. */
export interface Config {
    registryDirectory: string;
    tokens: Record<string, string>;
    connectTimeoutMs: number;
    requestTimeoutMs: number;
}

/** Load local configuration; an absent default file uses safe defaults. */
export async function loadConfig(explicitPath?: string): Promise<Config> {
    const path = explicitPath ?? join(homedir(), ".minecraft-driver-mcp", "gateway.json");
    let value: Record<string, unknown> = {};
    try {
        value = JSON.parse(await readFile(path, "utf8"));
    } catch (error) {
        if (explicitPath !== undefined || (error as NodeJS.ErrnoException).code !== "ENOENT")
            throw new Error("Cannot read gateway config; check its JSON and permissions.");
    }
    if (!value || typeof value !== "object" || Array.isArray(value))
        throw new Error("Gateway config must be an object.");
    const registry = value.registryDirectory ?? join(homedir(), ".minecraft-driver-mcp", "instances");
    if (typeof registry !== "string" || !registry) throw new Error("registryDirectory must be a path.");
    const tokens = value.tokens ?? {};
    if (
        !tokens ||
        typeof tokens !== "object" ||
        Array.isArray(tokens) ||
        Object.values(tokens).some((name) => typeof name !== "string" || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(name))
    ) {
        throw new Error("tokens must map instance names to environment variable names.");
    }
    return {
        registryDirectory: isAbsolute(registry) ? registry : resolve(dirname(path), registry),
        tokens: tokens as Record<string, string>,
        connectTimeoutMs: positiveTimeout(value.connectTimeoutMs, 5000),
        requestTimeoutMs: positiveTimeout(value.requestTimeoutMs, 70000),
    };
}

function positiveTimeout(value: unknown, fallback: number): number {
    if (value === undefined) return fallback;
    if (!Number.isSafeInteger(value) || (value as number) < 1 || (value as number) > 2147483647) {
        throw new Error("Timeouts must be positive integer milliseconds below 2147483648.");
    }
    return value as number;
}
