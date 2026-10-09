package net.peregrine.client.core;

/** Peregrine's colors, matching the launcher's Dusk theme. 0xAARRGGBB. */
public final class Theme {

    public static final int AMBER = 0xFFE8A33D;

    public static final int HUD_BG = 0x90171A21;
    public static final int MENU_SHADE = 0x48000000;   // dims the game behind the menu
    public static final int PANEL = 0xEC15171D;        // see-through menu window
    public static final int SIDEBAR = 0x6A0B0D11;      // the menu's category column
    public static final int TITLE_BAR = 0x400B0D11;
    public static final int CARD = 0xCC1F232C;
    public static final int CARD_HOVER = 0xDD272C37;
    public static final int ROW_HOVER = 0x12FFFFFF;    // light wash over a hovered row
    public static final int FIELD = 0xB00B0D11;        // text box background
    public static final int BORDER = 0xFF323846;
    public static final int HAIRLINE = 0x24FFFFFF;     // subtle dividers inside the window
    public static final int TEXT = 0xFFEDEEF2;
    public static final int MUTED = 0xFF959BAB;
    public static final int FAINT = 0xFF646B7B;
    public static final int GOOD = 0xFF7BC47F;
    public static final int OK = 0xFFD9C25A;
    public static final int BAD = 0xFFE07A7A;

    private Theme() {
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Blend between two colors (t = 0 gives a, 1 gives b), alpha included. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }
}
