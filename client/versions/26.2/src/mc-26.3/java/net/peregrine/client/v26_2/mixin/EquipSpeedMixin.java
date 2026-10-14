package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Item switch on 26.3: how fast the hand drops and comes back up (the hands' own tick now). */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class EquipSpeedMixin {

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = 0.4F), require = 0)
    private float peregrine$up(float v) {
        return Hooks.equipStep(v);
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = -0.4F), require = 0)
    private float peregrine$down(float v) {
        return Hooks.equipStep(v);
    }
}
