package net.peregrine.client.core.settings;

/** A button on a settings page (like Start or Reset). Nothing to save. */
public final class ActionSetting extends Setting {

    public final Runnable action;
    private final java.util.function.Supplier<String> button;

    public ActionSetting(String id, String label, java.util.function.Supplier<String> button, Runnable action) {
        super(id, label);
        this.button = button;
        this.action = action;
    }

    public String button() {
        return button.get();
    }

    @Override
    public Object save() {
        return null;
    }

    @Override
    public void load(String v) {
    }

    @Override
    public void reset() {
    }
}
