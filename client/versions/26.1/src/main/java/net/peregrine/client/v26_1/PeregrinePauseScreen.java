package net.peregrine.client.v26_1;

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

    private double[] at(MouseButtonEvent e) {
        return Panel3D.pointer(e.x(), e.y(), width, height);
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
        double[] p = at(e);
        return menu.mouseDragged((int) p[0], (int) p[1]) || super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (movingPanel) {
            movingPanel = false;
            return true;
        }
        double[] p = at(e);
        return menu.mouseReleased((int) p[0], (int) p[1]) || super.mouseReleased(e);
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
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Panel3D.passKey(e, true);
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent e) {
        Panel3D.passKey(e, false);
        return super.keyReleased(e);
    }

    
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the game stays sharp behind the menu (the menu dims it a little)
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (Panel3D.active() && !Panel3D.inWorld() && Panel3D.altDown(net.minecraft.client.Minecraft.getInstance())) {
            movingPanel = true;
            return true;
        }
        double[] p = at(e);
        return menu.mouseClicked((int) p[0], (int) p[1], e.button()) || super.mouseClicked(e, doubleClick);
    }
}
