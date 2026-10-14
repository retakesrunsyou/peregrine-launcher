package net.peregrine.client.v1_21_1;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.peregrine.client.core.Menu;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Theme;

/** The see-through Right Shift menu. All the logic and drawing is in the core's Menu. */
final class PeregrineScreen extends Screen {

    private final Menu menu = Peregrine.get().menu();

    PeregrineScreen() {
        super(Component.literal("Peregrine"));
        menu.open();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            renderPanorama(g, partialTick);  // opened from the main menu: show the panorama, not black
        }
        if (menu.wantsShade()) {
            int alpha = Math.round(((Theme.MENU_SHADE >>> 24) & 0xFF) * menu.fade());
            g.fill(0, 0, width, height, (alpha << 24) | (Theme.MENU_SHADE & 0xFFFFFF));
        }
        menu.render(new GuiDraw(g), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur: the game stays visible behind the menu.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return menu.mouseClicked((int) mouseX, (int) mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return menu.mouseDragged((int) mouseX, (int) mouseY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return menu.mouseReleased() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return menu.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menu.keyPressed(keyCode)) {
            return true;
        }
        if (PeregrineClientMod.menuKey.matches(keyCode, scanCode)) {
            onClose();  // the same key closes it
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);  // Esc closes
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return menu.charTyped(c) || super.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            // Opened from the main menu: go back to it (or to Minecraft's, if switched off).
            mc.setScreen(Peregrine.get().titleMenu().enabled() ? new PeregrineTitleScreen() : null);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        menu.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;  // the game keeps running behind the menu
    }
}
