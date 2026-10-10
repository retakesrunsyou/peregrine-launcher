package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.peregrine.client.v1_21_9.Extras;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitboxes: only the kinds of things chosen in the menu get one. */
@Mixin(EntityRenderer.class)
public abstract class HitboxFilterMixin {

    @Inject(method = "extractHitboxes(Lnet/minecraft/world/entity/Entity;FZ)Lnet/minecraft/client/renderer/entity/state/HitboxesRenderState;",
            at = @At("RETURN"), cancellable = true)
    private void peregrine$filter(Entity entity, float partial, boolean server, CallbackInfoReturnable<HitboxesRenderState> cir) {
        if (Hooks.hitboxHits < 1000) {
            Hooks.hitboxHits++;
        }
        if (!Hooks.showHitbox(Extras.hitboxKind(entity))) {
            cir.setReturnValue(null);
        }
    }
}
