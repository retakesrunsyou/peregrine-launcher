package net.peregrine.client.core.settings;

/** One adjustable option of a feature, shown on its settings page (the gear in the menu). */
public abstract class Setting {

    public final String id;
    public final String label;

    protected Setting(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** The value as saved in the settings file. */
    public abstract Object save();

    /** Sets the value from the settings file; bad values are ignored. */
    public abstract void load(String value);

    public abstract void reset();
}
