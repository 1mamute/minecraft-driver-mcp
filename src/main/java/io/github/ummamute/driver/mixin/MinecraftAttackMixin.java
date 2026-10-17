package io.github.ummamute.driver.mixin;

import io.github.ummamute.driver.client.ClientAttack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses vanilla's attack press and releases owned input before screens or disconnects suspend gameplay. */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @ModifyArg(
        method = "handleKeybinds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"),
        index = 0
    )
    private boolean continueOwnedAttack(boolean leftClick) {
        return ClientAttack.continuationInput(leftClick);
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void releaseForScreen(Screen screen, CallbackInfo ci) {
        if (screen != null) ClientAttack.onScreenOpened();
    }

    @Inject(method = "setOverlay", at = @At("HEAD"))
    private void releaseForOverlay(Overlay overlay, CallbackInfo ci) {
        if (overlay != null) ClientAttack.onScreenOpened();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void releaseForDisconnect(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
        ClientAttack.onDisconnect();
    }
}
