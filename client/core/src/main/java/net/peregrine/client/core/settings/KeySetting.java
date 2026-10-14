package net.peregrine.client.core.settings;

import net.peregrine.client.core.Keys;

/** A key to press while playing (GLFW code), or Keys.NONE. */
public final class KeySetting extends Setting {

    public int key = Keys.NONE;

    public KeySetting(String id, String label) {
        super(id, label);
    }

    public String text() {
        return Keys.name(key);
    }

    @Override
    public Object save() {
        return key;
    }

    @Override
    public void load(String v) {
        try {
            key = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            // keep it unset
        }
    }

    @Override
    public void reset() {
        key = Keys.NONE;
    }
}
