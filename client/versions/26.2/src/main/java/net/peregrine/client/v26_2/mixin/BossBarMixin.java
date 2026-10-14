package net.peregrine.client.v26_2.mixin;

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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;

/** Titles & boss bars: boss bars smaller (scaled from the top middle) or hidden. */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarMixin {

    @Unique
    private boolean peregrine$scaled;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void peregrine$in(CallbackInfo ci, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        if (Hooks.bossHidden) {
            ci.cancel();
            return;
        }
        peregrine$scaled = Hooks.bossScale != 1f;
        if (peregrine$scaled) {
            float cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f, s = Hooks.bossScale;
            graphics.pose().pushMatrix(); graphics.pose().translate(cx, 0f + 0f); graphics.pose().scale(s, s); graphics.pose().translate(-cx, -0f);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void peregrine$out(CallbackInfo ci, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        if (peregrine$scaled) {
            peregrine$scaled = false;
            graphics.pose().popMatrix();
        }
    }
}
