package com.blockbuilder;

import java.util.Arrays;
import java.util.Random;

/** Fixed-size voxel world split into 16x16 columns ("chunks") for meshing. */
final class World {
    static final int SX = 128, SY = 64, SZ = 128;
    static final int CS = 16, CX = SX / CS, CZ = SZ / CS;
    static final int SEA = 21;
    static final int FLAT_TOP = 10;

    final char[] blocks = new char[SX * SY * SZ];
    /** Orientation / open state of shaped blocks (see Shapes). */
    final byte[] meta = new byte[SX * SY * SZ];
    final boolean[] chunkDirty = new boolean[CX * CZ];
    /** Highest y that has ever held a non-air block (shadow rays stop above it). */
    int maxHeight = SY - 1;
    /** Direction toward the sun, per block climbed (x and z drift per +1 y). */
    static final float SUN_DX = 0.55f, SUN_DZ = 0.3f;
    /** Longest shadow, in blocks of height. */
    static final int SHADOW_STEPS = 32;
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

    int getMeta(int x, int y, int z) {
        if (!inside(x, y, z)) return 0;
        return meta[idx(x, y, z)];
    }

    void set(int x, int y, int z, int b) {
        set(x, y, z, b, 0);
    }

    void set(int x, int y, int z, int b, int m) {
        if (!inside(x, y, z)) return;
        int i = idx(x, y, z);
        blocks[i] = (char) b;
        meta[i] = (byte) m;
        if (b != Blocks.AIR && y > maxHeight) maxHeight = y;
        // Mark chunks whose mesh, ambient occlusion or shadows could change: the
        // neighbours, plus everything this block can cast a shadow onto (away from the sun).
        int reach = Math.min(y, SHADOW_STEPS);
        int xMin = (int) Math.floor(x - reach * SUN_DX) - 1, zMin = (int) Math.floor(z - reach * SUN_DZ) - 1;
        for (int cx = Math.max(0, xMin) / CS; cx <= Math.min(SX - 1, x + 1) / CS; cx++)
            for (int cz = Math.max(0, zMin) / CS; cz <= Math.min(SZ - 1, z + 1) / CS; cz++)
                chunkDirty[cx * CZ + cz] = true;
    }

    /** How much light passes through a block (1 = clear, 0 = blocks it). */
    static float transmit(int b) {
        if (b == Blocks.AIR) return 1f;
        if (Blocks.isOpaque(b)) return 0f;
        if (b == Blocks.LEAVES || b == Blocks.AUTUMN_LEAVES || b == Blocks.BLOSSOM_LEAVES) return 0.55f;
        if (b == Blocks.WATER) return 0.8f;
        if (b == Blocks.GLASS) return 0.92f;
        int shape = Blocks.shape(b);
        if (shape == Shapes.SLAB || shape == Shapes.STAIRS) return 0.35f;
        return 0.6f; // doors, trapdoors
    }

    /** Fraction of direct sunlight reaching the centre of a cell (soft shadows from leaves). */
    float sunlight(int x, int y, int z) {
        float t = 1f;
        float fx = x + 0.5f, fz = z + 0.5f;
        for (int k = 1; k <= SHADOW_STEPS; k++) {
            int py = y + k;
            if (py > maxHeight) return t;
            fx += SUN_DX;
            fz += SUN_DZ;
            int px = (int) Math.floor(fx), pz = (int) Math.floor(fz);
            if (px < 0 || pz < 0 || px >= SX || pz >= SZ) return t;
            t *= transmit(blocks[idx(px, py, pz)]);
            if (t < 0.03f) return 0f;
        }
        return t;
    }

    void recomputeMaxHeight() {
        maxHeight = 0;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] != Blocks.AIR) {
                int y = i % SY;
                if (y > maxHeight) maxHeight = y;
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
        Arrays.fill(blocks, (char) 0);
        Arrays.fill(meta, (byte) 0);
        if (flat) {
            for (int x = 0; x < SX; x++)
                for (int z = 0; z < SZ; z++)
                    for (int y = 0; y <= FLAT_TOP; y++) {
                        int b = y == 0 ? Blocks.BEDROCK : y < FLAT_TOP - 3 ? Blocks.STONE
                                : y < FLAT_TOP ? Blocks.DIRT : Blocks.GRASS;
                        blocks[idx(x, y, z)] = (char) b;
                    }
            maxHeight = FLAT_TOP;
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
                    blocks[idx(x, y, z)] = (char) b;
                }
                for (int y = h + 1; y <= SEA; y++) blocks[idx(x, y, z)] = (char) Blocks.WATER;
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
        recomputeMaxHeight();
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
        for (int i = 0; i < th; i++) blocks[idx(x, y + i, z)] = (char) log;
        for (int ly = y + th - 3; ly <= y + th; ly++) {
            int r = (ly >= y + th - 1) ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (ly == y + th || rnd.nextBoolean())) continue;
                    int bx = x + dx, bz = z + dz;
                    if (!inside(bx, ly, bz)) continue;
                    if (blocks[idx(bx, ly, bz)] == Blocks.AIR) blocks[idx(bx, ly, bz)] = (char) leaves;
                }
            }
        }
    }

    private final float[] rayBoxes = new float[6 * 4];

    /**
     * Voxel raycast against the real block shapes. On a hit, out[0..2] is the block,
     * out[3..5] the cell in front of the face that was hit and out[6] the face index
     * (0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z). hitPos receives the exact hit point.
     */
    boolean raycast(float ox, float oy, float oz, float dx, float dy, float dz, float maxDist, int[] out, float[] hitPos) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        float tdx = dx != 0 ? Math.abs(1f / dx) : Float.MAX_VALUE;
        float tdy = dy != 0 ? Math.abs(1f / dy) : Float.MAX_VALUE;
        float tdz = dz != 0 ? Math.abs(1f / dz) : Float.MAX_VALUE;
        float tmx = dx != 0 ? (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx : Float.MAX_VALUE;
        float tmy = dy != 0 ? (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy : Float.MAX_VALUE;
        float tmz = dz != 0 ? (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz : Float.MAX_VALUE;
        float t = 0;
        while (t <= maxDist) {
            int b = get(x, y, z);
            if (b != Blocks.AIR && b != Blocks.WATER) {
                int n = Shapes.boxes(b, getMeta(x, y, z), rayBoxes);
                float best = Float.MAX_VALUE;
                int bestFace = -1;
                for (int k = 0; k < n; k++) {
                    int o = k * 6;
                    float tNear = -Float.MAX_VALUE, tFar = Float.MAX_VALUE;
                    int face = -1;
                    boolean miss = false;
                    for (int a = 0; a < 3 && !miss; a++) {
                        float org = a == 0 ? ox : a == 1 ? oy : oz;
                        float d = a == 0 ? dx : a == 1 ? dy : dz;
                        int base = a == 0 ? x : a == 1 ? y : z;
                        float lo = base + rayBoxes[o + a], hi = base + rayBoxes[o + 3 + a];
                        if (Math.abs(d) < 1e-9f) {
                            if (org < lo || org > hi) miss = true;
                            continue;
                        }
                        float t1 = (lo - org) / d, t2 = (hi - org) / d;
                        int f1 = a * 2 + 1, f2 = a * 2; // entering through the low side = -axis face
                        if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; int ft = f1; f1 = f2; f2 = ft; }
                        if (t1 > tNear) { tNear = t1; face = f1; }
                        if (t2 < tFar) tFar = t2;
                        if (tNear > tFar) miss = true;
                    }
                    if (miss || tFar < 0 || tNear < 0 || tNear > maxDist) continue;
                    if (tNear < best) { best = tNear; bestFace = face; }
                }
                if (bestFace >= 0) {
                    int[] d = ChunkMesher.DIR[bestFace];
                    out[0] = x; out[1] = y; out[2] = z;
                    out[3] = x + d[0]; out[4] = y + d[1]; out[5] = z + d[2];
                    out[6] = bestFace;
                    hitPos[0] = ox + dx * best; hitPos[1] = oy + dy * best; hitPos[2] = oz + dz * best;
                    return true;
                }
            }
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

    /**
     * Appends the collision boxes of a cell, in world coordinates, to out at offset n*6.
     * Returns the new count. Cells outside the world sides and below it are solid.
     */
    int collisionBoxes(int x, int y, int z, float[] out, int n) {
        int b;
        if (x < 0 || z < 0 || x >= SX || z >= SZ || y < 0) b = Blocks.STONE;
        else if (y >= SY) return n;
        else b = blocks[idx(x, y, z)];
        if (!Blocks.isSolid(b)) return n;
        int m = Shapes.boxes(b, getMeta(x, y, z), rayBoxes);
        for (int k = 0; k < m; k++) {
            int o = n * 6, s = k * 6;
            out[o] = x + rayBoxes[s]; out[o + 1] = y + rayBoxes[s + 1]; out[o + 2] = z + rayBoxes[s + 2];
            out[o + 3] = x + rayBoxes[s + 3]; out[o + 4] = y + rayBoxes[s + 4]; out[o + 5] = z + rayBoxes[s + 5];
            n++;
        }
        return n;
    }
}
