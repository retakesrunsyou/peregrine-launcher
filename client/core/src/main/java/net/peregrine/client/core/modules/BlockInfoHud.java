package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

/** The name of the block you're looking at. */
public final class BlockInfoHud extends LineHud {

    public BlockInfoHud() {
        super("block_info", "Block info", "The name of the block you're looking at", "Looking at", 0.5f, 0.06f);
    }

    @Override
    String value(Platform p) {
        String b = p.lookedAtBlock();
        return b.isEmpty() ? null : b;
    }
}
