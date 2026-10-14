package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.TitleMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Names the game window "Peregrine Client" instead of just "Minecraft". */
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {

    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void peregrine$title(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(TitleMenu.windowTitle(cir.getReturnValue()));
    }
}
