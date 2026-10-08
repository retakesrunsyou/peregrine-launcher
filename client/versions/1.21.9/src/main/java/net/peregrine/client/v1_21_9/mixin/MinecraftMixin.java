package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.v1_21_9.PeregrineTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps Minecraft's title screen for Peregrine's main menu whenever the game opens it. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen peregrine$mainMenu(Screen screen) {
        Peregrine pc = Peregrine.get();
        if (screen instanceof TitleScreen && pc != null && pc.titleMenu().enabled()) {
            return new PeregrineTitleScreen();
        }
        return screen;
    }
}
