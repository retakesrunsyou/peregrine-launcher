package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** How many of the held item (like blocks for bridging) you have in total. */
public final class BlockCountHud extends LineHud {

    private final BoolSetting showName = add(new BoolSetting("name", "Show item name", false));

    public BlockCountHud() {
        super("block_count", "Block counter", "How many of the item in your hand you have in total, for bridging",
                "Held", 0.5f, 0.74f);
    }

    @Override
    String value(Platform p) {
        int n = p.heldItemCount();
        if (n <= 0) {
            return null;
        }
        return showName.value ? n + " " + p.heldItemName() : String.valueOf(n);
    }
}
