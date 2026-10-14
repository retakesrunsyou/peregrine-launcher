package net.peregrine.client.v1_21_6;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.peregrine.client.core.Hooks;

/**
 * Nick hider for styled text: finds your name in a piece of text and swaps in the
 * nick, keeping the colors and styles. Text without your name is left untouched
 * (and costs a single pass to check).
 */
public final class Nick {

    private Nick() {
    }

    private static int[] codePoints(String s) {
        return s.codePoints().toArray();
    }

    /** Where real appears in cps (start indexes). */
    private static List<Integer> find(int[] cps, int n, int[] real) {
        List<Integer> at = new ArrayList<Integer>();
        outer:
        for (int i = 0; i + real.length <= n; i++) {
            for (int j = 0; j < real.length; j++) {
                if (cps[i + j] != real[j]) {
                    continue outer;
                }
            }
            at.add(i);
            i += real.length - 1;
        }
        return at;
    }

    public static FormattedCharSequence apply(FormattedCharSequence seq) {
        String realName = Hooks.nickReal, nick = Hooks.nick;
        if (seq == null || nick == null || realName == null || realName.isEmpty() || nick.contains(realName)) {
            return seq;
        }
        final List<Style> styles = new ArrayList<Style>();
        final int[][] buf = {new int[32]};
        final int[] n = {0};
        seq.accept((index, style, cp) -> {
            if (n[0] == buf[0].length) {
                buf[0] = java.util.Arrays.copyOf(buf[0], n[0] * 2);
            }
            buf[0][n[0]++] = cp;
            styles.add(style);
            return true;
        });
        int[] cps = buf[0];
        int[] real = codePoints(realName);
        List<Integer> at = find(cps, n[0], real);
        if (at.isEmpty()) {
            return seq;
        }
        if (Hooks.textHits < 1000) {
            Hooks.textHits++;
        }
        int[] nk = codePoints(nick);
        final List<Integer> outCp = new ArrayList<Integer>();
        final List<Style> outSt = new ArrayList<Style>();
        int next = 0;
        for (int start : at) {
            for (int i = next; i < start; i++) {
                outCp.add(cps[i]);
                outSt.add(styles.get(i));
            }
            Style s = styles.get(start);
            for (int c : nk) {
                outCp.add(c);
                outSt.add(s);
            }
            next = start + real.length;
        }
        for (int i = next; i < n[0]; i++) {
            outCp.add(cps[i]);
            outSt.add(styles.get(i));
        }
        return sink -> {
            for (int i = 0; i < outCp.size(); i++) {
                if (!sink.accept(i, outSt.get(i), outCp.get(i))) {
                    return false;
                }
            }
            return true;
        };
    }

    /** How many times your name is in a piece of styled text. */
    public static int count(FormattedCharSequence seq) {
        String realName = Hooks.nickReal, nick = Hooks.nick;
        if (seq == null || nick == null || realName == null || realName.isEmpty() || nick.contains(realName)) {
            return 0;
        }
        StringBuilder b = new StringBuilder();
        seq.accept((index, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        return count(b.toString());
    }

    public static int count(String s) {
        String realName = Hooks.nickReal, nick = Hooks.nick;
        if (s == null || nick == null || realName == null || realName.isEmpty() || nick.contains(realName)) {
            return 0;
        }
        int c = 0;
        for (int i = s.indexOf(realName); i >= 0; i = s.indexOf(realName, i + realName.length())) {
            c++;
        }
        return c;
    }
}
