package net.peregrine.client.core.settings;

/** A small grid of pixels you draw by clicking (your own crosshair). Saved as a string of 0s and 1s. */
public final class PixelGridSetting extends Setting {

    public static final int N = 15;
    private final boolean[] def = new boolean[N * N];
    public final boolean[] on = new boolean[N * N];

    public PixelGridSetting(String id, String label) {
        super(id, label);
        // starts as a small plus with a gap, a good base to edit
        for (int i = 2; i <= 5; i++) {
            set(def, 7, i);
            set(def, 7, N - 1 - i);
            set(def, i, 7);
            set(def, N - 1 - i, 7);
        }
        System.arraycopy(def, 0, on, 0, on.length);
    }

    private static void set(boolean[] g, int x, int y) {
        g[y * N + x] = true;
    }

    public boolean get(int x, int y) {
        return on[y * N + x];
    }

    public void toggle(int x, int y) {
        on[y * N + x] = !on[y * N + x];
    }

    public void clear() {
        java.util.Arrays.fill(on, false);
    }

    @Override
    public Object save() {
        StringBuilder b = new StringBuilder(on.length);
        for (boolean v : on) {
            b.append(v ? '1' : '0');
        }
        return b.toString();
    }

    @Override
    public void load(String v) {
        if (v != null && v.length() == on.length && v.matches("[01]+")) {
            for (int i = 0; i < on.length; i++) {
                on[i] = v.charAt(i) == '1';
            }
        }
    }

    @Override
    public void reset() {
        System.arraycopy(def, 0, on, 0, on.length);
    }

    @Override
    public boolean isDefault() {
        return java.util.Arrays.equals(on, def);
    }
}
