package net.peregrine.client.v26_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.peregrine.client.core.InventoryFx;
import net.peregrine.client.v26_1.GuiDraw;
import net.peregrine.client.v26_1.InvFx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth inventory: open/close animation, gliding slot highlight, items flying to their slot. */
@Mixin(AbstractContainerScreen.class)
public abstract class SmoothInventoryMixin extends Screen {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected AbstractContainerMenu menu;

    @Unique
    private boolean peregrine$scaled;

    protected SmoothInventoryMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void peregrine$opened(CallbackInfo ci) {
        peregrine$depth = 0;
        if (!InventoryFx.closing()) {
            InventoryFx.opened();
        }
    }

    @Unique
    private int peregrine$depth;

    // The inventory draws through renderContents without passing render on newer versions, chests
    // through both: count the nesting so the screen is scaled exactly once per frame.
    @Inject(method = {"extractRenderState", "extractContents"}, at = @At("HEAD"), require = 0)
    private void peregrine$in(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        if (peregrine$depth++ > 0) {
            return;
        }
        float s = InventoryFx.scale();
        peregrine$scaled = s != 1f;
        if (peregrine$scaled) {
            float cx = this.width / 2f, cy = this.height / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx * (1f - s), cy * (1f - s));
            g.pose().scale(s, s);
        }
    }

    @Inject(method = {"extractRenderState", "extractContents"}, at = @At("RETURN"), require = 0)
    private void peregrine$out(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        if (--peregrine$depth > 0) {
            return;
        }
        peregrine$depth = 0;
        InventoryFx.drawFlights(new GuiDraw(g));
        if (peregrine$scaled) {
            peregrine$scaled = false;
            g.pose().popMatrix();
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void peregrine$close(CallbackInfo ci) {
        if (InventoryFx.startClose()) {
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void peregrine$removed(CallbackInfo ci) {
        InventoryFx.closed();
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void peregrine$slot(CallbackInfo ci, @Local(argsOnly = true) Slot slot) {
        if (InventoryFx.inFlight(slot.index)) {
            ci.cancel();  // its item is still flying here
        }
    }

    @ModifyExpressionValue(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/Slot;x:I"), require = 0)
    private int peregrine$glideX(int x) {
        return InventoryFx.highlightX(x);
    }

    @ModifyExpressionValue(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/Slot;y:I"), require = 0)
    private int peregrine$glideY(int y) {
        return InventoryFx.highlightY(y);
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"))
    private void peregrine$clicked(Slot slot, int id, int button, ContainerInput type, CallbackInfo ci) {
        if (InventoryFx.debug) {
            System.out.println("[Peregrine] inventory click " + type + " slot " + id + " button " + button
                    + " item " + (slot == null ? "-" : slot.getItem()));
        }
        if (type == ContainerInput.SWAP) {
            InvFx.swap(this.menu, slot, button, this.leftPos, this.topPos);
        }
    }

    @ModifyConstant(method = "extractLabels", constant = {@Constant(intValue = 4210752), @Constant(intValue = -12566464)}, require = 0)
    private int peregrine$labels(int color) {
        return InventoryFx.seeThrough ? 0xFFE6E6EA : color;  // readable on the dark glass
    }
}
