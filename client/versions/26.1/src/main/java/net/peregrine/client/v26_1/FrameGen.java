package net.peregrine.client.v26_1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.LockSupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.modules.FrameGeneration;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Frame generation (beta). Minecraft normally draws a frame and shows it on the same thread,
 * so extra frames could only come right after each real one. Instead, when this is on at
 * start-up, the game draws on a hidden window's context that shares everything with the real
 * window, and a separate "presenter" thread owns the real window: it shows each frame the game
 * finishes and, until the next one is ready, shows it again turned to where your view is heading
 * (from how fast you were turning), evenly spaced. Your HUD and hand stay put: wherever the
 * finished frame differs from the world alone, that part isn't turned.
 * <p>
 * Every frame the game finishes is copied into one of three shared pictures (the world alone and
 * the finished frame), handed over with GPU fences so neither side waits on the other's work.
 * If anything fails the presenter falls back to just showing the frames as they come.
 */
public final class FrameGen {

    private static long window, hidden;
    /** The contexts were split at start-up (frame generation can run this session). */
    static volatile boolean active;
    private static volatile boolean running, broken, started, dead;
    private static final Object LOCK = new Object();
    private static GLCapabilities gameCaps, windowCaps;
    private static int fallbackFbo;
    private static Thread presenter;
    private static volatile int swapInterval = 1;

    /** One handed-over frame. Written by the game, read by the presenter (the volatile index publishes it). */
    private static final class Slot {
        int texWorld, texFinal, fboWorld, fboFinal, w, h;
        volatile long ready, done;
        float yaw, pitch, fov, yawSpeed, pitchSpeed;
        long time;
        boolean warp, hasWorld;
    }

    private static final Slot[] SLOTS = {new Slot(), new Slot(), new Slot()};
    private static volatile int published = -1, showing = -1;
    // the game's side (render thread only)
    private static int writing = -1, readFbo;
    private static boolean worldDone;
    private static float lastYaw, lastPitch, yawSpeed, pitchSpeed;
    private static long lastTime;
    // what the presenter measured
    private static volatile int shown;
    private static volatile long shownAt;

    private FrameGen() {
    }

    /** Frames shown per second (or -1 when frame generation isn't running). */
    public static int shownFps() {
        return running && !broken && FrameGeneration.multiplier() > 1 && System.nanoTime() - shownAt < 2_000_000_000L ? shown : -1;
    }

    // ------------------------------------------------------------------ start-up (render thread)

    /** Was frame generation switched on when the game last saved its settings? */
    private static boolean wanted() {
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("peregrine-client.json");
            if (!Files.isRegularFile(f)) {
                return false;
            }
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement m = o.has("modules") ? o.getAsJsonObject("modules").get("frame_gen") : null;
            return m != null && m.isJsonObject() && m.getAsJsonObject().has("enabled") && m.getAsJsonObject().get("enabled").getAsBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Instead of the game's window context: a hidden window's context sharing with it (when wanted and possible). */
    public static void makeCurrent(long w) {
        if (!wanted()) {
            GLFW.glfwMakeContextCurrent(w);
            return;
        }
        long h = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            h = GLFW.glfwCreateWindow(16, 16, "Peregrine", 0L, w);
            if (h == 0) {
                throw new IllegalStateException("no shared context");
            }
            GLFW.glfwMakeContextCurrent(h);
            GLCapabilities caps = GL.createCapabilities();
            if (!caps.OpenGL45) {
                throw new IllegalStateException("needs OpenGL 4.5");
            }
            window = w;
            hidden = h;
            active = true;
            System.out.println("[Peregrine] frame generation ready (the game draws on a shared context)");
        } catch (Throwable e) {
            System.err.println("[Peregrine] frame generation off: " + e.getMessage());
            GLFW.glfwMakeContextCurrent(w);
            if (h != 0) {
                GLFW.glfwDestroyWindow(h);
            }
            active = false;
        }
    }

    /** Vsync: the presenter swaps the real window, so it takes the setting. */
    public static void swapInterval(int interval) {
        if (active) {
            swapInterval = interval;
        } else {
            GLFW.glfwSwapInterval(interval);
        }
    }

    // ------------------------------------------------------------------ the game's side (render thread)

    /** Before the hand and HUD are drawn: keep the world alone. */
    public static void captureWorld() {
        if (!active || broken || worldDone) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget t = mc.getMainRenderTarget();
            if (t == null || !(t.getColorTexture() instanceof GlTexture)) {
                return;
            }
            Slot s = slot(t.width, t.height);
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(15);
            copy(((GlTexture) t.getColorTexture()).glId(), s.fboWorld, s.w, s.h);
            s.hasWorld = true;
            worldDone = true;
            Camera c = mc.gameRenderer.getMainCamera();
            long now = System.nanoTime();
            float yaw = c.yRot(), pitch = c.xRot();
            if (lastTime != 0) {
                float dt = Math.max(1e-4f, (now - lastTime) / 1e9f);
                float dy = wrap(yaw - lastYaw), dp = pitch - lastPitch;
                if (Math.abs(dy) > 60 || Math.abs(dp) > 60 || dt > 0.25f) {   // a teleport or a long pause: no guessing
                    yawSpeed = pitchSpeed = 0;
                } else {
                    yawSpeed = yawSpeed * 0.35f + (dy / dt) * 0.65f;
                    pitchSpeed = pitchSpeed * 0.35f + (dp / dt) * 0.65f;
                }
            }
            lastYaw = yaw;
            lastPitch = pitch;
            lastTime = now;
            s.yaw = yaw;
            s.pitch = pitch;
            s.yawSpeed = yawSpeed;
            s.pitchSpeed = pitchSpeed;
            s.fov = mc.options.fov().get().floatValue();
            s.time = now;
            s.warp = mc.screen == null || !mc.screen.isPauseScreen();
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** The finished frame (instead of Minecraft copying it to the window): true if taken. */
    public static boolean capturePresent(GpuTextureView view) {
        if (!active) {   // (even after a problem: this is how frames reach the screen at all now)
            return false;
        }
        try {
            if (!(view.texture() instanceof GlTexture)) {
                return false;
            }
            Slot s = slot(view.getWidth(0), view.getHeight(0));
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(15);
            copy(((GlTexture) view.texture()).glId(), s.fboFinal, s.w, s.h);
            if (!worldDone) {   // no world this frame (a menu): show it as it is
                s.hasWorld = false;
                s.warp = false;
                s.time = System.nanoTime();
            }
            s.ready = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();
            return true;
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /** Instead of swapping the window: hand the frame to the presenter. */
    public static void present(long handle) {
        if (!active) {
            GLFW.glfwSwapBuffers(handle);
            return;
        }
        if (!started) {
            gameCaps = GL.getCapabilities();
            start();
        }
        if (writing < 0 || SLOTS[writing].ready == 0) {
            // the finished frame didn't come through Minecraft's copy to the window: take it here
            try {
                RenderTarget t = Minecraft.getInstance().getMainRenderTarget();
                if (t != null && t.getColorTextureView() != null) {
                    capturePresent(t.getColorTextureView());
                }
            } catch (Throwable e) {
                fail(e);
            }
        }
        if (writing >= 0 && SLOTS[writing].ready != 0) {
            published = writing;
        }
        writing = -1;
        worldDone = false;
        if (dead) {
            showHere();
            return;
        }
        // with vsync, don't run ahead of the screen: wait (briefly) until this frame has been shown
        if (swapInterval != 0 && running) {
            long until = System.nanoTime() + 40_000_000L;
            int p = published;
            while (running && p >= 0 && showing != p && System.nanoTime() < until) {
                LockSupport.parkNanos(200_000L);
            }
        }
    }

    /** The slot this frame goes into (one the presenter isn't using), sized to the frame. */
    private static Slot slot(int w, int h) {
        if (writing < 0) {
            synchronized (LOCK) {   // never one the presenter has or is about to take
                int pub = published, show = showing;
                for (int i = 0; i < 3; i++) {
                    int k = (Math.max(pub, 0) + 1 + i) % 3;
                    if (k != pub && k != show) {
                        writing = k;
                        break;
                    }
                }
            }
            Slot s = SLOTS[writing];
            long done = s.done;
            if (done != 0) {   // the presenter's last look at it has to finish first (on the GPU)
                GL32.glWaitSync(done, 0, GL32.GL_TIMEOUT_IGNORED);
                GL32.glDeleteSync(done);
                s.done = 0;
            }
            long ready = s.ready;
            if (ready != 0) {
                GL32.glDeleteSync(ready);
                s.ready = 0;
            }
            s.hasWorld = false;
        }
        Slot s = SLOTS[writing];
        if (s.w != w || s.h != h || s.texWorld == 0) {
            if (s.texWorld != 0) {
                GL11.glDeleteTextures(s.texWorld);
                GL11.glDeleteTextures(s.texFinal);
            }
            s.texWorld = texture(w, h);
            s.texFinal = texture(w, h);
            if (s.fboWorld == 0) {
                s.fboWorld = GL45.glCreateFramebuffers();
                s.fboFinal = GL45.glCreateFramebuffers();
            }
            GL45.glNamedFramebufferTexture(s.fboWorld, GL30.GL_COLOR_ATTACHMENT0, s.texWorld, 0);
            GL45.glNamedFramebufferTexture(s.fboFinal, GL30.GL_COLOR_ATTACHMENT0, s.texFinal, 0);
            s.w = w;
            s.h = h;
        }
        return s;
    }

    private static int texture(int w, int h) {
        int t = GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(t, 1, GL11.GL_RGBA8, Math.max(1, w), Math.max(1, h));
        GL45.glTextureParameteri(t, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL45.glTextureParameteri(t, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL45.glTextureParameteri(t, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL45.glTextureParameteri(t, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return t;
    }

    /** Copies a texture into a slot's picture without touching anything Minecraft has bound. */
    private static void copy(int src, int dstFbo, int w, int h) {
        if (readFbo == 0) {
            readFbo = GL45.glCreateFramebuffers();
        }
        GL45.glNamedFramebufferTexture(readFbo, GL30.GL_COLOR_ATTACHMENT0, src, 0);
        GL45.glBlitNamedFramebuffer(readFbo, dstFbo, 0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /**
     * The presenter stopped: show frames from the game's thread instead (switching to the
     * window's context for a moment each frame), so the screen never freezes.
     */
    private static void showHere() {
        int p = published;
        if (p < 0) {
            return;
        }
        Slot s = SLOTS[p];
        try {
            GLFW.glfwMakeContextCurrent(window);
            if (windowCaps == null) {
                windowCaps = GL.createCapabilities();
                fallbackFbo = GL45.glCreateFramebuffers();
            } else {
                GL.setCapabilities(windowCaps);
            }
            GLFW.glfwSwapInterval(swapInterval);
            long ready = s.ready;
            if (ready != 0) {
                GL32.glWaitSync(ready, 0, GL32.GL_TIMEOUT_IGNORED);
            }
            GL45.glNamedFramebufferTexture(fallbackFbo, GL30.GL_COLOR_ATTACHMENT0, s.texFinal, 0);
            GL45.glBlitNamedFramebuffer(fallbackFbo, 0, 0, 0, s.w, s.h, 0, 0, s.w, s.h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GLFW.glfwSwapBuffers(window);
        } catch (Throwable e) {
            System.err.println("[Peregrine] couldn't show a frame: " + e);
        } finally {
            GLFW.glfwMakeContextCurrent(hidden);
            GL.setCapabilities(gameCaps);
        }
    }

    private static void fail(Throwable e) {
        if (!broken) {
            broken = true;
            System.err.println("[Peregrine] frame generation stopped adding frames: " + e);
        }
    }

    // ------------------------------------------------------------------ the presenter

    private static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        running = true;
        presenter = new Thread(FrameGen::presenterLoop, "Peregrine frames");
        presenter.setDaemon(true);
        presenter.setPriority(Thread.MAX_PRIORITY);
        presenter.start();
    }

    /** Stops the presenter before the window goes away. */
    public static void shutdown() {
        if (!running || presenter == null) {
            return;
        }
        running = false;
        try {
            presenter.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final String VERT = "#version 330 core\n"
            + "out vec2 uv;\n"
            + "void main() {\n"
            + "  vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n"
            + "  uv = p;\n"
            + "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
            + "}\n";

    // the world turned by R (from where you look now back to where the frame was drawn), the HUD
    // and hand (wherever the finished frame differs from the world alone) left where they are
    private static final String FRAG = "#version 330 core\n"
            + "in vec2 uv;\n"
            + "out vec4 color;\n"
            + "uniform sampler2D world;\n"
            + "uniform sampler2D finished;\n"
            + "uniform mat3 turn;\n"
            + "uniform vec2 tanHalf;\n"
            + "uniform float warp;\n"
            + "void main() {\n"
            + "  vec4 f = texture(finished, uv);\n"
            + "  if (warp < 0.5) { color = vec4(f.rgb, 1.0); return; }\n"
            + "  vec4 w0 = texture(world, uv);\n"
            + "  vec3 d = abs(f.rgb - w0.rgb);\n"
            + "  if (max(d.r, max(d.g, d.b)) > 0.012) { color = vec4(f.rgb, 1.0); return; }\n"
            + "  vec2 ndc = uv * 2.0 - 1.0;\n"
            + "  vec3 dir = turn * vec3(ndc * tanHalf, -1.0);\n"
            + "  vec2 q = dir.z < -1e-4 ? (dir.xy / -dir.z) / tanHalf : ndc;\n"
            + "  vec2 src = clamp(q * 0.5 + 0.5, vec2(0.0), vec2(1.0));\n"
            + "  color = vec4(texture(world, src).rgb, 1.0);\n"
            + "}\n";

    private static int program, uTurn, uTan, uWarp, vao;

    private static void presenterLoop() {
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            setup();
            int interval = -1;
            long realInterval = 16_666_667L, lastFresh = 0, lastShow = 0;
            int repeats = 0, counted = 0;
            long countFrom = System.nanoTime();
            while (running) {
                if (interval != swapInterval) {
                    interval = swapInterval;
                    GLFW.glfwSwapInterval(interval);
                }
                int times = broken ? 1 : FrameGeneration.multiplier();
                int p;
                boolean fresh;
                synchronized (LOCK) {
                    p = published;
                    fresh = p >= 0 && p != showing;
                    if (fresh) {
                        showing = p;
                    }
                }
                long now = System.nanoTime();
                if (!fresh) {
                    // the same frame again, turned: only while there's still room before the next real one
                    if (p < 0 || repeats >= times - 1 || now < lastShow + realInterval / times) {
                        LockSupport.parkNanos(250_000L);
                        continue;
                    }
                }
                Slot s = SLOTS[fresh ? p : showing];
                if (fresh) {
                    long ready = s.ready;
                    if (ready != 0) {
                        GL32.glWaitSync(ready, 0, GL32.GL_TIMEOUT_IGNORED);
                    }
                    if (lastFresh != 0) {
                        long gap = Math.min(250_000_000L, now - lastFresh);
                        realInterval = (realInterval * 3 + gap) / 4;
                    }
                    lastFresh = now;
                    repeats = 0;
                } else {
                    repeats++;
                }
                draw(s, fresh ? 0f : (now - s.time) / 1e9f, Math.min(2.5f * realInterval / 1e9f, 0.1f), !broken);
                long old = s.done;
                s.done = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                if (old != 0) {
                    GL32.glDeleteSync(old);
                }
                GLFW.glfwSwapBuffers(window);
                lastShow = System.nanoTime();
                counted++;
                if (lastShow - countFrom >= 1_000_000_000L) {
                    shown = (int) Math.round(counted * 1e9 / (lastShow - countFrom));
                    shownAt = lastShow;
                    counted = 0;
                    countFrom = lastShow;
                }
            }
        } catch (Throwable e) {
            System.err.println("[Peregrine] frame generation presenter stopped: " + e);
            broken = true;
            running = false;
            dead = true;
        } finally {
            try {
                GLFW.glfwMakeContextCurrent(0L);
            } catch (Throwable ignored) {
                // leaving anyway
            }
        }
    }

    private static void setup() {
        int vs = shader(GL20.GL_VERTEX_SHADER, VERT), fs = shader(GL20.GL_FRAGMENT_SHADER, FRAG);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("presenter shader: " + GL20.glGetProgramInfoLog(program));
        }
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "world"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "finished"), 1);
        uTurn = GL20.glGetUniformLocation(program, "turn");
        uTan = GL20.glGetUniformLocation(program, "tanHalf");
        uWarp = GL20.glGetUniformLocation(program, "warp");
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
    }

    private static int shader(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("presenter shader: " + GL20.glGetShaderInfoLog(s));
        }
        return s;
    }

    /** Draws a slot to the window, turned by how far the view will have moved after dt seconds. */
    private static void draw(Slot s, float dt, float maxAhead, boolean warping) {
        GL11.glViewport(0, 0, s.w, s.h);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        GL45.glBindTextureUnit(0, s.texWorld);
        GL45.glBindTextureUnit(1, s.texFinal);
        float ahead = Math.max(0f, Math.min(dt, maxAhead));
        boolean warp = warping && s.warp && s.hasWorld && ahead > 0f;
        float yawNow = s.yaw + clamp(s.yawSpeed * ahead, -25f, 25f);
        float pitchNow = Math.max(-90f, Math.min(90f, s.pitch + clamp(s.pitchSpeed * ahead, -25f, 25f)));
        GL20.glUniformMatrix3fv(uTurn, false, turn(s.yaw, s.pitch, yawNow, pitchNow));
        float ty = (float) Math.tan(Math.toRadians(Math.max(30f, Math.min(110f, s.fov)) / 2.0));
        GL20.glUniform2f(uTan, ty * s.w / Math.max(1f, s.h), ty);
        GL20.glUniform1f(uWarp, warp ? 1f : 0f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /**
     * The turn from a direction seen from the view now (yaw1, pitch1) to the same direction seen
     * from when the frame was drawn (yaw0, pitch0), as a column-major 3x3 matrix. Minecraft's
     * camera is turned by Ry(180 - yaw) then Rx(-pitch).
     */
    static float[] turn(float yaw0, float pitch0, float yaw1, float pitch1) {
        float[] q0 = cameraToWorld(yaw0, pitch0), q1 = cameraToWorld(yaw1, pitch1);
        float[] r = new float[9];   // r = transpose(q0) * q1, both row-major here
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float v = 0;
                for (int k = 0; k < 3; k++) {
                    v += q0[k * 3 + i] * q1[k * 3 + j];
                }
                r[j * 3 + i] = v;   // column-major for GL
            }
        }
        return r;
    }

    /** Row-major rotation from the camera's own directions to the world's. */
    static float[] cameraToWorld(float yaw, float pitch) {
        double a = Math.PI - Math.toRadians(yaw), b = -Math.toRadians(pitch);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
        // Ry(a) * Rx(b)
        return new float[] {
            ca, sa * sb, sa * cb,
            0, cb, -sb,
            -sa, ca * sb, ca * cb,
        };
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
