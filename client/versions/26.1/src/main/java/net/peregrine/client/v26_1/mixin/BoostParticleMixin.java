package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performance boost: past the particle cap, new particles are skipped (explosions stay smooth). */
@Mixin(ParticleEngine.class)
public abstract class BoostParticleMixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$cap(Particle particle, CallbackInfo ci) {
        int cap = Boost.maxParticles;
        if (cap <= 0) {
            return;
        }
        int n = 0;
        for (Object g : ((ParticleEngineAccessor) this).peregrine$groups().values()) {
            n += ((ParticleGroup<?>) g).size();
        }
        if (n >= cap) {
            ci.cancel();
        }
    }
}
