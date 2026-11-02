package net.peregrine.client.v26_1;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.modules.Menu3D;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

/**
 * 3D menu in 26.1. While Peregrine's menu or pause menu is open, the menu layer is drawn into
 * a picture of its own instead of onto the screen, and that picture is then drawn onto the
 * frame as a panel in perspective: tilting toward the mouse (the mouse is traced through the
 * tilt, so it points at what it's over), or standing still in the world where it opened while
 * you walk and look around (the crosshair points). Any failure turns it off for the session and
 * menus are flat again.
 */
public final class Panel3D {

    private static TextureTarget target;
    private static boolean redirecting, broken;
    private static int program, vao, drawFbo, uCorner, uTex, uPointer, uSize;
    private static final float VIEW_FOV = 50f;
    // the panel as last drawn, in its camera's space: centre, unit right/up axes, half sizes, and the projection
    private static final float[] C = new float[3], U = new float[3], V = new float[3];
    private static float halfU, halfV, tanX = 1, tanY = 1;
    private static boolean placed;
    // tilt, eased toward the mouse
    private static float yaw, pitch;
    private static long lastFrame;
    // in the world: where the panel stands
    private static Object anchoredFor;
    private static double ax, ay, az;
    private static float anchorYaw, anchorPitch;

    private Panel3D() {
    }

    // ------------------------------------------------------------------ when

    /** A Peregrine menu is open and should be in 3D. */
    public static boolean active() {
        if (broken || Menu3D.get() == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && (mc.screen instanceof PeregrineScreen || mc.screen instanceof PeregrinePauseScreen);
    }

    /** ...and stays in the world (the crosshair points). */
    public static boolean inWorld() {
        Menu3D m = Menu3D.get();
        return active() && m.mode.index == Menu3D.WORLD;
    }

    /** While the menu layer draws: the picture it draws into. */
    public static RenderTarget current() {
        return redirecting ? target : null;
    }

    // ------------------------------------------------------------------ drawing

    /** Before Minecraft draws the menus: send them to the panel's picture. */
    public static void beginGui() {
        if (!active()) {
            return;
        }
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int w = main.width, h = main.height;
            if (target == null) {
                target = new TextureTarget("Peregrine 3D menu", w, h, true);
            } else if (target.width != w || target.height != h) {
                target.resize(w, h);
            }
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
            redirecting = true;
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** After: draw the panel onto the frame. */
    public static void endGui() {
        if (!redirecting) {
            return;
        }
        redirecting = false;
        try {
            composite();
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void composite() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (!(main.getColorTexture() instanceof GlTexture) || !(target.getColorTexture() instanceof GlTexture)) {
            return;
        }
        int w = main.width, h = main.height;
        float aspect = w / (float) Math.max(1, h);
        Menu3D m = Menu3D.get();
        float[] clip = new float[16];
        float[] pointer = {0, 0, 0};
        if (m.mode.index == Menu3D.WORLD) {
            placeInWorld(mc, m, aspect, clip, pointer);
        } else {
            placeTilted(mc, m, aspect, clip);
        }
        if (program == 0) {
            setup();
        }
        int src = ((GlTexture) target.getColorTexture()).glId();
        GL45.glNamedFramebufferTexture(drawFbo, GL30.GL_COLOR_ATTACHMENT0, ((GlTexture) main.getColorTexture()).glId(), 0);
        int oldFbo = GlStateManager.getFrameBuffer(GL30.GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawFbo);
        GlStateManager._viewport(0, 0, w, h);
        GlStateManager._disableScissorTest();
        GlStateManager._colorMask(15);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        GlStateManager._enableBlend();
        // the menu layer is premultiplied (drawn over a clear picture)
        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._glUseProgram(program);
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._activeTexture(GL20.GL_TEXTURE0);
        GlStateManager._bindTexture(src);
        GL20.glUniform1i(uTex, 0);
        GL20.glUniform4fv(uCorner, clip);
        GL20.glUniform3f(uPointer, pointer[0], pointer[1], pointer[2]);
        GL20.glUniform2f(uSize, w, h);
        GlStateManager._drawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GlStateManager._disableBlend();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GlStateManager._depthMask(true);
    }

    /** The panel facing you, turned a little toward the mouse. */
    private static void placeTilted(Minecraft mc, Menu3D m, float aspect, float[] clip) {
        float t = (float) Math.tan(Math.toRadians(VIEW_FOV / 2));
        tanX = t * aspect;
        tanY = t;
        float s = m.size.value / 100f;
        float ox = m.x.value / 100f * tanX, oy = m.y.value / 100f * tanY;
        // where the mouse is, against the panel's middle (-1..1)
        double[] mp = mouseNdc(mc);
        float mx = clamp((float) ((mp[0] - ox / tanX) / s), -1f, 1f), my = clamp((float) ((mp[1] - oy / tanY) / s), -1f, 1f);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float k = Math.min(1f, dt * 9f);
        float tilt = m.tilt.value;
        yaw += (tilt * mx - yaw) * k;
        pitch += (-tilt * my - pitch) * k;
        set(C, ox, oy, -1f);
        axes(yaw, pitch);
        halfU = tanX * s;
        halfV = tanY * s;
        corners(clip);
        placed = true;
    }

    /** The panel standing in the world where it opened (Alt carries it along with your view). */
    private static void placeInWorld(Minecraft mc, Menu3D m, float aspect, float[] clip, float[] pointer) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 e = cam.position();
        float camYaw = cam.yRot(), camPitch = cam.xRot();
        boolean carry = altDown(mc);
        if (anchoredFor != mc.screen || carry) {
            anchoredFor = mc.screen;
            float[] q = FrameGen.cameraToWorld(camYaw, camPitch);
            double d = m.distance.value;   // straight ahead: camera's -z in the world
            ax = e.x - q[2] * d;
            ay = e.y - q[5] * d;
            az = e.z - q[8] * d;
            anchorYaw = camYaw;
            anchorPitch = camPitch;
        }
        float fov = mc.options.fov().get().floatValue();
        float t = (float) Math.tan(Math.toRadians(fov / 2));
        tanX = t * aspect;
        tanY = t;
        // the panel's axes in the world, then in the camera's view
        float[] pa = FrameGen.cameraToWorld(anchorYaw, anchorPitch);
        float[] cam2w = FrameGen.cameraToWorld(camYaw, camPitch);
        float hv = (float) (m.distance.value * Math.tan(Math.toRadians(fov / 2))) * m.size.value / 100f;
        halfV = hv;
        halfU = hv * aspect;
        float[] centerW = {(float) (ax - e.x), (float) (ay - e.y), (float) (az - e.z)};
        float[] rightW = {pa[0], pa[3], pa[6]}, upW = {pa[1], pa[4], pa[7]};
        toCamera(cam2w, centerW, C);
        toCamera(cam2w, rightW, U);
        toCamera(cam2w, upW, V);
        corners(clip);
        placed = true;
        // the crosshair: straight ahead
        float[] uv = hit(0, 0, -1);
        if (uv != null) {
            pointer[0] = uv[0];
            pointer[1] = uv[1];
            pointer[2] = 1f;
        }
    }

    private static void corners(float[] clip) {
        // strip order: bottom left, bottom right, top left, top right
        int[][] sgn = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            float x = C[0] + U[0] * halfU * sgn[i][0] + V[0] * halfV * sgn[i][1];
            float y = C[1] + U[1] * halfU * sgn[i][0] + V[1] * halfV * sgn[i][1];
            float z = C[2] + U[2] * halfU * sgn[i][0] + V[2] * halfV * sgn[i][1];
            clip[i * 4] = x / tanX;
            clip[i * 4 + 1] = y / tanY;
            clip[i * 4 + 2] = 0f;
            clip[i * 4 + 3] = -z;
        }
    }

    /** Right and up of a panel turned by yaw (about up) then pitch (about right), degrees. */
    private static void axes(float yawDeg, float pitchDeg) {
        double a = Math.toRadians(yawDeg), b = Math.toRadians(pitchDeg);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
        // R = Ry(a) * Rx(b); right = R*(1,0,0), up = R*(0,1,0)
        set(U, ca, 0f, -sa);
        set(V, sa * sb, cb, ca * sb);
    }

    // ------------------------------------------------------------------ pointing

    /** Where a ray from the eye (camera space) meets the panel: {u, v} from the bottom left, or null. */
    private static float[] hit(float dx, float dy, float dz) {
        if (!placed) {
            return null;
        }
        float[] n = cross(U, V);
        float dn = dx * n[0] + dy * n[1] + dz * n[2];
        if (Math.abs(dn) < 1e-6f) {
            return null;
        }
        float t = (C[0] * n[0] + C[1] * n[1] + C[2] * n[2]) / dn;
        if (t <= 0) {
            return null;
        }
        float px = dx * t - C[0], py = dy * t - C[1], pz = dz * t - C[2];
        float su = (px * U[0] + py * U[1] + pz * U[2]) / halfU, sv = (px * V[0] + py * V[1] + pz * V[2]) / halfV;
        return new float[] {su * 0.5f + 0.5f, sv * 0.5f + 0.5f};
    }

    /**
     * Where on the menu the pointer is, in the menu's own coordinates (width x height): the mouse
     * traced through the tilt, or the crosshair in the world. {-1000, -1000} when it's off the panel.
     */
    public static double[] pointer(double mouseX, double mouseY, int width, int height) {
        if (!active() || !placed) {
            return new double[] {mouseX, mouseY};
        }
        float[] uv;
        if (inWorld()) {
            uv = hit(0, 0, -1);
        } else {
            float nx = (float) (mouseX / Math.max(1, width) * 2 - 1), ny = (float) (1 - mouseY / Math.max(1, height) * 2);
            uv = hit(nx * tanX, ny * tanY, -1);
        }
        if (uv == null || uv[0] < 0 || uv[0] > 1 || uv[1] < 0 || uv[1] > 1) {
            return new double[] {-1000, -1000};
        }
        return new double[] {uv[0] * width, (1 - uv[1]) * height};
    }

    private static double[] mouseNdc(Minecraft mc) {
        double x = mc.mouseHandler.xpos(), y = mc.mouseHandler.ypos();
        int w = mc.getWindow().getScreenWidth(), h = mc.getWindow().getScreenHeight();
        return new double[] {x / Math.max(1, w) * 2 - 1, 1 - y / Math.max(1, h) * 2};
    }

    public static boolean altDown(Minecraft mc) {
        long win = mc.getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    public static boolean ctrlDown(Minecraft mc) {
        long win = mc.getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    // ------------------------------------------------------------------ in the world: keep playing

    /** With the panel in the world, the mouse stays captured (to look around) and movement keys keep working. */
    public static void keepPlaying() {
        if (!inWorld()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive()) {
            // captured without closing the menu (Minecraft's own capture would close it)
            InputConstants.grabOrReleaseMouse(mc.getWindow(), GLFW.GLFW_CURSOR_DISABLED,
                    mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0);
            ((net.peregrine.client.v26_1.mixin.MouseGrabAccessor) mc.mouseHandler).peregrine$setGrabbed(true);
            net.minecraft.client.KeyMapping.setAll();
        }
    }

    /** A key the menu didn't use goes to the game (walking, jumping...) while the panel is in the world. */
    public static void passKey(net.minecraft.client.input.KeyEvent e, boolean down) {
        if (inWorld()) {
            net.minecraft.client.KeyMapping.set(InputConstants.getKey(e), down);
        }
    }

    // ------------------------------------------------------------------ GL

    private static final String VERT = "#version 330 core\n"
            + "uniform vec4 corner[4];\n"
            + "out vec2 uv;\n"
            + "void main() {\n"
            + "  uv = vec2(gl_VertexID & 1, gl_VertexID >> 1);\n"
            + "  gl_Position = corner[gl_VertexID];\n"
            + "}\n";

    // filtered by hand (texelFetch), so no sampler state of Minecraft's is touched
    private static final String FRAG = "#version 330 core\n"
            + "uniform sampler2D tex;\n"
            + "uniform vec3 pointer;\n"
            + "uniform vec2 size;\n"
            + "in vec2 uv;\n"
            + "out vec4 color;\n"
            + "vec4 at(ivec2 p) { return texelFetch(tex, clamp(p, ivec2(0), ivec2(size) - 1), 0); }\n"
            + "void main() {\n"
            + "  vec2 p = uv * size - 0.5;\n"
            + "  ivec2 i = ivec2(floor(p));\n"
            + "  vec2 f = p - vec2(i);\n"
            + "  vec4 c = mix(mix(at(i), at(i + ivec2(1, 0)), f.x), mix(at(i + ivec2(0, 1)), at(i + ivec2(1, 1)), f.x), f.y);\n"
            + "  if (pointer.z > 0.5) {\n"
            + "    float d = length((uv - pointer.xy) * size);\n"
            + "    float ring = smoothstep(7.0, 5.5, d) * smoothstep(3.0, 4.5, d);\n"
            + "    float dot = smoothstep(2.2, 1.2, d);\n"
            + "    float a = max(ring * 0.9, dot);\n"
            + "    c = vec4(mix(c.rgb, vec3(1.0), a), max(c.a, a));\n"
            + "  }\n"
            + "  color = c;\n"
            + "}\n";

    private static void setup() {
        int vs = shader(GL20.GL_VERTEX_SHADER, VERT), fs = shader(GL20.GL_FRAGMENT_SHADER, FRAG);
        int p = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(p, vs);
        GlStateManager.glAttachShader(p, fs);
        GlStateManager.glLinkProgram(p);
        if (GlStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("3D menu shader: " + GlStateManager.glGetProgramInfoLog(p, 4096));
        }
        program = p;
        uCorner = GlStateManager._glGetUniformLocation(p, "corner");
        uTex = GlStateManager._glGetUniformLocation(p, "tex");
        uPointer = GlStateManager._glGetUniformLocation(p, "pointer");
        uSize = GlStateManager._glGetUniformLocation(p, "size");
        vao = GlStateManager._glGenVertexArrays();
        drawFbo = GL45.glCreateFramebuffers();
    }

    private static int shader(int type, String src) {
        int s = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(s, src);
        GlStateManager.glCompileShader(s);
        if (GlStateManager.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("3D menu shader: " + GlStateManager.glGetShaderInfoLog(s, 4096));
        }
        return s;
    }

    private static void fail(Throwable e) {
        redirecting = false;
        if (!broken) {
            broken = true;
            System.err.println("[Peregrine] 3D menu turned off: " + e);
        }
    }

    // ------------------------------------------------------------------ small maths

    /** World direction to camera space (q: row-major camera-to-world). */
    private static void toCamera(float[] q, float[] w, float[] out) {
        out[0] = q[0] * w[0] + q[3] * w[1] + q[6] * w[2];
        out[1] = q[1] * w[0] + q[4] * w[1] + q[7] * w[2];
        out[2] = q[2] * w[0] + q[5] * w[1] + q[8] * w[2];
    }

    private static void set(float[] v, float x, float y, float z) {
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
