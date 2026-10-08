package net.peregrine.client.v1_21_2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Draw;

/** The shared core's drawing calls, done with Minecraft 1.21.1's GuiGraphics. */
final class GuiDraw implements Draw {

    private final GuiGraphics g;
    private final Font font = Minecraft.getInstance().font;

    GuiDraw(GuiGraphics g) {
        this.g = g;
    }

    @Override
    public void rect(int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + h, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        g.drawString(font, s, x, y, argb, shadow);
    }

    @Override
    public int width(String s) {
        return font.width(s);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void item(Object stack, int x, int y) {
        g.renderItem((ItemStack) stack, x, y);
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    @Override
    public void unclip() {
        g.disableScissor();
    }

    @Override
    public void textScaled(String s, int x, int y, int argb, float scale, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate((float) x, (float) y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, argb, shadow);
        g.pose().popPose();
    }
}
