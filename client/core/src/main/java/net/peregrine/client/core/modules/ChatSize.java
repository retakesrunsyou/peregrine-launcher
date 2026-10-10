package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Chat size: text size, width and height (sizes only, no colors), using Minecraft's
 * own chat settings, so servers see nothing different. Switching it off puts your
 * previous sizes back.
 */
public final class ChatSize extends Module {

    private final SliderSetting scale = add(new SliderSetting("scale", "Text size", 30f, 100f, 5f, 80f, "%.0f%%"));
    private final SliderSetting width = add(new SliderSetting("width", "Width", 40f, 320f, 10f, 320f, "%.0f px"));
    private final SliderSetting height = add(new SliderSetting("height", "Height when open", 20f, 180f, 10f, 180f, "%.0f px"));
    private final SliderSetting closed = add(new SliderSetting("closed", "Height when closed", 20f, 180f, 10f, 90f, "%.0f px"));
    private double[] saved;
    private String applied = "";

    public ChatSize() {
        super("chat_size", "Chat size", "Resize the chat: text size, width and height", Category.HUD, false);
    }

    @Override
    public void tick(Platform p) {
        String want = scale.value + "/" + width.value + "/" + height.value + "/" + closed.value;
        if (want.equals(applied) || p.chatOption("scale") < 0) {
            return;
        }
        if (saved == null) {
            saved = new double[] {p.chatOption("scale"), p.chatOption("width"), p.chatOption("height"), p.chatOption("unfocused")};
        }
        // Minecraft keeps these as 0-1: width is 40-320 px, heights 20-180 px.
        p.setChatOption("scale", scale.value / 100.0);
        p.setChatOption("width", (width.value - 40) / 280.0);
        p.setChatOption("height", (height.value - 20) / 160.0);
        p.setChatOption("unfocused", (closed.value - 20) / 160.0);
        applied = want;
    }

    @Override
    protected void onDisable() {
        if (saved != null) {
            Platform p = platform();
            p.setChatOption("scale", saved[0]);
            p.setChatOption("width", saved[1]);
            p.setChatOption("height", saved[2]);
            p.setChatOption("unfocused", saved[3]);
            saved = null;
        }
        applied = "";
    }
}
