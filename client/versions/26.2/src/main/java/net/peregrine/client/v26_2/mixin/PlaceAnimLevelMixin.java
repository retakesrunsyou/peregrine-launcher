package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.peregrine.client.v26_2.PlaceAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block place animation: keeps a just-placed block out of the world's mesh while it grows in.
 * Held where the level tells the renderer about changes, so other mods' renderers wait too.
 */
@Mixin(ClientLevel.class)
public abstract class PlaceAnimLevelMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void peregrine$hold(BlockPos pos, BlockState old, BlockState now, int flags, CallbackInfo ci) {
        if (PlaceAnim.holdBlock((ClientLevel) (Object) this, pos, old, now, flags)) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlocksDirty", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdStates(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
        ClientLevel self = (ClientLevel) (Object) this;
        BlockPos at = pos.immutable();
        if (PlaceAnim.holdStates(at, () -> self.setBlocksDirty(at, old, now))) {
            ci.cancel();
        }
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdNeighbours(int x, int y, int z, CallbackInfo ci) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (PlaceAnim.holdSections(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, () -> self.setSectionDirtyWithNeighbors(x, y, z))) {
            ci.cancel();
        }
    }

    @Inject(method = "setSectionRangeDirty", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$holdRange(int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfo ci) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (PlaceAnim.holdSections(x0, y0, z0, x1, y1, z1, () -> self.setSectionRangeDirty(x0, y0, z0, x1, y1, z1))) {
            ci.cancel();
        }
    }
}
