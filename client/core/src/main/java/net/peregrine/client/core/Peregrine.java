package net.peregrine.client.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.peregrine.client.core.modules.ArmorHud;
import net.peregrine.client.core.modules.ParticleControl;
import net.peregrine.client.core.modules.Hitboxes;
import net.peregrine.client.core.modules.OwnNameTag;
import net.peregrine.client.core.modules.ScoreboardSize;
import net.peregrine.client.core.modules.ChatSize;
import net.peregrine.client.core.modules.LowFire;
import net.peregrine.client.core.modules.SmallTotem;
import net.peregrine.client.core.modules.InventoryTweaks;
import net.peregrine.client.core.modules.OldAnimations;
import net.peregrine.client.core.modules.StableFps;
import net.peregrine.client.core.modules.AntiLeak;
import net.peregrine.client.core.modules.Crosshair;
import net.peregrine.client.core.modules.Freelook;
import net.peregrine.client.core.modules.HitColor;
import net.peregrine.client.core.modules.ItemPhysics;
import net.peregrine.client.core.modules.ReachHud;
import net.peregrine.client.core.modules.ComboHud;
import net.peregrine.client.core.modules.TargetHud;
import net.peregrine.client.core.modules.BlockInfoHud;
import net.peregrine.client.core.modules.BlockCountHud;
import net.peregrine.client.core.modules.PlayersHud;
import net.peregrine.client.core.modules.RotationHud;
import net.peregrine.client.core.modules.StopwatchHud;
import net.peregrine.client.core.modules.XpHud;
import net.peregrine.client.core.modules.ChunkHud;
import net.peregrine.client.core.modules.LightHud;
import net.peregrine.client.core.modules.DurabilityAlertHud;
import net.peregrine.client.core.modules.HealthHud;
import net.peregrine.client.core.modules.ItemCountHud;
import net.peregrine.client.core.modules.NetherCoordsHud;
import net.peregrine.client.core.modules.SessionHud;
import net.peregrine.client.core.modules.BiomeHud;
import net.peregrine.client.core.modules.CompassHud;
import net.peregrine.client.core.modules.Flag;
import net.peregrine.client.core.modules.FoodHud;
import net.peregrine.client.core.modules.MemoryHud;
import net.peregrine.client.core.modules.PacksHud;
import net.peregrine.client.core.modules.PotionCountHud;
import net.peregrine.client.core.modules.ClockHud;
import net.peregrine.client.core.modules.DayHud;
import net.peregrine.client.core.modules.MainMenu;
import net.peregrine.client.core.modules.PingHud;
import net.peregrine.client.core.modules.ServerHud;
import net.peregrine.client.core.modules.SpeedHud;
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
    private final TitleMenu titleMenu;
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
        modules.add(new ClockHud());
        modules.add(new PingHud());
        modules.add(new ServerHud());
        modules.add(new SpeedHud());
        modules.add(new DayHud());
        modules.add(new MainMenu());
        // Batch 2
        modules.add(new CompassHud());
        modules.add(new BiomeHud());
        modules.add(new MemoryHud());
        modules.add(new FoodHud());
        modules.add(new PotionCountHud());
        modules.add(new PacksHud());
        modules.add(new OptionModule("toggle_sneak", "Toggle sneak",
                "Press sneak once to stay crouched, again to stand", Module.Category.UTILITY, Platform.Option.TOGGLE_SNEAK));
        modules.add(new OptionModule("static_fov", "Static FOV",
                "Sprinting and speed effects don't stretch your view", Module.Category.VISUALS, Platform.Option.STATIC_FOV));
        modules.add(new OptionModule("steady_camera", "Steady camera",
                "No view bobbing as you walk", Module.Category.VISUALS, Platform.Option.STEADY_CAMERA));
        modules.add(new OptionModule("no_menu_blur", "No menu blur",
                "Menus don't blur the game behind them", Module.Category.VISUALS, Platform.Option.NO_MENU_BLUR));
        modules.add(new ParticleControl());
        modules.add(new OptionModule("chunk_borders", "Chunk borders",
                "Show chunk boundary lines", Module.Category.VISUALS, Platform.Option.CHUNK_BORDERS));
        modules.add(new Hitboxes());
        modules.add(new OptionModule("no_hurt_cam", "No hurt cam",
                "The screen doesn't shake when you take damage", Module.Category.VISUALS, Platform.Option.NO_HURT_CAM));
        // Batch 3
        modules.add(new HealthHud());
        modules.add(new NetherCoordsHud());
        modules.add(new SessionHud());
        modules.add(new ItemCountHud("totems", "Totem counter", "How many Totems of Undying you're carrying",
                "totem", "Totems", 1f, 0.62f));
        modules.add(new ItemCountHud("arrows", "Arrow counter", "Arrows left in your inventory",
                "arrow", "Arrows", 1f, 0.68f));
        modules.add(new DurabilityAlertHud());
        modules.add(new Flag("clean_edges", "Clean edges", "Remove the dark vignette around the screen",
                Module.Category.VISUALS));
        modules.add(new Flag("clear_weather", "Clear weather", "No rain or snow on your screen (the world isn't changed)",
                Module.Category.VISUALS));
        // Batch 4
        modules.add(new ReachHud());
        modules.add(new ComboHud());
        modules.add(new TargetHud());
        modules.add(new BlockInfoHud());
        modules.add(new BlockCountHud());
        modules.add(new PlayersHud());
        modules.add(new RotationHud());
        modules.add(new StopwatchHud());
        modules.add(new XpHud());
        modules.add(new ChunkHud());
        modules.add(new LightHud());
        // Performance
        modules.add(new StableFps());
        // Batch 5: render hooks
        modules.add(new AntiLeak());
        modules.add(new Crosshair());
        modules.add(new Freelook());
        modules.add(new HitColor());
        modules.add(new ItemPhysics());
        // Batch 6
        modules.add(new OwnNameTag());
        modules.add(new ScoreboardSize());
        modules.add(new ChatSize());
        modules.add(new LowFire());
        modules.add(new SmallTotem());
        modules.add(new net.peregrine.client.core.modules.CleanMenus());
        modules.add(new net.peregrine.client.core.modules.Fog());
        modules.add(new net.peregrine.client.core.modules.SwingSpeed());
        modules.add(new net.peregrine.client.core.modules.BlockOutline());
        modules.add(new net.peregrine.client.core.modules.OreOutline());
        modules.add(new net.peregrine.client.core.modules.StaticSky());
        modules.add(new net.peregrine.client.core.modules.ChunkFade());
        modules.add(new net.peregrine.client.core.modules.SoundFilters());
        modules.add(new net.peregrine.client.core.modules.AntiAfk());
        modules.add(new InventoryTweaks());
        modules.add(new OldAnimations());
        this.menu = new Menu(this);
        this.titleMenu = new TitleMenu(this);
    }

    public static Peregrine init(Platform platform) {
        instance = new Peregrine(platform);
        Config.load(instance);
        ((net.peregrine.client.core.modules.CleanMenus) instance.module("clean_menus")).applyQuietly();
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

    /**
     * Where settings are saved. The launcher passes one shared file for every
     * instance (-Dperegrine.config), so HUD layout and options follow the player
     * across Minecraft versions; otherwise it's this instance's config folder.
     */
    public java.nio.file.Path configPath() {
        String shared = System.getProperty("peregrine.config");
        if (shared != null && !shared.isEmpty()) {
            return java.nio.file.Paths.get(shared);
        }
        return platform.configFile();
    }

    public Menu menu() {
        return menu;
    }

    public TitleMenu titleMenu() {
        return titleMenu;
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

    // ---- keybinds: a key set on a feature's page switches it on and off while playing

    private final java.util.Set<Module> keysHeld = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<Module, Boolean>());
    private String toast;
    private boolean toastOn;
    private long toastAt;

    private boolean keyEvents;  // the adapter reports key presses as they happen

    /**
     * A key was pressed while playing (no screen open). Adapters call this from the
     * game's own keyboard handler, so even a very quick tap at low FPS counts.
     */
    public void onKey(int key) {
        keyEvents = true;
        if (!platform.inWorld() || key < 0) {
            return;
        }
        for (Module m : modules) {
            if (m.key == key) {
                keysHeld.add(m);
                toggleByKey(m);
            }
        }
    }

    private void toggleByKey(Module m) {
        if (!platform.supports(m.id)) {
            return;
        }
        m.setEnabled(!m.enabled());
        markDirty();
        toast = m.name;
        toastOn = m.enabled();
        toastAt = System.currentTimeMillis();
        SelfTest.count("keybind:" + m.id);
    }

    /** Called every tick and every frame; acts once per key press (when the adapter has no key events). */
    void pollKeys() {
        if (keyEvents) {
            return;
        }
        if (!platform.inWorld()) {
            keysHeld.clear();
            return;
        }
        for (Module m : modules) {
            if (m.key < 0) {
                continue;
            }
            boolean down = platform.keyDown(m.key);
            if (down && keysHeld.add(m)) {
                toggleByKey(m);
            } else if (!down) {
                keysHeld.remove(m);
            }
        }
    }

    /** A small note under the top of the screen for a moment: "Low fire  On". */
    private void renderToast(Draw d) {
        if (toast == null) {
            return;
        }
        long age = System.currentTimeMillis() - toastAt;
        if (age > 1400) {
            toast = null;
            return;
        }
        float in = 0.3f + 0.7f * Math.min(1f, age / 120f);
        float out = age > 1100 ? 1f - (age - 1100) / 300f : 1f;
        float t = Math.max(0f, Math.min(in, out));
        Draw f = t < 1f ? new FadeDraw(d, t) : d;
        String state = toastOn ? "On" : "Off";
        int w = d.width(toast) + d.width(state) + 30;
        int h = 15;
        int x = (platform.screenWidth() - w) / 2;
        // Above the hotbar and the action bar, clear of the HUD items that usually sit at the top.
        int y = platform.screenHeight() - 88 + Math.round((1f - in) * 4);
        f.roundRect(x, y, w, h, Theme.withAlpha(Theme.PANEL, 0xE6));
        f.roundOutline(x, y, w, h, Theme.HAIRLINE);
        f.roundRect(x + 6, y + 5, 5, 5, toastOn ? accent : Theme.FAINT);
        f.text(toast, x + 15, y + 4, Theme.TEXT, false);
        f.text(state, x + w - 6 - d.width(state), y + 4, toastOn ? accent : Theme.MUTED, false);
    }

    public void tick() {
        if (SelfTest.ACTIVE) {
            SelfTest.tick(this);
        }
        pollKeys();
        if (platform.inWorld()) {
            for (Module m : modules) {
                if (m.enabled() && platform.supports(m.id)) {
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
        SelfTest.count("hud");
        pollKeys();
        for (Module m : modules) {
            if (m.enabled() && m instanceof HudModule && platform.supports(m.id)) {
                ((HudModule) m).renderAt(d, platform);
            }
        }
        renderToast(d);
    }

    /** button: 0 = left, 1 = right. */
    public void onMouseButton(int button) {
        SelfTest.count("mouse");
        Deque<Long> q = button == 0 ? leftClicks : button == 1 ? rightClicks : null;
        if (q != null) {
            q.addLast(System.currentTimeMillis());
        }
        if (button == 0 && platform.inWorld() && platform.showing(Platform.Screen.NONE)) {
            double reach = platform.targetDistance();
            if (reach >= 0) {  // an attack on something: feed the reach display and combo counter
                ((ReachHud) module("reach")).hit(reach);
                ((ComboHud) module("combo")).hit();
            }
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

    /** Draws the custom crosshair (adapters call this instead of Minecraft's when it's on). */
    public void renderCrosshair(Draw d) {
        ((Crosshair) module("crosshair")).render(d, platform);
    }

    /** How much to divide the field of view by (1 = no zoom). */
    public double zoomDivisor() {
        Zoom zoom = (Zoom) module("zoom");
        double d = zoom != null ? zoom.divisor(platform) : 1.0;
        SelfTest.count(d != 1.0 ? "zoom-active" : "zoom-hook");
        return d;
    }

    /** For version adapters' hooks: is this module switched on? */
    public boolean on(String id) {
        SelfTest.count("on:" + id);
        Module m = module(id);
        return m != null && m.enabled();
    }

    public void shutdown() {
        for (Module m : modules) {
            m.shutdown(platform);
        }
        Config.save(this);
    }
}
