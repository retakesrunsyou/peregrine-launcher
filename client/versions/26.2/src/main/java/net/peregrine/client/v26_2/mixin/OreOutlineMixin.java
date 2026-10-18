package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.v26_2.OreScan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.peregrine.client.v26_2.mixin.LevelRendererOutline;

/** Ore outlines: one colored outline around each ore vein you can see (drawn like the block outline, so never through walls). */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class OreOutlineMixin {

    @Inject(method = "submitBlockOutline", at = @At("RETURN"))
    private void peregrine$ores(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        if (!(Hooks.oreOutlines || Hooks.schematicOn)) {
            return;
        }
        float width = mc$width();
        net.minecraft.world.phys.Vec3 cam = state.cameraRenderState.pos;
        Minecraft mc = Minecraft.getInstance();
        java.util.List<OreScan.Vein> ores = OreScan.all();
        if (ores.isEmpty() || mc.level == null) {
            return;
        }
        if (Hooks.oreHits < 1000) {
            Hooks.oreHits++;
        }
        Hooks.drawingOres = true;
        try {
            for (OreScan.Vein o : ores) {
                BlockPos pos = o.origin;
                VoxelShape shape = o.shape;
                int color = o.color;
                pose.pushPose();
                pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                ((LevelRendererOutline) (Object) this).peregrine$submitHitOutline(pose, collector, RenderTypes.lines(),
                        new BlockOutlineRenderState(pos, false, false, shape), color, width, false);
                pose.popPose();
            }
        } finally {
            Hooks.drawingOres = false;
        }
    }

    private static float mc$width() {
        return Minecraft.getInstance().getWindow().getAppropriateLineWidth() * Hooks.oreWidth;
    }
}
