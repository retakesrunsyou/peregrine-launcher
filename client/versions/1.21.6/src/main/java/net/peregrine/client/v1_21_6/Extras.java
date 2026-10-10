package net.peregrine.client.v1_21_6;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;

/** Shared helpers for the batch-5 hooks (called from mixins). */
public final class Extras {

    private Extras() {
    }

    /** Returns true when Peregrine's crosshair replaces Minecraft's this frame. */
    public static boolean crosshair(GuiGraphics graphics) {
        Peregrine pc = Peregrine.get();
        if (pc == null || !Hooks.customCrosshair) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getDebugOverlay().showDebugScreen()) {
            return false;  // keep F3's direction crosshair
        }
        boolean spectator = mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR;
        if (mc.options.getCameraType().isFirstPerson() && !spectator) {
            pc.renderCrosshair(new GuiDraw(graphics));
        }
        return true;
    }

    /** Hitbox kinds, as in Hooks.showHitbox. */
    public static int hitboxKind(net.minecraft.world.entity.Entity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) {
            return 0;
        }
        net.minecraft.world.entity.EntityType<?> type = e.getType();
        if (type == net.minecraft.world.entity.EntityType.EXPERIENCE_ORB
                || type == net.minecraft.world.entity.EntityType.EXPERIENCE_BOTTLE) {
            return 3;
        }
        if (type == net.minecraft.world.entity.EntityType.ITEM) {
            return 2;
        }
        if (e instanceof net.minecraft.world.entity.projectile.Projectile) {
            return 4;
        }
        if (e instanceof net.minecraft.world.entity.LivingEntity) {
            return 1;
        }
        return 5;
    }

    /** "(logo) | name" for your own name tag. */
    public static net.minecraft.network.chat.Component withLogo(net.minecraft.network.chat.Component name) {
        if (!Hooks.ownNameTag || !Hooks.nameLogo) {
            return name;
        }
        if (Hooks.nameTagHits < 1000) {
            Hooks.nameTagHits++;
        }
        return net.minecraft.network.chat.Component.literal(Hooks.LOGO)
                .append(net.minecraft.network.chat.Component.literal(" | ")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                .append(name);
    }
}
