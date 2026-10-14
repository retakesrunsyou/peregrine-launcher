package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen sits, for the see-through panel. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("leftPos")
    int peregrine$left();

    @Accessor("topPos")
    int peregrine$top();

    @Accessor("imageWidth")
    int peregrine$width();

    @Accessor("imageHeight")
    int peregrine$height();
}
