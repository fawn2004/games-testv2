package com.blockbuilder;

import java.util.Random;

/** Procedural tree shapes. Everything is written through a Sink that clips to one chunk. */
final class Trees {
    interface Sink {
        /** Block currently at a position (AIR outside the area being built). */
        int get(int x, int y, int z);

        void set(int x, int y, int z, int b, int meta);
    }

    /** Farthest a tree reaches from its trunk, so chunks know which neighbours' trees to replay. */
    static final int REACH = 8;

    static void grow(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        switch (t.shape) {
            case Biome.BROAD: broad(s, t, x, y, z, r); break;
            case Biome.SLENDER: slender(s, t, x, y, z, r); break;
            case Biome.CONE: cone(s, t, x, y, z, r); break;
            case Biome.PINE: pine(s, t, x, y, z, r); break;
            case Biome.GIANT: giant(s, t, x, y, z, r); break;
            case Biome.TROPICAL_TREE: tropical(s, t, x, y, z, r); break;
            case Biome.SAVANNA: savanna(s, t, x, y, z, r); break;
            case Biome.CEDAR: cedar(s, t, x, y, z, r); break;
            case Biome.CYPRESS: cypress(s, t, x, y, z, r); break;
            case Biome.SHRUB: shrub(s, t, x, y, z, r); break;
            case Biome.PALM: palm(s, t, x, y, z, r); break;
            default: round(s, t, x, y, z, r);
        }
    }

    private static void log(Sink s, Biome.Tree t, int x, int y, int z) {
        if (y > 0 && y < World.SY) s.set(x, y, z, t.log, 0);
    }

    private static void leaf(Sink s, Biome.Tree t, int x, int y, int z) {
        if (y <= 0 || y >= World.SY) return;
        int cur = s.get(x, y, z);
        if (cur == Blocks.AIR || Blocks.shape(cur) == Shapes.CROSS) s.set(x, y, z, t.leaves, t.leafTint);
    }

    private static void trunk(Sink s, Biome.Tree t, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) log(s, t, x, y + i, z);
    }

    /** A rough ellipsoid of leaves. */
    private static void blob(Sink s, Biome.Tree t, int cx, int cy, int cz, float rx, float ry, float rz, Random r) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        for (int dx = -ix; dx <= ix; dx++)
            for (int dy = -iy; dy <= iy; dy++)
                for (int dz = -iz; dz <= iz; dz++) {
                    float d = (dx * dx) / (rx * rx + 0.01f) + (dy * dy) / (ry * ry + 0.01f) + (dz * dz) / (rz * rz + 0.01f);
                    if (d > 1.0f || (d > 0.7f && r.nextFloat() < 0.35f)) continue;
                    leaf(s, t, cx + dx, cy + dy, cz + dz);
                }
    }

    private static void round(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 4 + r.nextInt(3);
        trunk(s, t, x, y, z, h);
        blob(s, t, x, y + h - 1, z, 2.4f, 2.0f, 2.4f, r);
        leaf(s, t, x, y + h + 1, z);
    }

    private static void broad(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 5 + r.nextInt(3);
        trunk(s, t, x, y, z, h);
        // a few branches with their own clumps of leaves
        int branches = 2 + r.nextInt(3);
        for (int i = 0; i < branches; i++) {
            int dx = r.nextInt(5) - 2, dz = r.nextInt(5) - 2, by = h - 2 + r.nextInt(2);
            int bx = x, bz = z;
            for (int k = 1; k <= 2; k++) {
                bx = x + dx * k / 2;
                bz = z + dz * k / 2;
                log(s, t, bx, y + by + k - 1, bz);
            }
            blob(s, t, bx, y + by + 2, bz, 2.2f, 1.6f, 2.2f, r);
        }
        blob(s, t, x, y + h, z, 3.4f, 2.2f, 3.4f, r);
    }

    private static void slender(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 7 + r.nextInt(4);
        trunk(s, t, x, y, z, h);
        blob(s, t, x, y + h - 2, z, 1.6f, 3.2f, 1.6f, r);
        leaf(s, t, x, y + h + 1, z);
    }

    private static void cone(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 10 + r.nextInt(6);
        trunk(s, t, x, y, z, h - 2);   // the tip is all needles
        int start = 2 + r.nextInt(2);
        for (int ly = y + start; ly <= y + h + 1; ly++) {
            float u = (ly - y - start) / (float) (h + 1 - start);
            float rad = (1 - u) * 3.2f + 0.4f;
            if (((ly - y) & 1) == 1) rad *= 0.72f;   // layered silhouette
            if (ly >= y + h - 3 && ly < y + h + 1) rad = Math.max(rad, 1.05f);
            int ir = (int) Math.ceil(rad);
            for (int dx = -ir; dx <= ir; dx++)
                for (int dz = -ir; dz <= ir; dz++)
                    if (dx * dx + dz * dz <= rad * rad) leaf(s, t, x + dx, ly, z + dz);
        }
    }

    private static void pine(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 11 + r.nextInt(5);
        trunk(s, t, x, y, z, h - 1);
        blob(s, t, x, y + h - 1, z, 2.6f, 1.6f, 2.6f, r);
        blob(s, t, x, y + h - 4, z, 1.8f, 1.0f, 1.8f, r);
        leaf(s, t, x, y + h + 1, z);
    }

    private static void giant(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 24 + r.nextInt(10);
        for (int i = 0; i < h; i++)
            for (int dx = 0; dx < 2; dx++)
                for (int dz = 0; dz < 2; dz++) log(s, t, x + dx, y + i, z + dz);
        // buttress roots
        log(s, t, x - 1, y, z); log(s, t, x + 2, y, z + 1); log(s, t, x, y, z + 2); log(s, t, x + 1, y, z - 1);
        int start = h / 2;
        for (int ly = y + start; ly <= y + h + 2; ly++) {
            float u = (ly - y - start) / (float) (h + 2 - start);
            float rad = (1 - u) * 5.0f + 1.2f;
            if (((ly - y) % 3) == 0) rad *= 0.6f;
            int ir = (int) Math.ceil(rad);
            for (int dx = -ir; dx <= ir + 1; dx++)
                for (int dz = -ir; dz <= ir + 1; dz++) {
                    float fx = dx - 0.5f, fz = dz - 0.5f;
                    if (fx * fx + fz * fz <= rad * rad) leaf(s, t, x + dx, ly, z + dz);
                }
        }
    }

    private static void tropical(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 10 + r.nextInt(7);
        trunk(s, t, x, y, z, h);
        blob(s, t, x, y + h, z, 4.2f, 1.6f, 4.2f, r);
        blob(s, t, x, y + h + 1, z, 2.6f, 1.2f, 2.6f, r);
        // side branch with its own canopy
        int dx = r.nextBoolean() ? 3 : -3, dz = r.nextInt(5) - 2, by = h - 4;
        log(s, t, x + dx / 3, y + by, z); log(s, t, x + 2 * dx / 3, y + by + 1, z + dz / 2);
        blob(s, t, x + dx, y + by + 2, z + dz, 2.4f, 1.2f, 2.4f, r);
        // hanging vines from the canopy edge
        for (int i = 0; i < 6; i++) {
            int vx = x + r.nextInt(9) - 4, vz = z + r.nextInt(9) - 4;
            int len = 2 + r.nextInt(4);
            for (int k = 1; k <= len; k++) {
                int vy = y + h - 1 - k;
                if (s.get(vx, vy, vz) != Blocks.AIR) break;
                s.set(vx, vy, vz, Blocks.VINES, 0);
            }
        }
    }

    private static void savanna(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 5 + r.nextInt(3);
        int dx = r.nextInt(3) - 1, dz = r.nextInt(3) - 1;
        int tx = x, tz = z;
        for (int i = 0; i < h; i++) {
            if (i == h / 2) { tx += dx; tz += dz; }
            log(s, t, tx, y + i, tz);
        }
        // flat umbrella crown
        for (int ddx = -4; ddx <= 4; ddx++)
            for (int ddz = -4; ddz <= 4; ddz++) {
                int d = ddx * ddx + ddz * ddz;
                if (d <= 17 && !(d > 12 && r.nextFloat() < 0.4f)) leaf(s, t, tx + ddx, y + h, tz + ddz);
                if (d <= 6) leaf(s, t, tx + ddx, y + h + 1, tz + ddz);
            }
    }

    private static void cedar(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 7 + r.nextInt(4);
        trunk(s, t, x, y, z, h);
        int[] tiers = {h - 5, h - 3, h - 1};
        float[] radii = {3.2f, 2.4f, 1.5f};
        for (int i = 0; i < 3; i++) {
            int ly = y + tiers[i];
            int ir = (int) Math.ceil(radii[i]);
            for (int dx = -ir; dx <= ir; dx++)
                for (int dz = -ir; dz <= ir; dz++)
                    if (dx * dx + dz * dz <= radii[i] * radii[i]) leaf(s, t, x + dx, ly, z + dz);
        }
        leaf(s, t, x, y + h, z);
    }

    private static void cypress(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 9 + r.nextInt(5);
        trunk(s, t, x, y - 1, z, h + 1);
        // flared base
        log(s, t, x + 1, y, z); log(s, t, x - 1, y, z); log(s, t, x, y, z + 1); log(s, t, x, y, z - 1);
        blob(s, t, x, y + h - 1, z, 2.6f, 1.8f, 2.6f, r);
        blob(s, t, x, y + h - 4, z, 1.8f, 1.2f, 1.8f, r);
        for (int i = 0; i < 5; i++) {
            int vx = x + r.nextInt(5) - 2, vz = z + r.nextInt(5) - 2;
            for (int k = 1; k <= 2 + r.nextInt(3); k++) {
                int vy = y + h - 3 - k;
                if (s.get(vx, vy, vz) != Blocks.AIR) break;
                s.set(vx, vy, vz, Blocks.VINES, 0);
            }
        }
    }

    private static void shrub(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        blob(s, t, x, y, z, 1.4f + r.nextFloat(), 1.0f + r.nextFloat() * 0.6f, 1.4f + r.nextFloat(), r);
    }

    private static void palm(Sink s, Biome.Tree t, int x, int y, int z, Random r) {
        int h = 6 + r.nextInt(4);
        int dx = r.nextBoolean() ? 1 : -1;
        int tx = x;
        for (int i = 0; i < h; i++) {
            if (i == h - 3) tx += dx;   // gentle lean
            log(s, t, tx, y + i, z);
        }
        int ty = y + h;
        leaf(s, t, tx, ty, z);
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] a : arms) {
            for (int k = 1; k <= 3; k++) leaf(s, t, tx + a[0] * k, ty - (k == 3 ? 1 : 0), z + a[1] * k);
        }
    }
}
