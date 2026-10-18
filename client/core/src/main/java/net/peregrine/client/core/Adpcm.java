package net.peregrine.client.core;

/**
 * IMA ADPCM: squeezes 16-bit voice samples to 4 bits each (a quarter of the size) with almost
 * no work, so voice fits easily through the party relay. Each frame starts with the predictor
 * and step index, so a lost packet never garbles the next one.
 */
public final class Adpcm {

    private static final int[] STEPS = {
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
        107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871,
        5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623,
        27086, 29794, 32767};
    private static final int[] INDEX = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};

    private Adpcm() {
    }

    /** Bytes for n samples: 3 header bytes and a nibble per sample. */
    public static int size(int n) {
        return 3 + (n + 1) / 2;
    }

    /** Encodes n samples into out at off. Returns the bytes written. */
    public static int encode(short[] pcm, int n, byte[] out, int off) {
        int predictor = pcm[0], index = 0;
        // a starting step that suits how loud the frame begins
        int diff0 = n > 1 ? Math.abs(pcm[1] - pcm[0]) : 0;
        while (index < 88 && STEPS[index] < diff0) {
            index++;
        }
        out[off] = (byte) predictor;
        out[off + 1] = (byte) (predictor >> 8);
        out[off + 2] = (byte) index;
        int o = off + 3;
        for (int i = 0; i < n; i++) {
            int step = STEPS[index];
            int diff = pcm[i] - predictor;
            int code = 0;
            if (diff < 0) {
                code = 8;
                diff = -diff;
            }
            int delta = step >> 3;
            if (diff >= step) {
                code |= 4;
                diff -= step;
                delta += step;
            }
            step >>= 1;
            if (diff >= step) {
                code |= 2;
                diff -= step;
                delta += step;
            }
            step >>= 1;
            if (diff >= step) {
                code |= 1;
                delta += step;
            }
            predictor += (code & 8) != 0 ? -delta : delta;
            predictor = Math.max(-32768, Math.min(32767, predictor));
            index = Math.max(0, Math.min(88, index + INDEX[code]));
            if ((i & 1) == 0) {
                out[o] = (byte) code;
            } else {
                out[o] |= (byte) (code << 4);
                o++;
            }
        }
        return size(n);
    }

    /** Decodes n samples from in at off. */
    public static void decode(byte[] in, int off, short[] pcm, int n) {
        int predictor = (short) ((in[off] & 0xFF) | (in[off + 1] << 8));
        int index = Math.max(0, Math.min(88, in[off + 2] & 0xFF));
        int o = off + 3;
        for (int i = 0; i < n; i++) {
            int code = (i & 1) == 0 ? in[o] & 0x0F : (in[o++] >> 4) & 0x0F;
            int step = STEPS[index];
            int delta = step >> 3;
            if ((code & 4) != 0) {
                delta += step;
            }
            if ((code & 2) != 0) {
                delta += step >> 1;
            }
            if ((code & 1) != 0) {
                delta += step >> 2;
            }
            predictor += (code & 8) != 0 ? -delta : delta;
            predictor = Math.max(-32768, Math.min(32767, predictor));
            index = Math.max(0, Math.min(88, index + INDEX[code]));
            pcm[i] = (short) predictor;
        }
    }
}
