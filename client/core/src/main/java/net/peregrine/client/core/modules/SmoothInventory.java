package net.peregrine.client.core.modules;

import net.peregrine.client.core.InventoryFx;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Inventories and chests open and close smoothly, the slot highlight glides, moved items fly. */
public final class SmoothInventory extends Module {

    private final BoolSetting animate = add(new BoolSetting("animate", "Open and close animation", true));
    private final SliderSetting speed = add(new SliderSetting("ms", "Animation length", 60f, 300f, 10f, 140f, "%.0f ms"));
    private final BoolSetting glide = add(new BoolSetting("glide", "Slot highlight glides", true));
    private final BoolSetting fly = add(new BoolSetting("fly", "Moved items fly to their slot", true));
    private final BoolSetting clear = add(new BoolSetting("clear", "See-through inventory", false));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "See-through darkness", 0f, 100f, 5f, 45f, "%.0f%%"));

    public SmoothInventory() {
        super("smooth_inventory", "Smooth inventory",
                "Inventories open smoothly, the highlight glides, items fly to their slot; optional see-through look",
                Category.VISUALS, true);
    }

    private void apply() {
        boolean on = enabled();
        InventoryFx.animate = on && animate.value;
        InventoryFx.animMs = Math.round(speed.value);
        InventoryFx.glide = on && glide.value;
        InventoryFx.flyItems = on && fly.value;
        InventoryFx.seeThrough = on && clear.value;
        InventoryFx.panelAlpha = opacity.value / 100f;
    }

    @Override
    public void tick(Platform p) {
        apply();
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        InventoryFx.animate = false;
        InventoryFx.glide = false;
        InventoryFx.flyItems = false;
        InventoryFx.seeThrough = false;
    }
}
