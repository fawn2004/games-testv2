package com.blockbuilder;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A near-infinite world made of 16x16 chunks that are generated (or loaded) around the
 * player and dropped again far away. Coordinates are ints; the usable area is
 * +-LIMIT blocks in x and z.
 */
final class World {
    static final int SY = 96, CS = 16;
    static final int SEA = 21;
    static final int FLAT_TOP = 10;
    /** Half-width of the usable world (an invisible wall stands there). */
    static final int LIMIT = 10_000_000;
    /** Direction toward the sun, per block climbed (x and z drift per +1 y). */
    static final float SUN_DX = 0.9f, SUN_DZ = 0.45f; // late-afternoon sun: long, warm shadows
    /** Longest shadow, in blocks of height. */
    static final int SHADOW_STEPS = 32;

    final ConcurrentHashMap<Long, Chunk> chunks = new ConcurrentHashMap<>();
    final Generator gen;
    final long seed;

    /** One-entry cache for chunk lookups (an immutable pair, so it is safe across threads). */
    private static final class Last {
        final long key;
        final Chunk chunk;

        Last(long key, Chunk chunk) {
            this.key = key;
            this.chunk = chunk;
        }
    }

    private volatile Last last = new Last(Long.MIN_VALUE, null);

    World(long seed, boolean flat) {
        this.seed = seed;
        this.gen = new Generator(seed, flat);
    }

    Chunk chunk(int cx, int cz) {
        long k = Chunk.key(cx, cz);
        Last l = last;
        if (l.key == k && l.chunk != null) return l.chunk;
        Chunk c = chunks.get(k);
        if (c != null) last = new Last(k, c);
        return c;
    }

    boolean isLoaded(int x, int z) {
        return chunk(x >> 4, z >> 4) != null;
    }

    void addChunk(Chunk c) {
        chunks.put(c.key(), c);
        // Neighbours need new meshes now that their border (and shadows) are known.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                Chunk n = chunks.get(Chunk.key(c.cx + dx, c.cz + dz));
                if (n != null) n.dirty.set(true);
            }
    }

    void removeChunk(Chunk c) {
        chunks.remove(c.key());
        last = new Last(Long.MIN_VALUE, null);
    }

    /** Block at a position. Below the world is bedrock; above it and in unloaded chunks, air. */
    int get(int x, int y, int z) {
        if (y < 0) return Blocks.BEDROCK;
        if (y >= SY) return Blocks.AIR;
        Chunk c = chunk(x >> 4, z >> 4);
        if (c == null) return Blocks.AIR;
        return c.blocks[Chunk.idx(x & 15, y, z & 15)];
    }

    int getMeta(int x, int y, int z) {
        if (y < 0 || y >= SY) return 0;
        Chunk c = chunk(x >> 4, z >> 4);
        if (c == null) return 0;
        return c.meta[Chunk.idx(x & 15, y, z & 15)];
    }

    static boolean insideY(int y) {
        return y >= 0 && y < SY;
    }

    static boolean insideXZ(int x, int z) {
        return x > -LIMIT && x < LIMIT && z > -LIMIT && z < LIMIT;
    }

    void set(int x, int y, int z, int b) {
        set(x, y, z, b, 0);
    }

    void set(int x, int y, int z, int b, int m) {
        if (!insideY(y) || !insideXZ(x, z)) return;
        Chunk c = chunk(x >> 4, z >> 4);
        if (c == null) return;
        int i = Chunk.idx(x & 15, y, z & 15);
        c.blocks[i] = (char) b;
        c.meta[i] = (byte) m;
        c.modified = true;
        if (b != Blocks.AIR && y > c.maxY) c.maxY = y;
        // Rebuild every chunk whose mesh, ambient occlusion or shadows could change.
        int reach = Math.min(y, SHADOW_STEPS);
        int xMin = (int) Math.floor(x - reach * SUN_DX) - 1, zMin = (int) Math.floor(z - reach * SUN_DZ) - 1;
        for (int cx = xMin >> 4; cx <= (x + 1) >> 4; cx++)
            for (int cz = zMin >> 4; cz <= (z + 1) >> 4; cz++) {
                Chunk n = chunk(cx, cz);
                if (n != null) n.dirty.set(true);
            }
    }

    /** Collision query: unloaded chunks and the world limit act as walls. */
    boolean solidAt(int x, int y, int z) {
        if (y < 0) return true;
        if (y >= SY) return false;
        if (!insideXZ(x, z)) return true;
        Chunk c = chunk(x >> 4, z >> 4);
        if (c == null) return true;
        return Blocks.isSolid(c.blocks[Chunk.idx(x & 15, y, z & 15)]);
    }

    /** How much light passes through a block (1 = clear, 0 = blocks it). */
    static float transmit(int b) {
        if (b == Blocks.AIR) return 1f;
        if (Blocks.isOpaque(b)) return 0f;
        if (Blocks.isLeaves(b)) return 0.55f;
        if (Blocks.isWater(b)) return 0.8f;
        if (b == Blocks.GLASS) return 0.92f;
        int shape = Blocks.shape(b);
        if (shape == Shapes.CROSS) return 0.85f;
        if (shape == Shapes.CARPET) return 1f;
        if (shape == Shapes.SLAB || shape == Shapes.STAIRS) return 0.35f;
        return 0.6f; // doors, trapdoors
    }

    private final float[] rayBoxes = new float[6 * 4];

    /**
     * Voxel raycast against the real block shapes. On a hit, out[0..2] is the block,
     * out[3..5] the cell in front of the face that was hit and out[6] the face index
     * (0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z). hitPos receives the exact hit point.
     */
    synchronized boolean raycast(double ox, double oy, double oz, float dx, float dy, float dz, float maxDist,
                                 int[] out, double[] hitPos) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tdx = dx != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tdy = dy != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tdz = dz != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tmx = dx != 0 ? (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx : Double.MAX_VALUE;
        double tmy = dy != 0 ? (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy : Double.MAX_VALUE;
        double tmz = dz != 0 ? (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz : Double.MAX_VALUE;
        double t = 0;
        while (t <= maxDist) {
            int b = get(x, y, z);
            if (b != Blocks.AIR && b != Blocks.WATER) {
                int n = Shapes.boxes(b, getMeta(x, y, z), rayBoxes);
                double best = Double.MAX_VALUE;
                int bestFace = -1;
                for (int k = 0; k < n; k++) {
                    int o = k * 6;
                    double tNear = -Double.MAX_VALUE, tFar = Double.MAX_VALUE;
                    int face = -1;
                    boolean miss = false;
                    for (int a = 0; a < 3 && !miss; a++) {
                        double org = a == 0 ? ox : a == 1 ? oy : oz;
                        double d = a == 0 ? dx : a == 1 ? dy : dz;
                        int base = a == 0 ? x : a == 1 ? y : z;
                        double lo = base + rayBoxes[o + a], hi = base + rayBoxes[o + 3 + a];
                        if (Math.abs(d) < 1e-9) {
                            if (org < lo || org > hi) miss = true;
                            continue;
                        }
                        double t1 = (lo - org) / d, t2 = (hi - org) / d;
                        int f1 = a * 2 + 1, f2 = a * 2; // entering through the low side = -axis face
                        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; int ft = f1; f1 = f2; f2 = ft; }
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
     * Appends the collision boxes of a cell to out (6 doubles each, world coordinates)
     * and returns the new count.
     */
    synchronized int collisionBoxes(int x, int y, int z, double[] out, int n) {
        int b;
        if (y < 0 || !insideXZ(x, z)) b = Blocks.STONE;
        else if (y >= SY) return n;
        else {
            Chunk c = chunk(x >> 4, z >> 4);
            b = c == null ? Blocks.STONE : c.blocks[Chunk.idx(x & 15, y, z & 15)];
        }
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
