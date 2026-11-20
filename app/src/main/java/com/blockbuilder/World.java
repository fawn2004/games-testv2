package com.blockbuilder;

import java.util.Arrays;
import java.util.Random;

/** Fixed-size voxel world split into 16x16 columns ("chunks") for meshing. */
final class World {
    static final int SX = 128, SY = 64, SZ = 128;
    static final int CS = 16, CX = SX / CS, CZ = SZ / CS;
    static final int SEA = 21;
    static final int FLAT_TOP = 10;

    final byte[] blocks = new byte[SX * SY * SZ];
    final boolean[] chunkDirty = new boolean[CX * CZ];
    long seed;

    static int idx(int x, int y, int z) {
        return (x * SZ + z) * SY + y;
    }

    /** Block at a position. Below the world is bedrock; outside it is air. */
    int get(int x, int y, int z) {
        if (y < 0) return Blocks.BEDROCK;
        if (y >= SY || x < 0 || z < 0 || x >= SX || z >= SZ) return Blocks.AIR;
        return blocks[idx(x, y, z)];
    }

    /** Collision query: the world edges act as invisible walls. */
    boolean solidAt(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= SX || z >= SZ || y < 0) return true;
        if (y >= SY) return false;
        return Blocks.isSolid(blocks[idx(x, y, z)]);
    }

    static boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < SX && y < SY && z < SZ;
    }

    void set(int x, int y, int z, int b) {
        if (!inside(x, y, z)) return;
        blocks[idx(x, y, z)] = (byte) b;
        // Mark this chunk and any neighbour whose mesh / AO could be affected.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = (x + dx) / CS, cz = (z + dz) / CS;
                if (x + dx < 0 || z + dz < 0 || cx >= CX || cz >= CZ) continue;
                chunkDirty[cx * CZ + cz] = true;
            }
        }
    }

    void markAllDirty() {
        Arrays.fill(chunkDirty, true);
    }

    int topY(int x, int z) {
        for (int y = SY - 1; y >= 0; y--) {
            if (blocks[idx(x, y, z)] != Blocks.AIR) return y;
        }
        return 0;
    }

    void generate(long seed, boolean flat) {
        this.seed = seed;
        Arrays.fill(blocks, (byte) 0);
        if (flat) {
            for (int x = 0; x < SX; x++)
                for (int z = 0; z < SZ; z++)
                    for (int y = 0; y <= FLAT_TOP; y++) {
                        int b = y == 0 ? Blocks.BEDROCK : y < FLAT_TOP - 3 ? Blocks.STONE
                                : y < FLAT_TOP ? Blocks.DIRT : Blocks.GRASS;
                        blocks[idx(x, y, z)] = (byte) b;
                    }
            markAllDirty();
            return;
        }
        Noise hills = new Noise(seed);
        Noise mountains = new Noise(seed * 31 + 7);
        Noise floor = new Noise(seed * 17 + 3);
        Random rnd = new Random(seed);
        int[] height = new int[SX * SZ];

        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                float h1 = hills.fbm(x / 40f, z / 40f, 4);
                float m = mountains.fbm(x / 70f, z / 70f, 3);
                float mount = Math.max(0f, m - 0.5f) * 2f;
                mount *= mount;
                int h = (int) (12 + h1 * 18 + mount * 30);
                if (h < 3) h = 3;
                if (h > SY - 8) h = SY - 8;
                height[x * SZ + z] = h;

                boolean beach = h <= SEA + 1;
                boolean rocky = h >= 40;
                int bed = Blocks.SAND;
                if (h < SEA - 1) {
                    float f = floor.value(x / 9f, z / 9f);
                    bed = f < 0.33f ? Blocks.GRAVEL : f > 0.7f ? Blocks.CLAY : Blocks.SAND;
                }
                for (int y = 0; y <= h; y++) {
                    int b;
                    if (y == 0) b = Blocks.BEDROCK;
                    else if (y < h - 3) b = ore(y, rnd);
                    else if (beach) b = bed;
                    else if (rocky) b = (y == h && h >= 45) ? Blocks.SNOW : Blocks.STONE;
                    else b = (y == h) ? Blocks.GRASS : Blocks.DIRT;
                    blocks[idx(x, y, z)] = (byte) b;
                }
                for (int y = h + 1; y <= SEA; y++) blocks[idx(x, y, z)] = (byte) Blocks.WATER;
            }
        }

        // Trees
        for (int x = 3; x < SX - 3; x++) {
            for (int z = 3; z < SZ - 3; z++) {
                int h = height[x * SZ + z];
                if (blocks[idx(x, h, z)] != Blocks.GRASS) continue;
                if (rnd.nextFloat() > 0.014f) continue;
                float kind = rnd.nextFloat();
                if (kind < 0.6f) growTree(x, h + 1, z, rnd, Blocks.LOG, Blocks.LEAVES, 0);
                else if (kind < 0.82f) growTree(x, h + 1, z, rnd, Blocks.LIGHT_LOG, Blocks.LEAVES, 1);
                else if (kind < 0.94f) growTree(x, h + 1, z, rnd, Blocks.LOG, Blocks.AUTUMN_LEAVES, 0);
                else growTree(x, h + 1, z, rnd, Blocks.DARK_LOG, Blocks.BLOSSOM_LEAVES, 0);
            }
        }
        markAllDirty();
    }

    /** Stone with the occasional ore; rarer ores only appear deeper down. */
    private static int ore(int y, Random rnd) {
        float p = rnd.nextFloat();
        if (p < 0.012f) return Blocks.COAL_ORE;
        if (p < 0.020f && y < 34) return Blocks.COPPER_ORE;
        if (p < 0.024f && y < 20) return Blocks.GOLD_ORE;
        if (p < 0.0258f && y < 12) return Blocks.CRYSTAL_ORE;
        return Blocks.STONE;
    }

    private void growTree(int x, int y, int z, Random rnd, int log, int leaves, int extra) {
        int th = 4 + rnd.nextInt(3) + extra;
        if (y + th + 1 >= SY) return;
        for (int i = 0; i < th; i++) blocks[idx(x, y + i, z)] = (byte) log;
        for (int ly = y + th - 3; ly <= y + th; ly++) {
            int r = (ly >= y + th - 1) ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (ly == y + th || rnd.nextBoolean())) continue;
                    int bx = x + dx, bz = z + dz;
                    if (!inside(bx, ly, bz)) continue;
                    if (blocks[idx(bx, ly, bz)] == Blocks.AIR) blocks[idx(bx, ly, bz)] = (byte) leaves;
                }
            }
        }
    }

    /**
     * Voxel raycast. Fills out[0..2] with the hit block and out[3..5] with the empty cell
     * in front of the hit face. Returns false if nothing was hit within maxDist.
     */
    boolean raycast(float ox, float oy, float oz, float dx, float dy, float dz, float maxDist, int[] out) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        float tdx = dx != 0 ? Math.abs(1f / dx) : Float.MAX_VALUE;
        float tdy = dy != 0 ? Math.abs(1f / dy) : Float.MAX_VALUE;
        float tdz = dz != 0 ? Math.abs(1f / dz) : Float.MAX_VALUE;
        float tmx = dx != 0 ? (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx : Float.MAX_VALUE;
        float tmy = dy != 0 ? (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy : Float.MAX_VALUE;
        float tmz = dz != 0 ? (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz : Float.MAX_VALUE;
        int px = x, py = y, pz = z;
        float t = 0;
        while (t <= maxDist) {
            int b = get(x, y, z);
            if (b != Blocks.AIR && b != Blocks.WATER) {
                out[0] = x; out[1] = y; out[2] = z;
                out[3] = px; out[4] = py; out[5] = pz;
                return true;
            }
            px = x; py = y; pz = z;
            if (tmx < tmy && tmx < tmz) {
                x += stepX; t = tmx; tmx += tdx;
            } else if (tmy < tmz) {
                y += stepY; t = tmy; tmy += tdy;
            } else {
                z += stepZ; t = tmz; tmz += tdz;
            }
        }
        return false;
    }
}
