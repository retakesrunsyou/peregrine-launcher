package net.peregrine.client.v26_2;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.peregrine.client.core.PauseMenu;
import net.peregrine.client.core.Peregrine;

/** Peregrine's pause menu over the game. Layout lives in the core's PauseMenu. */
public final class PeregrinePauseScreen extends Screen {

    /** Set to let Minecraft's own pause menu through once ("Advancements, stats & more"). */
    public static boolean vanillaNext;

    private final PauseMenu menu = Peregrine.get().pauseMenu();

    public PeregrinePauseScreen() {
        super(Component.translatable("menu.game"));
        menu.shown();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        menu.render(new GuiDraw(g), mouseX, mouseY);
    }

    
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the game stays sharp behind the menu (the menu dims it a little)
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return menu.mouseClicked((int) e.x(), (int) e.y(), Screens.button(e.button())) || super.mouseClicked(e, doubleClick);
    }
}
