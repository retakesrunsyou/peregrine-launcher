package net.peregrine.client.v26_1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.knife.M4;
import net.peregrine.client.core.modules.PickaxeLook;
import net.peregrine.client.core.skeleton.Skeleton;
import net.peregrine.client.core.skeleton.ToolLooks;

/**
 * The Skeleton look and pickaxe looks in 26.1: players' bodies and your first-person arm drawn
 * as bone, and your pickaxe as a star wand or peppermint axe. Anything that goes wrong turns that
 * part off for the session and Minecraft's own drawing comes back.
 */
public final class SkeletonDraw {

    private static final Identifier BONE = Identifier.parse(Skeleton.TEXTURE);
    private static final Identifier TOOLS = Identifier.parse(ToolLooks.TEXTURE);
    private static RenderType boneType, toolType, glowType;
    private static boolean broken, toolBroken;

    private SkeletonDraw() {
    }

    private static RenderType bone() {
        if (boneType == null) {
            boneType = RenderTypes.entityCutout(BONE);
        }
        return boneType;
    }

    // ------------------------------------------------------------------ players

    /** Draws a player as a skeleton instead of their model; false to let Minecraft draw it. */
    public static boolean player(PlayerModel model, AvatarRenderState s, PoseStack pose, SubmitNodeCollector collector,
                                 int light, int overlay, int tint) {
        if (broken) {
            return false;
        }
        try {
            int lod = Skeleton.lod(s.distanceToCameraSq);
            if (lod < 0) {
                return false;
            }
            model.setupAnim(s);
            final float[] p = new float[Skeleton.PARTS * 9];
            put(p, Skeleton.HEAD, model.head);
            put(p, Skeleton.BODY, model.body);
            put(p, Skeleton.RIGHT_ARM, model.rightArm);
            put(p, Skeleton.LEFT_ARM, model.leftArm);
            put(p, Skeleton.RIGHT_LEG, model.rightLeg);
            put(p, Skeleton.LEFT_LEG, model.leftLeg);
            final boolean slim = s.skin != null && s.skin.model() == PlayerModelType.SLIM;
            pose.pushPose();
            model.root().translateAndRotate(pose);
            collector.submitCustomGeometry(pose, bone(), (pp, vc) -> Skeleton.draw(p, slim, lod,
                    (x, y, z, nx, ny, nz, u, v, c) -> vc.addVertex(pp, x, y, z).setColor(mul(c, tint)).setUv(u, v)
                            .setOverlay(overlay).setLight(light).setNormal(pp, nx, ny, nz)));
            pose.popPose();
            return true;
        } catch (Throwable e) {
            broken = true;
            System.err.println("[Peregrine] skeleton look turned off: " + e);
            return false;
        }
    }

    private static void put(float[] p, int part, ModelPart m) {
        int o = part * 9;
        p[o] = m.x;
        p[o + 1] = m.y;
        p[o + 2] = m.z;
        p[o + 3] = m.xRot;
        p[o + 4] = m.yRot;
        p[o + 5] = m.zRot;
        p[o + 6] = m.xScale;
        p[o + 7] = m.yScale;
        p[o + 8] = m.zScale;
        if (!m.visible) {
            p[o + 6] = p[o + 7] = p[o + 8] = 0f;
        }
    }

    /** Your arm in first person as bone (the pose is the arm part's parent). */
    public static boolean hand(ModelPart arm, boolean right, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        if (broken) {
            return false;
        }
        try {
            int lod = Skeleton.lod(0);
            if (lod < 0) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            final boolean slim = mc.player != null && mc.player.getSkin().model() == PlayerModelType.SLIM;
            pose.pushPose();
            arm.translateAndRotate(pose);
            final M4 px = new M4().scale(1 / 16f);
            collector.submitCustomGeometry(pose, bone(), (pp, vc) -> Skeleton.arm(right, slim, lod, px,
                    (x, y, z, nx, ny, nz, u, v, c) -> vc.addVertex(pp, x, y, z).setColor(c).setUv(u, v)
                            .setOverlay(overlay).setLight(light).setNormal(pp, nx, ny, nz)));
            pose.popPose();
            return true;
        } catch (Throwable e) {
            broken = true;
            System.err.println("[Peregrine] skeleton look turned off: " + e);
            return false;
        }
    }

    // ------------------------------------------------------------------ pickaxes

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pickaxe");
    }

    /** Draws your pickaxe as its chosen look, where Minecraft was about to draw it; false to leave it. */
    public static boolean pickaxe(ItemStack stack, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector,
                                  int light, int overlay) {
        int look = PickaxeLook.current();
        if (toolBroken || look == ToolLooks.NORMAL || !isPickaxe(stack)) {
            return false;
        }
        int where;
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            where = ToolLooks.FIRST_RIGHT;
        } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            where = ToolLooks.FIRST_LEFT;
        } else if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            where = ToolLooks.THIRD_RIGHT;
        } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            where = ToolLooks.THIRD_LEFT;
        } else {
            return false;
        }
        try {
            if (toolType == null) {
                toolType = RenderTypes.entityCutout(TOOLS);
                glowType = RenderTypes.energySwirl(TOOLS, 0f, 0f);
            }
            final M4 m = ToolLooks.hold(where);
            final int lk = look;
            collector.submitCustomGeometry(pose, toolType, (pp, vc) -> ToolLooks.drawLit(lk, m,
                    (x, y, z, nx, ny, nz, u, v, c) -> vc.addVertex(pp, x, y, z).setColor(c).setUv(u, v)
                            .setOverlay(overlay).setLight(light).setNormal(pp, nx, ny, nz)));
            if (PickaxeLook.glows()) {
                final double t = System.nanoTime() / 1e9;
                collector.submitCustomGeometry(pose, glowType, (pp, vc) -> ToolLooks.drawGlow(lk, m, t,
                        (x, y, z, nx, ny, nz, u, v, c) -> vc.addVertex(pp, x, y, z).setColor(c).setUv(u, v)
                                .setOverlay(overlay).setLight(0xF000F0).setNormal(pp, nx, ny, nz)));
            }
            return true;
        } catch (Throwable e) {
            toolBroken = true;
            System.err.println("[Peregrine] pickaxe look turned off: " + e);
            return false;
        }
    }

    /** The local player's own render state? */
    public static boolean isYou(Object state) {
        Minecraft mc = Minecraft.getInstance();
        return state instanceof AvatarRenderState && mc.player != null && ((AvatarRenderState) state).id == mc.player.getId();
    }

    static int mul(int a, int b) {
        if (b == -1) {
            return a;
        }
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            r |= (((a >>> s) & 255) * ((b >>> s) & 255) / 255) << s;
        }
        return r;
    }
}
