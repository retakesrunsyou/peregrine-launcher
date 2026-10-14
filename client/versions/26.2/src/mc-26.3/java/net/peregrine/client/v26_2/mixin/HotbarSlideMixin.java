package net.peregrine.client.v26_2.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.modules.SmoothHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth hotbar on 26.3: the hotbar's selection box is drawn where it's sliding to. Caught where
 * sprites are drawn (only the selection sprite is touched), since 26.3 draws the hotbar differently.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class HotbarSlideMixin {

    @Unique
    private boolean peregrine$inside;

    @Inject(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private void peregrine$slide(RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, CallbackInfo ci) {
        if (peregrine$inside || Hooks.hotbarSpeed <= 0f || w != 24 || h != 23
                || !"hud/hotbar_selection".equals(sprite.getPath())) {
            return;
        }
        int shown = SmoothHotbar.slide(x);
        if (shown == x) {
            return;
        }
        ci.cancel();
        peregrine$inside = true;
        try {
            ((GuiGraphicsExtractor) (Object) this).blitSprite(pipeline, sprite, shown, y, w, h);
        } finally {
            peregrine$inside = false;
        }
    }
}
