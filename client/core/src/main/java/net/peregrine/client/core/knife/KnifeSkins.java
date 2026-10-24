package net.peregrine.client.core.knife;

/** The knife finishes: their names, pictures (textures/knife/) and how metallic the blade is. */
public final class KnifeSkins {

    public static final String[] NAMES = {"Steel", "Fade", "Ruby", "Sapphire", "Emerald", "Black pearl", "Tiger",
        "Marble", "Heat treated", "Gold", "Night", "Ocean", "Your own colors"};
    private static final String[] FILES = {"steel", "fade", "ruby", "sapphire", "emerald", "black_pearl", "tiger",
        "marble", "heat_treated", "gold", "night", "ocean", "custom"};
    private static final float[] METAL = {1f, 0.8f, 0.85f, 0.85f, 0.85f, 0.85f, 0.85f, 0.8f, 0.95f, 1f, 0.35f, 0.8f, 0.9f};

    private KnifeSkins() {
    }

    private static int at(int i) {
        return Math.max(0, Math.min(NAMES.length - 1, i));
    }

    public static String texture(int i) {
        return "peregrine-client:textures/knife/" + FILES[at(i)] + ".png";
    }

    public static float metal(int i) {
        return METAL[at(i)];
    }

    public static boolean isCustom(int i) {
        return at(i) == NAMES.length - 1;
    }

    /** Every picture the knives use (to load ahead of time). */
    public static String[] textures() {
        String[] r = new String[FILES.length + 1];
        for (int i = 0; i < FILES.length; i++) {
            r[i] = texture(i);
        }
        r[FILES.length] = "peregrine-client:textures/knife/trail.png";
        return r;
    }
}
