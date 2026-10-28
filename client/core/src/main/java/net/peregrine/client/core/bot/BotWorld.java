package net.peregrine.client.core.bot;

/**
 * What the area miner can see and do in the world (each Minecraft version implements it). All
 * positions are block coordinates; actions are called once per game tick and say whether
 * they're finished.
 */
public interface BotWorld {

    /** The block your feet are in: {x, y, z}. */
    int[] feet();

    /** Your exact position {x, y, z}. */
    double[] pos();

    boolean onGround();

    /** Something you can stand on or have to mine (not air, not a liquid, not a plant). */
    boolean solid(int x, int y, int z);

    /** Water or lava (still or flowing). */
    boolean fluid(int x, int y, int z);

    /** Lava. */
    boolean lava(int x, int y, int z);

    /** Can it be mined at all (not bedrock, barriers...)? */
    boolean breakable(int x, int y, int z);

    /** Something worth mining there (solid or a plant; not air or liquid). */
    boolean present(int x, int y, int z);

    /** Looks at a point (smoothly is fine). */
    void look(double x, double y, double z);

    /** Holds or lets go of the walk-forward key (no sprinting, no sneaking). */
    void walk(boolean forward);

    /** Keeps mining that block this tick; true once it's gone. */
    boolean mine(int x, int y, int z);

    /** Puts a filler block (off hand first, then hotbar) into that space; true once it's there. */
    boolean place(int x, int y, int z);

    /** Whether there's anything left to fill holes and liquids with. */
    boolean hasFiller();

    /** Hunger points (0..20). */
    int food();

    /** Eats something from the hotbar (call every tick until it says it's done). */
    boolean eat();

    boolean hasFood();

    /** The tool in hand can keep going (not about to break). */
    boolean toolOk();

    /** No room left for what's mined. */
    boolean inventoryFull();

    /** Lets go of every key the bot holds. */
    void release();
}
