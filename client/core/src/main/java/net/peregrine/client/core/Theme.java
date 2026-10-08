package net.peregrine.client.core;

/** Peregrine's colors, matching the launcher's Dusk theme. 0xAARRGGBB. */
public final class Theme {

    public static final int AMBER = 0xFFE8A33D;

    public static final int HUD_BG = 0x90171A21;
    public static final int MENU_SHADE = 0x70000000;   // dims the game behind the menu
    public static final int PANEL = 0xE6171A21;        // see-through menu panel
    public static final int CARD = 0xCC1F232C;
    public static final int CARD_HOVER = 0xDD272C37;
    public static final int BORDER = 0xFF323846;
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
}
