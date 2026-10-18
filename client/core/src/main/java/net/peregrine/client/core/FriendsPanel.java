package net.peregrine.client.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Your friends (your Xbox friends, the same list Minecraft uses): who's online and what they're
 * playing. The launcher keeps the list fresh while it runs; the game reads what it saved.
 */
final class FriendsPanel extends MenuPanel {

    static final class Friend {
        String tag = "", status = "", picture = "";
        boolean online, minecraft;
    }

    private List<Friend> friends = new ArrayList<Friend>();
    private long updated, readAt, stamp = -1;
    private String filter = "";

    FriendsPanel(Peregrine pc, Menu menu) {
        super(pc, menu);
    }

    @Override
    String placeholder() {
        return "Find a friend...";
    }

    @Override
    void searchChanged(String text) {
        filter = text.toLowerCase(Locale.ROOT);
        scroll = 0;
    }

    static Path file() {
        String data = System.getProperty("peregrine.data");
        return data != null && !data.isEmpty() ? Paths.get(data, "client", "friends.json")
                : Library.gameDir().resolve("peregrine-friends.json");
    }

    private void read() {
        long now = System.currentTimeMillis();
        if (now - readAt < 3000) {
            return;
        }
        readAt = now;
        Path f = file();
        try {
            long m = Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (m == stamp) {
                return;
            }
            stamp = m;
            List<Friend> out = new ArrayList<Friend>();
            if (m != 0) {
                JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                updated = o.has("updated") ? o.get("updated").getAsLong() * 1000 : m;
                for (JsonElement e : o.getAsJsonArray("friends")) {
                    JsonObject fo = e.getAsJsonObject();
                    Friend fr = new Friend();
                    fr.tag = Library.str(fo, "gamertag");
                    fr.status = Library.str(fo, "status");
                    fr.picture = Library.str(fo, "picture");
                    fr.online = fo.has("online") && fo.get("online").getAsBoolean();
                    fr.minecraft = fo.has("minecraft") && fo.get("minecraft").getAsBoolean();
                    out.add(fr);
                }
            }
            friends = out;
        } catch (Exception e) {
            // a half-written file: try again in a moment
            stamp = -1;
        }
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int a) {
        read();
        List<Friend> online = new ArrayList<Friend>(), offline = new ArrayList<Friend>();
        for (Friend f : friends) {
            if (!filter.isEmpty() && !f.tag.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            (f.online ? online : offline).add(f);
        }
        d.textScaled("Friends", x, y + 1, Theme.TEXT, 1.25f, true);
        String count = online.size() + " online";
        d.text(count, x + Math.round(d.width("Friends") * 1.25f) + 6, y + 3, a, false);
        String when = updated == 0 ? "" : "Updated " + ago(now - updated);
        d.text(when, x + w - d.width(when), y + 3, Theme.FAINT, false);
        int top = y + 20;
        if (friends.isEmpty()) {
            String t = stamp == 0 ? "Open the launcher's Friends page once to see them here" : "No friends yet";
            d.text(d.trim(t, w), x + (w - Math.min(w, d.width(t))) / 2, y + h / 2 - 10, Theme.MUTED, false);
            String t2 = "Your Xbox friends, the same list Minecraft uses";
            d.text(d.trim(t2, w), x + (w - Math.min(w, d.width(t2))) / 2, y + h / 2 + 2, Theme.FAINT, false);
            return;
        }
        if (updated != 0 && now - updated > 10 * 60_000L) {
            top += banner(d, x, top, w, "Keep the launcher open to keep this list fresh", a);
        }
        int listTop = top, listH = y + h - top;
        int rowH = 26, gap = 4;
        int total = (online.isEmpty() ? 0 : 14 + online.size() * (rowH + gap)) + (offline.isEmpty() ? 0 : 14 + offline.size() * (rowH + gap));
        maxScroll = Math.max(0, total - listH);
        menu.clipArea(d, x, listTop, w + 6, listH);
        int cy = listTop - Math.round(smooth);
        int i = 0;
        for (int part = 0; part < 2; part++) {
            List<Friend> l = part == 0 ? online : offline;
            if (l.isEmpty()) {
                continue;
            }
            d.text(part == 0 ? "Online" : "Offline", x + 2, cy + 2, Theme.MUTED, false);
            cy += 14;
            for (Friend f : l) {
                float ap = appear(i++);
                Draw rd = ap < 1f ? new FadeDraw(d, 0.1f + 0.9f * ap) : d;
                int ry = cy + Math.round((1f - ap) * 6);
                boolean hover = in(mx, my, x, ry, w, rowH);
                float g = glow("f:" + f.tag, hover, dt);
                Glass.pane(rd, x, ry, w, rowH, 4, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
                icon(rd, Library.webIcon(f.picture), f.tag, x + 5, ry + 4, 18, a);
                // a status dot on the picture's corner
                int dot = f.online ? (f.minecraft ? a : Theme.GOOD) : Theme.FAINT;
                rd.roundRect(x + 18, ry + 17, 7, 7, 3, 0xFF0C0E13);
                rd.roundRect(x + 19, ry + 18, 5, 5, 2, dot);
                rd.text(rd.trim(f.tag, w / 2), x + 30, ry + 4, f.online ? Theme.TEXT : Theme.MUTED, false);
                String s = f.online ? (f.status.isEmpty() ? "Online" : f.status) : "Offline";
                rd.text(rd.trim(s, w - 40), x + 30, ry + 15, f.minecraft ? a : Theme.FAINT, false);
                cy += rowH + gap;
            }
        }
        menu.unclipArea(d);
        menu.scrollbar(d, listTop, listH, scroll, maxScroll, smooth, a);
    }

    static String ago(long ms) {
        long m = ms / 60_000L;
        return m < 1 ? "just now" : m < 60 ? m + " min ago" : m / 60 < 24 ? (m / 60) + " h ago" : (m / 1440) + " days ago";
    }

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        return false;
    }
}
