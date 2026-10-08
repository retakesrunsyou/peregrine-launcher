package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;

/** A simple on/off switch that a version adapter's hook checks (e.g. Clean edges). */
public final class Flag extends Module {

    public Flag(String id, String name, String description, Category category) {
        super(id, name, description, category, false);
    }
}
