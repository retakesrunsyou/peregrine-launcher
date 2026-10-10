package net.peregrine.client.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatic play-through used by the release checks; does nothing in normal play.
 *
 * Turned on with -Dperegrine.selftest=DIR and -Dperegrine.selftest.phase=title|world.
 * It drives the menus, asks the test script (through files in DIR) to press real
 * keys, click and take screenshots, and writes PASS/FAIL lines to DIR/results.txt.
 */
public final class SelfTest {

    private static final String DIR = System.getProperty("peregrine.selftest");
    public static final boolean ACTIVE = DIR != null;
    private static final String PHASE = System.getProperty("peregrine.selftest.phase", "title");
    private static final int WINDOW_W = Integer.getInteger("peregrine.selftest.width", 1280);

    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<String, Integer>();

    private SelfTest() {}

    /** Counts that something happened (a frame drawn, a hook called). */
    public static void count(String what) {
        if (ACTIVE) {
            Integer n = COUNTS.get(what);
            COUNTS.put(what, n == null ? 1 : n + 1);
        }
    }

    static int get(String what) {
        Integer n = COUNTS.get(what);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------ steps

    private interface Cond {
        boolean ok();
    }

    private abstract static class Step {
        final String label;

        Step(String label) {
            this.label = label;
        }

        /** Called every tick; return true when done. */
        abstract boolean run(int ticks);
    }

    private static List<Step> steps;
    private static int index;
    private static int stepTicks;
    private static int requestNo;
    private static int pendingRequest = -1;
    private static int failures;
    private static Peregrine pc;
    private static Platform p;
    private static Menu menu;

    static void tick(Peregrine peregrine) {
        try {
            if (steps == null) {
                pc = peregrine;
                p = pc.platform();
                menu = pc.menu();
                new File(DIR).mkdirs();
                steps = new ArrayList<Step>();
                if (PHASE.equals("world")) {
                    worldSteps();
                } else if (PHASE.equals("bench")) {
                    benchSteps();
                } else if (PHASE.equals("smoke")) {
                    smokeSteps();
                } else {
                    titleSteps();
                }
                log("INFO", "phase " + PHASE + " on Minecraft " + p.minecraftVersion());
            }
            if (index >= steps.size()) {
                return;
            }
            Step s = steps.get(index);
            if (s.run(stepTicks++)) {
                index++;
                stepTicks = 0;
            }
        } catch (Throwable t) {
            log("FAIL", "step " + (index < steps.size() ? steps.get(index).label : "?") + " threw " + t);
            t.printStackTrace();
            index++;
            stepTicks = 0;
        }
    }

    // ------------------------------------------------------------ the plans

    private static void titleSteps() {
        waitFor("Peregrine main menu shows instead of Minecraft's", 2400, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.TITLE) && get("title") > 40;
            }
        });
        waitTicks(40);  // let the fade-in finish
        shot("01-title");
        open("Singleplayer button opens world list", Platform.Screen.SINGLEPLAYER);
        shot("02-singleplayer");
        open("Multiplayer button opens server list", Platform.Screen.MULTIPLAYER);
        open("Options button opens options", Platform.Screen.OPTIONS);
        shot("03-options");
        open("Back to the main menu", Platform.Screen.TITLE);
        clickTitle("clicking Peregrine on the main menu opens the menu", Platform.Screen.PEREGRINE_MENU);
        check("menu draws on the main menu", new Cond() {
            public boolean ok() {
                return get("menu") > 5;
            }
        });
        waitTicks(10);
        shot("04-menu-on-title");
        clickOn("Edit HUD button works on the main menu", "edit", new Cond() {
            public boolean ok() {
                return menu.editingHud();
            }
        });
        waitTicks(10);
        shot("05-editor-on-title");
        clickOn("Back button leaves the editor", "done", new Cond() {
            public boolean ok() {
                return !menu.editingHud();
            }
        });
        finish();
    }

    /** Quick check with no test script attached: main menu, Right Shift menu, quit. */
    private static void smokeSteps() {
        waitFor("Peregrine main menu shows", 2400, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.TITLE) && get("title") > 20;
            }
        });
        open("Peregrine menu opens", Platform.Screen.PEREGRINE_MENU);
        check("menu draws", new Cond() {
            public boolean ok() {
                return get("menu") > 5;
            }
        });
        finish();
    }

    /** Just the benchmark: join, let everything load, measure, quit. */
    private static void benchSteps() {
        run("hold Stable FPS off for the benchmark", new Runnable() {
            public void run() {
                pc.module("stable_fps").setEnabled(false);
            }
        });
        waitFor("joins the world", 6000, new Cond() {
            public boolean ok() {
                return p.inWorld() && p.showing(Platform.Screen.NONE);
            }
        });
        fixedRenderDistance();
        waitTicks(400);
        benchmark(600);
        run("Stable FPS back on", new Runnable() {
            public void run() {
                pc.module("stable_fps").setEnabled(true);
            }
        });
        finish();
    }

    private static void worldSteps() {
        run("hold Stable FPS off for the benchmark", new Runnable() {
            public void run() {
                pc.module("stable_fps").setEnabled(false);
            }
        });
        waitFor("joins the world", 6000, new Cond() {
            public boolean ok() {
                return p.inWorld() && p.showing(Platform.Screen.NONE);
            }
        });
        fixedRenderDistance();
        waitTicks(400);  // chunks load in and get built
        benchmark(400);
        run("Stable FPS back on", new Runnable() {
            public void run() {
                pc.module("stable_fps").setEnabled(true);
            }
        });
        check("HUD draws in game", new Cond() {
            public boolean ok() {
                return get("hud") > 20;
            }
        });
        shot("11-hud-default");
        // Aim for 240 FPS, which a test machine drawing in software never reaches,
        // so Stable FPS has to step in.
        run("set Stable FPS's target to 240", new Runnable() {
            public void run() {
                ((net.peregrine.client.core.settings.ChoiceSetting) pc.module("stable_fps").settings().get(0)).index = 5;
            }
        });
        waitFor("Stable FPS eases off settings when FPS is low", 600, new Cond() {
            public boolean ok() {
                return ((net.peregrine.client.core.modules.StableFps) pc.module("stable_fps")).level() > 0
                        && p.particleLevel() == 2;
            }
        });
        run("Stable FPS's target back to 60", new Runnable() {
            public void run() {
                ((net.peregrine.client.core.settings.ChoiceSetting) pc.module("stable_fps").settings().get(0)).index = 1;
            }
        });
        run("turn on every HUD item (and zoom)", new Runnable() {
            public void run() {
                for (Module m : pc.modules()) {
                    if (m instanceof HudModule) {
                        m.setEnabled(true);
                    }
                }
                pc.module("zoom").setEnabled(true);
            }
        });
        waitTicks(20);
        shot("12-hud-all");

        // Real input from here on.
        ask("mousemove " + px(p.screenWidth() / 2) + " " + px(p.screenHeight() / 2));
        ask("click 1");
        waitTicks(5);
        check("mouse clicks are counted (CPS)", new Cond() {
            public boolean ok() {
                return get("mouse") > 0;
            }
        });
        ask("keydown c");
        waitTicks(10);
        check("holding C zooms in", new Cond() {
            public boolean ok() {
                return get("zoom-active") > 0;
            }
        });
        shot("13-zoom");
        ask("keyup c");
        waitTicks(5);

        ask("key Shift_R");
        waitFor("Right Shift opens the menu", 100, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.PEREGRINE_MENU) && get("menu") > 5;
            }
        });
        waitTicks(10);
        shot("14-menu-in-game");
        ask("type clockx");
        ask("key BackSpace");
        waitFor("typing and Backspace work in the search box", 60, new Cond() {
            public boolean ok() {
                return menu.search().equals("clock");
            }
        });
        waitTicks(5);
        shot("15-search");
        final boolean[] before = new boolean[1];
        run("remember the Clock setting", new Runnable() {
            public void run() {
                before[0] = pc.module("clock").enabled();
            }
        });
        clickOn("clicking a card switches it", "clock", new Cond() {
            public boolean ok() {
                return pc.module("clock").enabled() != before[0];
            }
        });
        clickOn("Edit HUD opens the editor", "edit", new Cond() {
            public boolean ok() {
                return menu.editingHud();
            }
        });
        waitTicks(10);
        shot("16-editor-in-game");
        final int[] start = new int[2];
        run("find the FPS item", new Runnable() {
            public void run() {
                HudModule fps = (HudModule) pc.module("fps");
                start[0] = fps.lastX;
                start[1] = fps.lastY;
                int gx = fps.lastX + Math.max(1, fps.lastW / 2);
                int gy = fps.lastY + Math.max(1, fps.lastH / 2);
                request("drag " + px(gx) + " " + px(gy) + " " + px(gx + 60) + " " + px(gy + 40));
            }
        });
        waitAck();
        waitFor("dragging moves a HUD item", 60, new Cond() {
            public boolean ok() {
                HudModule fps = (HudModule) pc.module("fps");
                return fps.lastX != start[0] || fps.lastY != start[1];
            }
        });
        shot("17-editor-moved");
        // Lunar-style styling: right-click opens the style panel, pick a colour, drag the corner.
        run("right-click the FPS item", new Runnable() {
            public void run() {
                HudModule fps = (HudModule) pc.module("fps");
                request("rclickat " + px(fps.lastX + fps.lastW / 2) + " " + px(fps.lastY + fps.lastH / 2));
            }
        });
        waitAck();
        waitFor("right-click opens the style panel", 60, new Cond() {
            public boolean ok() {
                return menu.editor().styleOpen();
            }
        });
        waitTicks(5);
        shot("18-style-panel");
        clickOn("picking a colour recolours the text", "text-swatch-3", new Cond() {
            public boolean ok() {
                return ((HudModule) pc.module("fps")).textColor == HudEditor.COLORS[3];
            }
        });
        final float[] sizeBefore = new float[1];
        run("drag the resize corner", new Runnable() {
            public void run() {
                HudModule fps = (HudModule) pc.module("fps");
                sizeBefore[0] = fps.scale;
                int[] c = menu.centerOf("handle");
                request("drag " + px(c[0]) + " " + px(c[1]) + " " + px(c[0] + 40) + " " + px(c[1] + 12));
            }
        });
        waitAck();
        waitFor("dragging the corner makes it bigger", 60, new Cond() {
            public boolean ok() {
                return ((HudModule) pc.module("fps")).scale > sizeBefore[0];
            }
        });
        waitTicks(5);
        shot("19-editor-styled");
        ask("key Escape");
        waitFor("Esc saves and closes the editor", 60, new Cond() {
            public boolean ok() {
                return !menu.editingHud();
            }
        });
        // Settings pages: the gear on a row
        clickOn("the gear opens a settings page", "gear:clock", new Cond() {
            public boolean ok() {
                return menu.page() == pc.module("clock");
            }
        });
        waitTicks(10);
        shot("19b-settings-page");
        final boolean[] h24 = new boolean[1];
        run("remember the 24-hour setting", new Runnable() {
            public void run() {
                h24[0] = (Boolean) pc.module("clock").settings().get(0).save();
            }
        });
        clickOn("clicking an option changes it", "set:24h", new Cond() {
            public boolean ok() {
                return (Boolean) pc.module("clock").settings().get(0).save() != h24[0];
            }
        });
        clickOn("the keybind button waits for a key", "bind", new Cond() {
            public boolean ok() {
                return menu.binding() == pc.module("clock");
            }
        });
        waitTicks(5);
        shot("19b2-keybind-waiting");
        ask("key k");
        waitFor("pressing K binds it", 60, new Cond() {
            public boolean ok() {
                return pc.module("clock").key == 75 && menu.binding() == null && menu.page() == pc.module("clock");
            }
        });
        clickOn("Customize opens the editor with the style panel", "customize", new Cond() {
            public boolean ok() {
                return menu.editingHud() && menu.editor().styleOpen();
            }
        });
        waitTicks(10);
        shot("19c-customize");
        ask("key Escape");
        waitFor("Esc goes back to the settings page", 60, new Cond() {
            public boolean ok() {
                return !menu.editingHud() && menu.page() != null;
            }
        });
        ask("key Escape");
        waitFor("Esc goes back to the list", 60, new Cond() {
            public boolean ok() {
                return menu.page() == null && p.showing(Platform.Screen.PEREGRINE_MENU);
            }
        });
        clickOn("the Settings tab opens", "view:Settings", new Cond() {
            public boolean ok() {
                return menu.centerOf("accent:0") != null;
            }
        });
        waitTicks(8);
        shot("19e-settings-tab");
        clickOn("the Keybinds tab opens", "view:Keybinds", new Cond() {
            public boolean ok() {
                return menu.centerOf("view:Keybinds") != null;
            }
        });
        waitTicks(8);
        shot("19f-keybinds-tab");
        clickOn("back to the Mods tab", "view:Mods", new Cond() {
            public boolean ok() {
                return true;
            }
        });
        waitTicks(8);
        shot("19g-mods-tab");
        ask("key Escape");
        waitFor("Esc closes the menu", 60, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.NONE);
            }
        });
        final boolean[] clockBefore = new boolean[1];
        run("remember the Clock before its key", new Runnable() {
            public void run() {
                clockBefore[0] = pc.module("clock").enabled();
            }
        });
        ask("key k");
        waitFor("pressing K in game switches the Clock", 60, new Cond() {
            public boolean ok() {
                return pc.module("clock").enabled() != clockBefore[0];
            }
        });
        waitTicks(2);
        shot("19d-keybind-toast");

        run("switch every setting on and off", new Runnable() {
            public void run() {
                for (Module m : pc.modules()) {
                    if (!(m instanceof HudModule) && !m.id.equals("main_menu") && p.supports(m.id)) {
                        boolean was = m.enabled();
                        m.setEnabled(!was);
                        m.setEnabled(was);
                    }
                }
            }
        });
        for (final String id : new String[] {"clear_weather", "clean_edges"}) {
            if (!p.supports(id)) {
                continue;
            }
            run("turn on " + id, new Runnable() {
                public void run() {
                    pc.module(id).setEnabled(true);
                }
            });
            waitFor(id + " hook runs", 100, new Cond() {
                public boolean ok() {
                    return get("on:" + id) > 0;
                }
            });
        }
        waitTicks(10);
        shot("20-visuals-on");

        // Batch 5: render hooks
        if (p.supports("anti_leak")) {
            run("turn on anti base leak", new Runnable() {
                public void run() {
                    pc.module("anti_leak").setEnabled(true);
                }
            });
            waitFor("anti base leak: every block uses one rotation", 200, new Cond() {
                public boolean ok() {
                    return Hooks.seedHits > 0;
                }
            });
            waitFor("anti base leak: block model hook runs (diamond bedrock)", 200, new Cond() {
                public boolean ok() {
                    return Hooks.modelHits > 0;
                }
            });
            run("put bedrock and a dropped item in front of the player", new Runnable() {
                public void run() {
                    if (p.supports("item_physics")) {
                        pc.module("item_physics").setEnabled(true);
                    }
                    p.testScene();
                }
            });
            waitFor("bedrock is drawn as diamond blocks", 200, new Cond() {
                public boolean ok() {
                    return Hooks.bedrockSwaps > 0;
                }
            });
            if (p.supports("item_physics")) {
                waitFor("item physics lays the dropped item down", 200, new Cond() {
                    public boolean ok() {
                        return Hooks.itemPhysicsHits > 0;
                    }
                });
            }
            waitTicks(40);
            shot("21-anti-leak");
        }
        run("turn on the custom crosshair, hit color and item physics", new Runnable() {
            public void run() {
                pc.module("crosshair").setEnabled(true);
                pc.module("hit_color").setEnabled(true);
                if (p.supports("item_physics")) {
                    pc.module("item_physics").setEnabled(true);
                }
            }
        });
        waitFor("custom crosshair draws", 100, new Cond() {
            public boolean ok() {
                return Hooks.crosshairDraws > 0;
            }
        });
        waitTicks(10);
        shot("22-crosshair");
        run("turn on freelook", new Runnable() {
            public void run() {
                pc.module("freelook").setEnabled(true);
            }
        });
        ask("keydown Alt_L");
        waitFor("holding Alt starts freelook in third person", 100, new Cond() {
            public boolean ok() {
                return Hooks.freelook && p.cameraMode() == 1 && Hooks.cameraHits > 0;
            }
        });
        waitTicks(10);
        shot("23-freelook");
        ask("keyup Alt_L");
        waitFor("letting go of Alt goes back to first person", 100, new Cond() {
            public boolean ok() {
                return !Hooks.freelook && p.cameraMode() == 0;
            }
        });
        // Batch 6: particles, fullbright, animations, hitboxes, own name tag
        run("turn on particles, fullbright, 1.7 animations, hitboxes and your name tag", new Runnable() {
            public void run() {
                for (String id : new String[] {"fewer_particles", "fullbright", "old_animations", "hitboxes", "own_nametag",
                        "small_totem"}) {
                    if (p.supports(id)) {
                        pc.module(id).setEnabled(true);
                    }
                }
                setBool("hitboxes", "items", true);
                if (p.supports("stable_fps")) {
                    pc.module("stable_fps").setEnabled(false);  // it may have turned particles down to minimal
                }
                p.testScene();  // lava sparks, a dropped item and a sword in hand
            }
        });
        if (p.supports("fewer_particles")) {
            waitFor("particles: lava sparks are thinned out", 200, new Cond() {
                public boolean ok() {
                    return Hooks.particleHits > 0;
                }
            });
        }
        if (p.supports("fullbright")) {
            waitFor("fullbright lights up dark places", 200, new Cond() {
                public boolean ok() {
                    return Hooks.nightVisionHits > 0;
                }
            });
        }
        if (p.supports("old_animations")) {
            waitFor("1.7 animations: held item moves", 200, new Cond() {
                public boolean ok() {
                    return Hooks.animationHits > 0;
                }
            });
        }
        if (p.supports("hitboxes")) {
            waitFor("hitboxes draw without F3+B", 200, new Cond() {
                public boolean ok() {
                    return Hooks.hitboxHits > 0;
                }
            });
        }
        if (p.supports("clean_menus")) {
            waitFor("resource packs leave the menus alone (test pack's button and splash skipped)", 100, new Cond() {
                public boolean ok() {
                    return Hooks.packFiltered > 0;
                }
            });
        }
        if (p.supports("small_totem")) {
            waitFor("small totem: the totem in your hand is drawn smaller", 200, new Cond() {
                public boolean ok() {
                    return Hooks.totemHits > 0;
                }
            });
        }
        waitTicks(10);
        shot("24-batch6-first-person");
        if (p.supports("own_nametag")) {
            run("third person", new Runnable() {
                public void run() {
                    p.setCameraMode(2);  // facing the player, so the name tag is in view
                }
            });
            waitFor("your own name tag shows in third person", 200, new Cond() {
                public boolean ok() {
                    return Hooks.nameTagHits > 0;
                }
            });
            waitTicks(10);
            shot("25-own-name-tag");
            run("first person", new Runnable() {
                public void run() {
                    p.setCameraMode(0);
                }
            });
        }
        // Batch 8: fog, swing speed, block outline and ores, static sky, sound filters
        run("turn on fog, swing speed, block outline, static sky and sound filters", new Runnable() {
            public void run() {
                for (String id : new String[] {"fog", "swing_speed", "block_outline", "static_sky", "sound_filters"}) {
                    if (p.supports(id)) {
                        pc.module(id).setEnabled(true);
                    }
                }
                p.testScene();
                p.afkSwing();
                p.afkLook(0f, 90f);  // look down at the ground, so a block (not the dropped item) is outlined
            }
        });
        waitFor("fog is pushed away", 200, new Cond() {
            public boolean ok() {
                return Hooks.fogHits > 0;
            }
        });
        waitFor("block outline in the chosen color", 200, new Cond() {
            public boolean ok() {
                return Hooks.outlineHits > 0;
            }
        });
        waitFor("ores you can see get an outline", 200, new Cond() {
            public boolean ok() {
                return Hooks.oreHits > 0;
            }
        });
        waitFor("the sky stops moving", 200, new Cond() {
            public boolean ok() {
                return Hooks.skyHits > 0;
            }
        });
        waitFor("swing speed changes the swing", 200, new Cond() {
            public boolean ok() {
                return Hooks.swingHits > 0;
            }
        });
        waitTicks(10);
        shot("26-batch8");
        run("report hook counts", new Runnable() {
            public void run() {
                log("INFO", "hooks: seed=" + Hooks.seedHits + " offset=" + Hooks.offsetHits + " model=" + Hooks.modelHits
                        + " bedrock=" + Hooks.bedrockSwaps + " items=" + Hooks.itemPhysicsHits
                        + " crosshair=" + Hooks.crosshairDraws + " camera=" + Hooks.cameraHits
                        + " particles=" + Hooks.particleHits + " nightvision=" + Hooks.nightVisionHits
                        + " animation=" + Hooks.animationHits + " hitbox=" + Hooks.hitboxHits
                        + " nametag=" + Hooks.nameTagHits + " scoreboard=" + Hooks.scoreboardHits
                        + " fire=" + Hooks.fireHits + " inventory=" + Hooks.inventoryTweakHits
                        + " totem=" + Hooks.totemHits + " packs=" + Hooks.packFiltered
                        + " fog=" + Hooks.fogHits + " outline=" + Hooks.outlineHits + " ores=" + Hooks.oreHits
                        + " sky=" + Hooks.skyHits + " swing=" + Hooks.swingHits + " sound=" + Hooks.soundHits);
            }
        });
        finish();
    }

    // ------------------------------------------------------------ step builders

    /** Every benchmark uses the same render distance, so runs compare fairly. */
    private static void fixedRenderDistance() {
        run("render distance 8 for the benchmark", new Runnable() {
            public void run() {
                p.setRenderDistance(8);
            }
        });
    }

    /** Averages the FPS counter over a number of ticks, standing still, and logs it. */
    private static void benchmark(final int ticks) {
        steps.add(new Step("benchmark") {
            long sum;
            int n;
            int low = Integer.MAX_VALUE;

            boolean run(int t) {
                if (t % 20 == 10) {  // the counter updates once a second
                    int f = p.fps();
                    sum += f;
                    n++;
                    low = Math.min(low, f);
                }
                if (t < ticks) {
                    return false;
                }
                log("INFO", "benchmark: " + (n == 0 ? 0 : Math.round(sum / (double) n)) + " fps average, "
                        + (n == 0 ? 0 : low) + " lowest, render distance " + p.renderDistance()
                        + ", over " + (ticks / 20) + " s");
                return true;
            }
        });
    }

    private static void setBool(String module, String setting, boolean v) {
        if (!p.supports(module)) {
            return;
        }
        for (net.peregrine.client.core.settings.Setting s : pc.module(module).settings()) {
            if (s.id.equals(setting) && s instanceof net.peregrine.client.core.settings.BoolSetting) {
                ((net.peregrine.client.core.settings.BoolSetting) s).value = v;
            }
        }
    }

    private static void run(String label, final Runnable r) {
        steps.add(new Step(label) {
            boolean run(int ticks) {
                r.run();
                return true;
            }
        });
    }

    private static void waitTicks(final int n) {
        steps.add(new Step("wait") {
            boolean run(int ticks) {
                return ticks >= n;
            }
        });
    }

    private static void waitFor(String label, final int timeout, final Cond c) {
        steps.add(new Step(label) {
            boolean run(int ticks) {
                if (c.ok()) {
                    log("PASS", label);
                    return true;
                }
                if (ticks >= timeout) {
                    log("FAIL", label + " (timed out)");
                    return true;
                }
                return false;
            }
        });
    }

    /** Like waitFor, with room for slow machines (software rendering can drop to a few FPS). */
    private static void check(String label, Cond c) {
        waitFor(label, 400, c);
    }

    private static void open(String label, final Platform.Screen which) {
        run("open " + which, new Runnable() {
            public void run() {
                p.openScreen(which);
            }
        });
        waitFor(label, 100, new Cond() {
            public boolean ok() {
                return p.showing(which);
            }
        });
        waitTicks(10);
    }

    /** Asks the test script to do something (a key, a click, a screenshot) and waits. */
    private static void ask(final String command) {
        run(command, new Runnable() {
            public void run() {
                request(command);
            }
        });
        waitAck();
    }

    private static void waitAck() {
        steps.add(new Step("wait for the test script") {
            boolean run(int ticks) {
                if (pendingRequest < 0 || new File(DIR, "ack-" + pendingRequest).exists()) {
                    return true;
                }
                if (ticks > 1200) {
                    log("FAIL", "test script didn't answer request " + pendingRequest);
                    return true;
                }
                return false;
            }
        });
    }

    private static void shot(String name) {
        ask("shot " + name);
    }

    /** Clicks the centre of a menu element with the real mouse. */
    private static void clickOn(String label, final String what, Cond expect) {
        steps.add(new Step(label) {
            boolean run(int ticks) {
                int[] c = menu.centerOf(what);
                if (c == null) {
                    if (ticks > 40) {
                        log("FAIL", label + " (" + what + " isn't on screen)");
                        pendingRequest = -1;
                        return true;
                    }
                    return false;
                }
                request("clickat " + px(c[0]) + " " + px(c[1]));
                return true;
            }
        });
        waitAck();
        waitFor(label, 60, expect);
        waitTicks(5);
    }

    /** Clicks a main menu button with the real mouse. */
    private static void clickTitle(String label, final Platform.Screen target) {
        run("click " + target, new Runnable() {
            public void run() {
                int[] c = pc.titleMenu().centerOf(target);
                request("clickat " + px(c[0]) + " " + px(c[1]));
            }
        });
        waitAck();
        waitFor(label, 100, new Cond() {
            public boolean ok() {
                return p.showing(target);
            }
        });
        waitTicks(10);
    }

    private static void finish() {
        run("finish", new Runnable() {
            public void run() {
                log("INFO", "counts " + COUNTS);
                log(failures == 0 ? "PASS" : "INFO", "finished with " + failures + " failure(s)");
                write("finished", "done\n", false);
            }
        });
        waitTicks(20);
        run("quit", new Runnable() {
            public void run() {
                p.openScreen(Platform.Screen.QUIT);
            }
        });
    }

    // ------------------------------------------------------------ files

    private static int px(int gui) {
        double scale = (double) WINDOW_W / Math.max(1, p.screenWidth());
        return (int) Math.round((gui + 0.5) * scale);
    }

    private static void request(String command) {
        pendingRequest = ++requestNo;
        write("req-" + pendingRequest, command + "\n", false);
    }

    private static void log(String kind, String text) {
        if (kind.equals("FAIL")) {
            failures++;
        }
        System.out.println("[Peregrine self-test] " + kind + " " + text);
        write("results.txt", kind + " " + text + "\n", true);
    }

    private static void write(String name, String text, boolean append) {
        File f = new File(DIR, name);
        File tmp = new File(DIR, name + ".tmp");
        try {
            if (append) {
                try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
                    w.write(text);
                }
                return;
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(text);
            }
            if (!tmp.renameTo(f)) {
                throw new IOException("rename failed");
            }
        } catch (IOException e) {
            System.out.println("[Peregrine self-test] can't write " + f + ": " + e);
        }
    }
}
