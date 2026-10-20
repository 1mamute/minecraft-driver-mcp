package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientAttack
import io.github.ummamute.driver.client.ClientChat
import io.github.ummamute.driver.client.ClientDrops
import io.github.ummamute.driver.client.ClientInput
import io.github.ummamute.driver.client.ClientInventory
import io.github.ummamute.driver.client.ClientKeyboard
import io.github.ummamute.driver.client.ClientScreens
import io.github.ummamute.driver.client.ClientSlots
import io.github.ummamute.driver.client.ClientState
import io.github.ummamute.driver.client.RenderThread
import io.github.ummamute.driver.client.ScreenText
import io.github.ummamute.driver.client.SlotClickArgs
import io.github.ummamute.driver.client.SlotClickPlanner
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.arguments
import io.github.ummamute.driver.tools.ToolSupport.boolean
import io.github.ummamute.driver.tools.ToolSupport.double
import io.github.ummamute.driver.tools.ToolSupport.int
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.long
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.github.ummamute.driver.tools.ToolSupport.requireDouble
import io.github.ummamute.driver.tools.ToolSupport.requireString
import io.github.ummamute.driver.tools.ToolSupport.string
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.delay

/** Tools that act on the client the way a player would. */
internal object ActionTools {
    private const val HOLD_POLL_MS = 25L

    fun register(server: Server) {
        registerClick(server)
        registerClickText(server)
        registerClickSlot(server)
        registerCloseScreen(server)
        registerKey(server)
        registerLook(server)
        registerLookAt(server)
        registerSelectHotbarSlot(server)
        registerUse(server)
        registerAttack(server)
        registerDropItem(server)
        registerChat(server)
        registerTypeText(server)
        registerPressKey(server)
    }

    private fun registerClick(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_click"]) { request ->
            val args = arguments(request)
            val index = args.int("index")
            val label = args.string("label")
            val x = args.double("x")
            val y = args.double("y")
            val button = args.int("button") ?: 0
            val path = args.string("path")
            onRenderThread({ ClientScreens.click(index, label, x, y, button, path) }) { jsonResult(it) }
        }
    }

    private fun registerClickSlot(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_click_slot"]) { request ->
            val args = arguments(request)
            val click = SlotClickArgs(
                action = args.string("action"),
                slot = args.int("slot"),
                outside = args.boolean("outside") ?: false,
                button = args.int("button"),
                hotbar = args.int("hotbar"),
            )
            onRenderThread({ ClientSlots.click(click) }) { jsonResult(it) }
        }
    }

    private fun registerClickText(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_click_text"]) { request ->
            val args = arguments(request)
            val frameId = args.long("frame_id") ?: error("frame_id is required; call mc_read_screen_text and use its frameId")
            val text = args.requireString("text")
            val textIndex = args.int("text_index")
            val button = args.int("button") ?: 0
            onRenderThread({ ScreenText.click(frameId, text, textIndex, button) }) { jsonResult(it) }
        }
    }

    private fun registerCloseScreen(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_close_screen"]) { _ ->
            onRenderThread({ ClientScreens.close() }) { jsonResult(ClientState.snapshot()) }
        }
    }

    private fun registerKey(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_set_key"]) { request ->
            val args = arguments(request)
            onRenderThread({ ClientInput.setKey(args.requireString("name"), args.boolean("down") ?: true) }) { jsonResult(mapOf("down" to it)) }
        }
    }

    private fun registerLook(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_look"]) { request ->
            val args = arguments(request)
            val yaw = args.requireDouble("yaw")
            val pitch = args.requireDouble("pitch")
            onRenderThread({
                ClientInput.look(yaw, pitch)
                ClientState.snapshot()
            }) { jsonResult(it) }
        }
    }

    private fun registerLookAt(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_look_at"]) { request ->
            val args = arguments(request)
            val target = LookAtTarget.from(args)
            onRenderThread({
                ClientInput.lookAt(target)
                ClientState.snapshot()
            }) { jsonResult(it) }
        }
    }

    private fun registerSelectHotbarSlot(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_select_hotbar_slot"]) { request ->
            val slot = arguments(request).int("slot") ?: error("Argument \"slot\" is required; choose a hotbar index from 0 to 8")
            onRenderThread({ ClientInventory.selectHotbarSlot(slot) }) { jsonResult(it) }
        }
    }

    private fun registerUse(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_use"]) { request ->
            val holdTicks = arguments(request).int("hold_ticks") ?: 0
            val result = onRenderThread({ ClientInput.use(holdTicks) }) { jsonResult(mapOf("result" to it)) }
            if (result.isError != true) awaitUseRelease()
            result
        }
    }

    private fun registerDropItem(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_drop_item"]) { request ->
            val fullStack = arguments(request).boolean("full_stack") ?: false
            onRenderThread({ ClientDrops.drop(fullStack) }) { jsonResult(it) }
        }
    }

    private fun registerAttack(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_attack"]) { request ->
            val holdTicks = arguments(request).int("hold_ticks") ?: 0
            jsonResult(ClientAttack.attack(holdTicks))
        }
    }

    /** Polls between short render-thread reads, so the hold never blocks the game loop. */
    private suspend fun awaitUseRelease() {
        while (RenderThread.call { ClientInput.isHoldingUse }) delay(HOLD_POLL_MS)
    }

    private fun registerChat(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_send_chat"]) { request ->
            val args = arguments(request)
            onRenderThread({ ClientChat.send(args.requireString("text")) }) { jsonResult(mapOf("sent" to it)) }
        }
    }

    private fun registerTypeText(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_type_text"]) { request ->
            val text = arguments(request).requireString("text")
            onRenderThread({ ClientKeyboard.typeText(text) }) { jsonResult(it) }
        }
    }

    private fun registerPressKey(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_press_key"]) { request ->
            val args = arguments(request)
            val name = args.requireString("name")
            val times = args.int("times") ?: 1
            onRenderThread({ ClientKeyboard.pressKey(name, times) }) { jsonResult(it) }
        }
    }
}
