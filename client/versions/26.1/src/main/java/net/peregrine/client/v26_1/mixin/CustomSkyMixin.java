package net.peregrine.client.v26_1.mixin;

import net.peregrine.client.v26_1.SkyDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom sky: drawn over Minecraft's night sky, after its sun, moon and stars. */
@Mixin(targets = {"net.minecraft.client.renderer.SkyRenderer"})
public abstract class CustomSkyMixin {

    @Inject(method = "renderSunMoonAndStars", at = @At("RETURN"), require = 0)
    private void peregrine$customSky(CallbackInfo ci) {
        SkyDraw.draw();
    }
}
