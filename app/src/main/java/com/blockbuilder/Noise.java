package com.blockbuilder;

/** Small seeded 2D value noise with fractal octaves (double inputs, so it stays smooth far from the origin). */
final class Noise {
    private final long seed;

    Noise(long seed) {
        this.seed = seed;
    }

    float hash(long x, long z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    float value(double x, double z) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        float fx = (float) (x - ix), fz = (float) (z - iz);
        float sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        float a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        float ab = a + (b - a) * sx, cd = c + (d - c) * sx;
        return ab + (cd - ab) * sz;
    }

    /** Fractal noise in [0,1]. */
    float fbm(double x, double z, int octaves) {
        float sum = 0, amp = 1, norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += value(x * f + i * 17.3, z * f - i * 9.1) * amp;
            norm += amp;
            amp *= 0.5f;
            f *= 2;
        }
        return sum / norm;
    }
}
