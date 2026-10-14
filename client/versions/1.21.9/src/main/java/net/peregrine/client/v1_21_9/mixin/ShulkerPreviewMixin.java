package net.peregrine.client.v1_21_9.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.peregrine.client.v1_21_9.Previews;

/** Shulker preview: hovering a shulker box shows its contents instead of the tooltip. */
@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerPreviewMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void peregrine$preview(GuiGraphics graphics, int mx, int my, CallbackInfo ci) {
        Slot slot = this.hoveredSlot;
        if (slot != null && slot.hasItem() && Previews.shulker(graphics, slot.getItem(), mx, my)) {
            ci.cancel();
        }
    }
}
