package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anti base leak. Minecraft picks each block's texture variant and rotation, and
 * each plant's nudge inside its block, from the block's coordinates. These hooks
 * make every block use the same choice, so patterns can't be traced back.
 * Rendering only: the world and the server are unchanged.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class AntiLeakSeedMixin {

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void peregrine$sameRotation(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (Hooks.fixedRotation) {
            if (Hooks.seedHits < 1000) {
                Hooks.seedHits++;
            }
            cir.setReturnValue(Hooks.FIXED_SEED);
        }
    }

    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void peregrine$centred(BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
        if (Hooks.fixedOffset) {
            BlockState state = (BlockState) (Object) this;
            // Bamboo and dripstone have collision that follows the offset; leave them be.
            if (!state.is(Blocks.BAMBOO) && !state.is(Blocks.POINTED_DRIPSTONE)) {
                if (Hooks.offsetHits < 1000) {
                    Hooks.offsetHits++;
                }
                cir.setReturnValue(Vec3.ZERO);
            }
        }
    }
}
