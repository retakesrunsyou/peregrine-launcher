package net.peregrine.client.core;

/**
 * Switches that version adapters' rendering hooks read on hot paths (every
 * block of every chunk, every item, every frame). Plain fields, no lookups:
 * modules set them, hooks just read them.
 */
public final class Hooks {

    private Hooks() {
    }

    // ---- Anti base leak
    /** Every block uses the same random texture variant and rotation. */
    public static volatile boolean fixedRotation;
    /** Flowers, grass and similar plants sit in the middle of their block. */
    public static volatile boolean fixedOffset;
    /** Bedrock is drawn as diamond blocks. */
    public static volatile boolean diamondBedrock;

    /** The seed every block uses while fixedRotation is on. */
    public static final long FIXED_SEED = 0L;

    // ---- Visuals
    public static volatile boolean itemPhysics;
    public static volatile boolean customCrosshair;

    // ---- Freelook: the camera's own direction while looking around
    public static volatile boolean freelook;
    public static volatile float camYaw;
    public static volatile float camPitch;

    // ---- counters, so the self-test can see the hooks run (plain ints: racy, but
    // cheap on hot paths; hooks stop counting at 1000)
    public static int seedHits;
    public static int offsetHits;
    public static int modelHits;
    public static int bedrockSwaps;
    public static int itemPhysicsHits;
    public static int crosshairDraws;
    public static int cameraHits;

    /** Mouse movement while freelook is on turns the camera, not the player (same maths as Minecraft). */
    public static void turnCamera(double yaw, double pitch) {
        camYaw += (float) yaw * 0.15f;
        camPitch = Math.max(-90f, Math.min(90f, camPitch + (float) pitch * 0.15f));
    }

    /** The settled height of a resting item, used by item physics. */
    public static float bob(float ageInTicks, float bobOffset) {
        return (float) Math.sin(ageInTicks / 10.0F + bobOffset) * 0.1F + 0.1F;
    }
}
