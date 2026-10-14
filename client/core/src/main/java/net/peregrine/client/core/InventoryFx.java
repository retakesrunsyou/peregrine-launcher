package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Smooth inventory: the shared logic behind the effects every container screen gets
 * (inventory, chests, furnaces...). The version adapters only report what happens and ask
 * where to draw things; all timing and easing lives here.
 *
 * - opening and closing: the screen grows in from slightly smaller and shrinks away again
 * - the slot highlight glides to the slot under the mouse
 * - items you move (number keys, shift-click) fly to where they land
 * - see-through: no texture behind the slots, just a dark glass panel and grey slots
 */
public final class InventoryFx {

    private InventoryFx() {
    }

    // ---------------------------------------------------------------- settings (from the module)

    public static volatile boolean animate;
    public static volatile int animMs = 140;
    public static volatile boolean glide;
    public static volatile boolean flyItems;
    public static volatile boolean seeThrough;
    /** See-through panel opacity, 0..1. */
    public static volatile float panelAlpha = 0.45f;
    public static volatile int hits;
    /** Items sent flying so far (for the self-test). */
    public static volatile int flights;
    /** For the self-test: screens opened, frames drawn, close requests held back. */
    public static volatile int opens, frames, holds;
    /** Self-test: print each inventory click to the game log. */
    public static volatile boolean debug = System.getProperty("peregrine.selftest") != null;

    // ---------------------------------------------------------------- opening and closing

    private static long openedAt;
    private static long closingAt;

    /** A container screen just opened. */
    public static void opened() {
        opens++;
        openedAt = System.currentTimeMillis();
        closingAt = 0;
        noHighlight();
        synchronized (InventoryFx.class) {
            FLIGHTS.clear();
        }
    }

    /**
     * The player asked to close the screen. True: we play the closing animation first and the
     * adapter should hold the close back until {@link #closeDue()} says so.
     */
    public static boolean startClose() {
        if (!animate || closingAt != 0) {
            return false;
        }
        closingAt = System.currentTimeMillis();
        holds++;
        return true;
    }

    public static boolean closing() {
        return closingAt != 0;
    }

    /** The closing animation has finished: close the screen for real now. */
    public static boolean closeDue() {
        return closingAt != 0 && System.currentTimeMillis() - closingAt >= closeMs();
    }

    /** Called once the screen has really closed. */
    public static void closed() {
        closingAt = 0;
    }

    private static int closeMs() {
        return Math.max(40, animMs * 2 / 3);
    }

    /** How big to draw the screen right now (1 = normal size), around its middle. */
    public static float scale() {
        frames++;
        if (!animate) {
            return 1f;
        }
        long now = System.currentTimeMillis();
        if (closingAt != 0) {
            float t = clamp((now - closingAt) / (float) closeMs());
            return 1f - 0.06f * t * t;
        }
        float t = clamp((now - openedAt) / (float) Math.max(1, animMs));
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        if (t < 1f && hits < 100000) {
            hits++;
        }
        return 0.92f + 0.08f * ease;
    }

    /** How visible the dimmed backdrop and see-through panel are right now (0..1). */
    public static float fade() {
        if (!animate) {
            return 1f;
        }
        long now = System.currentTimeMillis();
        if (closingAt != 0) {
            return 1f - clamp((now - closingAt) / (float) closeMs());
        }
        return clamp((now - openedAt) / (float) Math.max(1, animMs));
    }

    // ---------------------------------------------------------------- the gliding slot highlight

    private static float hx = Float.NaN, hy = Float.NaN;
    private static long hxLast, hyLast;

    /** Where to draw the slot highlight across (the hovered slot's x, in whatever units the caller uses). */
    public static int highlightX(int x) {
        long now = System.nanoTime();
        float[] st = {hx};
        int r = smooth(st, x, now, hxLast);
        hx = st[0];
        hxLast = now;
        return r;
    }

    /** Same, down. */
    public static int highlightY(int y) {
        long now = System.nanoTime();
        float[] st = {hy};
        int r = smooth(st, y, now, hyLast);
        hy = st[0];
        hyLast = now;
        return r;
    }

    private static int smooth(float[] v, int target, long now, long last) {
        float dtRaw = (now - last) / 1e9f;
        // off, first time, a pause in hovering (mouse left the slots), or a long way: jump there
        if (!glide || Float.isNaN(v[0]) || dtRaw > 0.15f || Math.abs(v[0] - target) > 200) {
            v[0] = target;
            return target;
        }
        float dt = Math.min(0.05f, dtRaw);
        v[0] += (target - v[0]) * (1f - (float) Math.exp(-30f * dt));
        if (Math.abs(target - v[0]) < 0.5f) {
            v[0] = target;
        } else if (hits < 100000) {
            hits++;
        }
        return Math.round(v[0]);
    }

    /** The mouse left every slot: the next highlight appears where it is, without gliding in. */
    public static void noHighlight() {
        hx = Float.NaN;
        hy = Float.NaN;
    }

    // ---------------------------------------------------------------- items flying to their new slot

    private static final int FLIGHT_MS = 130;

    private static final class Flight {
        final Object stack;
        final int fromX, fromY, toX, toY, slot;
        final long start = System.currentTimeMillis();

        Flight(Object stack, int fromX, int fromY, int toX, int toY, int slot) {
            this.stack = stack;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.slot = slot;
        }
    }

    private static final List<Flight> FLIGHTS = new ArrayList<Flight>();

    /** An item moves from one slot to another (positions in screen px, slot = the menu's index of where it lands). */
    public static synchronized void fly(Object stack, int fromX, int fromY, int toX, int toY, int toSlot) {
        if (!flyItems || stack == null || (fromX == toX && fromY == toY)) {
            return;
        }
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            if (it.next().slot == toSlot) {
                it.remove();
            }
        }
        if (FLIGHTS.size() < 32) {
            FLIGHTS.add(new Flight(stack, fromX, fromY, toX, toY, toSlot));
            flights++;
        }
    }

    /** True while an item is still on its way to this slot (don't draw the slot's item yet). */
    public static synchronized boolean inFlight(int slot) {
        if (FLIGHTS.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Flight f : FLIGHTS) {
            if (f.slot == slot && now - f.start < FLIGHT_MS) {
                return true;
            }
        }
        return false;
    }

    /** Draws the items on their way (call after the slots, before the cursor item). */
    public static synchronized void drawFlights(Draw d) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            float t = clamp((now - f.start) / (float) FLIGHT_MS);
            if (t >= 1f) {
                it.remove();
                continue;
            }
            float e = 1f - (1f - t) * (1f - t);
            int x = Math.round(f.fromX + (f.toX - f.fromX) * e);
            int y = Math.round(f.fromY + (f.toY - f.fromY) * e);
            d.item(f.stack, x, y);
            if (hits < 100000) {
                hits++;
            }
        }
    }

    public static synchronized boolean flying() {
        return !FLIGHTS.isEmpty();
    }

    // ---------------------------------------------------------------- see-through background

    /**
     * The see-through look: a dark glass panel the size of the screen's background, and a grey
     * square behind every slot so you can still tell where things go. slots: {x, y} pairs (screen px, the
     * item's top-left corner).
     */
    public static void drawPanel(Draw d, int left, int top, int width, int height, int[] slots) {
        float f = fade();
        int a = Math.round(255 * Math.max(0f, Math.min(1f, panelAlpha)) * f);
        d.roundRect(left, top, width, height, 6, Theme.withAlpha(0x0F1118, a));
        d.roundOutline(left, top, width, height, 6, Theme.withAlpha(0xFFFFFF, Math.round(a * 0.18f)));
        int slotA = Math.round(Math.min(255, 70 + a * 0.5f) * Math.max(0.35f, f));
        for (int i = 0; i + 1 < slots.length; i += 2) {
            d.rect(slots[i] - 1, slots[i + 1] - 1, 18, 18, Theme.withAlpha(0x8B8B8B, slotA / 2));
            d.rect(slots[i], slots[i + 1], 16, 16, Theme.withAlpha(0x6E6E6E, slotA));
        }
        if (hits < 100000) {
            hits++;
        }
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
