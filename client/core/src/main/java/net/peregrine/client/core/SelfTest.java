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
        lockerSteps();
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
        ask("key j");
        waitFor("pressing K binds it", 60, new Cond() {
            public boolean ok() {
                return pc.module("clock").key == 74 && menu.binding() == null && menu.page() == pc.module("clock");
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
        run("menu background: none (fully see-through)", new Runnable() {
            public void run() {
                pc.setMenuBackground(2);
            }
        });
        waitTicks(6);
        shot("19e2-menu-see-through");
        run("menu background back to solid", new Runnable() {
            public void run() {
                pc.setMenuBackground(0);
            }
        });
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
        ask("key j");
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
                    return id.equals("clear_weather") ? Hooks.weatherHits > 0 : get("on:" + id) > 0;
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
                for (String id : new String[] {"fog", "clear_water", "clear_lava", "swing_speed", "block_outline", "ore_outline", "static_sky", "sound_filters"}) {
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
        run("look ahead at the ore vein", new Runnable() {
            public void run() {
                p.afkLook(0f, -60f);
            }
        });
        waitTicks(15);
        shot("27-ore-vein");
        // Batch 9: nick hider, time changer, shiny potions, glint color, stretched view
        run("turn on nick hider, time changer, shiny potions, glint color and stretched view", new Runnable() {
            public void run() {
                for (String id : new String[] {"nick_hider", "time_changer", "shiny_potions", "glint_color", "stretched",
                        "profile", "cooldowns", "item_tracker"}) {
                    if (p.supports(id)) {
                        pc.module(id).setEnabled(true);
                    }
                }
                p.sendChat("Peregrine test: hello " + p.playerName());  // your name in chat, to be swapped
            }
        });
        waitFor("your name shows as the nick", 200, new Cond() {
            public boolean ok() {
                return Hooks.textHits > 0;
            }
        });
        if (p.supports("time_changer")) {
            waitFor("time changer sets the time of day", 200, new Cond() {
                public boolean ok() {
                    return Hooks.timeHits > 0 || Hooks.clockHits > 0;
                }
            });
        }
        waitFor("potions shine", 200, new Cond() {
            public boolean ok() {
                return Hooks.foilHits > 0;
            }
        });
        waitFor("the enchantment shine is recolored", 600, new Cond() {
            public boolean ok() {
                return Hooks.glintHits > 0;
            }
        });
        if (p.supports("stretched")) {
            waitFor("stretched view widens the world", 200, new Cond() {
                public boolean ok() {
                    return Hooks.stretchHits > 0;
                }
            });
        }
        waitTicks(20);
        shot("28-batch9");
        if (p.supports("stretched")) {
            run("stretch the HUD too", new Runnable() {
                public void run() {
                    ((net.peregrine.client.core.settings.BoolSetting) pc.module("stretched").settings().get(3)).value = true;
                }
            });
            waitFor("the HUD is stretched too", 100, new Cond() {
                public boolean ok() {
                    return Hooks.stretchHudHits > 0;
                }
            });
            waitTicks(5);
            shot("28b-stretched-hud");
            run("HUD back to normal", new Runnable() {
                public void run() {
                    ((net.peregrine.client.core.settings.BoolSetting) pc.module("stretched").settings().get(3)).value = false;
                }
            });
        }
        // Batch 9b: labels over the world (waypoints), hand position, hide plants
        run("aim straight ahead, add a waypoint 8 blocks ahead, move the main hand, hide grass", new Runnable() {
            public void run() {
                p.afkLook(0f, -p.pitch());  // level gaze
                double yaw = Math.toRadians(p.yaw());
                double ax = p.x() - Math.sin(yaw) * 8, az = p.z() + Math.cos(yaw) * 8;
                ((net.peregrine.client.core.modules.Waypoints) pc.module("waypoints")).addAt(p, "Test spot",
                        (int) Math.floor(ax), (int) Math.floor(p.y()), (int) Math.floor(az));
                for (String id : new String[] {"waypoints", "hand_position", "hide_plants", "smooth_hotbar", "screen_text", "action_bar", "boss_bars", "tab_list"}) {
                    if (p.supports(id)) {
                        pc.module(id).setEnabled(true);
                    }
                }
                ((net.peregrine.client.core.settings.SliderSetting) pc.module("hand_position").settings().get(3)).set(0.6f);
            }
        });
        waitFor("a waypoint label shows over the world", 200, new Cond() {
            public boolean ok() {
                return Hooks.labelHits > 0;
            }
        });
        run("a point straight ahead lands in the middle of the screen", new Runnable() {
            public void run() {
                double yaw = Math.toRadians(p.yaw()), pitch = Math.toRadians(p.pitch());
                double ex = p.x() - Math.sin(yaw) * Math.cos(pitch) * 6, ey = p.y() + 1.62 - Math.sin(pitch) * 6,
                        ez = p.z() + Math.cos(yaw) * Math.cos(pitch) * 6;
                float[] s = p.project(ex, ey, ez);
                if (s == null || Math.abs(s[0] - p.screenWidth() / 2f) > p.screenWidth() * 0.08f
                        || Math.abs(s[1] - p.screenHeight() / 2f) > p.screenHeight() * 0.12f) {
                    throw new IllegalStateException("projected to " + (s == null ? "nothing" : s[0] + "," + s[1])
                            + " on a " + p.screenWidth() + "x" + p.screenHeight() + " screen");
                }
            }
        });
        waitFor("the main hand moves", 200, new Cond() {
            public boolean ok() {
                return Hooks.handHits > 0;
            }
        });
        waitTicks(40);
        run("count hidden plants (none nearby is fine)", new Runnable() {
            public void run() {
                log("INFO", "hide plants: " + Hooks.plantHits + " plants hidden while chunks rebuilt");
            }
        });
        shot("29-batch9b");
        run("Peregrine cape and item switch: third person, from behind", new Runnable() {
            public void run() {
                if (p.supports("item_switch")) {
                    pc.module("item_switch").setEnabled(true);
                }
                p.setCameraMode(1);
            }
        });
        waitFor("your Peregrine cape is on", 200, new Cond() {
            public boolean ok() {
                return Hooks.capeHits > 0;
            }
        });
        waitTicks(20);
        shot("30-cape");
        run("first person again", new Runnable() {
            public void run() {
                p.setCameraMode(0);
            }
        });
        waitTicks(40);
        run("music activity with a song playing", new Runnable() {
            public void run() {
                int[] art = new int[NowPlaying.ART * NowPlaying.ART];
                for (int i = 0; i < art.length; i++) {
                    int cx = i % NowPlaying.ART, cy = i / NowPlaying.ART;
                    art[i] = 0xFF000000 | (40 + cx * 8) << 16 | (30 + cy * 4) << 8 | 160;
                }
                NowPlaying.show(new NowPlaying.Track("Peregrine Test Song", "The Falcons", "Spotify", "", 203000, 64000, true), art);
                pc.module("music").setEnabled(true);
            }
        });
        waitTicks(30);
        shot("31-music");
        run("music activity off", new Runnable() {
            public void run() {
                pc.module("music").setEnabled(false);
                NowPlaying.show(null, null);
            }
        });
        run("weather: rain on your screen", new Runnable() {
            public void run() {
                pc.module("clear_weather").setEnabled(true);
                ((net.peregrine.client.core.settings.ChoiceSetting) pc.module("clear_weather").settings().get(0)).index = 1;
            }
        });
        waitTicks(60);
        shot("33-rain");
        run("weather back to clear", new Runnable() {
            public void run() {
                ((net.peregrine.client.core.settings.ChoiceSetting) pc.module("clear_weather").settings().get(0)).index = 0;
            }
        });
        if (p.supports("place_animation")) {
            final boolean[] placed = new boolean[1];
            run("place a block, slowed down so it can be seen growing", new Runnable() {
                public void run() {
                    pc.module("place_animation").setEnabled(false);
                    Hooks.placeAnimMs = 30000;  // very slow, so the screenshot catches it part way
                    placed[0] = p.testPlaceBlock();
                    if (!placed[0]) {
                        log("INFO", "place animation: no test placement on this version");
                    }
                }
            });
            waitFor("the placed block is held back while it grows", 100, new Cond() {
                public boolean ok() {
                    return Hooks.placeHits > 0;
                }
            });
            waitTicks(12);
            shot("34-place-growing");
            run("let the block finish", new Runnable() {
                public void run() {
                    Hooks.placeAnimMs = 50;
                }
            });
            waitTicks(40);
            shot("35-place-done");
            run("place animation back to normal", new Runnable() {
                public void run() {
                    log("INFO", "place animation: held back " + Hooks.placeHits + " mesh update(s), drew the growing block " + Hooks.placeDrawHits + " time(s)");
                    pc.module("place_animation").setEnabled(true);
                }
            });
        }
        // Smooth inventory: opens with an animation, the highlight glides, number-key swaps fly
        final int[] inv = new int[4];
        run("note the counters", new Runnable() {
            public void run() {
                inv[0] = InventoryFx.hits;
                inv[1] = InventoryFx.flights;
                log("INFO", "inventory: holding before " + held());
            }
        });
        ask("key e");
        waitFor("the inventory opens with an animation", 100, new Cond() {
            public boolean ok() {
                return !p.showing(Platform.Screen.NONE) && InventoryFx.frames > 0 && InventoryFx.opens > 0;
            }
        });
        waitTicks(10);
        shot("36-inventory");
        run("hover the first inventory slot", new Runnable() {
            public void run() {
                int left = (p.screenWidth() - 176) / 2, top = (p.screenHeight() - 166) / 2;
                request("mousemove " + px(left + 16) + " " + px(top + 92));
            }
        });
        waitAck();
        waitTicks(5);
        ask("key 1");
        waitFor("the swapped items fly to their new slots", 100, new Cond() {
            public boolean ok() {
                return InventoryFx.flights > inv[1];
            }
        });
        ask("key 1");
        waitTicks(10);
        run("see-through inventory on", new Runnable() {
            public void run() {
                log("INFO", "inventory: holding after swapping there and back " + held()
                        + " (the test's items are only on this screen, so the server may take them back)");
                ((net.peregrine.client.core.settings.BoolSetting) pc.module("smooth_inventory").settings().get(4)).value = true;
            }
        });
        waitTicks(10);
        shot("37-inventory-see-through");
        run("see-through off", new Runnable() {
            public void run() {
                ((net.peregrine.client.core.settings.BoolSetting) pc.module("smooth_inventory").settings().get(4)).value = false;
            }
        });
        ask("key e");
        waitFor("the inventory closes (after its closing animation)", 100, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.NONE);
            }
        });
        run("inventory counters", new Runnable() {
            public void run() {
                log("INFO", "inventory: opens=" + InventoryFx.opens + " frames=" + InventoryFx.frames + " holds="
                        + InventoryFx.holds + " flights=" + InventoryFx.flights + " fx=" + InventoryFx.hits);
            }
        });
        if (p.supports("motion_blur")) {
            run("motion blur on", new Runnable() {
                public void run() {
                    pc.module("motion_blur").setEnabled(true);
                }
            });
            waitFor("motion blur is applied", 100, new Cond() {
                public boolean ok() {
                    return Hooks.motionBlurHits > 0;
                }
            });
            run("turn while it's on", new Runnable() {
                public void run() {
                    request("mousemove " + px(p.screenWidth() / 2 + 120) + " " + px(p.screenHeight() / 2));
                }
            });
            waitAck();
            waitTicks(2);
            shot("38-motion-blur");
            run("motion blur off", new Runnable() {
                public void run() {
                    pc.module("motion_blur").setEnabled(false);
                }
            });
            waitTicks(10);
            shot("39-motion-blur-off");
        }
        run("press Escape for the pause menu", new Runnable() {
            public void run() {
                request("key Escape");
            }
        });
        waitAck();
        waitFor("Peregrine's pause menu shows", 100, new Cond() {
            public boolean ok() {
                return get("pause") > 0;
            }
        });
        waitTicks(15);
        shot("32-pause");
        run("Escape again to play", new Runnable() {
            public void run() {
                request("key Escape");
            }
        });
        waitAck();
        waitFor("back in the game", 100, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.NONE);
            }
        });
        run("report cosmetics", new Runnable() {
            public void run() {
                log("INFO", "cosmetics: cape=" + Hooks.capeHits + " badge=" + Hooks.badgeHits
                        + " clock=" + Hooks.clockHits + " equip=" + Hooks.equipHits);
                pc.module("item_switch").setEnabled(false);
            }
        });
        run("turn batch 9b back off", new Runnable() {
            public void run() {
                for (String id : new String[] {"waypoints", "hand_position", "hide_plants", "smooth_hotbar", "screen_text", "action_bar", "boss_bars", "tab_list"}) {
                    pc.module(id).setEnabled(false);
                }
            }
        });
        run("turn batch 9 back off", new Runnable() {
            public void run() {
                for (String id : new String[] {"nick_hider", "time_changer", "glint_color", "stretched"}) {
                    pc.module(id).setEnabled(false);
                }
            }
        });
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
                        + " sky=" + Hooks.skyHits + " swing=" + Hooks.swingHits + " sound=" + Hooks.soundHits
                        + " nick=" + Hooks.textHits + " time=" + Hooks.timeHits + " foil=" + Hooks.foilHits
                        + " glint=" + Hooks.glintHits + " stretch=" + Hooks.stretchHits
                        + " labels=" + Hooks.labelHits + " hand=" + Hooks.handHits + " plants=" + Hooks.plantHits
                        + " hotbar=" + Hooks.hotbarHits + " title=" + Hooks.titleHits + " tab=" + Hooks.tabHits);
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

    /** What you're holding (for checking that moving items never loses one). */
    private static String held() {
        List<Platform.ItemInfo> items = p.armor();
        return items.isEmpty() ? "nothing" : String.valueOf(items.get(items.size() - 1).stack);
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

    /** The Locker on the main menu: open it, pick a cape and a background, come back. */
    private static void lockerSteps() {
        waitTicks(20);
        shot("01a-title-player");
        clickPoint("the Locker opens from the main menu", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerButtonCenter();
            }
        }, new Cond() {
            public boolean ok() {
                return pc.titleMenu().lockerOpen();
            }
        });
        waitTicks(25);
        shot("01b-locker");
        clickPoint("picking the Ember cape wears it", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerCardCenter(0, 3);
            }
        }, new Cond() {
            public boolean ok() {
                return net.peregrine.client.core.modules.Cosmetics.capeIndex() == 3;
            }
        });
        waitTicks(30);
        shot("01c-locker-cape-back");
        clickPoint("the Skins tab opens", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerCardCenter(2, 1);
            }
        }, new Cond() {
            public boolean ok() {
                return pc.titleMenu().lockerCardCenter(3, 0) != null;
            }
        });
        clickPoint("clicking a skin tries it on", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerCardCenter(3, 0);
            }
        }, new Cond() {
            public boolean ok() {
                return pc.titleMenu().tryingSkin() == 0;
            }
        });
        waitTicks(25);
        shot("01c2-locker-skins");
        clickPoint("the Backgrounds tab opens", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerCardCenter(2, 2);
            }
        }, new Cond() {
            public boolean ok() {
                return pc.titleMenu().lockerCardCenter(1, 1) != null;
            }
        });
        clickPoint("picking Dusk sets the main menu background", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerCardCenter(1, 1);
            }
        }, new Cond() {
            public boolean ok() {
                return pc.titleMenu().drawsBackground();
            }
        });
        waitTicks(20);
        shot("01d-locker-backgrounds");
        clickPoint("Back leaves the Locker", new java.util.concurrent.Callable<int[]>() {
            public int[] call() {
                return pc.titleMenu().lockerButtonCenter();
            }
        }, new Cond() {
            public boolean ok() {
                return !pc.titleMenu().lockerOpen();
            }
        });
        waitTicks(25);
        shot("01e-title-dusk");
        run("back to the panorama and the Falcon cape", new Runnable() {
            public void run() {
                Module m = pc.module("main_menu");
                if (m instanceof net.peregrine.client.core.modules.MainMenu) {
                    ((net.peregrine.client.core.modules.MainMenu) m).background.index = 0;
                }
                net.peregrine.client.core.modules.Cosmetics.setCape(1);
            }
        });
        waitTicks(5);
    }

    /** Clicks wherever where() says, with the real mouse, then waits for expect. */
    private static void clickPoint(String label, final java.util.concurrent.Callable<int[]> where, Cond expect) {
        run("click for: " + label, new Runnable() {
            public void run() {
                try {
                    int[] c = where.call();
                    if (c == null) {
                        log("FAIL", label + ": nothing to click");
                        return;
                    }
                    request("clickat " + px(c[0]) + " " + px(c[1]));
                } catch (Exception e) {
                    log("FAIL", label + ": " + e);
                }
            }
        });
        waitAck();
        waitFor(label, 100, expect);
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
