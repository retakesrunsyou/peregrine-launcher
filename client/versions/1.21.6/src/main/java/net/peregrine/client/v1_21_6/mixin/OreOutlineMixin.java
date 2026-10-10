package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.v1_21_6.OreScan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.world.phys.Vec3;

/** Ore outlines: a colored outline on each ore you can see (drawn like the block outline, so never through walls). */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class OreOutlineMixin {

    @Inject(method = "renderBlockOutline", at = @At("RETURN"))
    private void peregrine$ores(Camera camera, MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucent,
                                CallbackInfo ci) {
        if (translucent || !Hooks.oreOutlines) {
            return;
        }
        Vec3 cam = camera.getPosition();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Minecraft mc = Minecraft.getInstance();
        java.util.List<long[]> ores = OreScan.ores();
        if (ores.isEmpty() || mc.level == null) {
            return;
        }
        if (Hooks.oreHits < 1000) {
            Hooks.oreHits++;
        }
        Hooks.drawingOres = true;
        try {
            for (long[] o : ores) {
                BlockPos pos = new BlockPos((int) o[0], (int) o[1], (int) o[2]);
                VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
                int color = (int) o[3];
                ShapeRenderer.renderShape(pose, lines, shape, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, color);
            }
        } finally {
            Hooks.drawingOres = false;
        }
        buffers.endLastBatch();
    }
}
