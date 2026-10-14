package net.peregrine.client.v1_21_1;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.v1_21_1.mixin.LevelRendererShapes;

/** Ore outlines on 1.21-1.21.1. */
final class OreOutlines {

    private OreOutlines() {
    }

    static void draw(PoseStack pose, MultiBufferSource buffers, Camera camera) {
        java.util.List<OreScan.Vein> ores = OreScan.veins();
        Minecraft mc = Minecraft.getInstance();
        if (ores.isEmpty() || mc.level == null) {
            return;
        }
        if (Hooks.oreHits < 1000) {
            Hooks.oreHits++;
        }
        Vec3 cam = camera.getPosition();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (OreScan.Vein o : ores) {
            BlockPos pos = o.origin;
            VoxelShape shape = o.shape;
            int c = o.color;
            LevelRendererShapes.peregrine$renderShape(pose, lines, shape, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                    ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
        }
    }
}
