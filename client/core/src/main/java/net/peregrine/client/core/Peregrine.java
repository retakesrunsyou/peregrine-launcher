package net.peregrine.client.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.peregrine.client.core.modules.ArmorHud;
import net.peregrine.client.core.modules.CoordsHud;
import net.peregrine.client.core.modules.CpsHud;
import net.peregrine.client.core.modules.EffectsHud;
import net.peregrine.client.core.modules.FpsHud;
import net.peregrine.client.core.modules.Fullbright;
import net.peregrine.client.core.modules.KeystrokesHud;
import net.peregrine.client.core.modules.ToggleSprint;
import net.peregrine.client.core.modules.Zoom;

/**
 * The heart of Peregrine Client. Version adapters create it once with their
 * Platform, then forward ticks, HUD rendering, clicks and menu input to it.
 */
public final class Peregrine {

    private static Peregrine instance;

    private final Platform platform;
    private final List<Module> modules = new ArrayList<Module>();
    private final Menu menu;
    private final Deque<Long> leftClicks = new ArrayDeque<Long>();
    private final Deque<Long> rightClicks = new ArrayDeque<Long>();
    private int accent = Theme.AMBER;
    private boolean dirty;
    private int saveCountdown;

    private Peregrine(Platform platform) {
        this.platform = platform;
        // Add new features here; they show up in the menu automatically.
        modules.add(new FpsHud());
        modules.add(new CoordsHud());
        modules.add(new KeystrokesHud());
        modules.add(new CpsHud());
        modules.add(new ArmorHud());
        modules.add(new EffectsHud());
        modules.add(new ToggleSprint());
        modules.add(new Zoom());
        modules.add(new Fullbright());
        this.menu = new Menu(this);
    }

    public static Peregrine init(Platform platform) {
        instance = new Peregrine(platform);
        Config.load(instance);
        return instance;
    }

    public static Peregrine get() {
        return instance;
    }

    public Platform platform() {
        return platform;
    }

    public List<Module> modules() {
        return Collections.unmodifiableList(modules);
    }

    public Module module(String id) {
        for (Module m : modules) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public Menu menu() {
        return menu;
    }

    public int accent() {
        return accent;
    }

    public void setAccent(int argb) {
        accent = argb | 0xFF000000;
        markDirty();
    }

    /** Settings changed; they're saved a moment later (so dragging doesn't spam the disk). */
    public void markDirty() {
        dirty = true;
        saveCountdown = 20;
    }

    // ---- called by the version adapter

    public void tick() {
        if (platform.inWorld()) {
            for (Module m : modules) {
                if (m.enabled()) {
                    m.tick(platform);
                }
            }
        }
        if (dirty && --saveCountdown <= 0) {
            dirty = false;
            Config.save(this);
        }
    }

    public void renderHud(Draw d) {
        if (!platform.inWorld()) {
            return;
        }
        for (Module m : modules) {
            if (m.enabled() && m instanceof HudModule) {
                ((HudModule) m).renderAt(d, platform);
            }
        }
    }

    /** button: 0 = left, 1 = right. */
    public void onMouseButton(int button) {
        Deque<Long> q = button == 0 ? leftClicks : button == 1 ? rightClicks : null;
        if (q != null) {
            q.addLast(System.currentTimeMillis());
        }
    }

    public int cps(int button) {
        Deque<Long> q = button == 0 ? leftClicks : rightClicks;
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.removeFirst();
        }
        return q.size();
    }

    /** How much to divide the field of view by (1 = no zoom). */
    public double zoomDivisor() {
        Zoom zoom = (Zoom) module("zoom");
        return zoom != null ? zoom.divisor(platform) : 1.0;
    }

    public void shutdown() {
        for (Module m : modules) {
            m.shutdown(platform);
        }
        Config.save(this);
    }
}
