package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets ore outlines use Minecraft's own outline drawing. */
@Mixin(LevelRenderer.class)
public interface LevelRendererOutline {

    @Invoker("submitHitOutline")
    void peregrine$submitHitOutline(PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state,
                                    int color, float width, boolean translucent);
}
