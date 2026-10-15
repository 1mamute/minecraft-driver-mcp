import { readFile } from "node:fs/promises";
import { ToolSchema } from "@modelcontextprotocol/sdk/types.js";

const catalogue = JSON.parse(await readFile(new URL("./catalogue.json", import.meta.url), "utf8"));
const pkg = JSON.parse(await readFile(new URL("./package.json", import.meta.url), "utf8"));
const properties = await readFile(new URL("../gradle.properties", import.meta.url), "utf8");
const modVersion = properties.match(/^mod_version\s*=\s*(\S+)\s*$/m)?.[1];
if (catalogue.version !== pkg.version || pkg.version !== modVersion) {
    throw new Error("Align catalogue.json and package.json versions with mod_version before building or packing.");
}
if (!Array.isArray(catalogue.tools) || !catalogue.tools.length) throw new Error("The built-in catalogue must contain tools.");
const names = new Set();
for (const definition of catalogue.tools) {
    ToolSchema.parse(definition);
    if (names.has(definition.name)) throw new Error(`Duplicate built-in tool: ${definition.name}`);
    names.add(definition.name);
    for (const hint of ["readOnlyHint", "destructiveHint", "idempotentHint", "openWorldHint"]) {
        if (typeof definition.annotations?.[hint] !== "boolean") throw new Error(`Missing ${hint} on ${definition.name}`);
    }
}
