package net.peregrine.client.v1_21_2;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.peregrine.client.core.ContainerPreview;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;

/** Shulker preview: what's in a shulker box, drawn at the mouse instead of its tooltip. */
public final class Previews {

    private Previews() {
    }

    /** Draws the preview if the stack is a shulker box with things in it; true if it did. */
    public static boolean shulker(net.minecraft.client.gui.GuiGraphics graphics, ItemStack stack, int mx, int my) {
        Peregrine pc = Peregrine.get();
        if (pc == null || !pc.on("shulker_preview") || stack == null || stack.isEmpty()) {
            return false;
        }
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (!id.endsWith("shulker_box")) {
            return false;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return false;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);
        Object[] stacks = new Object[27];
        int[] counts = new int[27];
        boolean any = false;
        for (int i = 0; i < 27; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                stacks[i] = s;
                counts[i] = s.getCount();
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        var w = Minecraft.getInstance().getWindow();
        ContainerPreview.draw(new GuiDraw(graphics), stack.getHoverName().getString(), stacks, counts, mx, my,
                w.getGuiScaledWidth(), w.getGuiScaledHeight(), Hooks.shulkerTint(id));
        return true;
    }
}
