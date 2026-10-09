package io.github.ummamute.driver

import io.github.ummamute.driver.client.ClientInput
import io.github.ummamute.driver.client.LogCapture
import io.github.ummamute.driver.client.MessageLog
import io.github.ummamute.driver.server.DriverInstance
import io.github.ummamute.driver.server.EndpointBinder
import io.github.ummamute.driver.server.InstanceRegistry
import io.github.ummamute.driver.server.McpEndpoint
import io.github.ummamute.driver.server.TokenAuthenticator
import io.github.ummamute.driver.tools.InstanceTools
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Starts the MCP server when the client starts and stops it when the client stops.
 *
 * System properties, so several clients can run at once:
 * - `driver.port`: fixed port. Default: the first free port from 25890.
 * - `driver.host`: bind address. Default `127.0.0.1`; keep it on localhost.
 * - `driver.name`: instance name shown to the agent. Default: the player name.
 * - `driver.token`: when set, requests need `Authorization: Bearer <token>`. Default: no authentication.
 * - `driver.registry`: directory of running instances. Default `~/.minecraft-driver-mcp/instances`.
 */
object DriverBootstrap {
    private const val MOD_ID = "minecraft-driver-mcp"
    private val logger = LoggerFactory.getLogger(DriverBootstrap::class.java)

    /** Starts the driver; a failure is logged and leaves the driver off instead of crashing the game. */
    fun start() {
        try {
            launch()
        } catch (e: Exception) {
            logger.error("Minecraft Driver MCP could not start and is disabled. The game continues without it", e)
        }
    }

    private fun launch() {
        MessageLog.register()
        ClientInput.register()
        LogCapture.install()
        val host = System.getProperty("driver.host", "127.0.0.1")
        val authenticator = TokenAuthenticator(System.getProperty("driver.token"))
        val name = System.getProperty("driver.name") ?: Minecraft.getInstance().user.name
        val extensions = ExtensionLoader.load()
        val bound = EndpointBinder.bind(Integer.getInteger("driver.port"), host) { port ->
            McpEndpoint(host, port, modVersion(), name, extensions, authenticator).also { it.start() }
        }
        val endpoint = bound.endpoint
        val instance = describeInstance(name, host, bound.port, authenticator)
        val registry = InstanceRegistry(registryDirectory())
        InstanceTools.registry = registry
        registry.register(instance)
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            endpoint.stop()
            registry.unregister(instance.pid)
            LogCapture.uninstall()
        }
        val authentication = if (authenticator.isRequired) "required" else "off"
        logger.info("Minecraft Driver MCP \"{}\" listening on {} (authentication {})", instance.name, instance.url, authentication)
    }

    private fun describeInstance(name: String, host: String, port: Int, authenticator: TokenAuthenticator) = DriverInstance(
        name = name,
        pid = ProcessHandle.current().pid(),
        url = "http://$host:$port${McpEndpoint.PATH}",
        port = port,
        minecraftVersion = FabricLoader.getInstance().getModContainer("minecraft").get().metadata.version.friendlyString,
        gameDirectory = FabricLoader.getInstance().gameDir.toAbsolutePath().toString(),
        authRequired = authenticator.isRequired,
    )

    private fun modVersion(): String =
        FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.version.friendlyString }.orElse("unknown")

    private fun registryDirectory(): Path =
        System.getProperty("driver.registry")?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".minecraft-driver-mcp", "instances")
}
