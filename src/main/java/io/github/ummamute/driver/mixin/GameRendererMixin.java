package io.github.ummamute.driver.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the game running when its window is not in front and {@code -Ddriver.unfocused=true}. Vanilla opens the pause
 * menu on every frame the window is inactive while the "pause on lost focus" option is on, so a screen an agent closed
 * would come back at once. The option itself is not changed, so options.txt keeps the player's choice.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z",
            opcode = Opcodes.GETFIELD
        )
    )
    private boolean pauseOnLostFocus(Options options, Operation<Boolean> original) {
        if (Boolean.getBoolean("driver.unfocused")) {
            return false;
        }
        return original.call(options);
    }
}
