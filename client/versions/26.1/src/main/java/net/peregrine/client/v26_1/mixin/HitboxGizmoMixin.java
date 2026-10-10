package net.peregrine.client.v26_1.mixin;

import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.peregrine.client.v26_1.Extras;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes (1.21.11 and later draw them as gizmos): which things, their color, the look line. */
@Mixin(targets = "net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer")
public abstract class HitboxGizmoMixin {

    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void peregrine$filter(Entity entity, float partial, boolean server, CallbackInfo ci) {
        if (Hooks.hitboxHits < 1000) {
            Hooks.hitboxHits++;
        }
        if (!Hooks.showHitbox(Extras.hitboxKind(entity))) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gizmos/GizmoStyle;stroke(I)Lnet/minecraft/gizmos/GizmoStyle;", ordinal = 0))
    private int peregrine$color(int color) {
        return Hooks.hitboxColor != 0 && color == -1 ? (0xFF000000 | Hooks.hitboxColor) : color;
    }

    @ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gizmos/Gizmos;arrow(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)Lnet/minecraft/gizmos/GizmoProperties;"),
            index = 2)
    private int peregrine$lookLine(int color) {
        return Hooks.hitboxLookLine ? color : 0;  // fully see-through
    }
}
