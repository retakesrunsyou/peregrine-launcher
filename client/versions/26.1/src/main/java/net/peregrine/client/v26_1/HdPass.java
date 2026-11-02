package net.peregrine.client.v26_1;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.modules.HdLook;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

/**
 * HD look in 26.1: one full-screen pass over the finished world (before the hand and HUD).
 * The world is copied aside, then drawn back sharpened. All state changes go through
 * Minecraft's own state tracker, so its drawing afterwards is unaffected; any failure turns the
 * pass off for the session.
 */
public final class HdPass {

    private static final String VERT = "#version 330 core\n"
            + "void main() {\n"
            + "  vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n"
            + "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
            + "}\n";

    // Adaptive sharpening: each pixel is sharpened against its four neighbours by an amount that
    // shrinks where the neighbourhood already spans a lot of contrast, so edges don't ring. Then
    // clarity (midtone contrast against a wider blur) and vibrance (more saturation for dull colors).
    private static final String FRAG = "#version 330 core\n"
            + "uniform sampler2D src;\n"
            + "uniform vec3 amount;\n"
            + "out vec4 color;\n"
            + "const vec3 LUM = vec3(0.2126, 0.7152, 0.0722);\n"
            + "vec3 at(ivec2 p, ivec2 d, ivec2 hi) { return texelFetch(src, clamp(p + d, ivec2(0), hi), 0).rgb; }\n"
            + "void main() {\n"
            + "  ivec2 p = ivec2(gl_FragCoord.xy);\n"
            + "  ivec2 hi = textureSize(src, 0) - 1;\n"
            + "  vec3 c = at(p, ivec2(0, 0), hi);\n"
            + "  vec3 n = at(p, ivec2(0, 1), hi), s = at(p, ivec2(0, -1), hi);\n"
            + "  vec3 e = at(p, ivec2(1, 0), hi), w = at(p, ivec2(-1, 0), hi);\n"
            + "  vec3 lo3 = min(c, min(min(n, s), min(e, w)));\n"
            + "  vec3 hi3 = max(c, max(max(n, s), max(e, w)));\n"
            + "  vec3 room = sqrt(clamp(min(lo3, 1.0 - hi3) / max(hi3, vec3(1e-4)), 0.0, 1.0));\n"
            + "  vec3 k = room * (-1.0 / mix(8.0, 4.5, amount.x));\n"
            + "  vec3 col = amount.x > 0.001 ? clamp((c + (n + s + e + w) * k) / (1.0 + 4.0 * k), 0.0, 1.0) : c;\n"
            + "  if (amount.y > 0.001) {\n"
            + "    vec3 blur = (at(p, ivec2(4, 0), hi) + at(p, ivec2(-4, 0), hi) + at(p, ivec2(0, 4), hi) + at(p, ivec2(0, -4), hi)\n"
            + "      + at(p, ivec2(3, 3), hi) + at(p, ivec2(-3, 3), hi) + at(p, ivec2(3, -3), hi) + at(p, ivec2(-3, -3), hi) + 2.0 * c) / 10.0;\n"
            + "    float l = dot(col, LUM), bl = dot(blur, LUM);\n"
            + "    float mid = 1.0 - abs(l * 2.0 - 1.0);\n"
            + "    col += (l - bl) * amount.y * 0.9 * mid;\n"
            + "  }\n"
            + "  if (amount.z > 0.001) {\n"
            + "    float sat = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));\n"
            + "    float l = dot(col, LUM);\n"
            + "    col = mix(vec3(l), col, 1.0 + amount.z * 0.85 * (1.0 - sat));\n"
            + "  }\n"
            + "  color = vec4(clamp(col, 0.0, 1.0), 1.0);\n"
            + "}\n";

    private static int program, uAmount, uSrc, vao, readFbo, copyFbo, drawFbo, copyTex, copyW, copyH;
    private static boolean broken, doneThisFrame;

    private HdPass() {
    }

    /** A new frame: the pass may run once. */
    public static void newFrame() {
        doneThisFrame = false;
    }

    /** Runs the pass over the main picture (once per frame). */
    public static void apply() {
        float[] a = HdLook.amounts();
        if (a == null || broken || doneThisFrame) {
            return;
        }
        doneThisFrame = true;
        try {
            RenderTarget t = Minecraft.getInstance().getMainRenderTarget();
            if (t == null || !(t.getColorTexture() instanceof GlTexture)) {
                return;
            }
            int src = ((GlTexture) t.getColorTexture()).glId(), w = t.width, h = t.height;
            setup(w, h);
            // copy the world aside
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(15);
            GL45.glNamedFramebufferTexture(readFbo, GL30.GL_COLOR_ATTACHMENT0, src, 0);
            GL45.glBlitNamedFramebuffer(readFbo, copyFbo, 0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            // draw it back, sharpened
            GL45.glNamedFramebufferTexture(drawFbo, GL30.GL_COLOR_ATTACHMENT0, src, 0);
            int oldFbo = GlStateManager.getFrameBuffer(GL30.GL_FRAMEBUFFER);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawFbo);
            GlStateManager._viewport(0, 0, w, h);
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._activeTexture(GL20.GL_TEXTURE0);
            GlStateManager._bindTexture(copyTex);
            GlStateManager._glUniform1i(uSrc, 0);
            GL20.glUniform3f(uAmount, a[0], a[1], a[2]);
            GlStateManager._drawArrays(GL11.GL_TRIANGLES, 0, 3);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
            GlStateManager._depthMask(true);
        } catch (Throwable e) {
            broken = true;
            System.err.println("[Peregrine] HD look turned off: " + e);
        }
    }

    private static void setup(int w, int h) {
        if (program == 0) {
            int vs = shader(GL20.GL_VERTEX_SHADER, VERT), fs = shader(GL20.GL_FRAGMENT_SHADER, FRAG);
            int p = GlStateManager.glCreateProgram();
            GlStateManager.glAttachShader(p, vs);
            GlStateManager.glAttachShader(p, fs);
            GlStateManager.glLinkProgram(p);
            if (GlStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("HD look shader: " + GlStateManager.glGetProgramInfoLog(p, 4096));
            }
            program = p;
            uAmount = GlStateManager._glGetUniformLocation(p, "amount");
            uSrc = GlStateManager._glGetUniformLocation(p, "src");
            vao = GlStateManager._glGenVertexArrays();
            readFbo = GL45.glCreateFramebuffers();
            copyFbo = GL45.glCreateFramebuffers();
            drawFbo = GL45.glCreateFramebuffers();
        }
        if (copyTex == 0 || copyW != w || copyH != h) {
            if (copyTex != 0) {
                GL11.glDeleteTextures(copyTex);
            }
            copyTex = GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
            GL45.glTextureStorage2D(copyTex, 1, GL11.GL_RGBA8, Math.max(1, w), Math.max(1, h));
            GL45.glNamedFramebufferTexture(copyFbo, GL30.GL_COLOR_ATTACHMENT0, copyTex, 0);
            copyW = w;
            copyH = h;
        }
    }

    private static int shader(int type, String src) {
        int s = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(s, src);
        GlStateManager.glCompileShader(s);
        if (GlStateManager.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("HD look shader: " + GlStateManager.glGetShaderInfoLog(s, 4096));
        }
        return s;
    }
}
