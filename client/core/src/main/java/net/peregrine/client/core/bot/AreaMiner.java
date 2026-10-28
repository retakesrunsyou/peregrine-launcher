package net.peregrine.client.core.bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The area miner's brain. You mark two corners; it clears everything between them, top layer
 * first, two layers at a time with a normal pickaxe (1x1) or three layers and three wide with a
 * tool that mines 3x3. It walks the rows back and forth, drops down to the next layers, fills in
 * lava and water it uncovers, lays a block under itself where the ground is missing, eats when
 * hungry, and stops by itself when the pickaxe is nearly broken, the inventory is full, it's out
 * of filler blocks, or something unexpected happens (like falling into a cave).
 */
public final class AreaMiner {

    public static final int ONE = 0, THREE = 1;

    private static final int MINE = 0, PLACE = 1, WALK = 2, FALL = 3, EAT = 4, FACE = 5;

    private static final class Goal {
        final int kind, x, y, z;
        int ticks;

        Goal(int kind, int x, int y, int z) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final int x0, y0, z0, x1, y1, z1, mode;
    private final Deque<Goal> goals = new ArrayDeque<Goal>();
    private final List<int[]> slices = new ArrayList<int[]>();  // {top, bottom}
    private int slice = -1;
    private List<int[]> path;          // columns of the current slice, in walking order
    private int feetY;                 // where your feet should be in this slice
    private boolean done;
    private String stopped;
    private int total, cleared;
    private boolean eating;

    /** The area between two corners (inclusive), mined in the given mode. */
    public AreaMiner(int[] a, int[] b, int mode) {
        x0 = Math.min(a[0], b[0]);
        x1 = Math.max(a[0], b[0]);
        y0 = Math.min(a[1], b[1]);
        y1 = Math.max(a[1], b[1]);
        z0 = Math.min(a[2], b[2]);
        z1 = Math.max(a[2], b[2]);
        this.mode = mode;
        int h = mode == THREE ? 3 : 2;
        for (int top = y1; top >= y0; top -= h) {
            slices.add(new int[] {top, Math.max(y0, top - h + 1)});
        }
        total = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
    }

    public boolean done() {
        return done;
    }

    /** Why it stopped by itself (null while running or when it finished). */
    public String stopped() {
        return stopped;
    }

    public int total() {
        return total;
    }

    public int cleared() {
        return cleared;
    }

    /** Whether you stand where it can start: on top of the area, on a corner. */
    public boolean canStartFrom(int[] feet) {
        boolean corner = (feet[0] == x0 || feet[0] == x1) && (feet[2] == z0 || feet[2] == z1);
        return corner && feet[1] == y1 + 1;
    }

    public String startHint() {
        return "Stand on a corner of the top layer to start";
    }

    private void stop(BotWorld w, String why) {
        stopped = why;
        done = true;
        goals.clear();
        w.release();
    }

    /** One game tick. */
    public void tick(BotWorld w) {
        if (done) {
            return;
        }
        // look after yourself first
        if (!eating && w.food() <= 14 && w.hasFood() && (goals.isEmpty() || goals.peekFirst().kind != FALL)) {
            w.walk(false);
            goals.addFirst(new Goal(EAT, 0, 0, 0));
            eating = true;
        }
        if (!w.toolOk()) {
            stop(w, "Stopped: your tool is about to break");
            return;
        }
        if (w.inventoryFull()) {
            stop(w, "Stopped: your inventory is full");
            return;
        }
        int[] f = w.feet();
        if (w.lava(f[0], f[1], f[2]) || w.lava(f[0], f[1] + 1, f[2])) {
            stop(w, "Stopped: in lava!");
            return;
        }
        if (slice >= 0 && w.onGround() && f[1] < feetY - 1 && (goals.isEmpty() || goals.peekFirst().kind != FALL)) {
            stop(w, "Stopped: fell below where it should be");
            return;
        }
        if (goals.isEmpty() && !plan(w)) {
            return;
        }
        Goal g = goals.peekFirst();
        g.ticks++;
        switch (g.kind) {
            case EAT:
                if (w.eat() || g.ticks > 120) {
                    goals.pollFirst();
                    eating = false;
                }
                break;
            case MINE:
                mine(w, g);
                break;
            case FACE:
                face(w, g);
                break;
            case PLACE:
                if (w.solid(g.x, g.y, g.z) || w.place(g.x, g.y, g.z)) {
                    goals.pollFirst();
                } else if (!w.hasFiller()) {
                    stop(w, "Stopped: no blocks left to fill holes with (keep some in your off hand)");
                } else if (g.ticks > 60) {
                    stop(w, "Stopped: couldn't fill a hole");
                }
                break;
            case WALK:
                walk(w, g);
                break;
            case FALL:
                w.walk(false);
                if (w.onGround() && w.feet()[1] <= g.y) {
                    goals.pollFirst();
                } else if (g.ticks > 100) {
                    stop(w, "Stopped: didn't land where expected");
                }
                break;
            default:
                goals.pollFirst();
        }
    }

    private void mine(BotWorld w, Goal g) {
        w.walk(false);
        if (!inside(g.x, g.y, g.z)) {
            goals.pollFirst();
            return;
        }
        if (w.fluid(g.x, g.y, g.z) && !w.present(g.x, g.y, g.z)) {
            goals.addFirst(new Goal(PLACE, g.x, g.y, g.z));   // liquid in the way: fill, then mine the filler
            return;
        }
        if (!w.present(g.x, g.y, g.z) || !w.breakable(g.x, g.y, g.z)) {
            goals.pollFirst();
            cleared++;
            return;
        }
        w.look(g.x + 0.5, g.y + 0.5, g.z + 0.5);
        if (w.mine(g.x, g.y, g.z) || g.ticks > 600) {
            goals.pollFirst();
            cleared++;
            hazards(w, g.x, g.y, g.z);
        }
    }

    /** A 3x3 face: mine its middle (the tool takes the rest), then anything left over. */
    private void face(BotWorld w, Goal g) {
        goals.pollFirst();
        List<Goal> rest = new ArrayList<Goal>();
        // (x, y, z) is the face's middle; the face runs across z (walking along x) and up/down
        for (int dy = 1; dy >= -1; dy--) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dy != 0 || dz != 0) {
                    rest.add(new Goal(MINE, g.x, g.y + dy, g.z + dz));
                }
            }
        }
        for (int i = rest.size() - 1; i >= 0; i--) {
            goals.addFirst(rest.get(i));
        }
        goals.addFirst(new Goal(MINE, g.x, g.y, g.z));
    }

    /** Lava and water next to what was just mined get filled before they can flow in. */
    private void hazards(BotWorld w, int x, int y, int z) {
        int[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : n) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (w.fluid(nx, ny, nz)) {
                goals.addFirst(new Goal(PLACE, nx, ny, nz));
            }
        }
    }

    private void walk(BotWorld w, Goal g) {
        double[] p = w.pos();
        double tx = g.x + 0.5, tz = g.z + 0.5;
        double dx = tx - p[0], dz = tz - p[2];
        if (dx * dx + dz * dz < 0.04) {
            w.walk(false);
            goals.pollFirst();
            return;
        }
        // ground missing ahead: lay a block first
        if (!w.solid(g.x, g.y - 1, g.z) && g.ticks == 1) {
            w.walk(false);
            goals.addFirst(new Goal(PLACE, g.x, g.y - 1, g.z));
            return;
        }
        w.look(tx, p[1] + 1.5, tz);
        w.walk(true);
        if (g.ticks > 100) {
            stop(w, "Stopped: stuck walking");
        }
    }

    private boolean inside(int x, int y, int z) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
    }

    // ------------------------------------------------------------------ planning

    /** Lines up the next slice's work; false when everything is done. */
    private boolean plan(BotWorld w) {
        if (slice + 1 >= slices.size()) {
            done = true;
            w.release();
            return false;
        }
        int[] f = w.feet();
        if (slice < 0 && !canStartFrom(f)) {
            stop(w, startHint());
            return false;
        }
        slice++;
        int top = slices.get(slice)[0], bottom = slices.get(slice)[1];
        // drop down: mine straight down under you to the new slice's floor level
        int from = f[1] - 1;
        for (int y = from; y >= bottom; y--) {
            goals.addLast(new Goal(MINE, f[0], y, f[2]));
        }
        goals.addLast(new Goal(FALL, f[0], bottom, f[2]));
        feetY = bottom;
        path = slice == 0 ? route(f[0], f[2]) : reverse(path);
        int[] here = {f[0], f[2]};
        // the column you landed in, full height of the slice (and its sides in 3x3)
        addColumn(here, top, bottom, mode == THREE && isLaneCentre(here[1]));
        for (int i = 1; i < path.size(); i++) {
            int[] prev = path.get(i - 1), next = path.get(i);
            boolean along = next[0] != prev[0];
            addColumn(next, top, bottom, mode == THREE && along && isLaneCentre(next[1]));
            goals.addLast(new Goal(WALK, next[0], bottom, next[1]));
        }
        return true;
    }

    private void addColumn(int[] c, int top, int bottom, boolean wide) {
        if (wide && top - bottom == 2) {
            goals.addLast(new Goal(FACE, c[0], bottom + 1, c[1]));
            return;
        }
        for (int y = top; y >= bottom; y--) {
            if (wide) {
                for (int dz = -1; dz <= 1; dz++) {
                    goals.addLast(new Goal(MINE, c[0], y, c[1] + dz));
                }
            } else {
                goals.addLast(new Goal(MINE, c[0], y, c[1]));
            }
        }
    }

    private List<Integer> lanes() {
        List<Integer> out = new ArrayList<Integer>();
        if (mode == ONE || z1 - z0 < 2) {
            for (int z = z0; z <= z1; z++) {
                out.add(z);
            }
            return out;
        }
        for (int z = z0 + 1; ; z += 3) {
            int c = Math.min(z, z1 - 1);
            out.add(c);
            if (c + 1 >= z1) {
                break;
            }
        }
        return out;
    }

    private boolean isLaneCentre(int z) {
        return mode == THREE && lanes().contains(z);
    }

    /** The walk for a slice, starting at your corner: lanes along x, back and forth, stepping across in z. */
    private List<int[]> route(int sx, int sz) {
        List<Integer> lanes = lanes();
        if (sz == z1) {
            java.util.Collections.reverse(lanes);
        }
        List<int[]> p = new ArrayList<int[]>();
        int x = sx, z = sz;
        p.add(new int[] {x, z});
        int xEnd = sx == x0 ? x1 : x0;
        for (int lane : lanes) {
            while (z != lane) {                       // across to the lane
                z += Integer.signum(lane - z);
                p.add(new int[] {x, z});
            }
            int target = x == sx ? xEnd : sx;
            while (x != target) {
                x += Integer.signum(target - x);
                p.add(new int[] {x, z});
            }
        }
        return p;
    }

    private static List<int[]> reverse(List<int[]> p) {
        List<int[]> r = new ArrayList<int[]>(p);
        java.util.Collections.reverse(r);
        return r;
    }

    /** "1,234 left (40%)" for the HUD. */
    public String progress() {
        int pct = total == 0 ? 100 : Math.min(100, Math.round(100f * cleared / total));
        return pct + "%  (layer " + Math.max(1, slice + 1) + " of " + slices.size() + ")";
    }
}
