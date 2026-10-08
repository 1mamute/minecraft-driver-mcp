package io.github.ummamute.driver

import io.github.ummamute.driver.client.MessageLog
import io.github.ummamute.driver.server.DriverInstance
import io.github.ummamute.driver.server.InstanceRegistry
import io.github.ummamute.driver.server.McpEndpoint
import io.github.ummamute.driver.server.PortSelector
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
 * - `driver.registry`: directory of running instances. Default `~/.minecraft-driver-mcp/instances`.
 */
object DriverBootstrap {
    private const val MOD_ID = "minecraft-driver-mcp"
    private val logger = LoggerFactory.getLogger(DriverBootstrap::class.java)

    fun start() {
        MessageLog.register()
        val host = System.getProperty("driver.host", "127.0.0.1")
        val port = PortSelector.choose(Integer.getInteger("driver.port"), host)
        val instance = DriverInstance(
            name = System.getProperty("driver.name") ?: Minecraft.getInstance().user.name,
            pid = ProcessHandle.current().pid(),
            url = "http://$host:$port${McpEndpoint.PATH}",
            port = port,
            minecraftVersion = FabricLoader.getInstance().getModContainer("minecraft").get().metadata.version.friendlyString,
            gameDirectory = FabricLoader.getInstance().gameDir.toAbsolutePath().toString(),
        )
        val registry = InstanceRegistry(registryDirectory())
        InstanceTools.registry = registry
        val endpoint = McpEndpoint(host, port, modVersion(), instance.name)
        endpoint.start()
        registry.register(instance)
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            endpoint.stop()
            registry.unregister(instance.pid)
        }
        logger.info("Minecraft Driver MCP \"{}\" listening on {}", instance.name, instance.url)
    }

    private fun modVersion(): String =
        FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.version.friendlyString }.orElse("unknown")

    private fun registryDirectory(): Path =
        System.getProperty("driver.registry")?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".minecraft-driver-mcp", "instances")
}
