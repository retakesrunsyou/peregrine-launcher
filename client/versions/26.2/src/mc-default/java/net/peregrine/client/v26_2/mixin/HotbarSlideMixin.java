package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;

/** 26.2 slides the hotbar selection in SmoothHotbarMixin; this one is only needed on 26.3. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class HotbarSlideMixin {
}
