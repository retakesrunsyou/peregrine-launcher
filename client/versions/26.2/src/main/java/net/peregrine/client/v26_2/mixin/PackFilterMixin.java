package net.peregrine.client.v26_2.mixin;

import java.io.InputStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keep packs out of menus: resource packs the player added (zip or folder) don't
 * supply menu textures, fonts or splash texts, so Minecraft's own are used there.
 */
@Mixin({FilePackResources.class, PathPackResources.class})
public abstract class PackFilterMixin {

    @Inject(method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/server/packs/resources/IoSupplier;",
            at = @At("HEAD"), cancellable = true)
    private void peregrine$skip(PackType type, Identifier location, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (type == PackType.CLIENT_RESOURCES && Hooks.keepOutOfPack(((PackResources) (Object) this).packId(),
                location.getNamespace(), location.getPath())) {
            cir.setReturnValue(null);
        }
    }

    @ModifyVariable(method = "listResources", at = @At("HEAD"), argsOnly = true)
    private PackResources.ResourceOutput peregrine$skipListed(PackResources.ResourceOutput output) {
        final String id = ((PackResources) (Object) this).packId();
        if (!Hooks.cleanMenus || id == null || !id.startsWith("file/")) {
            return output;
        }
        return (location, supplier) -> {
            if (!Hooks.keepOutOfPack(id, location.getNamespace(), location.getPath())) {
                output.accept(location, supplier);
            }
        };
    }
}
