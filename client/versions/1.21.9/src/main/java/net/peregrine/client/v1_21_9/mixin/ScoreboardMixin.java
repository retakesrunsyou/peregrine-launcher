package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoreboard size: scales the server's sidebar around its right-middle edge (where it sits). */
@Mixin(Gui.class)
public abstract class ScoreboardMixin {

    @Unique
    private boolean peregrine$scaled;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void peregrine$scale(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        float s = Hooks.scoreboardScale;
        peregrine$scaled = s != 1.0F;
        if (peregrine$scaled) {
            if (Hooks.scoreboardHits < 1000) {
                Hooks.scoreboardHits++;
            }
            float ax = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            float ay = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2.0F;
            graphics.pose().pushMatrix();
            graphics.pose().translate(ax, ay);
            graphics.pose().scale(s, s);
            graphics.pose().translate(-ax, -ay);
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void peregrine$unscale(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        if (peregrine$scaled) {
            peregrine$scaled = false;
            graphics.pose().popMatrix();
        }
    }
}
