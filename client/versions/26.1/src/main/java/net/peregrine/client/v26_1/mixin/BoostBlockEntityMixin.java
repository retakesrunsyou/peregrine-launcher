package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance boost: chests, signs, banners and the like are left out past the chosen distance. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BoostBlockEntityMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$far(BlockEntity be, float partial, ModelFeatureRenderer.CrumblingOverlay crumbling,
                               CallbackInfoReturnable<Object> cir) {
        int d = Boost.blockEntityDistance;
        if (d <= 0 || be instanceof BeaconBlockEntity) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        BlockPos p = be.getBlockPos();
        double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
        if (dx * dx + dy * dy + dz * dz > (double) d * d) {
            cir.setReturnValue(null);
        }
    }
}
