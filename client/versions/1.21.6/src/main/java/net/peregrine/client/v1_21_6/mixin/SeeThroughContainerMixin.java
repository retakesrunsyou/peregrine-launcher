package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.peregrine.client.core.InventoryFx;
import net.peregrine.client.v1_21_6.InvFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See-through chests, barrels, shulker boxes, dispensers and hoppers. */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class, DispenserScreen.class, HopperScreen.class})
public abstract class SeeThroughContainerMixin {

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$glass(GuiGraphics g, float delta, int mx, int my, CallbackInfo ci) {
        if (!InventoryFx.seeThrough) {
            return;
        }
        ContainerScreenAccessor a = (ContainerScreenAccessor) this;
        InvFx.panel(g, ((AbstractContainerScreen<?>) (Object) this).getMenu(), a.peregrine$left(), a.peregrine$top(),
                a.peregrine$width(), a.peregrine$height());
        ci.cancel();
    }
}
