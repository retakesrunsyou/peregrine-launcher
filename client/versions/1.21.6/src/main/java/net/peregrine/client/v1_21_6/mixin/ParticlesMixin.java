package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particles: thins out each kind by the chosen strength, and colors crit sparks. */
@Mixin(ParticleEngine.class)
public abstract class ParticlesMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void peregrine$thinOut(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                                   CallbackInfoReturnable<Particle> cir) {
        if (Hooks.particlesFiltered
                && !Hooks.keepParticle(String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType())))) {
            if (Hooks.particleHits < 1000) {
                Hooks.particleHits++;
            }
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void peregrine$critColor(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                                     CallbackInfoReturnable<Particle> cir) {
        int c = Hooks.critColor;
        Particle p = cir.getReturnValue();
        if (c != 0 && p != null && (options.getType() == ParticleTypes.CRIT || options.getType() == ParticleTypes.ENCHANTED_HIT)) {
            p.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
