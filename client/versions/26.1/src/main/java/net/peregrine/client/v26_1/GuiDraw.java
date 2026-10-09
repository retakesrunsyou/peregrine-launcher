package net.peregrine.client.v26_1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Draw;

/** The shared core's drawing calls, done with Minecraft 1.21.1's GuiGraphicsExtractor. */
final class GuiDraw implements Draw {

    private final GuiGraphicsExtractor g;
    private final Font font = Minecraft.getInstance().font;

    GuiDraw(GuiGraphicsExtractor g) {
        this.g = g;
    }

    @Override
    public void rect(int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + h, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        g.text(font, s, x, y, argb, shadow);
    }

    @Override
    public int width(String s) {
        return font.width(s);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    private static java.lang.reflect.Method itemMethod;
    private static boolean itemLookedUp;

    @Override
    public void item(Object stack, int x, int y) {
        if (!itemLookedUp) {
            itemLookedUp = true;
            for (String name : new String[] {"item", "renderItem", "fakeItem"}) {
                try {
                    itemMethod = GuiGraphicsExtractor.class.getMethod(name, ItemStack.class, int.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try the next name
                }
            }
        }
        if (itemMethod != null) {
            try {
                itemMethod.invoke(g, stack, x, y);
            } catch (ReflectiveOperationException ignored) {
                // skip the icon
            }
        }
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
        g.text(font, s, 0, 0, argb, shadow);
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
}
