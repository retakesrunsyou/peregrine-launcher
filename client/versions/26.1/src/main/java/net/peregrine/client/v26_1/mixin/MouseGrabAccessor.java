package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 3D menu in the world: capture the mouse without Minecraft closing the menu. */
@Mixin(MouseHandler.class)
public interface MouseGrabAccessor {

    @Accessor("mouseGrabbed")
    void peregrine$setGrabbed(boolean grabbed);
}
