package net.peregrine.client.v1_21_9.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21.11 and newer pass the outline's width straight to it (see OutlineColorMixin). */
@Mixin(RenderSystem.class)
public abstract class LineWidthMixin {
}
