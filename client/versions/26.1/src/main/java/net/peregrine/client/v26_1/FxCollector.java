package net.peregrine.client.v26_1;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.knife.KnifeView;
import org.joml.Quaternionf;

/**
 * Viewmodel effects for ordinary items: sits between Minecraft's hand drawing and the real
 * collector, redrawing held items (and the bare arm) see-through, as a hologram, glowing, or as
 * a fading afterimage. Everything else passes straight through.
 */
public final class FxCollector implements SubmitNodeCollector {

    private final OrderedSubmitNodeCollector d;
    private final SubmitNodeCollector root;
    /** 0..1: an afterimage this bright (only light is added), or -1 for the normal effect. */
    private final float ghost;
    private final String skin;

    public FxCollector(SubmitNodeCollector root, float ghost, String skin) {
        this(root, root, ghost, skin);
    }

    private FxCollector(OrderedSubmitNodeCollector d, SubmitNodeCollector root, float ghost, String skin) {
        this.d = d;
        this.root = root;
        this.ghost = ghost;
        this.skin = skin;
    }

    /** The real collector underneath. */
    public SubmitNodeCollector root() {
        return root;
    }

    @Override
    public OrderedSubmitNodeCollector order(int i) {
        return new FxCollector(root.order(i), root, ghost, skin);
    }

    // ------------------------------------------------------------------ what gets changed

    @Override
    public void submitItem(PoseStack pose, ItemDisplayContext ctx, int light, int overlay, int outline, int[] tints,
                           List<BakedQuad> quads, ItemStackRenderState.FoilType foil) {
        KnifeView kv = KnifeView.INSTANCE;
        if (quads.isEmpty() || (ghost < 0 && kv.fx == KnifeView.FX_OFF)) {
            d.submitItem(pose, ctx, light, overlay, outline, tints, quads, foil);
            return;
        }
        // quads drawn from the same picture atlas go together
        net.minecraft.resources.Identifier atlas = quads.get(0).materialInfo().sprite().atlasLocation();
        final int fx = kv.fx;
        final int col = kv.fxColor;
        final float glow = kv.fxGlow;
        final int alpha = ghost >= 0 ? 255 : fx == KnifeView.FX_GLASS ? Math.round(255 * kv.fxOpacity)
                : fx == KnifeView.FX_HOLOGRAM ? Math.round(255 * Math.min(0.55f, kv.fxOpacity)) : 255;
        final List<BakedQuad> list = quads;
        final int[] tl = tints;
        if (ghost < 0) {
            d.submitCustomGeometry(pose, KnifeHand.type(atlas.toString(), false), (p, vc) -> {
                QuadInstance qi = new QuadInstance();
                qi.setLightCoords(light);
                qi.setOverlayCoords(overlay);
                for (BakedQuad q : list) {
                    int c = tint(q, tl);
                    if (fx == KnifeView.FX_HOLOGRAM) {
                        c = mul(c, 0xFF000000 | lighten(col));
                    }
                    qi.setColor((alpha << 24) | (c & 0xFFFFFF));
                    vc.putBakedQuad(p, q, qi);
                }
            });
        }
        if (ghost >= 0 || fx == KnifeView.FX_GLOW || fx == KnifeView.FX_HOLOGRAM) {
            float k = ghost >= 0 ? ghost : Math.min(1f, glow * (fx == KnifeView.FX_HOLOGRAM ? 0.7f : 0.5f));
            final int add = 0xFF000000 | scale(col, k);
            d.submitCustomGeometry(pose, KnifeHand.type(atlas.toString(), true), (p, vc) -> {
                QuadInstance qi = new QuadInstance();
                qi.setLightCoords(0xF000F0);
                qi.setOverlayCoords(overlay);
                qi.setColor(add);
                for (BakedQuad q : list) {
                    vc.putBakedQuad(p, q, qi);
                }
            });
        }
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite,
                                boolean sheeted, boolean foil, int tint, ModelFeatureRenderer.CrumblingOverlay crumbling, int outline) {
        KnifeView kv = KnifeView.INSTANCE;
        if (skin == null || sprite != null || (ghost < 0 && kv.fx == KnifeView.FX_OFF)) {
            d.submitModelPart(part, pose, type, light, overlay, sprite, sheeted, foil, tint, crumbling, outline);
            return;
        }
        // the bare arm (drawn from your skin)
        int fx = kv.fx;
        if (ghost < 0) {
            int alpha = fx == KnifeView.FX_GLASS ? Math.round(255 * kv.fxOpacity) : fx == KnifeView.FX_HOLOGRAM ? Math.round(255 * Math.min(0.55f, kv.fxOpacity)) : 255;
            int c = fx == KnifeView.FX_HOLOGRAM ? lighten(kv.fxColor) : 0xFFFFFF;
            d.submitModelPart(part, pose, KnifeHand.type(skin, false), light, overlay, null, sheeted, false,
                    (alpha << 24) | c, crumbling, outline);
        }
        if (ghost >= 0 || fx == KnifeView.FX_GLOW || fx == KnifeView.FX_HOLOGRAM) {
            float k = ghost >= 0 ? ghost : Math.min(1f, kv.fxGlow * 0.35f);
            d.submitModelPart(part, pose, KnifeHand.type(skin, true), 0xF000F0, overlay, null, sheeted, false,
                    0xFF000000 | scale(kv.fxColor, k), crumbling, outline);
        }
    }

    private static int tint(BakedQuad q, int[] tints) {
        BakedQuad.MaterialInfo m = q.materialInfo();
        if (m.isTinted() && tints != null && m.tintIndex() >= 0 && m.tintIndex() < tints.length) {
            return tints[m.tintIndex()];
        }
        return -1;
    }

    private static int lighten(int c) {
        return 0x404040 | (c & 0xFFFFFF);
    }

    private static int mul(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }

    private static int scale(int c, float k) {
        k = Math.max(0, Math.min(1, k));
        return Math.round(((c >> 16) & 255) * k) << 16 | Math.round(((c >> 8) & 255) * k) << 8 | Math.round((c & 255) * k);
    }

    // ------------------------------------------------------------------ everything else passes through

    @Override
    public void submitShadow(PoseStack pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        if (ghost < 0) {
            d.submitShadow(pose, radius, pieces);
        }
    }

    @Override
    public void submitNameTag(PoseStack pose, Vec3 at, int y, Component name, boolean seeThrough, int light, double dist,
                              CameraRenderState camera) {
        if (ghost < 0) {
            d.submitNameTag(pose, at, y, name, seeThrough, light, dist, camera);
        }
    }

    @Override
    public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode,
                           int light, int color, int background, int outline) {
        if (ghost < 0) {
            d.submitText(pose, x, y, text, shadow, mode, light, color, background, outline);
        }
    }

    @Override
    public void submitFlame(PoseStack pose, EntityRenderState state, Quaternionf rot) {
        if (ghost < 0) {
            d.submitFlame(pose, state, rot);
        }
    }

    @Override
    public void submitLeash(PoseStack pose, EntityRenderState.LeashState leash) {
        if (ghost < 0) {
            d.submitLeash(pose, leash);
        }
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int tint,
                                TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        if (ghost < 0) {
            d.submitModel(model, state, pose, type, light, overlay, tint, sprite, outline, crumbling);
        }
    }

    @Override
    public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state) {
        if (ghost < 0) {
            d.submitMovingBlock(pose, state);
        }
    }

    @Override
    public void submitBlockModel(PoseStack pose, RenderType type, List<BlockStateModelPart> parts, int[] tints, int light, int overlay,
                                 int outline) {
        if (ghost < 0) {
            d.submitBlockModel(pose, type, parts, tints, light, overlay, outline);
        }
    }

    @Override
    public void submitBreakingBlockModel(PoseStack pose, BlockStateModel model, long seed, int progress) {
        if (ghost < 0) {
            d.submitBreakingBlockModel(pose, model, seed, progress);
        }
    }

    @Override
    public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (ghost < 0) {
            d.submitCustomGeometry(pose, type, renderer);
        }
    }

    @Override
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
        if (ghost < 0) {
            d.submitParticleGroup(renderer);
        }
    }
}
