package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** Shows your own name tag in third person, with the Peregrine logo in front of it. */
public final class OwnNameTag extends Module {

    private final BoolSetting logo = add(new BoolSetting("logo", "Peregrine logo before your name", true));

    public OwnNameTag() {
        super("own_nametag", "Your name tag", "See your own name tag in third person, with the Peregrine logo",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.ownNameTag = true;
        Hooks.nameLogo = logo.value;
    }

    @Override
    protected void onEnable() {
        Hooks.ownNameTag = true;
        Hooks.nameLogo = logo.value;
    }

    @Override
    protected void onDisable() {
        Hooks.ownNameTag = false;
    }
}
