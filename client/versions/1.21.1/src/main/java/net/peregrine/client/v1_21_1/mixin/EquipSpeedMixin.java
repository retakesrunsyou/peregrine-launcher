package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Item switch: how fast the hand drops and comes back up when you change items. */
@Mixin(ItemInHandRenderer.class)
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
