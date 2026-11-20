package com.blockbuilder;

/** Growable float array used while building meshes. */
final class FloatBuilder {
    float[] data = new float[1 << 14];
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

    void vertex(float x, float y, float z, float u, float v, float light) {
        float[] d = data;
        int s = size;
        d[s] = x; d[s + 1] = y; d[s + 2] = z; d[s + 3] = u; d[s + 4] = v; d[s + 5] = light;
        size = s + 6;
    }
}
