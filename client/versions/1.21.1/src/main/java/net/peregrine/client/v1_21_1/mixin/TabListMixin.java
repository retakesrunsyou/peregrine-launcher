package net.peregrine.client.v1_21_1.mixin;

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
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.peregrine.client.core.modules.TabList;

/** Compact tab list: smaller, ping as numbers, and players' tiers (MC Tiers) after their names. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {

    @Unique
    private boolean peregrine$scaled;

    @Inject(method = "render", at = @At("HEAD"))
    private void peregrine$in(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        peregrine$scaled = Hooks.tabScale != 1f;
        if (peregrine$scaled) {
            if (Hooks.tabHits < 1000) {
                Hooks.tabHits++;
            }
            float cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f, s = Hooks.tabScale;
            graphics.pose().pushPose(); graphics.pose().translate(cx, 10f + 0f, 0f); graphics.pose().scale(s, s, 1f); graphics.pose().translate(-cx, -10f, 0f);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void peregrine$out(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        if (peregrine$scaled) {
            peregrine$scaled = false;
            graphics.pose().popPose();
        }
    }

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void peregrine$ping(GuiGraphics graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        if (!Hooks.tabPingNumbers) {
            return;
        }
        ci.cancel();
        int ms = info.getLatency();
        String t = ms < 0 ? "?" : ms > 9999 ? "9k+" : ms > 999 ? (ms / 100) / 10f + "k" : String.valueOf(ms);
        var font = Minecraft.getInstance().font;
        float s = 0.7f;
        int tw = font.width(t);
        graphics.pose().pushPose();
        graphics.pose().translate(x + width - 1 - tw * s, y + 1.5f, 0f);
        graphics.pose().scale(s, s, 1f);
        graphics.drawString(font, t, 0, 0, TabList.pingColor(ms), true);
        graphics.pose().popPose();
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void peregrine$tier(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!Hooks.tiersInTab() || info.getProfile() == null) {
            return;
        }
        String uuid = String.valueOf(info.getProfile().getId());
        String tier = Hooks.tierText(uuid);
        if (tier != null && cir.getReturnValue() != null) {
            int c = Hooks.tierColor(uuid);
            cir.setReturnValue(cir.getReturnValue().copy().append(Component.literal(" " + tier)
                    .withStyle(st -> st.withColor(c).withBold(true))));
        }
    }
}
