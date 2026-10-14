package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.v26_1.PeregrineTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps Minecraft's title screen and pause menu for Peregrine's whenever the game opens them. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen peregrine$mainMenu(Screen screen) {
        Peregrine pc = Peregrine.get();
        if (screen instanceof TitleScreen && pc != null && pc.titleMenu().enabled()) {
            return new PeregrineTitleScreen();
        }
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen && pc != null) {
            if (net.peregrine.client.v26_1.PeregrinePauseScreen.vanillaNext) {
                net.peregrine.client.v26_1.PeregrinePauseScreen.vanillaNext = false;  // "Advancements, stats & more"
                return screen;
            }
            if (((net.minecraft.client.gui.screens.PauseScreen) screen).showsPauseMenu() && pc.pauseMenu().enabled()) {
                return new net.peregrine.client.v26_1.PeregrinePauseScreen();
            }
        }
        return screen;
    }
}
