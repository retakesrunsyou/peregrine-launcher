package net.peregrine.client.core.settings;

public final class BoolSetting extends Setting {

    private final boolean def;
    public boolean value;

    public BoolSetting(String id, String label, boolean def) {
        super(id, label);
        this.def = def;
        this.value = def;
    }

    @Override
    public Object save() {
        return value;
    }

    @Override
    public void load(String v) {
        value = Boolean.parseBoolean(v);
    }

    @Override
    public void reset() {
        value = def;
    }
}
