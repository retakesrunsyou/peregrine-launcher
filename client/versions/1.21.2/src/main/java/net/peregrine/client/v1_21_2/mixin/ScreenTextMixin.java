package net.peregrine.client.v1_21_2.mixin;

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

/** Titles & boss bars: size, height or hide the big titles and the action bar text. */
@Mixin(targets = {"net.minecraft.client.gui.Gui"})
public abstract class ScreenTextMixin {

    @Unique
    private boolean peregrine$title;
    @Unique
    private boolean peregrine$action;

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    private void peregrine$titleIn(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        if (Hooks.titleHidden) {
            ci.cancel();
            return;
        }
        peregrine$title = Hooks.titleScale != 1f || Hooks.titleOffset != 0f;
        if (peregrine$title) {
            if (Hooks.titleHits < 1000) {
                Hooks.titleHits++;
            }
            float cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f, cy = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2f, s = Hooks.titleScale, dy = Hooks.titleOffset;
            graphics.pose().pushPose(); graphics.pose().translate(cx, cy + dy, 0f); graphics.pose().scale(s, s, 1f); graphics.pose().translate(-cx, -cy, 0f);
        }
    }

    @Inject(method = "renderTitle", at = @At("RETURN"))
    private void peregrine$titleOut(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        if (peregrine$title) {
            peregrine$title = false;
            graphics.pose().popPose();
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void peregrine$actionIn(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        if (Hooks.actionHidden) {
            ci.cancel();
            return;
        }
        peregrine$action = Hooks.actionScale != 1f;
        if (peregrine$action) {
            float cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f, cy = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 68f, s = Hooks.actionScale;
            graphics.pose().pushPose(); graphics.pose().translate(cx, cy + 0f, 0f); graphics.pose().scale(s, s, 1f); graphics.pose().translate(-cx, -cy, 0f);
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void peregrine$actionOut(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        if (peregrine$action) {
            peregrine$action = false;
            graphics.pose().popPose();
        }
    }
}
