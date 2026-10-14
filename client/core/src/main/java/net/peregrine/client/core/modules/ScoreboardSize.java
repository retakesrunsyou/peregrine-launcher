package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Resizes the server's scoreboard (the sidebar on the right). Size only. */
public final class ScoreboardSize extends Module {

    private final SliderSetting size = add(new SliderSetting("size", "Size", 30f, 150f, 5f, 75f, "%.0f%%"));

    public ScoreboardSize() {
        super("scoreboard", "Scoreboard size", "Make the server's sidebar scoreboard smaller or bigger",
                Category.HUD, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.scoreboardScale = size.value / 100f;
    }

    @Override
    protected void onEnable() {
        Hooks.scoreboardScale = size.value / 100f;
    }

    @Override
    protected void onDisable() {
        Hooks.scoreboardScale = 1f;
    }
}
