package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Fullbright set brightness above the slider's maximum. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {

    @Accessor("value")
    void peregrine$setValue(Object value);
}
