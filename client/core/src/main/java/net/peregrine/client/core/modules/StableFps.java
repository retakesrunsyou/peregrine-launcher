package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Stable FPS: when a busy server or a big build drags FPS under your target for a
 * few seconds, it eases off the costliest settings one step at a time (particles,
 * then entity distance, then render distance), and puts them back once FPS has
 * room again. Your own choices are what it goes back to; nothing is saved lowered.
 */
public final class StableFps extends Module {

    static final int[] TARGETS = {30, 60, 90, 120, 144, 240};
    static final int SLOW_TICKS = 100;    // 5 s under target before stepping down
    static final int FAST_TICKS = 400;    // 20 s with room to spare before stepping up
    static final int SETTLE_TICKS = 100;  // wait after a change for FPS to settle

    private final ChoiceSetting target = add(new ChoiceSetting("target", "Keep FPS above", 1,
            "30", "60", "90", "120", "144", "240"));
    private final BoolSetting distance = add(new BoolSetting("distance", "Lower render distance if needed", true));
    private final SliderSetting floor = add(new SliderSetting("floor", "Lowest render distance", 2f, 16f, 1f, 6f, "%.0f chunks"));

    private int level;          // 0 = your settings; each step lowers one more thing
    private int origRender = -1;
    private double origEntity;
    private int origParticles;
    private int setRender = -1; // what we last set, to notice changes made by the player
    private int slow, fast, settle;

    public StableFps() {
        super("stable_fps", "Stable FPS",
                "Eases off particles, entity and render distance when FPS dips, and back when it's smooth",
                Category.PERFORMANCE, true);
    }

    public int level() {
        return level;
    }

    @Override
    public void tick(Platform p) {
        if (!p.showing(Platform.Screen.NONE)) {
            slow = fast = 0;  // menus and loading screens don't count
            return;
        }
        if (level > 0 && setRender >= 0 && p.renderDistance() != setRender) {
            // The player changed render distance themselves: that's the new normal.
            origRender = p.renderDistance();
            level = 0;
            restore(p, false);
        }
        if (settle > 0) {
            settle--;
            return;
        }
        int fps = p.fps();
        if (fps <= 0) {
            return;
        }
        int want = TARGETS[target.index];
        slow = fps < want * 0.85 ? slow + 1 : 0;
        fast = level > 0 && fps > want * 1.3 ? fast + 1 : 0;
        if (slow >= SLOW_TICKS) {
            slow = 0;
            if (level == 0) {
                origRender = p.renderDistance();
                origEntity = p.entityDistance();
                origParticles = p.particleLevel();
            }
            if (level < maxLevel()) {
                level++;
                apply(p);
                settle = SETTLE_TICKS;
            }
        } else if (fast >= FAST_TICKS) {
            fast = 0;
            level--;
            apply(p);
            settle = SETTLE_TICKS;
        }
    }

    private int maxLevel() {
        int steps = 2;
        if (distance.value && origRender > 0) {
            steps += Math.max(0, (origRender - Math.round(floor.value) + 1) / 2);
        }
        return steps;
    }

    private void apply(Platform p) {
        if (level <= 0) {
            restore(p, true);
            return;
        }
        p.setParticleLevel(Math.max(origParticles, 2));
        p.setEntityDistance(level >= 2 ? Math.min(origEntity, Math.max(0.5, origEntity * 0.6)) : origEntity);
        if (origRender > 0) {
            int r = origRender;
            if (level >= 3) {
                r = Math.max(Math.min(origRender, Math.round(floor.value)), origRender - 2 * (level - 2));
            }
            if (p.renderDistance() != r) {
                p.setRenderDistance(r);
            }
            setRender = r;
        }
    }

    private void restore(Platform p, boolean render) {
        p.setParticleLevel(origParticles);
        p.setEntityDistance(origEntity);
        if (render && origRender > 0 && p.renderDistance() != origRender) {
            p.setRenderDistance(origRender);
        }
        level = 0;
        setRender = -1;
        origRender = -1;
    }

    @Override
    protected void onDisable() {
        if (level > 0) {
            level = 0;
            restore(platform(), true);
        }
    }

    @Override
    public void shutdown(Platform p) {
        if (level > 0) {
            level = 0;
            restore(p, true);
        }
    }
}
