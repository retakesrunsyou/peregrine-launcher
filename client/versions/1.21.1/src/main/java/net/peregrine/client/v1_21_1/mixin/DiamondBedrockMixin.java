package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Anti base leak: bedrock is drawn with the diamond block's model. */
@Mixin(BlockModelShaper.class)
public abstract class DiamondBedrockMixin {

    @ModifyVariable(method = "getBlockModel", at = @At("HEAD"), argsOnly = true)
    private BlockState peregrine$diamondBedrock(BlockState state) {
        if (Hooks.modelHits < 1000) {
            Hooks.modelHits++;
        }
        if (Hooks.diamondBedrock && state.is(Blocks.BEDROCK)) {
            if (Hooks.bedrockSwaps < 1000) {
                Hooks.bedrockSwaps++;
            }
            return Blocks.DIAMOND_BLOCK.defaultBlockState();
        }
        return state;
    }
}
