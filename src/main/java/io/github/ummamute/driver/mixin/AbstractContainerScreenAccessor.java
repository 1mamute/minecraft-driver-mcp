package io.github.ummamute.driver.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the protected slot click of a container screen. Screens such as the creative inventory override it to
 * send their own packets, so a click must go through the screen the way a mouse click does.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Invoker("slotClicked")
    void driver$slotClicked(Slot slot, int slotId, int button, ClickType type);
}
