package net.peregrine.client.v26_2;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            Panorama.draw(this, g, partialTick);  // opened from the main menu: show the panorama, not black
        }
        if (menu.wantsShade()) {
            int alpha = Math.round(((Theme.MENU_SHADE >>> 24) & 0xFF) * menu.fade());
            g.fill(0, 0, width, height, (alpha << 24) | (Theme.MENU_SHADE & 0xFFFFFF));
        }
        menu.render(new GuiDraw(g), mouseX, mouseY);
    }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No blur: the game stays visible behind the menu.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return menu.mouseClicked((int) e.x(), (int) e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        return menu.mouseDragged((int) e.x(), (int) e.y()) || super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return menu.mouseReleased() || super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return menu.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (menu.keyPressed(menuKey(e.key()))) {
            return true;
        }
        if (PeregrineClientMod.menuKey.matches(e)) {
            onClose();  // the same key closes it
            return true;
        }
        return super.keyPressed(e);  // Esc closes
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        boolean used = false;
        for (char c : e.codepointAsString().toCharArray()) {
            used |= menu.charTyped(c);
        }
        return used || super.charTyped(e);
    }

    @Override
    public void onClose() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            // Opened from the main menu: go back to it (or to Minecraft's, if switched off).
            Screens.open(Peregrine.get().titleMenu().enabled() ? new PeregrineTitleScreen() : null);
        } else {
            super.onClose();
        }
    }

    /** 26.3 uses different key numbers; the menu expects the classic ones. */
    private static int menuKey(int key) {
        if (key == InputConstants.KEY_ESCAPE) {
            return Menu.KEY_ESCAPE;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            return Menu.KEY_ENTER;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            return Menu.KEY_BACKSPACE;
        }
        return -1;
    }

    @Override
    public void added() {
        super.added();
        Screens.textInput(this, true);
    }

    @Override
    public void removed() {
        Screens.textInput(this, false);
        menu.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;  // the game keeps running behind the menu
    }
}
