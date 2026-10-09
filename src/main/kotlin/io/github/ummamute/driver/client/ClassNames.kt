package io.github.ummamute.driver.client

import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CraftingScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen

/**
 * Stable names for vanilla classes. Reflection (`javaClass.name`) returns the obfuscated name outside the development
 * environment, so a class literal, which the build remaps, is paired with its official simple name. Classes that are
 * not listed, such as screens from other mods, keep their runtime class name.
 */
object ClassNames {
    private val screens: Map<Class<*>, String> = mapOf(
        TitleScreen::class.java to "TitleScreen",
        PauseScreen::class.java to "PauseScreen",
        OptionsScreen::class.java to "OptionsScreen",
        ChatScreen::class.java to "ChatScreen",
        ConnectScreen::class.java to "ConnectScreen",
        DisconnectedScreen::class.java to "DisconnectedScreen",
        DeathScreen::class.java to "DeathScreen",
        SelectWorldScreen::class.java to "SelectWorldScreen",
        JoinMultiplayerScreen::class.java to "JoinMultiplayerScreen",
        InventoryScreen::class.java to "InventoryScreen",
        CreativeModeInventoryScreen::class.java to "CreativeModeInventoryScreen",
        CraftingScreen::class.java to "CraftingScreen",
        ContainerScreen::class.java to "ContainerScreen",
    )

    /** The official simple name of a listed vanilla class, or the runtime class name. */
    fun of(value: Any): String = screens[value.javaClass] ?: value.javaClass.name
}
