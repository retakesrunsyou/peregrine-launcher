package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Pick your own time of day: the sky, light and shadows follow, only for you. */
public final class TimeChanger extends Module {

    /** Hours on a clock (0-24); Minecraft's day starts at 6 in the morning. */
    private final SliderSetting hour = add(new SliderSetting("hour", "Time of day", 0f, 23.75f, 0.25f, 12f, "%.2f")
            .withText(TimeChanger::label));

    public TimeChanger() {
        super("time_changer", "Time changer", "Your own time of day: always noon, sunset or night (only for you)",
                Category.VISUALS, false);
    }

    /** Clock hour to Minecraft ticks (6:00 = 0, noon = 6000, midnight = 18000). */
    static long ticks(float hour) {
        return (long) (((hour - 6f + 24f) % 24f) * 1000f);
    }

    /** "12:00 (noon)" style text for the menu. */
    public static String label(float hour) {
        int h = (int) hour, m = Math.round((hour - h) * 60);
        String when = hour >= 5 && hour < 7 ? "sunrise" : hour >= 7 && hour < 11.5f ? "morning"
                : hour >= 11.5f && hour < 13 ? "noon" : hour >= 13 && hour < 17.5f ? "afternoon"
                : hour >= 17.5f && hour < 19.5f ? "sunset" : "night";
        return String.format("%02d:%02d %s", h, m, when);
    }

    @Override
    public void tick(Platform p) {
        Hooks.timeOverride = ticks(hour.value);
    }

    @Override
    protected void onEnable() {
        Hooks.timeOverride = ticks(hour.value);
    }

    @Override
    protected void onDisable() {
        Hooks.timeOverride = -1;
    }
}
