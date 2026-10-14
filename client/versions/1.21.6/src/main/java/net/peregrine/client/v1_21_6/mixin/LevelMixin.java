package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clear weather: your screen shows no rain or snow. Only the client's copy of
 *  the world is affected, so crops, mobs and the server don't notice. */
@Mixin(Level.class)
public abstract class LevelMixin {

    private boolean peregrine$client() {
        return (Object) this instanceof ClientLevel;
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void peregrine$noRain(float delta, CallbackInfoReturnable<Float> cir) {
        float w = Hooks.weather == 0 ? -1f : Hooks.weatherLevel(false);
        if (w >= 0f && peregrine$client()) {
            cir.setReturnValue(w);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void peregrine$noThunder(float delta, CallbackInfoReturnable<Float> cir) {
        float w = Hooks.weather == 0 ? -1f : Hooks.weatherLevel(true);
        if (w >= 0f && peregrine$client()) {
            cir.setReturnValue(w);
        }
    }
}
