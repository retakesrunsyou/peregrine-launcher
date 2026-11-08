package net.peregrine.client.v26_1.mixin;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.modules.ChatTweaks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat: a time in front of each message, and repeats folded into one line with a count. */
@Mixin(ChatComponent.class)
public abstract class ChatTweaksMixin {

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {
    }

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component peregrine$chat(Component message) {
        Peregrine pc = Peregrine.get();
        Module m = pc == null ? null : pc.module("chat");
        if (!(m instanceof ChatTweaks) || !m.enabled() || message == null) {
            return message;
        }
        try {
            ChatTweaks.Result r = ((ChatTweaks) m).incoming(message.getString());
            if (r.replacePrevious && !allMessages.isEmpty()) {
                allMessages.remove(0);   // the newest is first: the same message, about to come back with its count
                refreshTrimmedMessages();
            }
            if (r.prefix == null && r.count <= 1) {
                return message;
            }
            MutableComponent out = Component.empty();
            if (r.prefix != null) {
                out.append(Component.literal(r.prefix).withStyle(ChatFormatting.GRAY));
            }
            out.append(message);
            if (r.count > 1) {
                out.append(Component.literal(" (x" + r.count + ")").withStyle(ChatFormatting.GRAY));
            }
            return out;
        } catch (RuntimeException e) {
            return message;
        }
    }
}
