package net.peregrine.client.v1_21_2;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return menu.mouseClicked((int) mouseX, (int) mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return menu.mouseDragged((int) mouseX, (int) mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return menu.mouseReleased((int) mouseX, (int) mouseY) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return menu.mouseScrolled((int) mouseX, (int) mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE
                && menu.keyPressed(net.peregrine.client.core.Menu.KEY_ESCAPE)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
