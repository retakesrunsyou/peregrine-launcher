package net.peregrine.client.v1_21_2;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.InventoryFx;

/** Smooth inventory: the version-specific bits (slots, positions, items) for the shared InventoryFx. */
public final class InvFx {

    private InvFx() {
    }

    /** The see-through panel behind a container screen's slots. */
    public static void panel(GuiGraphics g, AbstractContainerMenu menu, int left, int top, int width, int height) {
        int n = 0;
        int[] at = new int[menu.slots.size() * 2];
        for (Slot s : menu.slots) {
            if (s.isActive()) {
                at[n++] = left + s.x;
                at[n++] = top + s.y;
            }
        }
        int[] slots = new int[n];
        System.arraycopy(at, 0, slots, 0, n);
        InventoryFx.drawPanel(new GuiDraw(g), left, top, width, height, slots);
    }

    /** A number-key swap between a slot and the hotbar: both items fly to where they land. */
    public static void swap(AbstractContainerMenu menu, Slot slot, int button, int left, int top) {
        if (!InventoryFx.flyItems || slot == null) {
            return;
        }
        Slot hot = null;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory && s.getContainerSlot() == button && s.isActive()) {
                hot = s;
                break;
            }
        }
        if (hot == null || hot == slot) {
            return;
        }
        ItemStack a = slot.getItem().copy(), b = hot.getItem().copy();
        if (!a.isEmpty()) {
            InventoryFx.fly(a, left + slot.x, top + slot.y, left + hot.x, top + hot.y, hot.index);
        }
        if (!b.isEmpty()) {
            InventoryFx.fly(b, left + hot.x, top + hot.y, left + slot.x, top + slot.y, slot.index);
        }
    }
}
