package net.peregrine.client.core.settings;

/** One of a few named options; clicking it moves to the next. */
public final class ChoiceSetting extends Setting {

    public final String[] options;
    private final int def;
    public int index;

    public ChoiceSetting(String id, String label, int def, String... options) {
        super(id, label);
        this.options = options;
        this.def = def;
        this.index = def;
    }

    public String text() {
        return options[index];
    }

    public void next() {
        index = (index + 1) % options.length;
    }

    @Override
    public Object save() {
        return index;
    }

    @Override
    public void load(String v) {
        try {
            int i = Integer.parseInt(v);
            if (i >= 0 && i < options.length) {
                index = i;
            }
        } catch (NumberFormatException e) {
            // keep the default
        }
    }

    @Override
    public void reset() {
        index = def;
    }
}
