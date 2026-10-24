package net.peregrine.client.core.knife;

/** A 4x4 transform (column-major, column vectors), made for chaining: a.mul(b) applies b first. */
public final class M4 {

    public final float[] m = new float[16];

    public M4() {
        identity();
    }

    public M4(M4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
    }

    public M4 identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    public M4 set(M4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    /** this = this * o */
    public M4 mul(M4 o) {
        float[] a = m, b = o.m, r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int rr = 0; rr < 4; rr++) {
                r[c * 4 + rr] = a[rr] * b[c * 4] + a[4 + rr] * b[c * 4 + 1] + a[8 + rr] * b[c * 4 + 2] + a[12 + rr] * b[c * 4 + 3];
            }
        }
        System.arraycopy(r, 0, m, 0, 16);
        return this;
    }

    public M4 translate(float x, float y, float z) {
        M4 t = new M4();
        t.m[12] = x;
        t.m[13] = y;
        t.m[14] = z;
        return mul(t);
    }

    public M4 scale(float s) {
        return scale(s, s, s);
    }

    public M4 scale(float x, float y, float z) {
        M4 t = new M4();
        t.m[0] = x;
        t.m[5] = y;
        t.m[10] = z;
        return mul(t);
    }

    public M4 rotX(float deg) {
        if (deg == 0) {
            return this;
        }
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg));
        M4 t = new M4();
        t.m[5] = c;
        t.m[6] = s;
        t.m[9] = -s;
        t.m[10] = c;
        return mul(t);
    }

    public M4 rotY(float deg) {
        if (deg == 0) {
            return this;
        }
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg));
        M4 t = new M4();
        t.m[0] = c;
        t.m[2] = -s;
        t.m[8] = s;
        t.m[10] = c;
        return mul(t);
    }

    public M4 rotZ(float deg) {
        if (deg == 0) {
            return this;
        }
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg));
        M4 t = new M4();
        t.m[0] = c;
        t.m[1] = s;
        t.m[4] = -s;
        t.m[5] = c;
        return mul(t);
    }

    /** Turns about an axis (unit) through the origin. */
    public M4 rot(float deg, float ax, float ay, float az) {
        if (deg == 0) {
            return this;
        }
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg)), t1 = 1 - c;
        M4 t = new M4();
        t.m[0] = c + ax * ax * t1;
        t.m[1] = ay * ax * t1 + az * s;
        t.m[2] = az * ax * t1 - ay * s;
        t.m[4] = ax * ay * t1 - az * s;
        t.m[5] = c + ay * ay * t1;
        t.m[6] = az * ay * t1 + ax * s;
        t.m[8] = ax * az * t1 + ay * s;
        t.m[9] = ay * az * t1 - ax * s;
        t.m[10] = c + az * az * t1;
        return mul(t);
    }

    /** Columns are where the x, y and z axes go. */
    public M4 basis(float[] x, float[] y, float[] z) {
        M4 t = new M4();
        for (int i = 0; i < 3; i++) {
            t.m[i] = x[i];
            t.m[4 + i] = y[i];
            t.m[8 + i] = z[i];
        }
        return mul(t);
    }

    public void point(float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    /** A direction (no move), renormalised (the transforms here only scale evenly). */
    public void dir(float x, float y, float z, float[] out) {
        float a = m[0] * x + m[4] * y + m[8] * z;
        float b = m[1] * x + m[5] * y + m[9] * z;
        float c = m[2] * x + m[6] * y + m[10] * z;
        float l = (float) Math.sqrt(a * a + b * b + c * c);
        if (l < 1e-9f) {
            l = 1;
        }
        out[0] = a / l;
        out[1] = b / l;
        out[2] = c / l;
    }

    /** Mirrors left and right (for players whose main hand is the left). */
    public static M4 mirrorX() {
        return new M4().scale(-1, 1, 1);
    }
}
