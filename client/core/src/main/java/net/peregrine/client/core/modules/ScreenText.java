package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Titles: size, move or hide the big text servers put in the middle of the screen. */
public final class ScreenText extends Module {

    private final SliderSetting title = add(new SliderSetting("title", "Title size", 30f, 150f, 5f, 70f, "%.0f%%"));
    private final SliderSetting titleY = add(new SliderSetting("title_y", "Title height", -60f, 60f, 2f, 0f, "%+.0f"));
    private final BoolSetting hideTitle = add(new BoolSetting("hide_title", "Hide titles", false));

    public ScreenText() {
        super("screen_text", "Titles", "Resize, move or hide the big titles in the middle of the screen",
                Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.titleScale = on ? title.value / 100f : 1f;
        Hooks.titleOffset = on ? -titleY.value : 0f;
        Hooks.titleHidden = on && hideTitle.value;
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
