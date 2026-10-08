package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean edges: skips the dark vignette around the screen. */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void peregrine$cleanEdges(GuiGraphics g, Entity entity, CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && pc.on("clean_edges")) {
            ci.cancel();
        }
    }
}
