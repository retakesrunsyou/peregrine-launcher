package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hit color: reaches the texture that tints hurt mobs, to recolor it. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {

    @Accessor("texture")
    DynamicTexture peregrine$texture();
}
