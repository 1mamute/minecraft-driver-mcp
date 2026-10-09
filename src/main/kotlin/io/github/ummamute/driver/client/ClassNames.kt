package io.github.ummamute.driver.client

import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractSelectionList
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.FocusableTextWidget
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.ImageWidget
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.StateSwitchingButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.TabButton
import net.minecraft.client.gui.components.events.GuiEventListener
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
 * environment, so a class literal, which the build remaps, is paired with its official simple name. Screens that are
 * not listed, such as screens from other mods, keep their runtime class name. Widgets are named by the first listed
 * class they extend, so a vanilla subclass reads the same in every environment; see [widget].
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

    /** Most specific first: the first entry a widget is an instance of names it. */
    private val widgets: List<Pair<Class<*>, String>> = listOf(
        Checkbox::class.java to "Checkbox",
        CycleButton::class.java to "CycleButton",
        ImageButton::class.java to "ImageButton",
        SpriteIconButton::class.java to "SpriteIconButton",
        PlainTextButton::class.java to "PlainTextButton",
        TabButton::class.java to "TabButton",
        Button::class.java to "Button",
        AbstractButton::class.java to "AbstractButton",
        StateSwitchingButton::class.java to "StateSwitchingButton",
        AbstractSliderButton::class.java to "Slider",
        EditBox::class.java to "EditBox",
        MultiLineEditBox::class.java to "MultiLineEditBox",
        StringWidget::class.java to "StringWidget",
        MultiLineTextWidget::class.java to "MultiLineTextWidget",
        FocusableTextWidget::class.java to "FocusableTextWidget",
        ImageWidget::class.java to "ImageWidget",
        AbstractSelectionList::class.java to "SelectionList",
        AbstractWidget::class.java to "Widget",
    )

    private const val VANILLA_PACKAGE = "net.minecraft."

    /** The official simple name of a listed vanilla class, or the runtime class name. */
    fun of(value: Any): String = screens[value.javaClass] ?: value.javaClass.name

    /**
     * A name for a widget that is the same in the development environment and in a normal install. A class from another
     * mod keeps its own name. A vanilla class is named by the closest class in the widget table it extends.
     */
    fun widget(child: GuiEventListener): String {
        val runtimeName = child.javaClass.name
        val listed = widgets.firstOrNull { it.first.isInstance(child) }?.second
        return widgetName(runtimeName, listed)
    }

    /** Picks the name for [runtimeName]: a vanilla class uses [listed] (or `Component`), any other class keeps its name. */
    internal fun widgetName(runtimeName: String, listed: String?): String {
        if (!runtimeName.startsWith(VANILLA_PACKAGE)) return runtimeName
        return listed ?: "Component"
    }
}
