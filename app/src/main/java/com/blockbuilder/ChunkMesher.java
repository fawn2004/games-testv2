package com.blockbuilder;

import java.util.Arrays;

/**
 * Turns a 16x16 column of the world into triangle lists. Each vertex is
 * x, y, z, u, v, r, g, b, flags where rgb is baked lighting in linear units (may exceed 1):
 * warm direct sun where the sun can reach, cool sky light reduced under cover,
 * ambient occlusion in corners, and a little natural colour variation.
 */
final class ChunkMesher {
    static final int[][] DIR = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** Corner offsets per face, counter-clockwise seen from outside. */
    static final int[][][] VERTS = {
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
    };
    static final float[] AO = {0.42f, 0.62f, 0.82f, 1.0f};

    // Lighting (linear). The sun shines from +X, +Y, +Z (see World.SUN_DX / SUN_DZ).
    static final float SUN_X, SUN_Y, SUN_Z;
    static {
        float l = (float) Math.sqrt(World.SUN_DX * World.SUN_DX + 1 + World.SUN_DZ * World.SUN_DZ);
        SUN_X = World.SUN_DX / l; SUN_Y = 1 / l; SUN_Z = World.SUN_DZ / l;
    }
    private static final float SUN_R = 1.95f, SUN_G = 1.55f, SUN_B = 1.05f; // golden afternoon
    private static final float SKY_R = 0.24f, SKY_G = 0.32f, SKY_B = 0.50f;
    private static final float BOUNCE_R = 0.10f, BOUNCE_G = 0.10f, BOUNCE_B = 0.08f;
    private static final float MIN_R = 0.025f, MIN_G = 0.028f, MIN_B = 0.036f;
    /** Sun intensity on each face direction (n . sun). */
    private static final float[] NDL = new float[6];
    /** Sky light per face direction: up-facing faces see the whole sky. */
    private static final float[] HEMI = {0.62f, 0.62f, 1.0f, 0.28f, 0.62f, 0.62f};
    static {
        for (int f = 0; f < 6; f++) {
            int[] d = DIR[f];
            NDL[f] = Math.max(0f, d[0] * SUN_X + d[1] * SUN_Y + d[2] * SUN_Z);
        }
    }

    private static final int PR = TextureAtlas.PER_ROW;
    private static final float T = 1f / PR, E = 0.0002f;
    private final float[] boxes = new float[6 * 4];

    private final int[] ao = new int[4];
    private final float[] vx = new float[4], vy = new float[4], vz = new float[4],
            vu = new float[4], vv = new float[4], vr = new float[4], vg = new float[4], vb = new float[4],
            vf = new float[4];

    // Per-chunk caches of sun and sky visibility for cells around the chunk (with a 1-block margin).
    private static final int MW = World.CS + 2;
    private final float[] sunCache = new float[MW * World.SY * MW];
    private final float[] skyCache = new float[MW * World.SY * MW];
    private final boolean[] skyDone = new boolean[MW * MW];
    private int cx0, cz0;
    private World w;
    private Chunk center;
    /** The 5x5 chunks around the one being meshed (long shadows reach two chunks). */
    private final Chunk[] nb = new Chunk[25];
    private int ncx, ncz, nbMaxY;
    private final Noise tintNoise = new Noise(4242);

    /** Block lookup through the neighbourhood (falls back to the world further away). */
    private int get(int x, int y, int z) {
        if (y < 0) return Blocks.BEDROCK;
        if (y >= World.SY) return Blocks.AIR;
        int i = (x >> 4) - ncx + 2, j = (z >> 4) - ncz + 2;
        if (i < 0 || j < 0 || i > 4 || j > 4) return w.get(x, y, z);
        Chunk c = nb[i * 5 + j];
        return c == null ? Blocks.AIR : c.blocks[Chunk.idx(x & 15, y, z & 15)];
    }

    /** Fraction of direct sunlight reaching the centre of a cell (soft shadows through leaves). */
    private float sunlight(int x, int y, int z) {
        float t = 1f;
        float fx = x + 0.5f, fz = z + 0.5f;
        for (int k = 1; k <= World.SHADOW_STEPS; k++) {
            int py = y + k;
            if (py > nbMaxY) return t;
            fx += World.SUN_DX;
            fz += World.SUN_DZ;
            t *= World.transmit(get((int) Math.floor(fx), py, (int) Math.floor(fz)));
            if (t < 0.03f) return 0f;
        }
        return t;
    }

    private static boolean shouldEmit(int b, int n) {
        if (n == Blocks.AIR) return true;
        if (Blocks.isOpaque(n)) return false;
        if (b == Blocks.WATER) return !Blocks.isWater(n);
        if (b == Blocks.GLASS) return n != Blocks.GLASS;
        return true; // opaque or leaves next to glass / leaves / water
    }

    private int occ(int x, int y, int z) {
        return Blocks.isOpaque(get(x, y, z)) ? 1 : 0;
    }

    private int cacheIdx(int x, int y, int z) {
        int lx = x - cx0 + 1, lz = z - cz0 + 1;
        if (lx < 0 || lz < 0 || lx >= MW || lz >= MW || y < 0 || y >= World.SY) return -1;
        return (lx * MW + lz) * World.SY + y;
    }

    /** Direct sunlight reaching a cell, 0..1. */
    private float sun(int x, int y, int z) {
        if (y >= World.SY) return 1f;
        if (y < 0) return 0f;
        int i = cacheIdx(x, y, z);
        if (i < 0) return sunlight(x, y, z);
        float v = sunCache[i];
        if (v < 0) {
            v = sunlight(x, y, z);
            sunCache[i] = v;
        }
        return v;
    }

    /** Open sky straight above a cell, 0..1 (fills the whole column on first use). */
    private float sky(int x, int y, int z) {
        if (y >= World.SY) return 1f;
        if (y < 0) return 0f;
        int lx = x - cx0 + 1, lz = z - cz0 + 1;
        if (lx < 0 || lz < 0 || lx >= MW || lz >= MW) return 1f;
        int col = lx * MW + lz;
        if (!skyDone[col]) {
            float t = 1f;
            for (int yy = World.SY - 1; yy >= 0; yy--) {
                skyCache[col * World.SY + yy] = t;
                t *= World.transmit(get(x, yy, z));
            }
            skyDone[col] = true;
        }
        return skyCache[col * World.SY + y];
    }

    /** Meshes a chunk. Vertex positions are relative to the chunk's corner. */
    void build(World world, Chunk chunk, FloatBuilder solid, FloatBuilder water) {
        w = world;
        center = chunk;
        int cx = chunk.cx, cz = chunk.cz;
        ncx = cx;
        ncz = cz;
        nbMaxY = 0;
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++) {
                Chunk c = (i == 2 && j == 2) ? chunk : world.chunk(cx + i - 2, cz + j - 2);
                nb[i * 5 + j] = c;
                if (c != null && c.maxY > nbMaxY) nbMaxY = c.maxY;
            }
        cx0 = cx * World.CS;
        cz0 = cz * World.CS;
        Arrays.fill(sunCache, -1f);
        Arrays.fill(skyDone, false);
        solid.clear();
        water.clear();
        for (int x = cx0; x < cx0 + World.CS; x++) {
            for (int z = cz0; z < cz0 + World.CS; z++) {
                float[] tint = tint(x, z);
                for (int y = 0; y < World.SY; y++) {
                    int ci = Chunk.idx(x - cx0, y, z - cz0);
                    int b = center.blocks[ci];
                    if (b == Blocks.AIR) continue;
                    int shape = Blocks.shape(b);
                    if (shape == Shapes.CROSS) {
                        cross(x, y, z, b, solid, tint);
                        if (b != Blocks.SEAGRASS) continue;
                        b = Blocks.WATER; // seagrass stands in water: mesh the water too
                    } else if (shape != Shapes.CUBE) {
                        shaped(x, y, z, b, center.meta[ci], solid);
                        continue;
                    }
                    boolean isWater = b == Blocks.WATER;
                    boolean glow = Blocks.isEmissive(b);
                    boolean plant = b == Blocks.GRASS;
                    float[] leafTint = Blocks.isLeaves(b) && center.meta[ci] > 0
                            && center.meta[ci] < Biome.LEAF_TINT.length ? Biome.LEAF_TINT[center.meta[ci]] : null;
                    if (b == Blocks.LEAVES && leafTint == null) plant = true;
                    float jitter = 0.97f + 0.06f * hash(x, y, z);
                    FloatBuilder out = isWater ? water : solid;
                    float waterTop = 1f;
                    if (isWater && !Blocks.isWater(get(x, y + 1, z))) waterTop = 0.875f;
                    float sway = Blocks.sway(b);
                    for (int f = 0; f < 6; f++) {
                        int[] d = DIR[f];
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        int nb = get(nx, ny, nz);
                        if (!shouldEmit(b, nb)) continue;
                        float flags = sway + (!isWater && Blocks.isWater(nb) ? 2f : 0f);
                        int tile = Blocks.tile(b, f);
                        float u0 = (tile % PR) * T + E, v0 = (tile / PR) * T + E, us = T - 2 * E;
                        int na = f >> 1, a1 = (na + 1) % 3, a2 = (na + 2) % 3;
                        int[][] vs = VERTS[f];
                        for (int i = 0; i < 4; i++) {
                            int[] c = vs[i];
                            float sunV, skyV;
                            if (isWater || glow) {
                                ao[i] = 3;
                                sunV = sun(nx, ny, nz);
                                skyV = sky(nx, ny, nz);
                            } else {
                                int s1 = c[a1] == 1 ? 1 : -1, s2 = c[a2] == 1 ? 1 : -1;
                                int x1 = 0, y1 = 0, z1 = 0, x2 = 0, y2 = 0, z2 = 0;
                                if (a1 == 0) x1 = s1; else if (a1 == 1) y1 = s1; else z1 = s1;
                                if (a2 == 0) x2 = s2; else if (a2 == 1) y2 = s2; else z2 = s2;
                                int side1 = occ(nx + x1, ny + y1, nz + z1);
                                int side2 = occ(nx + x2, ny + y2, nz + z2);
                                int corner = (side1 == 1 && side2 == 1) ? 1 : occ(nx + x1 + x2, ny + y1 + y2, nz + z1 + z2);
                                ao[i] = (side1 == 1 && side2 == 1) ? 0 : 3 - (side1 + side2 + corner);
                                // Average sun / sky over the open cells touching this corner (soft edges).
                                sunV = sun(nx, ny, nz);
                                skyV = sky(nx, ny, nz);
                                int n = 1;
                                if (side1 == 0) { sunV += sun(nx + x1, ny + y1, nz + z1); skyV += sky(nx + x1, ny + y1, nz + z1); n++; }
                                if (side2 == 0) { sunV += sun(nx + x2, ny + y2, nz + z2); skyV += sky(nx + x2, ny + y2, nz + z2); n++; }
                                if (corner == 0) {
                                    sunV += sun(nx + x1 + x2, ny + y1 + y2, nz + z1 + z2);
                                    skyV += sky(nx + x1 + x2, ny + y1 + y2, nz + z1 + z2);
                                    n++;
                                }
                                sunV /= n;
                                skyV /= n;
                            }
                            vx[i] = x + c[0];
                            vy[i] = y + (c[1] == 1 ? (isWater ? waterTop : 1f) : 0f);
                            vz[i] = z + c[2];
                            float tu, tv;
                            if (f == 2 || f == 3) {
                                tu = c[0]; tv = c[2];
                            } else {
                                tv = 1 - c[1];
                                tu = (f < 2) ? c[2] : c[0];
                            }
                            vu[i] = u0 + tu * us;
                            vv[i] = v0 + tv * us;
                            vf[i] = flags;
                            if (isWater) {
                                // Water shader reads: r = top face flag, g = sky, b = sun.
                                vr[i] = f == 2 ? 1f : 0f;
                                vg[i] = skyV;
                                vb[i] = sunV;
                            } else if (glow) {
                                vr[i] = 2.2f; vg[i] = 1.9f; vb[i] = 1.4f;
                            } else {
                                light(i, f, AO[ao[i]], sunV, skyV, jitter);
                                if (plant && (f == 2 || b == Blocks.LEAVES)) {
                                    vr[i] *= tint[0]; vg[i] *= tint[1]; vb[i] *= tint[2];
                                } else if (leafTint != null) {
                                    vr[i] *= leafTint[0]; vg[i] *= leafTint[1]; vb[i] *= leafTint[2];
                                }
                            }
                        }
                        out.ensure(6 * FloatBuilder.STRIDE);
                        if (ao[0] + ao[2] >= ao[1] + ao[3]) {
                            emit(out, 0); emit(out, 1); emit(out, 2);
                            emit(out, 0); emit(out, 2); emit(out, 3);
                        } else {
                            emit(out, 1); emit(out, 2); emit(out, 3);
                            emit(out, 1); emit(out, 3); emit(out, 0);
                        }
                    }
                }
            }
        }
    }

    /** Baked lighting for vertex i of a face. */
    private void light(int i, int f, float ao, float sunV, float skyV, float jitter) {
        float ndl = NDL[f] * sunV;
        float sky = HEMI[f] * (0.25f + 0.75f * skyV);
        float aoAmb = ao, aoSun = 0.55f + 0.45f * ao; // corners darken ambient more than sun
        float bounce = f == 3 ? 1f : 0.35f;           // light bouncing off the ground
        vr[i] = jitter * (SUN_R * ndl * aoSun + (SKY_R * sky + BOUNCE_R * bounce * skyV) * aoAmb + MIN_R);
        vg[i] = jitter * (SUN_G * ndl * aoSun + (SKY_G * sky + BOUNCE_G * bounce * skyV) * aoAmb + MIN_G);
        vb[i] = jitter * (SUN_B * ndl * aoSun + (SKY_B * sky + BOUNCE_B * bounce * skyV) * aoAmb + MIN_B);
    }

    /** Plant colour: the biome's grass colour plus gentle local variation. */
    private float[] tint(int x, int z) {
        int packed = center.tint[(x - cx0) * 16 + (z - cz0)];
        float br = ((packed >> 16) & 255) / 128f, bg = ((packed >> 8) & 255) / 128f, bb = (packed & 255) / 128f;
        if (packed == 0) { br = bg = bb = 1f; }
        float n = tintNoise.fbm(x / 28.0, z / 28.0, 2);
        float k = Math.max(0f, Math.min(1f, (n - 0.3f) * 2.5f));
        tintBuf[0] = br * (1.06f - 0.12f * k);
        tintBuf[1] = bg * (1.02f - 0.02f * k);
        tintBuf[2] = bb * (0.86f + 0.2f * k);
        return tintBuf;
    }

    private final float[] tintBuf = new float[3];

    private static float hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    /** Stairs, slabs, doors and trapdoors: each box face, culled only against full opaque neighbours. */
    private void shaped(int x, int y, int z, int b, int meta, FloatBuilder out) {
        int n = Shapes.boxes(b, meta, boxes);
        boolean door = Blocks.shape(b) == Shapes.DOOR;
        boolean upper = (meta & Shapes.UPPER) != 0;
        float ownSun = sun(x, y, z), ownSky = sky(x, y, z);
        float jitter = 0.97f + 0.06f * hash(x, y, z);
        for (int k = 0; k < n; k++) {
            int o = k * 6;
            float x0 = boxes[o], y0 = boxes[o + 1], z0 = boxes[o + 2];
            float x1 = boxes[o + 3], y1 = boxes[o + 4], z1 = boxes[o + 5];
            for (int f = 0; f < 6; f++) {
                boolean onEdge = (f == 0 && x1 >= 1) || (f == 1 && x0 <= 0) || (f == 2 && y1 >= 1)
                        || (f == 3 && y0 <= 0) || (f == 4 && z1 >= 1) || (f == 5 && z0 <= 0);
                float sunV = ownSun, skyV = ownSky;
                float flags = 0f;
                if (onEdge) {
                    int[] d = DIR[f];
                    int nb = get(x + d[0], y + d[1], z + d[2]);
                    if (Blocks.isOpaque(nb)) continue;
                    if (Blocks.isWater(nb)) flags = 2f;
                    sunV = sun(x + d[0], y + d[1], z + d[2]);
                    skyV = sky(x + d[0], y + d[1], z + d[2]);
                }
                int tile = door ? Blocks.doorTile(b, upper) : Blocks.tile(b, f);
                float u0 = (tile % PR) * T + E, v0 = (tile / PR) * T + E, us = T - 2 * E;
                int[][] vs = VERTS[f];
                for (int i = 0; i < 4; i++) {
                    int[] c = vs[i];
                    float lx = c[0] == 1 ? x1 : x0, ly = c[1] == 1 ? y1 : y0, lz = c[2] == 1 ? z1 : z0;
                    vx[i] = x + lx;
                    vy[i] = y + ly;
                    vz[i] = z + lz;
                    float tu, tv;
                    if (f == 2 || f == 3) {
                        tu = lx; tv = lz;
                    } else {
                        tv = 1 - ly;
                        tu = (f < 2) ? lz : lx;
                    }
                    vu[i] = u0 + tu * us;
                    vv[i] = v0 + tv * us;
                    light(i, f, 0.9f, sunV, skyV, jitter);
                    vf[i] = flags;
                }
                out.ensure(6 * FloatBuilder.STRIDE);
                emit(out, 0); emit(out, 1); emit(out, 2);
                emit(out, 0); emit(out, 2); emit(out, 3);
            }
        }
    }

    /** Plants: two crossed quads, visible from both sides, nudged a little off-centre. */
    private void cross(int x, int y, int z, int b, FloatBuilder out, float[] tint) {
        int tile = Blocks.tile(b, 2);
        float u0 = (tile % PR) * T + E, v0 = (tile / PR) * T + E, us = T - 2 * E;
        float sunV = sun(x, y, z), skyV = sky(x, y, z);
        float ox = 0, oz = 0;
        if (b != Blocks.VINES) {
            ox = (hash(x, y, z) - 0.5f) * 0.3f;
            oz = (hash(z, x, y) - 0.5f) * 0.3f;
        }
        float swayTop = Blocks.sway(b), swayBottom = 0f;
        if (b == Blocks.VINES) { swayBottom = swayTop; swayTop = 0.15f; }
        float under = b == Blocks.SEAGRASS ? 2f : 0f;
        boolean green = b == Blocks.GRASS_TUFT || b == Blocks.FERN || b == Blocks.BUSH || b == Blocks.VINES
                || b == Blocks.SEAGRASS;
        float a = 0.08f, c = 0.92f;
        for (int q = 0; q < 2; q++) {
            float xa = x + ox + a, za = z + oz + (q == 0 ? a : c);
            float xb = x + ox + c, zb = z + oz + (q == 0 ? c : a);
            // corners: 0 bottom-a, 1 top-a, 2 top-b, 3 bottom-b
            vx[0] = xa; vy[0] = y;     vz[0] = za; vu[0] = u0;      vv[0] = v0 + us;
            vx[1] = xa; vy[1] = y + 1; vz[1] = za; vu[1] = u0;      vv[1] = v0;
            vx[2] = xb; vy[2] = y + 1; vz[2] = zb; vu[2] = u0 + us; vv[2] = v0;
            vx[3] = xb; vy[3] = y;     vz[3] = zb; vu[3] = u0 + us; vv[3] = v0 + us;
            for (int i = 0; i < 4; i++) {
                boolean top = i == 1 || i == 2;
                float ao = top ? 1f : 0.62f;
                float ndl = 0.55f * sunV;
                float sky = 0.8f * (0.25f + 0.75f * skyV);
                vr[i] = SUN_R * ndl * ao + SKY_R * sky * ao + MIN_R;
                vg[i] = SUN_G * ndl * ao + SKY_G * sky * ao + MIN_G;
                vb[i] = SUN_B * ndl * ao + SKY_B * sky * ao + MIN_B;
                if (green) { vr[i] *= tint[0]; vg[i] *= tint[1]; vb[i] *= tint[2]; }
                vf[i] = (top ? swayTop : swayBottom) + under;
            }
            out.ensure(12 * FloatBuilder.STRIDE);
            emit(out, 0); emit(out, 1); emit(out, 2);
            emit(out, 0); emit(out, 2); emit(out, 3);
            emit(out, 0); emit(out, 2); emit(out, 1);   // back side
            emit(out, 0); emit(out, 3); emit(out, 2);
        }
    }

    private void emit(FloatBuilder out, int i) {
        out.vertex(vx[i] - cx0, vy[i], vz[i] - cz0, vu[i], vv[i], vr[i], vg[i], vb[i], vf[i]);
    }
}
