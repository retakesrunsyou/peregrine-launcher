package net.peregrine.client.v1_21_1.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Hide plants: grass, flowers and the like are left out when chunks are built (only for you). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class HidePlantsMixin {

    @Unique
    private static final Map<Block, Integer> PEREGRINE$KIND = new ConcurrentHashMap<Block, Integer>();

    @Inject(method = "getRenderShape()Lnet/minecraft/world/level/block/RenderShape;", at = @At("HEAD"), cancellable = true)
    private void peregrine$hide(CallbackInfoReturnable<RenderShape> cir) {
        if (Hooks.plantMask == 0) {
            return;
        }
        Block b = ((BlockBehaviour.BlockStateBase) (Object) this).getBlock();
        Integer kind = PEREGRINE$KIND.get(b);
        if (kind == null) {
            kind = Hooks.plantKind(String.valueOf(BuiltInRegistries.BLOCK.getKey(b)));
            PEREGRINE$KIND.put(b, kind);
        }
        if (Hooks.hidePlant(kind)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}
