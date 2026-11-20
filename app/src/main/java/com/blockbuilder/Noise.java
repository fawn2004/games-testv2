package com.blockbuilder;

/** Small seeded 2D value noise with fractal octaves. */
final class Noise {
    private final long seed;

    Noise(long seed) {
        this.seed = seed;
    }

    private float hash(int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    float value(float x, float z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float fx = x - ix, fz = z - iz;
        float sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        float a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        float ab = a + (b - a) * sx, cd = c + (d - c) * sx;
        return ab + (cd - ab) * sz;
    }

    /** Fractal noise in [0,1]. */
    float fbm(float x, float z, int octaves) {
        float sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += value(x * f + i * 17.3f, z * f - i * 9.1f) * amp;
            norm += amp;
            amp *= 0.5f;
            f *= 2f;
        }
        return sum / norm;
    }
}
