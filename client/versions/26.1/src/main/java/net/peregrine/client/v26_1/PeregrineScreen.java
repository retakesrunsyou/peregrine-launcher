package net.peregrine.client.v26_1;

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
        if (menu.wantsShade() && !Panel3D.active()) {
            int alpha = Math.round(((Theme.MENU_SHADE >>> 24) & 0xFF) * menu.fade());
            g.fill(0, 0, width, height, (alpha << 24) | (Theme.MENU_SHADE & 0xFFFFFF));
        }
        double[] pt = Panel3D.pointer(mouseX, mouseY, width, height);
        menu.render(new GuiDraw(g), (int) pt[0], (int) pt[1]);
    }

    // ---- 3D menu: the pointer traced onto the panel, Alt + drag moves it, Ctrl + scroll resizes it

    private boolean movingPanel;

    @Override
    public void tick() {
        super.tick();
        Panel3D.keepPlaying();
    }

    private int px(MouseButtonEvent e) {
        return (int) Panel3D.pointer(e.x(), e.y(), width, height)[0];
    }

    private int py(MouseButtonEvent e) {
        return (int) Panel3D.pointer(e.x(), e.y(), width, height)[1];
    }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No blur: the game stays visible behind the menu.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (Panel3D.active() && !Panel3D.inWorld() && Panel3D.altDown(net.minecraft.client.Minecraft.getInstance())) {
            movingPanel = true;
            return true;
        }
        return menu.mouseClicked(px(e), py(e), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (movingPanel) {
            net.peregrine.client.core.modules.Menu3D m = net.peregrine.client.core.modules.Menu3D.get();
            if (m != null) {
                m.nudge((float) (dragX / Math.max(1, width) * 2), (float) (-dragY / Math.max(1, height) * 2));
                Peregrine.get().markDirty();
            }
            return true;
        }
        return menu.mouseDragged(px(e), py(e)) || super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (movingPanel) {
            movingPanel = false;
            return true;
        }
        return menu.mouseReleased() || super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Panel3D.active() && Panel3D.ctrlDown(net.minecraft.client.Minecraft.getInstance())) {
            net.peregrine.client.core.modules.Menu3D m = net.peregrine.client.core.modules.Menu3D.get();
            if (m != null) {
                m.resize((float) scrollY);
                Peregrine.get().markDirty();
            }
            return true;
        }
        return menu.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (menu.keyPressed(e.key())) {
            return true;
        }
        if (PeregrineClientMod.menuKey.matches(e)) {
            onClose();  // the same key closes it
            return true;
        }
        if (e.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Panel3D.passKey(e, true);   // in the world: walk, jump...
        }
        return super.keyPressed(e);  // Esc closes
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        Panel3D.passKey(e, false);
        return super.keyReleased(e);
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

    @Override
    public void removed() {
        menu.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;  // the game keeps running behind the menu
    }
}
