package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.v1_21_1.Nick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nick hider: your name shows as the nick in every piece of text the game draws. */
@Mixin(Font.class)
public abstract class NickTextMixin {

    @ModifyVariable(method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private String peregrine$drawString(String text) {
        return Hooks.nick == null ? text : Hooks.hideNick(text);
    }

    @ModifyVariable(method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private FormattedCharSequence peregrine$drawSeq(FormattedCharSequence text) {
        return Hooks.nick == null ? text : Nick.apply(text);
    }

    @ModifyVariable(method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private String peregrine$internalString(String text) {
        return Hooks.nick == null ? text : Hooks.hideNick(text);
    }

    @ModifyVariable(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private FormattedCharSequence peregrine$internalSeq(FormattedCharSequence text) {
        return Hooks.nick == null ? text : Nick.apply(text);
    }

    @ModifyVariable(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private FormattedCharSequence peregrine$internalSeq2(FormattedCharSequence text) {
        return Hooks.nick == null ? text : Nick.apply(text);
    }

    @Unique
    private static boolean peregrine$measuring;

    /** Extra width per name swapped (the nick may be longer or shorter). */
    @Unique
    private int peregrine$delta() {
        peregrine$measuring = true;
        try {
            Font f = (Font) (Object) this;
            return f.width(Hooks.nick) - f.width(Hooks.nickReal);
        } finally {
            peregrine$measuring = false;
        }
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"), cancellable = true)
    private void peregrine$widthString(String s, CallbackInfoReturnable<Integer> cir) {
        if (Hooks.nick != null && !peregrine$measuring) {
            int n = Nick.count(s);
            if (n > 0) {
                cir.setReturnValue(cir.getReturnValue() + n * peregrine$delta());
            }
        }
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("RETURN"), cancellable = true)
    private void peregrine$widthText(FormattedText t, CallbackInfoReturnable<Integer> cir) {
        if (Hooks.nick != null && !peregrine$measuring) {
            int n = Nick.count(t.getString());
            if (n > 0) {
                cir.setReturnValue(cir.getReturnValue() + n * peregrine$delta());
            }
        }
    }

    @Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("RETURN"), cancellable = true)
    private void peregrine$widthSeq(FormattedCharSequence t, CallbackInfoReturnable<Integer> cir) {
        if (Hooks.nick != null && !peregrine$measuring) {
            int n = Nick.count(t);
            if (n > 0) {
                cir.setReturnValue(cir.getReturnValue() + n * peregrine$delta());
            }
        }
    }
}
