package io.github.ummamute.driver.mixin;

import io.github.ummamute.driver.client.WidgetRenderCapture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes parent render transforms without assuming they also transform mouse input. */
@Mixin(AbstractWidget.class)
public class AbstractWidgetMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void recordBounds(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        WidgetRenderCapture.record((AbstractWidget) (Object) this, graphics);
    }
}
