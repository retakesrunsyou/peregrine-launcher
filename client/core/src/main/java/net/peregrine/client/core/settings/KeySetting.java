package net.peregrine.client.core.settings;

import net.peregrine.client.core.Keys;

/** A key to press while playing (GLFW code), or Keys.NONE. */
public final class KeySetting extends Setting {

    public int key = Keys.NONE;
    private final int def;

    public KeySetting(String id, String label) {
        this(id, label, Keys.NONE);
    }

    /** With a key it starts on (GLFW code). */
    public KeySetting(String id, String label, int def) {
        super(id, label);
        this.def = def;
        this.key = def;
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
        key = def;
    }

    @Override
    public boolean isDefault() {
        return key == def;
    }
}
