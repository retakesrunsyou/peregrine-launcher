package net.peregrine.client.core;

/** A feature players can turn on and off from the in-game menu. */
public abstract class Module {

    public enum Category {
        HUD("HUD"), UTILITY("Utility"), VISUALS("Visuals"), PERFORMANCE("Performance");

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
    /** A key that switches this feature on and off while playing (GLFW code), or Keys.NONE. */
    public int key = Keys.NONE;

    protected Module(String id, String name, String description, Category category, boolean defaultOn) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultOn = defaultOn;
        this.enabled = defaultOn;
    }

    private final java.util.List<net.peregrine.client.core.settings.Setting> settings =
            new java.util.ArrayList<net.peregrine.client.core.settings.Setting>();

    /** Options shown on this feature's settings page (the gear in the menu). */
    public java.util.List<net.peregrine.client.core.settings.Setting> settings() {
        return settings;
    }

    protected <T extends net.peregrine.client.core.settings.Setting> T add(T setting) {
        settings.add(setting);
        return setting;
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
