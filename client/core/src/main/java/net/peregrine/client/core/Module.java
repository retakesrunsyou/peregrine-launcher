package net.peregrine.client.core;

/** A feature players can turn on and off from the in-game menu. */
public abstract class Module {

    public enum Category {
        HUD("HUD"), UTILITY("Utility");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    private final boolean defaultOn;
    private boolean enabled;

    protected Module(String id, String name, String description, Category category, boolean defaultOn) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultOn = defaultOn;
        this.enabled = defaultOn;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean defaultOn() {
        return defaultOn;
    }

    public void setEnabled(boolean on) {
        if (on == enabled) {
            return;
        }
        enabled = on;
        if (on) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** For loading saved settings: sets the state without running enable/disable. */
    void setEnabledQuietly(boolean on) {
        enabled = on;
    }

    /** Some servers ban certain features; this text warns about it in the menu. */
    public String warning() {
        return null;
    }

    protected Platform platform() {
        return Peregrine.get().platform();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called 20 times a second while enabled and in a world. */
    public void tick(Platform p) {
    }

    /** Called when the game closes, so modules can undo changes (like gamma). */
    public void shutdown(Platform p) {
    }

    public boolean matches(String query) {
        if (query.isEmpty()) {
            return true;
        }
        String q = query.toLowerCase();
        return name.toLowerCase().contains(q) || description.toLowerCase().contains(q)
                || category.label.toLowerCase().contains(q);
    }
}
