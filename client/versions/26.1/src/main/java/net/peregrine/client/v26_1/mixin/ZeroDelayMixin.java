package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.peregrine.client.v26_1.ZeroDelayHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zero delay: crystals you hit and anchors you blow up disappear right away. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ZeroDelayMixin {

    @Inject(method = "attack", at = @At("TAIL"), require = 0)
    private void peregrine$crystal(Player player, Entity target, CallbackInfo ci) {
        ZeroDelayHooks.attacked(player, target);
    }

    @Inject(method = "performUseItemOn", at = @At("HEAD"), require = 0)
    private void peregrine$anchor(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                  CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        ZeroDelayHooks.usingBlock(player, hand, hit);
    }
}
