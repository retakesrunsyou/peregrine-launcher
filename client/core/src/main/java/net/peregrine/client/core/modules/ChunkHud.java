package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** Which chunk you're in, and where inside it. */
public final class ChunkHud extends LineHud {

    private final BoolSetting inside = add(new BoolSetting("inside", "Position inside the chunk", true));

    public ChunkHud() {
        super("chunk", "Chunk position", "Which chunk you're in, and where inside it", "Chunk", 0f, 0.73f);
    }

    @Override
    String value(Platform p) {
        int bx = (int) Math.floor(p.x());
        int bz = (int) Math.floor(p.z());
        String s = (bx >> 4) + ", " + (bz >> 4);
        return inside.value ? s + "  [" + (bx & 15) + ", " + (bz & 15) + "]" : s;
    }
}
