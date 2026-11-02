package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.TextSetting;

/** On/off switch for Peregrine's pause menu; the menu itself is PauseMenu. Drag its buttons to rearrange them. */
public final class PauseMenuToggle extends Module {

    public static final String DEFAULT_ORDER = "resume,modules,options,locker,more,leave";

    public final TextSetting order = add(new TextSetting("order", "Button order (drag the buttons to change it)", DEFAULT_ORDER, 80,
            DEFAULT_ORDER));
    public final ActionSetting resetOrder = add(new ActionSetting("reset_order", "Button order", () -> "Reset",
            () -> order.value = DEFAULT_ORDER));

    public PauseMenuToggle() {
        super("pause_menu", "Peregrine pause menu", "Replaces Minecraft's pause menu with Peregrine's. Drag its buttons to rearrange them",
                Category.UTILITY, true);
    }
}
