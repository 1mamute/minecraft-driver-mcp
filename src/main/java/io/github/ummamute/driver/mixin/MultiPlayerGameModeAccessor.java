package io.github.ummamute.driver.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Synchronizes the held slot and the game mode's cached selection before a player drop. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Invoker("ensureHasSentCarriedItem")
    void driver$ensureHasSentCarriedItem();
}
