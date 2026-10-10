package net.peregrine.client.v1_21_2.mixin;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inventory tweaks (Mouse Tweaks style). Everything is done with the same clicks you
 * could make yourself, so servers see nothing unusual.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryTweaksMixin {

    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int button, ClickType type);

    @Unique
    private final Set<Slot> peregrine$visited = new HashSet<>();

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void peregrine$start(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        peregrine$visited.clear();
        if (Hooks.dragMove && net.minecraft.client.gui.screens.Screen.hasShiftDown() && this.hoveredSlot != null) {
            peregrine$visited.add(this.hoveredSlot);  // Minecraft moves this first one itself
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void peregrine$drag(double x, double y, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = this.hoveredSlot;
        if (!Hooks.dragMove || button != 0 || !net.minecraft.client.gui.screens.Screen.hasShiftDown() || slot == null) {
            return;
        }
        if (this.menu.getCarried().isEmpty() && slot.hasItem() && peregrine$visited.add(slot)) {
            if (Hooks.inventoryTweakHits < 1000) {
                Hooks.inventoryTweakHits++;
            }
            this.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void peregrine$scroll(double x, double y, double sx, double sy, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = this.hoveredSlot;
        if (!Hooks.scrollMove || slot == null || sy == 0 || !this.menu.getCarried().isEmpty()) {
            return;
        }
        boolean moved = sy < 0 ? peregrine$moveOne(slot, slot) : peregrine$pullOne(slot);
        if (moved) {
            if (Hooks.inventoryTweakHits < 1000) {
                Hooks.inventoryTweakHits++;
            }
            cir.setReturnValue(true);
        }
    }

    /** Which side a slot is on: the open container, the hotbar, or the rest of your inventory. */
    @Unique
    private int peregrine$side(Slot s) {
        if (!(s.container instanceof Inventory)) {
            return 0;
        }
        boolean anyContainer = false;
        for (Slot o : this.menu.slots) {
            if (!(o.container instanceof Inventory)) {
                anyContainer = true;
                break;
            }
        }
        if (anyContainer) {
            return 1;  // with a chest open, all of your inventory is one side
        }
        return s.getContainerSlot() < 9 ? 2 : 1;  // just your inventory: hotbar <-> the rest
    }

    @Unique
    private boolean peregrine$usable(Slot s) {
        return !(s instanceof ResultSlot) && !(s.container instanceof Inventory && s.getContainerSlot() >= 36);
    }

    /** Moves one item from a slot to the other side (scroll down). */
    @Unique
    private boolean peregrine$moveOne(Slot from, Slot ignored) {
        ItemStack stack = from.getItem();
        if (stack.isEmpty() || !peregrine$usable(from)) {
            return false;
        }
        Slot to = null;
        int side = peregrine$side(from);
        for (Slot s : this.menu.slots) {  // a matching stack with room first, then an empty slot
            if (s == from || peregrine$side(s) == side || !peregrine$usable(s) || !s.mayPlace(stack)) {
                continue;
            }
            ItemStack there = s.getItem();
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                to = s;
                break;
            }
            if (there.isEmpty() && to == null) {
                to = s;
            }
        }
        if (to == null) {
            return false;
        }
        this.slotClicked(from, from.index, 0, ClickType.PICKUP);   // pick up the stack
        this.slotClicked(to, to.index, 1, ClickType.PICKUP);       // drop one
        this.slotClicked(from, from.index, 0, ClickType.PICKUP);   // put the rest back
        return true;
    }

    /** Pulls one matching item from the other side onto this stack (scroll up). */
    @Unique
    private boolean peregrine$pullOne(Slot into) {
        ItemStack stack = into.getItem();
        if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize() || !peregrine$usable(into)) {
            return false;
        }
        int side = peregrine$side(into);
        for (Slot s : this.menu.slots) {
            if (s != into && peregrine$side(s) != side && peregrine$usable(s)
                    && ItemStack.isSameItemSameComponents(s.getItem(), stack)) {
                this.slotClicked(s, s.index, 0, ClickType.PICKUP);
                this.slotClicked(into, into.index, 1, ClickType.PICKUP);
                this.slotClicked(s, s.index, 0, ClickType.PICKUP);
                return true;
            }
        }
        return false;
    }
}
