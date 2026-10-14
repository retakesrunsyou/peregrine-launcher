package net.peregrine.client.core.settings;

/** A short piece of text typed into a box (a command, a list of items, a name). */
public final class TextSetting extends Setting {

    public final int maxLength;
    public final String placeholder;
    private final String def;
    public String value;

    public TextSetting(String id, String label, String def, int maxLength, String placeholder) {
        super(id, label);
        this.def = def;
        this.value = def;
        this.maxLength = maxLength;
        this.placeholder = placeholder;
    }

    public void type(char c) {
        if (value.length() < maxLength && !Character.isISOControl(c)) {
            value += c;
        }
    }

    public void backspace() {
        if (!value.isEmpty()) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        }
    }

    @Override
    public Object save() {
        return value;
    }

    @Override
    public void load(String v) {
        if (v != null) {
            value = v.length() > maxLength ? v.substring(0, maxLength) : v;
        }
    }

    @Override
    public void reset() {
        value = def;
    }
}
