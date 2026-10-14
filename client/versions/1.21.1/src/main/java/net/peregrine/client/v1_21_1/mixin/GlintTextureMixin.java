package net.peregrine.client.v1_21_1.mixin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glint color: the enchantment shine texture is recolored as it loads (once per color change). */
@Mixin(FallbackResourceManager.class)
public abstract class GlintTextureMixin {

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void peregrine$tint(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (Hooks.glintColor == 0 || !Hooks.isGlintTexture(location.getPath())) {
            return;
        }
        Optional<Resource> found = cir.getReturnValue();
        if (found == null || !found.isPresent()) {
            return;
        }
        Resource orig = found.get();
        cir.setReturnValue(Optional.of(new Resource(orig.source(), () -> {
            try (InputStream in = orig.open()) {
                return new ByteArrayInputStream(Hooks.tintGlint(in.readAllBytes()));
            }
        }, orig::metadata)));
    }
}
