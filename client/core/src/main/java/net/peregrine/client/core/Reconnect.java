package net.peregrine.client.core;

/**
 * Auto reconnect: when a server drops you (kicked for being idle, a restart, lost connection,
 * a failed join), Peregrine joins it again by itself: after a few seconds, then waiting twice as
 * long each try (up to 5 minutes between tries) until you're back in. Never after a ban, or when
 * you logged in somewhere else, and never if you left yourself.
 */
public final class Reconnect {

    /** Settings (from the Auto reconnect module). */
    public static volatile boolean enabled;
    public static volatile int firstWait = 5;          // seconds
    public static volatile boolean keepTrying = true;
    public static volatile boolean skipBans = true;

    private static String address, name;
    private static long inWorldSince;
    private static int attempt;
    private static long nextAt;
    private static boolean waiting;
    private static String why;

    private static final String[] NEVER = {"banned", "ban ", "blacklist", "logged in from another location",
        "already connected", "duplicate login", "you are not whitelisted", "not white-listed"};

    private Reconnect() {
    }

    /** While playing on a server: remember it; after half a minute back in, the waits start over. */
    public static void playing(Platform p) {
        String a = p.serverAddress();
        if (a == null || a.isEmpty() || p.localWorld()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!a.equals(address)) {
            inWorldSince = now;
        }
        address = a;
        name = p.serverName();
        if (now - inWorldSince > 30_000) {
            attempt = 0;
        }
        waiting = false;
    }

    /** A server is being joined (by you or by a rejoin): that's the one to come back to. */
    public static void joining(String a, String n) {
        if (a == null || a.isEmpty()) {
            return;
        }
        if (!a.equals(address)) {
            attempt = 0;
        }
        address = a;
        name = n;
        inWorldSince = System.currentTimeMillis();
    }

    /** You left by yourself (Disconnect / Quit): nothing to rejoin. */
    public static void leftOnPurpose() {
        address = null;
        waiting = false;
        attempt = 0;
    }

    /** The server dropped you, with this reason: decide whether and when to rejoin. */
    public static void dropped(String reason) {
        waiting = false;
        why = null;
        if (!enabled || address == null) {
            return;
        }
        String r = reason == null ? "" : reason.toLowerCase();
        if (skipBans) {
            for (String w : NEVER) {
                if (r.contains(w)) {
                    why = "Not rejoining: that looks like a ban or another login";
                    return;
                }
            }
        }
        if (attempt > 0 && !keepTrying) {
            why = "Couldn't get back in";
            return;
        }
        long wait = Math.min(300_000L, Math.max(1, firstWait) * 1000L << Math.min(attempt, 10));
        nextAt = System.currentTimeMillis() + wait;
        waiting = true;
    }

    /** Whether it's time to rejoin now (then the caller joins and this counts the try). */
    public static boolean due() {
        if (!waiting || System.currentTimeMillis() < nextAt) {
            return false;
        }
        waiting = false;
        attempt++;
        return true;
    }

    public static String address() {
        return address;
    }

    public static String name() {
        return name;
    }

    public static boolean waiting() {
        return waiting;
    }

    public static void cancel() {
        waiting = false;
        why = "Auto reconnect stopped";
    }

    /** What the button on the disconnected screen says (null: no button). */
    public static String label() {
        if (waiting) {
            long s = Math.max(0, (nextAt - System.currentTimeMillis() + 999) / 1000);
            String when = s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
            return "Rejoining in " + when + (attempt > 0 ? " (try " + (attempt + 1) + ")" : "") + "  -  click to stop";
        }
        return why;
    }
}
