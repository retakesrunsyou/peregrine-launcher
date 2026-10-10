package net.peregrine.client.v1_21_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets ore outlines use Minecraft's own outline drawing. */
@Mixin(LevelRenderer.class)
public interface LevelRendererShapes {

    @Invoker("renderShape")
    static void peregrine$renderShape(PoseStack pose, VertexConsumer buffer, VoxelShape shape, double x, double y, double z,
                                      float r, float g, float b, float a) {
        throw new AssertionError();
    }
}
