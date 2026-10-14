package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.peregrine.client.v1_21_2.PlaceAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block place animation: keeps a just-placed block out of the world's mesh while it grows in.
 * Applied after other mods' changes to these methods (Sodium replaces the section one).
 */
@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class PlaceAnimRendererMixin {

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void peregrine$hold(BlockGetter level, BlockPos pos, BlockState old, BlockState now, int flags, CallbackInfo ci) {
        if (PlaceAnim.holdBlock((LevelRenderer) (Object) this, level, pos, old, now, flags)) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdBlockDirty(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
        int x = pos.getX() >> 4, y = pos.getY() >> 4, z = pos.getZ() >> 4;
        LevelRenderer self = (LevelRenderer) (Object) this;
        if (PlaceAnim.holdSections(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, () -> self.setBlockDirty(pos, old, now))) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlocksDirty(IIIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdBlocks(int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfo ci) {
        LevelRenderer self = (LevelRenderer) (Object) this;
        if (PlaceAnim.holdSections((x0 - 1) >> 4, (y0 - 1) >> 4, (z0 - 1) >> 4, (x1 + 1) >> 4, (y1 + 1) >> 4, (z1 + 1) >> 4,
                () -> self.setBlocksDirty(x0, y0, z0, x1, y1, z1))) {
            ci.cancel();
        }
    }

    @Inject(method = "setSectionDirtyWithNeighbors(III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdNeighbours(int x, int y, int z, CallbackInfo ci) {
        LevelRenderer self = (LevelRenderer) (Object) this;
        if (PlaceAnim.holdSections(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, () -> self.setSectionDirtyWithNeighbors(x, y, z))) {
            ci.cancel();
        }
    }

    @Inject(method = "setSectionDirty(III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdSection3(int x, int y, int z, CallbackInfo ci) {
        LevelRenderer self = (LevelRenderer) (Object) this;
        if (PlaceAnim.holdSections(x, y, z, x, y, z, () -> self.setSectionDirty(x, y, z))) {
            ci.cancel();
        }
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdSection(int x, int y, int z, boolean important, CallbackInfo ci) {
        LevelRenderer self = (LevelRenderer) (Object) this;
        if (PlaceAnim.holdSections(x, y, z, x, y, z, () -> self.setSectionDirty(x, y, z))) {
            ci.cancel();
        }
    }
}
