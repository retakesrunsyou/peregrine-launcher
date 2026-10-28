package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.bot.AreaMiner;
import net.peregrine.client.core.bot.BotWorld;
import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Area miner (singleplayer): mark two corners, stand on a top corner, turn it on, and it clears
 * the box between them. Turn it off (or press its key) to stop at any time.
 */
public final class Miner extends Module {

    private int[] cornerA, cornerB;
    private AreaMiner miner;
    private String note;
    private long noteAt;

    public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Tool", 0,
            "1x1 (a normal pickaxe)", "3x3 (a tool that mines 3x3)"));
    public final ActionSetting first = add(new ActionSetting("corner1", "Corner 1: the block you stand on",
            () -> cornerA == null ? "Set" : text(cornerA), () -> cornerA = here(Peregrine.get().platform(), false)));
    public final ActionSetting second = add(new ActionSetting("corner2", "Corner 2: the block you look at (or stand on)",
            () -> cornerB == null ? "Set" : text(cornerB), () -> cornerB = here(Peregrine.get().platform(), true)));

    public Miner() {
        super("area_miner", "Area miner", "Singleplayer: mark two corners and it mines out the box between them, 1x1 or 3x3",
                Category.UTILITY, false);
    }

    @Override
    public String warning() {
        return "Singleplayer only. Keep some blocks (cobblestone, dirt...) in your off hand for filling lava and holes";
    }

    private static String text(int[] c) {
        return c[0] + ", " + c[1] + ", " + c[2];
    }

    private static int[] here(Platform p, boolean look) {
        if (look) {
            int[] l = p.lookedAtPos();
            if (l != null) {
                return l;
            }
        }
        return new int[] {(int) Math.floor(p.x()), (int) Math.floor(p.y()) - 1, (int) Math.floor(p.z())};
    }

    private void say(String s) {
        note = s;
        noteAt = System.currentTimeMillis();
    }

    @Override
    protected void onEnable() {
        miner = null;
        Platform p = Peregrine.get().platform();
        if (!(p.botWorld() instanceof BotWorld)) {
            say("Area miner only works in singleplayer");
            return;
        }
        if (cornerA == null || cornerB == null) {
            say("Set both corners first (Area miner settings)");
            return;
        }
        miner = new AreaMiner(cornerA, cornerB, mode.index);
        say("Area miner started: " + miner.total() + " blocks");
    }

    @Override
    protected void onDisable() {
        Object w = Peregrine.get() == null ? null : Peregrine.get().platform().botWorld();
        if (w instanceof BotWorld) {
            ((BotWorld) w).release();
        }
        miner = null;
        note = null;
    }

    @Override
    public void tick(Platform p) {
        if (miner == null) {
            // finished or couldn't start: show why for a few seconds, then switch off
            if (note == null || System.currentTimeMillis() - noteAt > 8000) {
                setEnabled(false);
                Peregrine.get().markDirty();
            }
            return;
        }
        Object w = p.botWorld();
        if (!(w instanceof BotWorld)) {
            say("Area miner only works in singleplayer");
            miner = null;
            return;
        }
        miner.tick((BotWorld) w);
        if (miner.done()) {
            say(miner.stopped() != null ? miner.stopped() : "Area miner: all done!");
            miner = null;
            Object bw = p.botWorld();
            if (bw instanceof BotWorld) {
                ((BotWorld) bw).release();
            }
        }
    }

    @Override
    public void renderWorld(Draw d, Platform p) {
        String s = miner != null ? "Area miner: " + miner.progress()
                : note != null && System.currentTimeMillis() - noteAt < 8000 ? note : null;
        if (s == null) {
            return;
        }
        int w = d.width(s) + 16;
        d.roundRect(6, 6, w, 16, 5, 0xB0101216);
        d.text(s, 14, 10, Theme.TEXT, false);
    }
}
