package net.peregrine.client.core.modules;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Chat: the time in front of each message, the same message over and over shown once with a
 * count ("(x3)"), and how see-through the chat's background is.
 */
public final class ChatTweaks extends Module {

    private final BoolSetting times = add(new BoolSetting("timestamps", "Time in front of messages", true));
    private final ChoiceSetting clock = add(new ChoiceSetting("clock", "Clock", 0, "12-hour", "24-hour", "With seconds"));
    private final BoolSetting stack = add(new BoolSetting("stack", "Repeated messages shown once, with a count", true));
    private final SliderSetting background = add(new SliderSetting("background", "Chat background", 0f, 100f, 5f, 50f, "%.0f%%"));

    private String last;
    private int count = 1;
    private long lastAt;
    private float appliedBg = -1;

    public ChatTweaks() {
        super("chat", "Chat", "Timestamps, repeated messages stacked into one with a count, and the chat's background",
                Category.UTILITY, false);
    }

    /** What to do with an incoming message. */
    public static final class Result {
        public String prefix;          // the time, or null
        public int count = 1;          // shown as "(xN)" when more than 1
        public boolean replacePrevious;
    }

    /** Called by the adapter for each message the chat is about to show. */
    public Result incoming(String plain) {
        Result r = new Result();
        long now = System.currentTimeMillis();
        if (stack.value && plain.equals(last) && now - lastAt < 60_000) {
            count++;
            r.count = count;
            r.replacePrevious = true;
        } else {
            count = 1;
        }
        last = plain;
        lastAt = now;
        if (times.value) {
            String f = clock.index == 0 ? "h:mm a" : clock.index == 1 ? "HH:mm" : "HH:mm:ss";
            r.prefix = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern(f)) + "] ";
        }
        return r;
    }

    @Override
    public void tick(Platform p) {
        if (background.value != appliedBg) {
            appliedBg = background.value;
            p.setChatBackground(background.value / 100f);
        }
    }

    @Override
    protected void onDisable() {
        appliedBg = -1;
        platform().setChatBackground(-1f);   // back to Minecraft's own setting
        last = null;
    }
}
