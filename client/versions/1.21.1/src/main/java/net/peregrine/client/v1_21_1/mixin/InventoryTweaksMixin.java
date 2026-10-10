package net.peregrine.client.v1_21_1.mixin;

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
}
