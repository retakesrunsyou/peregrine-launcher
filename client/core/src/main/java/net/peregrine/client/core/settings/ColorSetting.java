package net.peregrine.client.core.settings;

/**
 * Any colour: picked from a colour picker, with opacity, or RGB (a rainbow that cycles).
 * Saved as "aarrggbb", plus ";rgb" when the rainbow is on.
 */
public final class ColorSetting extends Setting {

    private final int def;
    private final boolean defChroma;
    /** Whether the colour can be see-through (the picker shows an opacity bar). */
    public final boolean alpha;
    /** Colours an older version offered as a list: an old saved index maps to one of these. */
    private final int[] legacy;
    public int color;
    public boolean chroma;
    /** Which entry of the old list was loaded (-1: none), for features that need to know. */
    public int legacyIndex = -1;
    /** The accent colour, for old saved choices of "Accent". */
    public static java.util.function.IntSupplier accent = () -> 0xFFE8A33D;

    public ColorSetting(String id, String label, int def, boolean alpha, int... legacy) {
        this(id, label, def, false, alpha, legacy);
    }

    public ColorSetting(String id, String label, int def, boolean defChroma, boolean alpha, int... legacy) {
        super(id, label);
        this.def = def;
        this.defChroma = defChroma;
        this.alpha = alpha;
        this.legacy = legacy;
        this.color = def;
        this.chroma = defChroma;
    }

    /** The colour to draw with right now (the rainbow moves when RGB is on). */
    public int value() {
        if (!chroma) {
            return color;
        }
        double hue = (System.nanoTime() / 1e9) / 4.0;
        return (color & 0xFF000000) | rainbow(hue);
    }

    /** Like value(), but each thing drawn can be at its own place in the rainbow. */
    public int value(double offset) {
        if (!chroma) {
            return color;
        }
        return (color & 0xFF000000) | rainbow((System.nanoTime() / 1e9) / 4.0 + offset);
    }

    static int rainbow(double hue) {
        float h = (float) (hue - Math.floor(hue));
        return java.awt.Color.HSBtoRGB(h, 0.75f, 1f) & 0xFFFFFF;
    }

    public String hex() {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    @Override
    public boolean isDefault() {
        return color == def && chroma == defChroma;
    }

    @Override
    public Object save() {
        return String.format("%08x", color) + (chroma ? ";rgb" : "");
    }

    @Override
    public void load(String v) {
        if (v == null) {
            return;
        }
        v = v.trim();
        try {
            if (v.matches("\\d{1,2}") && legacy.length > 0) {   // an index into the old list of colours
                int i = Integer.parseInt(v);
                if (i >= 0 && i < legacy.length) {
                    legacyIndex = i;
                    int c = legacy[i];
                    if (c == -2) {
                        chroma = true;   // the old "Rainbow" choice
                    } else if (c == -1) {
                        color = accent.getAsInt() | 0xFF000000;
                        chroma = false;
                    } else if (c != 0) {
                        color = c | 0xFF000000;
                        chroma = false;
                    }
                }
                return;
            }
            String[] parts = v.split(";");
            long c = Long.parseLong(parts[0].replace("#", ""), 16);
            if (parts[0].replace("#", "").length() <= 6) {
                c |= 0xFF000000L;
            }
            color = (int) c;
            chroma = parts.length > 1 && parts[1].equals("rgb");
        } catch (NumberFormatException e) {
            // keep what it was
        }
    }

    @Override
    public void reset() {
        color = def;
        chroma = defChroma;
    }
}
