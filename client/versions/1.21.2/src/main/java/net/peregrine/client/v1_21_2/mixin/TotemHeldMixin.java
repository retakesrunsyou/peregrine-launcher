package net.peregrine.client.v1_21_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Small totem: the totem in your own hands is drawn smaller (first person only). */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class TotemHeldMixin {

    @Unique
    private int peregrine$shrunk;

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void peregrine$small(CallbackInfo ci, @Local(argsOnly = true) ItemStack stack,
                                 @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) PoseStack pose) {
        float s = Hooks.totemHeld;
        if (s < 1f && context.firstPerson() && stack.is(Items.TOTEM_OF_UNDYING)) {
            if (Hooks.totemHits < 1000) {
                Hooks.totemHits++;
            }
            pose.pushPose();
            pose.scale(s, s, s);
            peregrine$shrunk++;
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void peregrine$restore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        if (peregrine$shrunk > 0) {
            peregrine$shrunk--;
            pose.popPose();
        }
    }
}
