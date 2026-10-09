package io.github.ummamute.driver.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes the protected hover flag of a widget. Mixin remaps the field name, so this works outside the development
 * environment, where reflection by the official name does not.
 */
@Mixin(AbstractWidget.class)
public interface AbstractWidgetAccessor {
    @Accessor("isHovered")
    void driver$setHovered(boolean hovered);
}
