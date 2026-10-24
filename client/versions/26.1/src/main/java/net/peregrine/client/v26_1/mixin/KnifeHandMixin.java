package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v26_1.ViewmodelHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knives and viewmodel effects: each hand Minecraft is about to draw in first person goes
 * through Peregrine first (a knife instead of the sword, see-through or glowing items, swing
 * afterimages).
 */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class KnifeHandMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$knife(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                 ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light,
                                 CallbackInfo ci) {
        if (ViewmodelHook.before(this, player, partial, pitch, hand, swing, stack, equip, pose, collector, light)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private SubmitNodeCollector peregrine$effects(SubmitNodeCollector collector, AbstractClientPlayer player, float partial,
                                                  float pitch, InteractionHand hand, float swing, ItemStack stack) {
        return ViewmodelHook.collector(collector, player, hand, stack);
    }
}
