package io.github.ummamute.driver.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sends a press through the vanilla attack method, including other mods' injections. */
@Mixin(Minecraft.class)
public interface MinecraftAttackAccessor {
    @Invoker("startAttack")
    boolean driver$startAttack();
}
