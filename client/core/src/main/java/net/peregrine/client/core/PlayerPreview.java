package net.peregrine.client.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Your player on the main menu and in the Locker, drawn from your skin and Peregrine cape.
 * It turns (drag it, or click to spin it round), sways gently when left alone, and shows the
 * cape from behind. Drawn the same way as the launcher's Locker, so both look alike.
 *
 * The skin comes from the launcher's copy (skins/<your id>.png in Peregrine's data folder).
 */
public final class PlayerPreview {

    private String skinId;
    private boolean legacy, slim;
    /** A skin being tried on in the Locker (shown instead of yours until cleared). */
    private String tryId;
    private boolean trySlim, tryLegacy;
    private long checkedAt;
    private String forUuid;

    private float angle = 20f;      // degrees: 0 faces you, 180 shows the back
    private float velocity;         // degrees per second, after a drag
    private float target = Float.NaN;
    private boolean dragging;
    private int dragX;
    private long lastFrame;
    private long idleSince = System.currentTimeMillis();

    private static float[][] faces(boolean slim, boolean legacy) {
        int aw = slim ? 3 : 4;
        // per part: front, back, overlay front, overlay back as {x, y, w, h} in the 64x64 layout
        return new float[][] {
            {8, 8, 8, 8, 24, 8, 8, 8, 40, 8, 8, 8, 56, 8, 8, 8},                                   // head
            {20, 20, 8, 12, 32, 20, 8, 12, 20, 36, 8, 12, 32, 36, 8, 12},                          // body
            {44, 20, aw, 12, 48 + aw, 20, aw, 12, 44, 36, aw, 12, 48 + aw, 36, aw, 12},            // right arm
            {36, 52, aw, 12, 40 + aw, 52, aw, 12, 52, 52, aw, 12, 56 + aw, 52, aw, 12},            // left arm
            {4, 20, 4, 12, 12, 20, 4, 12, 4, 36, 4, 12, 12, 36, 4, 12},                            // right leg
            {20, 52, 4, 12, 28, 52, 4, 12, 4, 52, 4, 12, 12, 52, 4, 12},                           // left leg
        };
    }

    private void loadSkin(Platform p) {
        String uuid = p.playerUuid();
        long now = System.currentTimeMillis();
        if (uuid != null && uuid.equals(forUuid) && now - checkedAt < 30000) {
            return;
        }
        checkedAt = now;
        forUuid = uuid;
        String data = System.getProperty("peregrine.data");
        if (uuid != null && data != null && !data.isEmpty()) {
            String plain = uuid.replace("-", "");
            for (String name : new String[] {plain, uuid}) {
                Path f = Paths.get(data, "skins", name + ".png");
                if (Files.isRegularFile(f) && use(p, f)) {
                    return;
                }
            }
        }
        if (skinId == null) {
            Path f = defaultSkin(p);
            if (f != null) {
                use(p, f);
            }
        }
    }

    private boolean use(Platform p, Path f) {
        int[] size = TitleMenu.pngSize(f);
        if (size == null) {
            return false;
        }
        String id = p.loadImage(f);
        if (id == null) {
            return false;
        }
        legacy = size[1] * 2 == size[0];
        slim = !legacy && isSlim(f);
        skinId = id;
        return true;
    }

    /** Minecraft's own Steve, for offline accounts and before your skin has been fetched. */
    private static Path defaultSkin(Platform p) {
        for (String res : new String[] {"/assets/minecraft/textures/entity/player/wide/steve.png",
                "/assets/minecraft/textures/entity/steve.png"}) {
            try (java.io.InputStream in = PlayerPreview.class.getResourceAsStream(res)) {
                if (in == null) {
                    continue;
                }
                Path out = Files.createTempFile("peregrine-steve", ".png");
                out.toFile().deleteOnExit();
                Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return out;
            } catch (Exception e) {
                // try the next place
            }
        }
        return null;
    }

    private static boolean isSlim(Path f) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f.toFile());
            if (img == null || img.getWidth() < 64) {
                return false;
            }
            int k = img.getWidth() / 64, clear = 0;
            for (int y = 20; y < 32; y++) {
                if ((img.getRGB(54 * k, y * k) >>> 24) == 0) {
                    clear++;
                }
            }
            return clear >= 10;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shows a skin from the Locker on the player, without wearing it. */
    public void tryOn(String id, boolean slim, boolean legacy) {
        tryId = id;
        trySlim = slim;
        tryLegacy = legacy;
    }

    public void stopTrying() {
        tryId = null;
    }

    /** Reads your skin file again (after wearing a new one). */
    public void reload() {
        checkedAt = 0;
        forUuid = null;
    }

    public boolean ready(Platform p) {
        loadSkin(p);
        return skinId != null;
    }

    // ---------------------------------------------------------------- turning

    public void press(int mx) {
        dragging = true;
        dragX = mx;
        velocity = 0;
        target = Float.NaN;
    }

    public void drag(int mx) {
        if (!dragging) {
            return;
        }
        float d = (mx - dragX) * 2.2f;
        angle += d;
        velocity = d * 30f;
        dragX = mx;
        idleSince = System.currentTimeMillis();
    }

    public void release() {
        dragging = false;
        idleSince = System.currentTimeMillis();
    }

    /** Spins round to show the front or the back. */
    public void spin() {
        float a = ((angle % 360f) + 360f) % 360f;
        target = angle + (a > 90 && a < 270 ? 360f - a : 180f - a);
        idleSince = System.currentTimeMillis();
    }

    /** Turns to show the back (to see a cape you just picked). */
    public void showBack() {
        float a = ((angle % 360f) + 360f) % 360f;
        target = angle + (180f - a);
        idleSince = System.currentTimeMillis();
    }

    private void step() {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (dragging) {
            return;
        }
        if (!Float.isNaN(target)) {
            angle += (target - angle) * Math.min(1f, dt * 7f);
            if (Math.abs(target - angle) < 0.3f) {
                angle = target;
                target = Float.NaN;
            }
            return;
        }
        if (Math.abs(velocity) > 1f) {
            angle += velocity * dt;
            velocity *= (float) Math.pow(0.04, dt);
            return;
        }
        // left alone for a few seconds: drift back to a gentle sway facing you
        if (now - idleSince > 4000) {
            float a = ((angle % 360f) + 360f) % 360f;
            float rest = 20f * (float) Math.sin(now / 1600.0);
            float diff = rest - (a > 180 ? a - 360 : a);
            angle += diff * Math.min(1f, dt * 1.5f);
        }
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the player standing in the box x, y, w, h. */
    public void draw(Draw d, Platform p, int x, int y, int w, int h, int capeIndex) {
        step();
        loadSkin(p);
        float s = Math.min(w / 18f, h / 34f);
        int cx = x + w / 2;
        int top = y + Math.round((h - 32 * s) / 2) - 2;
        float rad = (float) Math.toRadians(angle);
        float k = (float) Math.cos(rad);
        boolean back = k < 0;
        float squash = Math.max(0.06f, Math.abs(k));
        // shadow on the floor
        int sw = Math.round(9 * s * Math.max(0.55f, squash));
        d.roundRect(cx - sw, top + Math.round(32 * s) - 1, sw * 2, Math.max(2, Math.round(s * 1.2f)), 2, 0x50000000);
        String useId = tryId != null ? tryId : skinId;
        boolean useSlim = tryId != null ? trySlim : slim, useLegacy = tryId != null ? tryLegacy : legacy;
        if (useId == null) {
            String n = p.playerName();
            d.text(n, cx - d.width(n) / 2, y + h / 2 - 4, Theme.MUTED, false);
            return;
        }
        if (d.supportsQuads()) {  // the real thing: a 3D player you can turn all the way round
            String capeFile3d = capeIndex > 0 && capeIndex < net.peregrine.client.core.modules.Cosmetics.FILES.length
                    ? net.peregrine.client.core.modules.Cosmetics.FILES[capeIndex] : null;
            String capeTex = capeFile3d == null ? null
                    : "peregrine-client:textures/cape/" + net.peregrine.client.core.modules.Cosmetics.frame(capeFile3d) + ".png";
            float s3 = s * 0.92f;
            Model3D.draw(d, useId, useSlim, useLegacy, capeTex, cx, top + 32 * (s - s3) / 2, s3, angle, -10f,
                    (System.currentTimeMillis() % 100000L) / 1000f);
            return;
        }
        float tw = 64f, th = useLegacy ? 32f : 64f;
        float[][] f = faces(useSlim, useLegacy);
        int aw = useSlim ? 3 : 4;
        // where each part sits (skin pixels from the figure's left edge, seen from the front)
        float[][] place = back
                ? new float[][] {{4, 0}, {4, 8}, {12, 8}, {4 - aw, 8}, {8, 20}, {4, 20}}
                : new float[][] {{4, 0}, {4, 8}, {4 - aw, 8}, {12, 8}, {4, 20}, {8, 20}};
        int[] order = {4, 5, 1, 2, 3, 0};
        for (int part : order) {
            drawFace(d, useId, f, part, back ? 1 : 0, place[part], cx, top, s, squash, tw, th, useLegacy, 0f);
        }
        String capeFile = capeIndex > 0 && capeIndex < net.peregrine.client.core.modules.Cosmetics.FILES.length
                ? net.peregrine.client.core.modules.Cosmetics.FILES[capeIndex] : null;
        if (back && capeFile != null) {
            int x0 = Math.round(cx + (3 - 8) * s * squash);
            d.texture("peregrine-client:textures/cape/" + net.peregrine.client.core.modules.Cosmetics.frame(capeFile) + ".png", x0, Math.round(top + 8 * s),
                    Math.max(1, Math.round(10 * s * squash)), Math.round(16 * s),
                    1f / 64, 1f / 32, 11f / 64, 17f / 32, 0xFFFFFFFF);
        }
        for (int part : order) {
            if (useLegacy && part != 0) {
                continue;  // old skins have only the hat layer
            }
            if (back && capeFile != null && part == 1) {
                continue;  // the jacket's back is under the cape
            }
            drawFace(d, useId, f, part, back ? 3 : 2, place[part], cx, top, s, squash, tw, th, useLegacy, part == 0 ? 0.6f : 0.4f);
        }
        // a touch of shade as it turns side-on
        if (squash < 0.7f) {
            int a = Math.round((0.7f - squash) / 0.7f * 0x50);
            int fw = Math.round(16 * s * squash);
            d.rect(cx - fw / 2, top, fw, Math.round(32 * s), Theme.withAlpha(0, a));
        }
    }

    /** A skin seen flat from the front, standing in x, y, w, h (the Locker's skin cards). */
    public static void drawFront(Draw d, String id, boolean slim, boolean legacy, int x, int y, int w, int h) {
        float s = Math.min(w / 16f, h / 32f);
        int cx = x + w / 2;
        int top = y + Math.round((h - 32 * s) / 2);
        float[][] f = faces(slim, legacy);
        int aw = slim ? 3 : 4;
        float[][] place = {{4, 0}, {4, 8}, {4 - aw, 8}, {12, 8}, {4, 20}, {8, 20}};
        int[] order = {4, 5, 1, 2, 3, 0};
        float th = legacy ? 32f : 64f;
        for (int part : order) {
            drawFace(d, id, f, part, 0, place[part], cx, top, s, 1f, 64f, th, legacy, 0f);
        }
        for (int part : order) {
            if (!legacy || part == 0) {
                drawFace(d, id, f, part, 2, place[part], cx, top, s, 1f, 64f, th, legacy, part == 0 ? 0.6f : 0.4f);
            }
        }
    }

    private static void drawFace(Draw d, String skinId, float[][] f, int part, int which, float[] at, int cx, int top, float s,
                                 float squash, float tw, float th, boolean legacy, float inflate) {
        boolean mirror = false;
        float[] src = f[part];
        if (legacy && (part == 3 || part == 5)) {  // old skins: left limbs are the right ones flipped
            src = f[part - 1];
            mirror = true;
        }
        float fx = src[which * 4], fy = src[which * 4 + 1], fw = src[which * 4 + 2], fh = src[which * 4 + 3];
        float px = (at[0] - 8 - inflate) * s * squash;
        float pw = (fw + 2 * inflate) * s * squash;
        int x0 = Math.round(cx + px);
        int x1 = Math.round(cx + px + pw);
        int y0 = Math.round(top + (at[1] - inflate) * s);
        int y1 = Math.round(top + (at[1] + fh + inflate) * s);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        float u0 = fx / tw, u1 = (fx + fw) / tw;
        if (mirror) {
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        d.texture(skinId, x0, y0, x1 - x0, y1 - y0, u0, fy / th, u1, (fy + fh) / th, 0xFFFFFFFF);
    }
}
