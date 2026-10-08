package net.peregrine.client.core;

/**
 * A module that switches one of Minecraft's own settings. Turning it off puts
 * the setting back to Minecraft's default.
 */
public class OptionModule extends Module {

    private final Platform.Option option;
    private boolean applied;

    public OptionModule(String id, String name, String description, Category category, Platform.Option option) {
        super(id, name, description, category, false);
        this.option = option;
    }

    @Override
    public void tick(Platform p) {
        if (!applied) {  // also covers modules switched on in a saved config
            p.setOption(option, true);
            applied = true;
        }
    }

    @Override
    protected void onDisable() {
        if (applied) {
            platform().setOption(option, false);
            applied = false;
        }
    }
}
