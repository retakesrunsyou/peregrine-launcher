package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Peregrine draw a hand again (for swing afterimages). */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandAccessor {

    @Invoker("renderArmWithItem")
    void peregrine$renderArmWithItem(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                     ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light);
}
