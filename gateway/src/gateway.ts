import { setTimeout as delay } from "node:timers/promises";
import type { CallToolResult, Tool } from "@modelcontextprotocol/sdk/types.js";
import type { Config } from "./config.js";
import { listInstances, selectInstance } from "./registry.js";
import { ReadinessError, Upstream } from "./upstream.js";

const annotations = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };
const selectors = {
    instance_name: {
        type: "string",
        description: "Stable configured instance name. Duplicate names require instance_pid.",
    },
    instance_pid: { type: "integer", minimum: 1, description: "Exact process ID; never rerouted after restart." },
};
const localTools: Tool[] = [
    {
        name: "mc_list_instances",
        description:
            "List live local Minecraft Driver clients without needing a running game. Optionally check authenticated endpoint readiness.",
        inputSchema: {
            type: "object",
            properties: { check_ready: { type: "boolean", default: false } },
            additionalProperties: false,
        },
        annotations,
    },
    {
        name: "mc_wait_for_instance",
        description:
            "Wait for a matching local client, by default until its authenticated endpoint accepts this gateway release. Does not require a loaded world.",
        inputSchema: {
            type: "object",
            properties: {
                ...selectors,
                ready: { type: "boolean", default: true },
                timeout_ms: { type: "integer", minimum: 0, maximum: 60000, default: 10000 },
            },
            additionalProperties: false,
        },
        annotations,
    },
];

/** Local discovery tools and explicit routing for the bundled mod tool catalogue. */
export class Gateway {
    readonly tools: Tool[];
    private readonly upstream: Upstream;

    constructor(
        private readonly config: Config,
        catalogue: { version: string; tools: Tool[] },
    ) {
        this.upstream = new Upstream(config, catalogue.version);
        this.tools = [
            ...localTools,
            ...catalogue.tools
                .filter((tool) => !localTools.some((local) => local.name === tool.name))
                .map((tool) => ({
                    ...tool,
                    inputSchema: { ...tool.inputSchema, properties: { ...tool.inputSchema.properties, ...selectors } },
                })),
        ];
    }

    async call(name: string, args: Record<string, unknown>, signal: AbortSignal): Promise<CallToolResult> {
        try {
            signal.throwIfAborted();
            if (!this.tools.some((tool) => tool.name === name)) throw new Error("Unknown tool. Call tools/list.");
            if (name === "mc_list_instances") return await this.list(args, signal);
            const selector = this.selector(args);
            if (name === "mc_wait_for_instance") return await this.wait(args, selector, signal);
            const instances = await listInstances(this.config.registryDirectory);
            const instance = selectInstance(instances, selector.name, selector.pid);
            const { instance_name, instance_pid, ...forwarded } = args;
            return (await this.upstream.use(instance, instances, signal, { name, arguments: forwarded }))!;
        } catch (error) {
            return {
                isError: true,
                content: [
                    {
                        type: "text",
                        text: error instanceof Error ? error.message : "Request failed. Call mc_list_instances.",
                    },
                ],
            };
        }
    }

    private selector(args: Record<string, unknown>): { name?: string; pid?: number } {
        if (args.instance_name !== undefined && (typeof args.instance_name !== "string" || !args.instance_name)) {
            throw new Error("instance_name must be a nonempty string.");
        }
        if (
            args.instance_pid !== undefined &&
            (!Number.isSafeInteger(args.instance_pid) || (args.instance_pid as number) < 1)
        ) {
            throw new Error("instance_pid must be a positive integer.");
        }
        return { name: args.instance_name as string | undefined, pid: args.instance_pid as number | undefined };
    }

    private async list(args: Record<string, unknown>, signal: AbortSignal): Promise<CallToolResult> {
        if (args.check_ready !== undefined && typeof args.check_ready !== "boolean")
            throw new Error("check_ready must be boolean.");
        const instances = await listInstances(this.config.registryDirectory);
        const result = await Promise.all(
            instances.map(async (instance) => {
                const listed = { ...instance, selector: { instance_name: instance.name, instance_pid: instance.pid } };
                if (!args.check_ready) return listed;
                try {
                    await this.upstream.use(instance, instances, signal);
                    return { ...listed, ready: true, status: "ready" };
                } catch (error) {
                    signal.throwIfAborted();
                    return {
                        ...listed,
                        ready: false,
                        status: error instanceof ReadinessError ? error.status : "connection_failed",
                        reason: (error as Error).message,
                    };
                }
            }),
        );
        return json({ instances: result });
    }

    private async wait(
        args: Record<string, unknown>,
        selector: { name?: string; pid?: number },
        signal: AbortSignal,
    ): Promise<CallToolResult> {
        const timeout = args.timeout_ms ?? 10000;
        if (!Number.isSafeInteger(timeout) || (timeout as number) < 0 || (timeout as number) > 60000) {
            throw new Error("timeout_ms must be an integer between 0 and 60000.");
        }
        if (args.ready !== undefined && typeof args.ready !== "boolean") throw new Error("ready must be boolean.");
        const deadline = Date.now() + (timeout as number);
        const bounded = AbortSignal.any([signal, AbortSignal.timeout(Math.max(1, timeout as number))]);
        let reason = "No matching live instance.";
        do {
            signal.throwIfAborted();
            const instances = await listInstances(this.config.registryDirectory);
            try {
                const instance = selectInstance(instances, selector.name, selector.pid);
                if (args.ready !== false) await this.upstream.use(instance, instances, bounded);
                signal.throwIfAborted();
                if (args.ready !== false) bounded.throwIfAborted();
                return json({
                    instance,
                    selector: { instance_name: instance.name, instance_pid: instance.pid },
                    ready: args.ready !== false,
                });
            } catch (error) {
                signal.throwIfAborted();
                reason = (error as Error).message;
                if (reason.startsWith("Multiple live instances")) throw error;
            }
            if (Date.now() >= deadline) break;
            await delay(Math.min(200, deadline - Date.now()), undefined, { signal });
        } while (Date.now() <= deadline);
        throw new Error(`Timed out waiting for an instance. ${reason} Call mc_list_instances to inspect clients.`);
    }
}

function json(value: unknown): CallToolResult {
    return { content: [{ type: "text", text: JSON.stringify(value) }] };
}
