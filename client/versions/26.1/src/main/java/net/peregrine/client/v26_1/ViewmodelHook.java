package net.peregrine.client.v26_1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.knife.KnifeView;
import net.peregrine.client.v26_1.mixin.ItemInHandAccessor;

/** Decides, for each first-person hand, whether it's a knife, gets effects, or leaves afterimages. */
public final class ViewmodelHook {

    private static boolean inGhost;
    private static boolean broken;

    private ViewmodelHook() {
    }

    /** Before Minecraft draws a hand: true when Peregrine drew it instead (a knife). */
    public static boolean before(Object renderer, AbstractClientPlayer player, float partial, float pitch, InteractionHand hand,
                                 float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (inGhost) {
            return false;
        }
        SubmitNodeCollector real = collector instanceof FxCollector ? ((FxCollector) collector).root() : collector;
        if (KnifeHand.draw(player, partial, hand, swing, stack, pose, real, light)) {
            return true;
        }
        KnifeView kv = KnifeView.INSTANCE;
        if (broken || !kv.trails || swing <= 0.02f || hand != InteractionHand.MAIN_HAND || !swings(kv.swingFor, stack)) {
            return false;
        }
        // afterimages: the same hand drawn again a moment earlier in its swing, as fading light
        inGhost = true;
        try {
            float step = 0.07f * Math.max(0.4f, Math.min(2f, kv.trailSeconds / 0.14f));
            String skin = skin(player);
            for (int k = 1; k <= 3; k++) {
                float s = swing - k * step;
                if (s <= 0) {
                    break;
                }
                float bright = 0.45f * (1 - k / 4f);
                ((ItemInHandAccessor) renderer).peregrine$renderArmWithItem(player, partial, pitch, hand, s, stack, equip, pose,
                        new FxCollector(real, bright, skin), light);
            }
        } catch (Throwable e) {
            broken = true;
            System.err.println("[Peregrine] swing afterimages turned off: " + e);
        } finally {
            inGhost = false;
        }
        return false;
    }

    /** The collector Minecraft draws this hand into: wrapped when viewmodel effects are on. */
    public static SubmitNodeCollector collector(SubmitNodeCollector c, AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
        KnifeView kv = KnifeView.INSTANCE;
        if (c instanceof FxCollector || inGhost || kv.fx == KnifeView.FX_OFF) {
            return c;
        }
        if (hand == InteractionHand.MAIN_HAND && kv.wants(KnifeHand.isSword(stack))) {
            return c;   // the knife draws itself, with the effects
        }
        return new FxCollector(c, -1f, skin(player));
    }

    private static boolean swings(int mode, ItemStack stack) {
        switch (mode) {
            case 0:
                return true;
            case 2:
                return KnifeHand.isSword(stack) || KnifeHand.isAxe(stack);
            default:
                return KnifeHand.isSword(stack);
        }
    }

    private static String skin(AbstractClientPlayer player) {
        try {
            return player.getSkin().body().texturePath().toString();
        } catch (Throwable e) {
            return null;
        }
    }
}
