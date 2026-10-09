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

    private static void worldSteps() {
        waitFor("joins the world", 6000, new Cond() {
            public boolean ok() {
                return p.inWorld() && p.showing(Platform.Screen.NONE);
            }
        });
        waitTicks(100);  // chunks load in
        check("HUD draws in game", new Cond() {
            public boolean ok() {
                return get("hud") > 20;
            }
        });
        shot("11-hud-default");
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
        clickOn("Done closes the editor", "done", new Cond() {
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
        clickOn("Customize opens the editor with the style panel", "customize", new Cond() {
            public boolean ok() {
                return menu.editingHud() && menu.editor().styleOpen();
            }
        });
        waitTicks(10);
        shot("19c-customize");
        clickOn("Done goes back to the settings page", "done", new Cond() {
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
        ask("key Escape");
        waitFor("Esc closes the menu", 60, new Cond() {
            public boolean ok() {
                return p.showing(Platform.Screen.NONE);
            }
        });

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
        finish();
    }

    // ------------------------------------------------------------ step builders

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
