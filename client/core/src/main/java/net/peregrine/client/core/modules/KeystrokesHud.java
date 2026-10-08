package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Platform.Key;
import net.peregrine.client.core.Theme;

public final class KeystrokesHud extends HudModule {

    private static final int S = 22;   // key size
    private static final int GAP = 2;

    public KeystrokesHud() {
        super("keystrokes", "Keystrokes", "Shows WASD, mouse buttons and jump as you press them",
                false, 0f, 1f);
    }

    @Override
    public int width(Draw d, Platform p) {
        return S * 3 + GAP * 2;
    }

    @Override
    public int height(Draw d, Platform p) {
        return S * 3 + GAP * 3 + 12;
    }

    private void key(Draw d, int x, int y, int w, int h, String label, boolean down) {
        int accent = Peregrine.get().accent();
        d.rect(x, y, w, h, down ? Theme.withAlpha(accent, 0xD0) : Theme.HUD_BG);
        int tx = x + (w - d.width(label)) / 2;
        int ty = y + (h - d.lineHeight()) / 2 + 1;
        d.text(label, tx, ty, down ? 0xFF15171C : Theme.TEXT, !down);
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        int col2 = x + S + GAP;
        int col3 = x + (S + GAP) * 2;
        key(d, col2, y, S, S, "W", p.isDown(Key.FORWARD));
        int row2 = y + S + GAP;
        key(d, x, row2, S, S, "A", p.isDown(Key.LEFT));
        key(d, col2, row2, S, S, "S", p.isDown(Key.BACK));
        key(d, col3, row2, S, S, "D", p.isDown(Key.RIGHT));
        int row3 = row2 + S + GAP;
        int half = (S * 3 + GAP) / 2;
        Peregrine pc = Peregrine.get();
        key(d, x, row3, half, S, "LMB " + pc.cps(0), p.isDown(Key.ATTACK));
        key(d, x + half + GAP, row3, half, S, "RMB " + pc.cps(1), p.isDown(Key.USE));
        int row4 = row3 + S + GAP;
        key(d, x, row4, S * 3 + GAP * 2, 12, "", p.isDown(Key.JUMP));
        d.rect(x + S, row4 + 5, S + GAP * 2, 2, p.isDown(Key.JUMP) ? 0xFF15171C : Theme.TEXT);
    }
}
