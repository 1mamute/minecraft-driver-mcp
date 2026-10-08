package io.github.ummamute.driver.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Creates the game window without taking keyboard focus when {@code -Ddriver.unfocused=true}, so an agent can
 * drive the client while the developer keeps working in other applications.
 */
@Mixin(value = Window.class, remap = false)
public class WindowMixin {
    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"
        )
    )
    private long createWithoutFocus(int width, int height, CharSequence title, long monitor, long share, Operation<Long> original) {
        if (Boolean.getBoolean("driver.unfocused")) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
        return original.call(width, height, title, monitor, share);
    }
}
