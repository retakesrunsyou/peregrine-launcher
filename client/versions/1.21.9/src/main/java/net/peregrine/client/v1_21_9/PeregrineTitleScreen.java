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
        if (!menu.drawsBackground()) {
            renderPanorama(g, partialTick);
        } else {
            g.fill(0, 0, width, height, 0xFF0B0D12);
        }
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

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dragX, double dragY) {
        return menu.mouseDragged((int) e.x(), (int) e.y()) || super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        return menu.mouseReleased((int) e.x(), (int) e.y()) || super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return menu.mouseScrolled((int) mouseX, (int) mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE
                && menu.keyPressed(net.peregrine.client.core.Menu.KEY_ESCAPE)) {
            return true;
        }
        return super.keyPressed(e);
    }
}
