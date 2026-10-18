package net.peregrine.client.core;

import java.util.List;
import java.util.Locale;

import net.peregrine.client.core.modules.VoiceHud;

/** Voice chat in the Right Shift menu: start or join a party, see who's in it and who's talking. */
final class VoicePanel extends MenuPanel {

    private String code = "";
    private boolean typing;

    VoicePanel(Peregrine pc, Menu menu) {
        super(pc, menu);
    }

    @Override
    String placeholder() {
        return "";
    }

    private VoiceHud module() {
        return (VoiceHud) pc.module("voice");
    }

    @Override
    boolean typed(char c) {
        if (!typing) {
            return false;
        }
        char u = Character.toUpperCase(c);
        if (code.length() < 6 && ((u >= 'A' && u <= 'Z') || (u >= '0' && u <= '9'))) {
            code += u;
        }
        return true;
    }

    @Override
    boolean key(int key) {
        if (!typing) {
            return false;
        }
        if (key == Menu.KEY_BACKSPACE) {
            code = code.isEmpty() ? code : code.substring(0, code.length() - 1);
            return true;
        }
        if (key == Menu.KEY_ENTER) {
            if (code.length() == 6) {
                VoiceChat.join(code, pc.platform());
                typing = false;
            }
            return true;
        }
        if (key == Menu.KEY_ESCAPE) {
            typing = false;
            return true;
        }
        return false;
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int a) {
        d.textScaled("Voice chat", x, y + 1, Theme.TEXT, 1.25f, true);
        boolean in = VoiceChat.inParty();
        String state = in ? "In a party" : VoiceChat.connecting() ? "Joining..." : "Not in a party";
        int sw = d.width(state) + 16;
        d.roundRect(x + w - sw, y, sw, 13, 6, in ? Theme.withAlpha(Theme.GOOD, 0x30) : 0x24FFFFFF);
        d.roundRect(x + w - sw + 5, y + 5, 3, 3, 1, in ? Theme.GOOD : Theme.FAINT);
        d.text(state, x + w - sw + 11, y + 3, in ? Theme.TEXT : Theme.MUTED, false);
        int top = y + 22;
        String st = VoiceChat.status();
        if (!st.isEmpty()) {
            top += banner(d, x, top, w, st, st.startsWith("Couldn't") || st.startsWith("No ") || st.contains("lost") ? Theme.BAD : a);
        }
        if (!VoiceChat.available()) {
            banner(d, x, top, w, "Voice needs the Peregrine service: start the game from the launcher", Theme.BAD);
            return;
        }
        if (in) {
            drawParty(d, mx, my, dt, now, a, top);
        } else {
            drawJoin(d, mx, my, dt, now, a, top);
        }
    }

    private void drawJoin(Draw d, int mx, int my, float dt, long now, int a, int top) {
        int half = (w - 8) / 2;
        // start a party
        Glass.pane(d, x, top, half, 74, 6, Glass.PANE);
        d.text("Start a party", x + 10, top + 9, Theme.TEXT, false);
        d.text("You get a code to", x + 10, top + 22, Theme.FAINT, false);
        d.text("share with friends", x + 10, top + 32, Theme.FAINT, false);
        float g = glow("start", in(mx, my, x + 10, top + 49, half - 20, 16), dt);
        Glass.pill(d, x + 10, top + 49, half - 20, 16, true, g, a);
        MenuIcons.draw(d, "mic", x + 16, top + 53, Theme.onColor(a));
        d.text("Start", x + 10 + (half - 20 - d.width("Start")) / 2 + 4, top + 53, Theme.onColor(a), false);
        spot("start", x + 10, top + 49, half - 20, 16);
        // join one
        int jx = x + half + 8, jw = w - half - 8;
        Glass.pane(d, jx, top, jw, 74, 6, Glass.PANE);
        d.text("Join a party", jx + 10, top + 9, Theme.TEXT, false);
        int bx = jx + 10, by = top + 24, bw = jw - 20;
        d.roundRect(bx, by, bw, 18, 5, 0x60000000);
        d.roundOutline(bx, by, bw, 18, 5, typing ? a : 0x30FFFFFF);
        String shown = code.isEmpty() && !typing ? "Type the code" : spaced(code) + (typing && (now / 500) % 2 == 0 ? "_" : "");
        d.text(shown, bx + (bw - d.width(shown)) / 2, by + 5, code.isEmpty() && !typing ? Theme.FAINT : Theme.TEXT, false);
        spot("code", bx, by, bw, 18);
        boolean ready = code.length() == 6;
        float jg = glow("join", ready && in(mx, my, jx + 10, top + 49, jw - 20, 16), dt);
        Glass.pill(d, jx + 10, top + 49, jw - 20, 16, ready, jg, a);
        d.text("Join", jx + 10 + (jw - 20 - d.width("Join")) / 2, top + 53, ready ? Theme.onColor(a) : Theme.FAINT, false);
        if (ready) {
            spot("join", jx + 10, top + 49, jw - 20, 16);
        }
        if (VoiceChat.connecting()) {
            dots(d, x + w / 2 - 5, top + 84, now, a);
        }
        int iy = top + 96;
        VoiceHud m = module();
        String how = m != null && m.mode.index == 1 ? "Push to talk: hold " + Keys.name(m.talkKey.key) : "Your mic turns on when you speak";
        d.text(how, x, iy, Theme.MUTED, false);
        d.text("Change it in Modules > HUD > Voice chat", x, iy + 11, Theme.FAINT, false);
        d.text("Up to 8 players. Voice passes through the Peregrine service", x, iy + 28, Theme.FAINT, false);
        d.text("and is never stored or recorded.", x, iy + 39, Theme.FAINT, false);
    }

    private static String spaced(String c) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < c.length(); i++) {
            if (i == 3) {
                b.append(' ');
            }
            b.append(c.charAt(i));
        }
        return b.toString();
    }

    private void drawParty(Draw d, int mx, int my, float dt, long now, int a, int top) {
        // the code, big
        Glass.pane(d, x, top, w, 34, 6, Glass.PANE);
        d.text("Party code", x + 10, top + 6, Theme.FAINT, false);
        d.text("Friends join with it", x + 10, top + 18, Theme.FAINT, false);
        String c = spaced(VoiceChat.party());
        int cw = Math.round(d.width(c) * 2f);
        d.textScaled(c, x + w - 12 - cw, top + 9, a, 2f, true);
        top += 40;
        // you: mic level, mute, leave
        boolean muted = VoiceChat.micMuted;
        String mt = muted ? "Unmute" : "Mute mic";
        int mw = d.width(mt) + 26;
        float mg = glow("mute", in(mx, my, x, top, mw, 16), dt);
        Glass.pill(d, x, top, mw, 16, muted, mg, a);
        MenuIcons.draw(d, "mic", x + 6, top + 4, muted ? Theme.onColor(a) : Theme.TEXT);
        if (muted) {
            for (int i = 0; i < 9; i++) {
                d.rect(x + 6 + i, top + 4 + i, 1, 1, Theme.BAD);
            }
        }
        d.text(mt, x + 19, top + 4, muted ? Theme.onColor(a) : Theme.TEXT, false);
        spot("mute", x, top, mw, 16);
        String lv = "Leave party";
        int lw = d.width(lv) + 14, lx = x + w - lw;
        float lg = glow("leave", in(mx, my, lx, top, lw, 16), dt);
        d.roundRect(lx, top, lw, 16, 7, Theme.withAlpha(Theme.BAD, Math.round(0x18 + 0x50 * lg)));
        d.roundOutline(lx, top, lw, 16, 7, Theme.mix(Theme.withAlpha(Theme.BAD, 0x90), Theme.BAD, lg));
        d.text(lv, lx + 7, top + 4, Theme.mix(0xFFE8B4B4, 0xFFFFFFFF, lg), false);
        spot("leave", lx, top, lw, 16);
        // the mic meter between them
        int mxx = x + mw + 10, mww = lx - 10 - mxx;
        if (mww > 30) {
            d.roundRect(mxx, top + 6, mww, 4, 2, 0x30FFFFFF);
            int lvl = Math.round(mww * VoiceChat.micLevel);
            if (lvl > 0) {
                d.roundRect(mxx, top + 6, Math.max(4, lvl), 4, 2, VoiceChat.talking() ? Theme.GOOD : Theme.withAlpha(a, 0x90));
            }
        }
        top += 24;
        // who's here
        List<VoiceChat.Member> ms = VoiceChat.members();
        d.text("In the party · " + ms.size() + " / 8", x, top, Theme.MUTED, false);
        top += 12;
        int rowH = 22, gap = 4;
        int listH = y + h - top;
        maxScroll = Math.max(0, ms.size() * (rowH + gap) - listH);
        menu.clipArea(d, x, top, w + 6, listH);
        for (int i = 0; i < ms.size(); i++) {
            VoiceChat.Member m = ms.get(i);
            int ry = top + i * (rowH + gap) - Math.round(smooth);
            boolean me = m.id == VoiceChat.myId();
            boolean talking = me ? VoiceChat.talking() : m.talking();
            float tg = glow("t:" + m.id, talking, dt * 2f);
            Glass.pane(d, x, ry, w, rowH, 4, Theme.mix(Glass.PANE, Theme.withAlpha(Theme.GOOD, 0x40), tg * 0.6f));
            if (tg > 0.01f) {
                d.roundOutline(x, ry, w, rowH, 4, Theme.withAlpha(Theme.GOOD, Math.round(0xC0 * tg)));
            }
            int[] face = SkinFaces.get(m.uuid);
            if (face != null) {
                SkinFaces.draw(d, face, x + 6, ry + 5, 12);
            } else {
                d.roundRect(x + 6, ry + 5, 12, 12, 3, Theme.withAlpha(a, 0x50));
            }
            d.text(m.name, x + 24, ry + 7, Theme.TEXT, false);
            if (me) {
                int tx = x + 30 + d.width(m.name);
                d.roundRect(tx, ry + 5, d.width("You") + 8, 12, 4, Theme.withAlpha(a, 0x38));
                d.text("You", tx + 4, ry + 7, a, false);
            }
            boolean muted2 = me ? VoiceChat.micMuted : m.muted;
            String right = muted2 ? "Muted" : talking ? "Talking" : "";
            d.text(right, x + w - 8 - d.width(right), ry + 7, muted2 ? Theme.BAD : Theme.GOOD, false);
        }
        menu.unclipArea(d);
    }

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        Platform p = pc.platform();
        if (id.equals("code")) {
            typing = true;
            return true;
        }
        typing = false;
        if (id.equals("start")) {
            VoiceChat.join(VoiceChat.newCode(), p);
        } else if (id.equals("join")) {
            VoiceChat.join(code.toUpperCase(Locale.ROOT), p);
        } else if (id.equals("mute")) {
            VoiceChat.setMicMuted(!VoiceChat.micMuted);
        } else if (id.equals("leave")) {
            VoiceChat.leave();
        } else {
            return false;
        }
        return true;
    }
}
