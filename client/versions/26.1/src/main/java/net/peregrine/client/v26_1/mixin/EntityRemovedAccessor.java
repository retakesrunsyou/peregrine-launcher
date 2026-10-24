package net.peregrine.client.v26_1.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zero delay: a crystal the server didn't remove after all can come back. */
@Mixin(Entity.class)
public interface EntityRemovedAccessor {

    @Invoker("unsetRemoved")
    void peregrine$unsetRemoved();
}
