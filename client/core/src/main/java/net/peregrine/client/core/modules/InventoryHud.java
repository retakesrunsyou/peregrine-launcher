package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Inventory HUD: everything in your inventory, on screen, wherever you drag it. Item counts
 * and durability bars like in the real inventory; empty slots can be hidden for a slimmer look.
 */
public final class InventoryHud extends HudModule {

    static final int SLOT = 18, PAD = 4;

    private final ChoiceSetting rows = add(new ChoiceSetting("rows", "Show", 0, "Inventory (3 rows)", "Inventory and hotbar",
            "Only rows with items"));
    private final BoolSetting slots = add(new BoolSetting("slots", "Draw the slots", true));
    private final BoolSetting counts = add(new BoolSetting("counts", "Item counts", true));
    private final BoolSetting bars = add(new BoolSetting("bars", "Durability bars", true));
    private final BoolSetting hideEmpty = add(new BoolSetting("hide_empty", "Hide when the inventory is empty", false));

    public InventoryHud() {
        super("inventory_hud", "Inventory HUD", "Your whole inventory on screen, anywhere you like", false, 0.5f, 0.05f);
        scale = 0.75f;
    }

    /** The rows to show: each is the slot index of its first slot. */
    private int[] shownRows(Platform p) {
        List<Object> inv = p.inventory();
        if (rows.index == 1) {
            return new int[] {9, 18, 27, 0};
        }
        if (rows.index == 2) {
            int n = 0;
            int[] out = new int[3];
            for (int r = 9; r < 36; r += 9) {
                for (int i = r; i < r + 9 && i < inv.size(); i++) {
                    if (inv.get(i) != null) {
                        out[n++] = r;
                        break;
                    }
                }
            }
            return java.util.Arrays.copyOf(out, Math.max(1, n));
        }
        return new int[] {9, 18, 27};
    }

    @Override
    public boolean hasContent(Platform p) {
        if (!hideEmpty.value) {
            return true;
        }
        List<Object> inv = p.inventory();
        for (int i = 9; i < inv.size(); i++) {
            if (inv.get(i) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int width(Draw d, Platform p) {
        return 9 * SLOT + PAD * 2;
    }

    @Override
    public int height(Draw d, Platform p) {
        int[] r = shownRows(p);
        boolean hotbar = r.length == 4;
        return r.length * SLOT + PAD * 2 + (hotbar ? 3 : 0);
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<Object> inv = p.inventory();
        int[] r = shownRows(p);
        panel(d, x, y, width(d, p), height(d, p));
        int accent = Peregrine.get().accent();
        for (int row = 0; row < r.length; row++) {
            int sy = y + PAD + row * SLOT + (row == 3 ? 3 : 0);
            if (row == 3) {  // a thin accent line above the hotbar
                d.rect(x + PAD, sy - 2, 9 * SLOT, 1, Theme.withAlpha(accent, 0x70));
            }
            for (int col = 0; col < 9; col++) {
                int i = r[row] + col;
                int sx = x + PAD + col * SLOT;
                if (slots.value) {
                    d.roundRect(sx + 1, sy + 1, SLOT - 2, SLOT - 2, 0x30FFFFFF);
                }
                Object stack = i < inv.size() ? inv.get(i) : null;
                if (stack == null) {
                    continue;
                }
                d.item(stack, sx + 1, sy + 1);
                int[] info = p.stackInfo(stack);
                if (bars.value && info[2] > 0 && info[1] > 0) {
                    float left = 1f - info[1] / (float) info[2];
                    int bw = Math.round(13 * left);
                    int c = left > 0.5f ? Theme.GOOD : left > 0.2f ? Theme.OK : Theme.BAD;
                    d.rect(sx + 3, sy + 14, 13, 2, 0xFF000000);
                    d.rect(sx + 3, sy + 14, bw, 1, c);
                }
                if (counts.value && info[0] > 1) {
                    String n = String.valueOf(info[0]);
                    d.text(n, sx + 18 - d.width(n), sy + 10, Theme.TEXT, true);
                }
            }
        }
    }
}
