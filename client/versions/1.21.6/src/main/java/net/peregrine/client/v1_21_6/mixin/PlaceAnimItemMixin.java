package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.peregrine.client.v1_21_6.PlaceAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block place animation: notes where you're placing a block, before it goes into the world. */
@Mixin(BlockItem.class)
public abstract class PlaceAnimItemMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void peregrine$placing(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide()) {
            PlaceAnim.begin(context.getClickedPos());
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void peregrine$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide()) {
            PlaceAnim.cancel(context.getClickedPos(), cir.getReturnValue() != null && cir.getReturnValue().consumesAction());
        }
    }
}
