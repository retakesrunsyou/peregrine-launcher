package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shiny potions: potions get the enchantment shine again, like before 1.20.5. */
@Mixin(ItemStack.class)
public abstract class ShinyPotionsMixin {

    @Inject(method = "hasFoil()Z", at = @At("RETURN"), cancellable = true)
    private void peregrine$shine(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        if (self.is(Items.POTION) || self.is(Items.SPLASH_POTION) || self.is(Items.LINGERING_POTION)) {
            Peregrine pc = Peregrine.get();
            if (pc != null && pc.on("shiny_potions")) {
                if (Hooks.foilHits < 1000) {
                    Hooks.foilHits++;
                }
                cir.setReturnValue(true);
            }
        }
    }
}
