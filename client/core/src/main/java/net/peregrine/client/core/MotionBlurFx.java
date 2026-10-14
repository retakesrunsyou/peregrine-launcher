package net.peregrine.client.core;

/** Motion blur: which of the bundled effects (10% .. 90%) matches the strength picked. */
public final class MotionBlurFx {

    private MotionBlurFx() {
    }

    /** "motion_blur_50" for 50%, or null when motion blur is off. */
    public static String effect(String prefix) {
        float s = Hooks.motionBlur;
        if (s <= 0f) {
            return null;
        }
        int step = Math.max(1, Math.min(9, Math.round(s * 10f)));
        if (Hooks.motionBlurHits < 100000) {
            Hooks.motionBlurHits++;
        }
        return prefix + (step * 10);
    }

    public static boolean ours(String path) {
        return path != null && path.contains("motion_blur_");
    }
}
