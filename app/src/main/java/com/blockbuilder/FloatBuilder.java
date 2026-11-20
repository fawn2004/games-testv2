package com.blockbuilder;

/** Growable float array used while building meshes (9 floats per vertex). */
final class FloatBuilder {
    static final int STRIDE = 9;
    float[] data = new float[1 << 15];
    int size;

    void clear() {
        size = 0;
    }

    void ensure(int extra) {
        if (size + extra > data.length) {
            float[] n = new float[Math.max(data.length * 2, size + extra)];
            System.arraycopy(data, 0, n, 0, size);
            data = n;
        }
    }

    /** flags = sway amount (0..1) + 2 if the surface is under water. */
    void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float flags) {
        float[] d = data;
        int s = size;
        d[s] = x; d[s + 1] = y; d[s + 2] = z; d[s + 3] = u; d[s + 4] = v;
        d[s + 5] = r; d[s + 6] = g; d[s + 7] = b; d[s + 8] = flags;
        size = s + 9;
    }
}
