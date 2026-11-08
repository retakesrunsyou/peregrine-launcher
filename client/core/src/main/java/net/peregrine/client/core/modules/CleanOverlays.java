package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.BoolSetting;

/** Clean overlays: no pumpkin mask over the screen, and no frost from powder snow. */
public final class CleanOverlays extends Module {

    public final BoolSetting pumpkin = add(new BoolSetting("pumpkin", "No pumpkin overlay", true));
    public final BoolSetting frost = add(new BoolSetting("frost", "No powder snow frost", true));

    public CleanOverlays() {
        super("clean_overlays", "Clean overlays", "No pumpkin mask or powder snow frost over your screen",
                Category.VISUALS, false);
    }

    /** Whether to skip a full-screen overlay picture (its resource path). */
    public boolean hides(String path) {
        if (!enabled() || path == null) {
            return false;
        }
        return (pumpkin.value && path.contains("pumpkinblur")) || (frost.value && path.contains("powder_snow"));
    }
}
