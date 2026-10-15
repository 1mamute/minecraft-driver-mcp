import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import type { Config } from "./config.js";
import { isEndpoint, type Instance } from "./registry.js";

/** One fresh authenticated MCP lifecycle per operation, with no tool replay. */
export class Upstream {
    constructor(
        private readonly config: Config,
        private readonly version: string,
    ) {}

    async use(
        instance: Instance,
        all: Instance[],
        signal: AbortSignal,
        call?: { name: string; arguments: Record<string, unknown> },
    ): Promise<CallToolResult | undefined> {
        if (!isEndpoint(instance))
            throw new ReadinessError("unsafe_endpoint", "Unsafe instance endpoint. Check the local instance registry.");
        const token = this.token(instance, all);
        const client = new Client({ name: "minecraft-driver-mcp-gateway", version: this.version });
        const operationSignal = AbortSignal.any([signal, AbortSignal.timeout(this.config.requestTimeoutMs)]);
        const connectionSignal = AbortSignal.any([operationSignal, AbortSignal.timeout(this.config.connectTimeoutMs)]);
        let phase = "connect";
        const transport = this.transport(instance, token, () =>
            phase === "connect" ? connectionSignal : operationSignal,
        );
        try {
            signal.throwIfAborted();
            await client.connect(transport, { timeout: this.config.connectTimeoutMs, signal: connectionSignal });
            connectionSignal.throwIfAborted();
            const release = client.getServerVersion()?.version.split("+")[0];
            if (release !== this.version)
                throw new ReadinessError(
                    "version_mismatch",
                    "Mod release does not match this gateway. Install matching mod and npm package releases.",
                );
            if (!call) return;
            phase = "tool";
            return (await client.callTool(call, undefined, {
                timeout: this.config.requestTimeoutMs,
                signal: operationSignal,
            })) as CallToolResult;
        } catch (error) {
            if (signal.aborted)
                throw new Error(
                    "Request cancelled. A forwarded action may already have run; inspect game state before repeating it.",
                );
            if (error instanceof ReadinessError) throw error;
            if (phase === "tool")
                throw new Error(
                    "Tool connection failed or timed out; outcome is unknown. Inspect game state before deciding whether to repeat the action.",
                );
            throw new ReadinessError(
                "connection_failed",
                "Endpoint is not ready. Check the client and its endpoint; call mc_list_instances with check_ready.",
            );
        } finally {
            await client.close().catch(() => {});
        }
    }

    private transport(
        instance: Instance,
        token: string | undefined,
        requestSignal: () => AbortSignal,
    ): StreamableHTTPClientTransport {
        return new StreamableHTTPClientTransport(new URL(instance.url), {
            reconnectionOptions: {
                maxRetries: 0,
                initialReconnectionDelay: 1000,
                maxReconnectionDelay: 1000,
                reconnectionDelayGrowFactor: 1,
            },
            fetch: async (url, init) => {
                // Stateless endpoints have no notification stream. Never reconnect or replay a tool POST.
                if (init?.method === "GET") return new Response(null, { status: 405 });
                if (String(url) !== instance.url) throw new Error("Endpoint changed.");
                const headers = new Headers(init?.headers);
                if (token) headers.set("Authorization", `Bearer ${token}`);
                const signals = [requestSignal()];
                if (init?.signal) signals.push(init.signal);
                const response = await fetch(url, {
                    ...init,
                    headers,
                    redirect: "manual",
                    signal: AbortSignal.any(signals),
                });
                if (response.status >= 300 && response.status < 400) {
                    await response.body?.cancel();
                    throw new Error("Redirect refused.");
                }
                if (response.status === 401 || response.status === 403) {
                    await response.body?.cancel();
                    throw new ReadinessError(
                        "authentication_failed",
                        "Endpoint rejected authentication. Check the configured token environment variable.",
                    );
                }
                return response;
            },
        });
    }

    private token(instance: Instance, all: Instance[]): string | undefined {
        const variable = Object.hasOwn(this.config.tokens, instance.name)
            ? this.config.tokens[instance.name]
            : undefined;
        if (variable && all.filter((candidate) => candidate.name === instance.name).length > 1) {
            throw new ReadinessError(
                "ambiguous_credentials",
                "Token mapping has a duplicate instance name. Give each authenticated client a unique instance name.",
            );
        }
        const token = variable ? process.env[variable] : undefined;
        if (instance.authRequired && !token)
            throw new ReadinessError(
                "authentication_missing",
                "Authentication required. Map the instance name to a token environment variable in gateway config.",
            );
        return token;
    }
}
export class ReadinessError extends Error {
    constructor(
        readonly status: string,
        message: string,
    ) {
        super(message);
    }
}
