package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/** Your own weather: always clear, or rain or a thunderstorm, only on your screen. */
public final class Weather extends Module {

    public static final String[] MODES = {"Clear", "Rain", "Thunderstorm"};

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Weather", 0, MODES));

    public Weather() {
        // (keeps the old "Clear weather" id, so it stays on for players who had it on)
        super("clear_weather", "Weather", "Clear skies, rain or a storm, only on your screen (the world isn't changed)",
                Category.VISUALS, false);
    }

    private void apply() {
        Hooks.weather = enabled() ? mode.index + 1 : 0;
    }

    @Override
    public void tick(Platform p) {
        apply();
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        apply();
    }
}
