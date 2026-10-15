package net.peregrine.client.v1_21_6;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Draw;

/** The shared core's drawing calls, done with Minecraft 1.21.1's GuiGraphics. */
public final class GuiDraw implements Draw {

    private final GuiGraphics g;
    private final Font font = Minecraft.getInstance().font;

    public GuiDraw(GuiGraphics g) {
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
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.drawString(font, s, 0, 0, argb, shadow);
        g.pose().popMatrix();
    }

    @Override
    public void pushScale(int x, int y, float s) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(s, s);
    }

    @Override
    public void popScale() {
        g.pose().popMatrix();
    }

    @Override
    public void texture(String id, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int argb) {
        if (w <= 0 || h <= 0 || id == null) {
            return;
        }
        final int N = 4096;  // UVs as fractions of this size
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.resources.ResourceLocation.parse(id), x, y, u0 * N, v0 * N, w, h,
                Math.round((u1 - u0) * N), Math.round((v1 - v0) * N), N, N, argb);
    }
}
