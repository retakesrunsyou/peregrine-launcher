package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

public final class Fullbright extends Module {

    private final net.peregrine.client.core.settings.SliderSetting bright = add(
            new net.peregrine.client.core.settings.SliderSetting("brightness", "Brightness", 100f, 1600f, 50f, 1600f, "%.0f%%"));
    private Double saved;

    public Fullbright() {
        super("fullbright", "Fullbright", "See clearly in caves and at night", Category.UTILITY, false);
    }

    @Override
    public String warning() {
        return "Some servers don't allow this";
    }

    @Override
    public void tick(Platform p) {
        if (Math.abs(p.gamma() - bright.value / 100.0) > 1e-6) {
            if (saved == null) {
                saved = p.gamma();
            }
            p.setGamma((bright.value / 100.0));
        }
    }

    @Override
    protected void onDisable() {
        restore(platform());
    }

    @Override
    public void shutdown(Platform p) {
        restore(p);
    }

    private void restore(Platform p) {
        if (saved != null) {  // only undo what we changed
            p.setGamma(saved);
            saved = null;
        }
    }
}
