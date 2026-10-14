package net.peregrine.client.v26_1;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.Hooks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** World to screen: where a 3D point shows up on the HUD (GUI pixels). */
public final class Proj {

    /** The world's field of view (degrees) as last drawn. */
    public static volatile float fov = 70f;

    private Proj() {
    }

    public static float[] project(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null) {
            return null;
        }
        Vec3 c = cam.position();
        Vector3f v = new Vector3f((float) (x - c.x), (float) (y - c.y), (float) (z - c.z));
        float dist = v.length();
        v.rotate(new Quaternionf(cam.rotation()).conjugate());  // into the camera's view: forward is -z
        if (v.z > -0.05f) {
            return null;
        }
        float f = (float) (1.0 / Math.tan(Math.toRadians(cam.getFov()) / 2.0));
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float nx = v.x / -v.z * f / aspect * Hooks.stretch;
        float ny = v.y / -v.z * f;
        return new float[] {(nx + 1f) / 2f * w, (1f - ny) / 2f * h, dist};
    }
}
