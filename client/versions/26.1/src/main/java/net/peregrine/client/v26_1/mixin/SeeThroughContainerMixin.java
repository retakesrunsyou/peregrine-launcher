package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.peregrine.client.core.InventoryFx;
import net.peregrine.client.v26_1.InvFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See-through chests, barrels, shulker boxes, dispensers and hoppers. */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class, DispenserScreen.class, HopperScreen.class})
public abstract class SeeThroughContainerMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$glass(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        if (!InventoryFx.seeThrough) {
            return;
        }
        Screen screen = (Screen) (Object) this;
        g.fill(0, 0, screen.width, screen.height, 0x90101010);
        ContainerScreenAccessor a = (ContainerScreenAccessor) this;
        InvFx.panel(g, ((AbstractContainerScreen<?>) (Object) this).getMenu(), a.peregrine$left(), a.peregrine$top(),
                a.peregrine$width(), a.peregrine$height());
        ci.cancel();
    }
}
