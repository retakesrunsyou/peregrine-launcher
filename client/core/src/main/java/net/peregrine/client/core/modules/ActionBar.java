package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Action bar: the line of text above your hotbar, smaller, bigger or hidden. */
public final class ActionBar extends Module {

    private final SliderSetting size = add(new SliderSetting("action", "Size", 50f, 150f, 5f, 100f, "%.0f%%"));
    private final BoolSetting hide = add(new BoolSetting("hide_action", "Hide it", false));

    public ActionBar() {
        super("action_bar", "Action bar", "Resize or hide the text just above your hotbar", Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.actionScale = on ? size.value / 100f : 1f;
        Hooks.actionHidden = on && hide.value;
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
