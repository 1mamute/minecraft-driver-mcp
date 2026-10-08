package io.github.ummamute.driver.mixin;

import io.github.ummamute.driver.client.ScreenText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reports each line of a tooltip as it is drawn. */
@Mixin(ClientTextTooltip.class)
public class ClientTextTooltipMixin {
    @Shadow
    @Final
    private FormattedCharSequence text;

    @Inject(method = "renderText", at = @At("HEAD"))
    private void recordLine(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer, CallbackInfo ci) {
        ScreenText.tooltipLine(text, x, y);
    }
}
