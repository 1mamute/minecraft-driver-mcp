package io.github.ummamute.driver.mixin;

import io.github.ummamute.driver.client.ScreenText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the start and end of one screen render pass, which is the span {@link ScreenText} records. */
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void beginFrame(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenText.begin((Screen) (Object) this);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void endFrame(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenText.end();
    }
}
