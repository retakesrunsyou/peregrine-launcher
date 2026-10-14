package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.peregrine.client.core.InventoryFx;
import net.peregrine.client.v1_21_2.InvFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See-through inventory: dark glass and grey slots instead of the texture; you stay in the frame. */
@Mixin(InventoryScreen.class)
public abstract class SeeThroughInventoryMixin {

    @Shadow private float xMouse;
    @Shadow private float yMouse;

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$glass(GuiGraphics g, float delta, int mx, int my, CallbackInfo ci) {
        if (!InventoryFx.seeThrough) {
            return;
        }
        ContainerScreenAccessor a = (ContainerScreenAccessor) this;
        int x = a.peregrine$left(), y = a.peregrine$top();
        InvFx.panel(g, ((AbstractContainerScreen<?>) (Object) this).getMenu(), x, y, a.peregrine$width(), a.peregrine$height());
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 26, y + 8, x + 75, y + 78, 30, 0.0625F,
                this.xMouse, this.yMouse, Minecraft.getInstance().player);
        ci.cancel();
    }
}
