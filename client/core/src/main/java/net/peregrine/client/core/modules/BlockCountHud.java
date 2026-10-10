package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** How many of the held item (like blocks for bridging) you have in total. */
public final class BlockCountHud extends LineHud {

    private final BoolSetting showName = add(new BoolSetting("name", "Show item name (held item mode)", false));
    private final net.peregrine.client.core.settings.ChoiceSetting count = add(
            new net.peregrine.client.core.settings.ChoiceSetting("count", "Count", 0, "Every block you have", "Only the held item"));

    public BlockCountHud() {
        super("block_count", "Block counter", "How many blocks you have in total (hotbar and inventory), for bridging",
                "Blocks", 0.5f, 0.74f);
    }

    @Override
    String value(Platform p) {
        if (count.index == 0) {
            int all = p.blockItemCount();
            return all <= 0 ? null : String.valueOf(all);
        }
        int n = p.heldItemCount();
        if (n <= 0) {
            return null;
        }
        return showName.value ? n + " " + p.heldItemName() : String.valueOf(n);
    }
}
