package net.peregrine.client.core;

/** Shulker preview: the box's 27 slots drawn next to the mouse, like a chest. */
public final class ContainerPreview {

    private ContainerPreview() {
    }

    /**
     * stacks/counts: one entry per slot (null = empty). tint: the box's color (RGB).
     * Drawn above everything at the mouse, kept on screen.
     */
    public static void draw(Draw d, String title, Object[] stacks, int[] counts, int mx, int my, int sw, int sh, int tint) {
        int cols = 9, rows = Math.max(1, (stacks.length + 8) / 9);
        int cell = 18, pad = 5;
        int w = cols * cell + pad * 2;
        int head = title == null || title.isEmpty() ? 0 : d.lineHeight() + 4;
        int h = rows * cell + pad * 2 + head;
        int x = mx + 12, y = my - 12;
        if (x + w > sw - 2) {
            x = mx - 12 - w;
        }
        x = Math.max(2, x);
        y = Math.max(2, Math.min(y, sh - h - 2));
        int c = 0xFF000000 | tint;
        d.shadow(x, y, w, h);
        d.roundRect(x, y, w, h, 4, 0xF0101218);
        d.roundOutline(x, y, w, h, 4, Theme.withAlpha(c, 0xC0));
        d.rect(x + 4, y, w - 8, 2, c);
        if (head > 0) {
            d.text(d.trim(title, w - 2 * pad), x + pad, y + pad, Theme.TEXT, false);
        }
        int gy = y + pad + head;
        for (int i = 0; i < stacks.length; i++) {
            int cx = x + pad + (i % cols) * cell, cy = gy + (i / cols) * cell;
            d.rect(cx, cy, cell - 1, cell - 1, 0x40FFFFFF);
            if (stacks[i] != null) {
                d.item(stacks[i], cx, cy);
                if (counts[i] > 1) {
                    String n = String.valueOf(counts[i]);
                    d.text(n, cx + 17 - d.width(n), cy + 9, 0xFFFFFFFF, true);
                }
            }
        }
        if (Hooks.previewHits < 1000) {
            Hooks.previewHits++;
        }
    }
}
