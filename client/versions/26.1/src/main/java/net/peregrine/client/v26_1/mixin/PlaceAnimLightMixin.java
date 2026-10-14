package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.peregrine.client.v26_1.PlaceAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block place animation: the light change from a placed block rebuilds its section too, so hold that as well. */
@Mixin(ClientChunkCache.class)
public abstract class PlaceAnimLightMixin {

    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void peregrine$holdLight(LightLayer layer, SectionPos pos, CallbackInfo ci) {
        ClientChunkCache self = (ClientChunkCache) (Object) this;
        if (PlaceAnim.holdSections(pos.x(), pos.y(), pos.z(), pos.x(), pos.y(), pos.z(), () -> self.onLightUpdate(layer, pos))) {
            ci.cancel();
        }
    }
}
