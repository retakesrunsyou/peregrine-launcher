package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggle sneak stays on: Minecraft lets go of every key when a menu opens (Esc, Right Shift,
 * your inventory...) or the window loses focus, which made you stand up. With Peregrine's toggle
 * sneak on, a crouch you toggled stays toggled through all of that, until you press sneak again.
 */
@Mixin(KeyMapping.class)
public abstract class KeepSneakMixin {

    @Unique
    private static boolean peregrine$crouched;

    @Unique
    private static boolean peregrine$keep() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Peregrine pc = Peregrine.get();
            return mc != null && mc.options != null && mc.player != null && pc != null && pc.on("toggle_sneak")
                    && mc.options.toggleCrouch().get() && mc.options.keyShift.isDown();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Unique
    private static void peregrine$restore() {
        if (peregrine$crouched) {
            peregrine$crouched = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && !mc.options.keyShift.isDown()) {
                mc.options.keyShift.setDown(true);
            }
        }
    }

    @Inject(method = "releaseAll", at = @At("HEAD"), require = 0)
    private static void peregrine$beforeRelease(CallbackInfo ci) {
        peregrine$crouched = peregrine$keep();
    }

    @Inject(method = "releaseAll", at = @At("TAIL"), require = 0)
    private static void peregrine$afterRelease(CallbackInfo ci) {
        peregrine$restore();
    }

    @Inject(method = "resetToggleKeys", at = @At("HEAD"), require = 0)
    private static void peregrine$beforeReset(CallbackInfo ci) {
        peregrine$crouched = peregrine$keep();
    }

    @Inject(method = "resetToggleKeys", at = @At("TAIL"), require = 0)
    private static void peregrine$afterReset(CallbackInfo ci) {
        peregrine$restore();
    }

    @Inject(method = "setAll", at = @At("HEAD"), require = 0)
    private static void peregrine$beforeSetAll(CallbackInfo ci) {
        peregrine$crouched = peregrine$keep();
    }

    @Inject(method = "setAll", at = @At("TAIL"), require = 0)
    private static void peregrine$afterSetAll(CallbackInfo ci) {
        peregrine$restore();
    }
}
