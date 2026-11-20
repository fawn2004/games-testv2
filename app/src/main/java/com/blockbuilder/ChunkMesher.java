package com.blockbuilder;

/** Turns a 16x16 column of the world into triangle lists (x,y,z,u,v,light per vertex). */
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
    static final float[] SHADE = {0.8f, 0.8f, 1.0f, 0.55f, 0.68f, 0.68f};
    static final float[] AO = {0.5f, 0.68f, 0.84f, 1.0f};
    private static final int PR = TextureAtlas.PER_ROW;
    private static final float T = 1f / PR, E = 0.0002f;
    private final float[] boxes = new float[6 * 4];

    private final int[] ao = new int[4];
    private final float[] vx = new float[4], vy = new float[4], vz = new float[4],
            vu = new float[4], vv = new float[4], vl = new float[4];

    private static boolean shouldEmit(int b, int n) {
        if (n == Blocks.AIR) return true;
        if (Blocks.isOpaque(n)) return false;
        if (b == Blocks.WATER) return n != Blocks.WATER;
        if (b == Blocks.GLASS) return n != Blocks.GLASS;
        return true; // opaque or leaves next to glass / leaves / water
    }

    private static int occ(World w, int x, int y, int z) {
        return Blocks.isOpaque(w.get(x, y, z)) ? 1 : 0;
    }

    void build(World w, int cx, int cz, FloatBuilder solid, FloatBuilder water) {
        solid.clear();
        water.clear();
        int x0 = cx * World.CS, z0 = cz * World.CS;
        for (int x = x0; x < x0 + World.CS; x++) {
            for (int z = z0; z < z0 + World.CS; z++) {
                for (int y = 0; y < World.SY; y++) {
                    int b = w.blocks[World.idx(x, y, z)];
                    if (b == Blocks.AIR) continue;
                    if (Blocks.shape(b) != Shapes.CUBE) {
                        shaped(w, x, y, z, b, w.meta[World.idx(x, y, z)], solid);
                        continue;
                    }
                    boolean isWater = b == Blocks.WATER;
                    boolean glow = Blocks.isEmissive(b);
                    FloatBuilder out = isWater ? water : solid;
                    float waterTop = 1f;
                    if (isWater && w.get(x, y + 1, z) != Blocks.WATER) waterTop = 0.875f;
                    for (int f = 0; f < 6; f++) {
                        int[] d = DIR[f];
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        if (!shouldEmit(b, w.get(nx, ny, nz))) continue;
                        int tile = Blocks.tile(b, f);
                        float u0 = (tile % PR) * T + E, v0 = (tile / PR) * T + E, us = T - 2 * E;
                        int na = f >> 1, a1 = (na + 1) % 3, a2 = (na + 2) % 3;
                        int[][] vs = VERTS[f];
                        for (int i = 0; i < 4; i++) {
                            int[] c = vs[i];
                            if (isWater) {
                                ao[i] = 3;
                            } else {
                                int s1 = c[a1] == 1 ? 1 : -1, s2 = c[a2] == 1 ? 1 : -1;
                                int x1 = 0, y1 = 0, z1 = 0, x2 = 0, y2 = 0, z2 = 0;
                                if (a1 == 0) x1 = s1; else if (a1 == 1) y1 = s1; else z1 = s1;
                                if (a2 == 0) x2 = s2; else if (a2 == 1) y2 = s2; else z2 = s2;
                                int side1 = occ(w, nx + x1, ny + y1, nz + z1);
                                int side2 = occ(w, nx + x2, ny + y2, nz + z2);
                                int corner = occ(w, nx + x1 + x2, ny + y1 + y2, nz + z1 + z2);
                                ao[i] = (side1 == 1 && side2 == 1) ? 0 : 3 - (side1 + side2 + corner);
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
                            vl[i] = glow ? 1.0f : SHADE[f] * AO[ao[i]];
                        }
                        out.ensure(36);
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

    /** Stairs, slabs, doors and trapdoors: each box face, culled only against full opaque neighbours. */
    private void shaped(World w, int x, int y, int z, int b, int meta, FloatBuilder out) {
        int n = Shapes.boxes(b, meta, boxes);
        boolean door = Blocks.shape(b) == Shapes.DOOR;
        boolean upper = (meta & Shapes.UPPER) != 0;
        for (int k = 0; k < n; k++) {
            int o = k * 6;
            float x0 = boxes[o], y0 = boxes[o + 1], z0 = boxes[o + 2];
            float x1 = boxes[o + 3], y1 = boxes[o + 4], z1 = boxes[o + 5];
            for (int f = 0; f < 6; f++) {
                boolean onEdge = (f == 0 && x1 >= 1) || (f == 1 && x0 <= 0) || (f == 2 && y1 >= 1)
                        || (f == 3 && y0 <= 0) || (f == 4 && z1 >= 1) || (f == 5 && z0 <= 0);
                if (onEdge) {
                    int[] d = DIR[f];
                    if (Blocks.isOpaque(w.get(x + d[0], y + d[1], z + d[2]))) continue;
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
                    vl[i] = SHADE[f];
                }
                out.ensure(36);
                emit(out, 0); emit(out, 1); emit(out, 2);
                emit(out, 0); emit(out, 2); emit(out, 3);
            }
        }
    }

    private void emit(FloatBuilder out, int i) {
        out.vertex(vx[i], vy[i], vz[i], vu[i], vv[i], vl[i]);
    }
}
