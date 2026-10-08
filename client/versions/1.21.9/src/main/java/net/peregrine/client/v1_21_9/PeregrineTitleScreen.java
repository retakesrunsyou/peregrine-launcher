package net.peregrine.client.v1_21_9;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.TitleMenu;

/** Peregrine's main menu over Minecraft's moving panorama. Layout lives in the core's TitleMenu. */
public final class PeregrineTitleScreen extends Screen {

    private final TitleMenu menu = Peregrine.get().titleMenu();

    public PeregrineTitleScreen() {
        super(Component.literal("Peregrine"));
        menu.shown();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanorama(g, partialTick);
        menu.render(new GuiDraw(g), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The panorama is drawn in render(); no blur.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return menu.mouseClicked((int) e.x(), (int) e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
