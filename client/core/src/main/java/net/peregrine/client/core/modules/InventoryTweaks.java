package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/**
 * Inventory tweaks, in the spirit of Mouse Tweaks: hold Shift and drag to move every
 * stack you pass over, and scroll over a stack to move items one at a time (down
 * sends one to the other side, up pulls one back). Uses normal clicks, so it works
 * on any server.
 */
public final class InventoryTweaks extends Module {

    private final BoolSetting drag = add(new BoolSetting("drag", "Shift + drag moves every stack you pass", true));
    private final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll over a stack to move items one at a time", true));

    public InventoryTweaks() {
        super("inventory_tweaks", "Inventory tweaks",
                "Mouse Tweaks style: Shift-drag to move stacks, scroll over a stack to move one item at a time",
                Category.UTILITY, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.dragMove = on && drag.value;
        Hooks.scrollMove = on && scroll.value;
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
