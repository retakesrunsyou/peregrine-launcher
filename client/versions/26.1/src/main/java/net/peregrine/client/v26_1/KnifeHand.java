package net.peregrine.client.v26_1;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.knife.KnifeView;
import net.peregrine.client.core.knife.Out;

/**
 * Knives in first person: when you hold a sword and Knives is on, Peregrine draws your arm and
 * knife itself (from {@link KnifeView}) instead of Minecraft's hand. If drawing ever fails it
 * turns itself off for the session and Minecraft's own hand comes back.
 */
public final class KnifeHand {

    private static final Out OUT = new Out();
    private static final KnifeView.Frame FRAME = new KnifeView.Frame();
    private static final Map<String, RenderType> LIT = new HashMap<String, RenderType>();
    private static final Map<String, RenderType> ADD = new HashMap<String, RenderType>();
    private static boolean broken;

    private KnifeHand() {
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_sword");
    }

    static boolean isAxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_axe");
    }

    /** Draws the knife hand; true if it did (then Minecraft's hand is skipped). */
    public static boolean draw(AbstractClientPlayer player, float partial, InteractionHand hand, float swing, ItemStack stack,
                               PoseStack pose, SubmitNodeCollector collector, int light) {
        KnifeView kv = KnifeView.INSTANCE;
        if (broken || hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        if (!kv.wants(isSword(stack))) {
            kv.hidden();
            return false;
        }
        try {
            frame(player, partial, swing, light);
            kv.build(FRAME, OUT);
            submit(pose, collector, light);
            return true;
        } catch (Throwable e) {
            broken = true;
            kv.hidden();
            System.err.println("[Peregrine] knives turned off: " + e);
            return false;
        }
    }

    private static void frame(AbstractClientPlayer player, float partial, float swing, int light) {
        Minecraft mc = Minecraft.getInstance();
        KnifeView.Frame f = FRAME;
        f.swing = swing;
        f.critReady = player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && !player.isSprinting()
                && player.getAttackStrengthScale(0.5f) > 0.9f
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
        f.walk = player.moveDist * 0.6f;
        Vec3 dm = player.getDeltaMovement();
        float speed = (float) Math.sqrt(dm.x * dm.x + dm.z * dm.z);
        f.moving = player.onGround() ? Math.min(1.3f, speed / 0.2f) : 0f;
        f.leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        try {
            f.slim = player.getSkin().model() == PlayerModelType.SLIM;
            f.skinTexture = player.getSkin().body().texturePath().toString();
        } catch (Throwable e) {
            f.skinTexture = null;
        }
        // how bright the hand is: the light where you stand, sunlight fading at night
        int block = (light >> 4) & 15, sky = (light >> 20) & 15;
        float day = 1f;
        if (mc.level != null) {
            long t = mc.level.getOverworldClockTime() % 24000L;
            double sun = Math.cos((t / 24000.0 - 0.25) * 2 * Math.PI);
            day = (float) Math.max(0.15, Math.min(1, sun * 2.5 + 0.5));
        }
        f.light = Math.max(block / 15f, sky / 15f * day);
        f.pitch = (float) Math.toRadians(-player.getXRot(partial));
    }

    private static void submit(PoseStack pose, SubmitNodeCollector collector, int light) {
        for (Out.Batch b : OUT.batches) {
            if (b.count == 0) {
                continue;
            }
            final boolean add = b.layer == Out.ADD;
            RenderType rt = type(b.texture, add);
            final int start = b.start, end = b.start + b.count;
            collector.submitCustomGeometry(pose, rt, (p, vc) -> {
                float[] v = OUT.v;
                int[] c = OUT.c;
                for (int i = start; i < end; i++) {
                    int o = i * 8;
                    int argb = c[i];
                    if (add) {   // added as light: the strength goes into the colour
                        float a = (argb >>> 24) / 255f;
                        argb = 0xFF000000 | Math.round(((argb >> 16) & 255) * a) << 16
                                | Math.round(((argb >> 8) & 255) * a) << 8 | Math.round((argb & 255) * a);
                    }
                    vc.addVertex(p, v[o], v[o + 1], v[o + 2])
                            .setColor(argb)
                            .setUv(v[o + 6], v[o + 7])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(p, v[o + 3], v[o + 4], v[o + 5]);
                }
            });
        }
    }

    static RenderType type(String texture, boolean add) {
        Map<String, RenderType> m = add ? ADD : LIT;
        RenderType t = m.get(texture);
        if (t == null) {
            Identifier id = Identifier.parse(texture);
            t = add ? RenderTypes.energySwirl(id, 0f, 0f) : RenderTypes.entityTranslucent(id);   // energy swirl: added as light, unlit
            m.put(texture, t);
        }
        return t;
    }
}
