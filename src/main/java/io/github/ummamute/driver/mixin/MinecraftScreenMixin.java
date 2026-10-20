package io.github.ummamute.driver.mixin;

import io.github.ummamute.driver.client.ScreenText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Returning to a previous screen instance must not revive an earlier text capture. */
@Mixin(Minecraft.class)
public class MinecraftScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void invalidateTextCapture(Screen screen, CallbackInfo ci) {
        ScreenText.invalidate();
    }
}
