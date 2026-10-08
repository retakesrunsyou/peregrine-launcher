package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

public final class Fullbright extends Module {

    private static final double BRIGHT = 16.0;
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
        if (p.gamma() < BRIGHT) {
            if (saved == null) {
                saved = p.gamma();
            }
            p.setGamma(BRIGHT);
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
