package com.blockbuilder;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Procedurally painted 16x16-pixel tiles packed into a 512x512 atlas (32x32 tiles). */
final class TextureAtlas {
    static final int SIZE = 512, TILE = 16, PER_ROW = 32;
    private static int[] cached;

    /** ARGB pixels (non-premultiplied). */
    static synchronized int[] pixels() {
        if (cached == null) cached = paint();
        return cached;
    }

    static int upload() {
        int[] px = pixels();
        ByteBuffer buf = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder());
        for (int p : px) {
            buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        buf.position(0);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SIZE, SIZE, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        return ids[0];
    }

    // ---------------------------------------------------------------- painting

    private static int[] px;
    private static Random r;

    private static void set(int tile, int x, int y, int argb) {
        px[((tile / PER_ROW) * TILE + y) * SIZE + (tile % PER_ROW) * TILE + x] = argb;
    }

    private static int shade(int rgb, float f) {
        int rr = clamp((int) (((rgb >> 16) & 255) * f));
        int gg = clamp((int) (((rgb >> 8) & 255) * f));
        int bb = clamp((int) ((rgb & 255) * f));
        return (rr << 16) | (gg << 8) | bb;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float vary(float amount) {
        return 1f - amount + r.nextFloat() * amount * 2f;
    }

    private static void noisy(int tile, int rgb, float amount) {
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++)
                set(tile, x, y, 0xFF000000 | shade(rgb, vary(amount)));
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** Voronoi cells: stones separated by mortar. */
    private static void cells(int tile, int n, int stone, int mortar, float mortarW, int moss) {
        float[] cx = new float[n], cy = new float[n], cs = new float[n];
        for (int i = 0; i < n; i++) {
            cx[i] = r.nextFloat() * 16; cy[i] = r.nextFloat() * 16; cs[i] = 0.8f + r.nextFloat() * 0.4f;
        }
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float d1 = 1e9f, d2 = 1e9f; int best = 0;
                for (int i = 0; i < n; i++) {
                    for (int ox = -16; ox <= 16; ox += 16) {
                        for (int oy = -16; oy <= 16; oy += 16) {
                            float ddx = x + 0.5f - (cx[i] + ox), ddy = y + 0.5f - (cy[i] + oy);
                            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                            if (d < d1) { d2 = d1; d1 = d; best = i; } else if (d < d2) d2 = d;
                        }
                    }
                }
                int c = (d2 - d1 < mortarW) ? shade(mortar, vary(0.1f))
                        : shade(stone, cs[best] * vary(0.08f) * (1.1f - d1 * 0.04f));
                if (moss != 0 && r.nextFloat() < (d2 - d1 < mortarW + 0.8f ? 0.55f : 0.12f))
                    c = shade(moss, vary(0.15f));
                set(tile, x, y, argb(c));
            }
        }
    }

    /** Running-bond bricks. */
    private static void bricks(int tile, int brick, int mortar, int bw, int bh, float var, int moss) {
        for (int y = 0; y < TILE; y++) {
            int row = y / bh;
            int off = (row % 2) * (bw / 2);
            for (int x = 0; x < TILE; x++) {
                boolean m = y % bh == bh - 1 || (x + off) % bw == bw - 1;
                int c = m ? shade(mortar, vary(0.05f)) : shade(brick, vary(var) * (y % bh == 0 ? 1.08f : 1f));
                if (moss != 0 && r.nextFloat() < (m ? 0.5f : 0.1f)) c = shade(moss, vary(0.15f));
                set(tile, x, y, argb(c));
            }
        }
    }

    private static void planks(int tile, int color) {
        for (int y = 0; y < TILE; y++) {
            int row = y / 4;
            int seam = (row * 7 + 3) % 16;
            float rowShade = 0.92f + (row % 2) * 0.08f;
            for (int x = 0; x < TILE; x++) {
                int c;
                if (y % 4 == 3 || x == seam) c = shade(color, 0.62f);
                else c = shade(color, rowShade * vary(0.06f) * ((x + row) % 5 == 0 ? 0.92f : 1f));
                set(tile, x, y, argb(c));
            }
        }
    }

    private static void logSide(int tile, int bark, boolean spots) {
        for (int x = 0; x < TILE; x++) {
            float stripe = (x % 4 == 0) ? 0.72f : (x % 4 == 2 ? 0.9f : 1f);
            for (int y = 0; y < TILE; y++) {
                int c = shade(bark, stripe * vary(0.1f));
                set(tile, x, y, argb(c));
            }
        }
        if (spots) {
            for (int i = 0; i < 7; i++) {
                int x = r.nextInt(13), y = r.nextInt(16);
                for (int k = 0; k < 3; k++) set(tile, x + k, y, argb(shade(0x2E2A26, vary(0.1f))));
            }
        }
    }

    private static void logTop(int tile, int bark, int ringA, int ringB) {
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float ddx = x - 7.5f, ddy = y - 7.5f;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                int c;
                if (d > 6.8f) c = shade(bark, vary(0.1f));
                else c = shade(((int) d) % 2 == 0 ? ringA : ringB, vary(0.05f));
                set(tile, x, y, argb(c));
            }
        }
    }

    private static void leaves(int tile, int color, float holes, int dots) {
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int c = argb(shade(color, vary(0.25f)));
                set(tile, x, y, r.nextFloat() < holes ? 0 : c);
            }
        if (dots != 0) {
            for (int i = 0; i < 14; i++) set(tile, r.nextInt(16), r.nextInt(16), argb(shade(dots, vary(0.08f))));
        }
    }

    /** Copies a stone tile and sprinkles clusters of mineral flecks. */
    private static void ore(int tile, int fleck, int clusters) {
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++)
                set(tile, x, y, argb(shade(0x7F7F7F, vary(0.1f))));
        for (int i = 0; i < clusters; i++) {
            int x = 1 + r.nextInt(13), y = 1 + r.nextInt(13);
            set(tile, x, y, argb(shade(fleck, 1.15f)));
            set(tile, x + 1, y, argb(shade(fleck, 0.95f)));
            set(tile, x, y + 1, argb(shade(fleck, 0.85f)));
            if (r.nextBoolean()) set(tile, x + 1, y + 1, argb(shade(fleck, 0.7f)));
        }
    }

    /** Metal-like block: bevel, sheen and corner rivets. */
    private static void metal(int tile, int color, boolean rivets) {
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = 0.95f + 0.1f * (float) Math.sin((x - y) * 0.35f) + r.nextFloat() * 0.03f;
                if (x == 0 || y == 0) f *= 1.25f;
                else if (x == 15 || y == 15) f *= 0.7f;
                if (rivets && (x == 2 || x == 13) && (y == 2 || y == 13)) f *= 0.65f;
                set(tile, x, y, argb(shade(color, f)));
            }
    }

    private static void colorBlock(int tile, int base, boolean darkEdge) {
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = vary(0.03f);
                if (x == 0 || y == 0) f *= 1.18f;
                else if (x == 15 || y == 15) f *= 0.75f;
                else if (x == 1 || y == 1) f *= 1.07f;
                else if (x == 14 || y == 14) f *= 0.88f;
                int b = base;
                if (darkEdge && (x == 0 || y == 0)) b = 0x55555C;
                set(tile, x, y, argb(shade(b, f)));
            }
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Six tiles per species: log side, log top, planks, door lower, door upper, trapdoor. */
    private static void paintWood(int t, Wood w, int index) {
        int side = t, top = t + 1, plank = t + 2, doorLo = t + 3, doorHi = t + 4, trap = t + 5;

        // ---- bark
        for (int x = 0; x < TILE; x++) {
            float col = vary(0.06f);
            for (int y = 0; y < TILE; y++) {
                int c;
                switch (w.barkStyle) {
                    case Wood.B_SMOOTH:
                        c = shade(w.bark, vary(0.05f) * (x % 7 == 0 ? 0.92f : 1f));
                        break;
                    case Wood.B_BIRCH:
                        c = shade(w.bark, vary(0.04f));
                        break;
                    case Wood.B_PATCHY: {
                        float n = (float) Math.sin(x * 0.9f + Math.sin(y * 0.7f) * 2f) + (float) Math.sin(y * 0.6f - x * 0.3f);
                        c = shade(n > 0.4f ? w.accent : w.bark, vary(0.05f));
                        break;
                    }
                    case Wood.B_FIBROUS:
                        c = shade(w.bark, col * ((x % 3 == 0) ? 0.78f : 1.05f) * vary(0.05f));
                        break;
                    case Wood.B_LENTICEL:
                        c = shade(w.bark, vary(0.06f) * ((y % 5 == 2 && (x + y) % 6 < 3) ? 1.0f : 0.95f));
                        if (y % 5 == 2 && (x + y * 3) % 7 < 3) c = shade(w.accent, vary(0.05f));
                        break;
                    case Wood.B_SHAGGY:
                        c = shade(w.bark, ((x + (y / 5)) % 4 == 0 ? 0.6f : 1.05f) * vary(0.08f));
                        break;
                    case Wood.B_PLATED: {
                        int plate = ((y + (x / 4) * 3) / 5);
                        boolean crack = (y + (x / 4) * 3) % 5 == 0 || x % 4 == 0;
                        c = shade(w.bark, crack ? 0.68f : (0.95f + (plate % 3) * 0.05f) * vary(0.05f));
                        break;
                    }
                    default: // furrowed
                        c = shade(w.bark, ((x % 4 == 0) ? 0.7f : (x % 4 == 2 ? 0.9f : 1.04f)) * vary(0.08f));
                }
                set(side, x, y, argb(c));
            }
        }
        if (w.barkStyle == Wood.B_BIRCH) {
            for (int i = 0; i < 8; i++) {
                int x = r.nextInt(12), y = r.nextInt(16), len = 2 + r.nextInt(3);
                for (int k = 0; k < len; k++) set(side, x + k, y, argb(shade(w.accent, vary(0.1f))));
            }
        }

        // ---- log top: rings with bark rim
        int heart = shade(w.planks, 0.88f);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float ddx = x - 7.5f, ddy = y - 7.5f;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                int c;
                if (d > 6.8f) c = shade(w.bark, vary(0.08f));
                else if (d < 1.2f) c = shade(heart, 0.8f);
                else c = shade(((int) (d * 1.2f)) % 2 == 0 ? w.planks : heart, vary(0.04f));
                set(top, x, y, argb(c));
            }

        // ---- planks
        int dark = w.accent != 0 ? w.accent : shade(w.planks, 0.78f);
        for (int y = 0; y < TILE; y++) {
            int row = y / 4;
            int seam = (row * 7 + 3 + index) % 16;
            float rowShade = 0.93f + ((row + index) % 2) * 0.07f;
            for (int x = 0; x < TILE; x++) {
                int c = shade(w.planks, rowShade * vary(0.04f));
                switch (w.grain) {
                    case Wood.G_STRONG:
                        if ((x * 2 + row * 5 + (y % 4) * 3) % 9 == 0) c = shade(w.planks, 0.8f);
                        break;
                    case Wood.G_KNOTS:
                        if ((x + row * 5) % 11 == 0 && y % 4 == 1) c = shade(w.accent, 0.8f);
                        else if ((x * 3 + row) % 13 == 0) c = shade(w.planks, 0.86f);
                        break;
                    case Wood.G_STRIPES:
                        if (((x + y * 2 + index) / 2) % 3 == 0) c = shade(dark, vary(0.06f));
                        break;
                    case Wood.G_RIBBON:
                        if (((x + row * 3) / 3) % 2 == 0) c = shade(mix(w.planks, dark, 0.5f), vary(0.04f));
                        break;
                    case Wood.G_STREAKS:
                        if ((x * 5 + row * 7) % 6 == 0) c = shade(dark, vary(0.06f));
                        break;
                    default:
                        if ((x * 3 + row * 7) % 14 == 0) c = shade(w.planks, 0.9f);
                }
                if (y % 4 == 3 || x == seam) c = shade(w.planks, 0.6f);
                set(plank, x, y, argb(c));
            }
        }

        // ---- door (two halves) and trapdoor; three door designs cycle through the species
        int frame = shade(w.planks, 0.72f), panel = shade(w.planks, 1.0f), inset = shade(w.planks, 0.86f);
        int style = index % 3;
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                boolean edge = x <= 1 || x >= 14;
                int lo, hi;
                if (style == 0) {           // raised panels, solid
                    boolean rail = y <= 1 || y >= 14;
                    lo = (edge || rail) ? frame : ((x == 2 || y == 2) ? inset : panel);
                    hi = lo;
                } else if (style == 1) {    // glazed top half
                    boolean rail = y <= 1 || y >= 14;
                    lo = (edge || rail) ? frame : ((x == 2 || y == 2) ? inset : panel);
                    boolean bar = x == 7 || x == 8 || y == 7 || y == 8;
                    hi = (edge || y <= 1 || y >= 13 || bar) ? frame : 0;
                } else {                    // vertical boards with a brace
                    boolean gap = x % 4 == 3;
                    lo = gap ? frame : panel;
                    hi = lo;
                    if (Math.abs((15 - y) - x) <= 1) lo = frame;
                    if (y == 2 || y == 3) hi = frame;
                    if (y == 12 || y == 13) lo = frame;
                }
                if (lo != 0) lo = argb(shade(lo, vary(0.04f)));
                if (hi != 0) hi = argb(shade(hi, vary(0.04f)));
                if (x == 12 && (y == 1 || y == 2)) lo = argb(0x3A3A3A); // handle (top of lower half)
                set(doorLo, x, y, lo);
                set(doorHi, x, y, hi);

                int tc;
                boolean tEdge = x <= 1 || y <= 1 || x >= 14 || y >= 14;
                if (index % 2 == 0) {       // grid trapdoor with holes
                    boolean hole = !tEdge && (x % 5 != 2 && x % 5 != 3) && (y % 5 != 2 && y % 5 != 3) && x > 3 && x < 12 && y > 3 && y < 12;
                    tc = hole ? 0 : argb(shade(tEdge ? frame : panel, vary(0.04f)));
                } else {                    // solid boards
                    tc = argb(shade((tEdge || y % 5 == 0) ? frame : panel, vary(0.04f)));
                }
                set(trap, x, y, tc);
            }
    }

    private static int[] paint() {
        px = new int[SIZE * SIZE];
        r = new Random(1337);

        // ---------------- original tiles
        noisy(Blocks.T_GRASS_TOP, 0x5FA13A, 0.14f);
        noisy(Blocks.T_DIRT, 0x866043, 0.14f);
        for (int i = 0; i < 18; i++)
            set(Blocks.T_DIRT, r.nextInt(16), r.nextInt(16), argb(shade(0x866043, 0.7f)));
        for (int x = 0; x < TILE; x++) {
            int depth = 3 + r.nextInt(2) + (r.nextInt(5) == 0 ? 2 : 0);
            for (int y = 0; y < TILE; y++) {
                int c = y < depth ? shade(0x5A9A36, vary(0.12f)) : shade(0x866043, vary(0.14f));
                set(Blocks.T_GRASS_SIDE, x, y, argb(c));
            }
        }
        noisy(Blocks.T_STONE, 0x7F7F7F, 0.1f);
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(15), y = r.nextInt(16);
            set(Blocks.T_STONE, x, y, argb(shade(0x7F7F7F, 0.72f)));
            set(Blocks.T_STONE, x + 1, y, argb(shade(0x7F7F7F, 0.78f)));
        }
        cells(Blocks.T_COBBLE, 9, 0x8C8C8C, 0x4E4E4E, 1.1f, 0);
        planks(Blocks.T_PLANKS, 0xB08A55);
        logSide(Blocks.T_LOG_SIDE, 0x6B4F2F, false);
        logTop(Blocks.T_LOG_TOP, 0x6B4F2F, 0xBC9862, 0xA07E4C);
        leaves(Blocks.T_LEAVES, 0x3B8A2A, 0.16f, 0);
        noisy(Blocks.T_SAND, 0xDBCF8E, 0.07f);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int c = 0;
                if (x == 0 || y == 0 || x == 15 || y == 15) c = 0xFFCFE8F2;
                else if ((x + y == 7 && x >= 2 && x <= 5) || (x + y == 10 && x >= 3 && x <= 6)) c = 0xFFF4FBFF;
                set(Blocks.T_GLASS, x, y, c);
            }
        bricks(Blocks.T_BRICK, 0x9C4A35, 0xBDB5A6, 8, 4, 0.1f, 0);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float w = 0.92f + 0.1f * (float) Math.sin((x + y * 0.5f) * 0.8f) + r.nextFloat() * 0.06f;
                set(Blocks.T_WATER, x, y, 0xB0000000 | shade(0x3466D6, w));
            }
        int[] levels = {0x2E2E2E, 0x555555, 0x7A7A7A};
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++)
                set(Blocks.T_BEDROCK, x, y, argb(levels[r.nextInt(3)]));
        noisy(Blocks.T_SNOW, 0xF2F6FA, 0.03f);
        int[] colors = {0xD83A2E, 0xF08A24, 0xF5D33A, 0x4CB84A, 0x2F6FE0, 0x8E44C4, 0xEDEDED, 0x2C2C30};
        for (int i = 0; i < colors.length; i++) colorBlock(Blocks.T_COLOR0 + i, colors[i], i == 7);

        // ---------------- natural
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int[] g = {0x8A8580, 0x6E6A66, 0xA39E98, 0x5A5652, 0x948C84};
                set(Blocks.T_GRAVEL, x, y, argb(shade(g[r.nextInt(g.length)], vary(0.06f))));
            }
        noisy(Blocks.T_CLAY, 0x9EA4B0, 0.05f);
        for (int i = 0; i < 6; i++) {
            int y = r.nextInt(16), x = r.nextInt(12);
            for (int k = 0; k < 4; k++) set(Blocks.T_CLAY, x + k, y, argb(shade(0x9EA4B0, 0.9f)));
        }
        cells(Blocks.T_MOSSY_COBBLE, 9, 0x8C8C8C, 0x4E4E4E, 1.1f, 0x4F7A32);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float p = r.nextFloat();
                int c = p < 0.12f ? 0x5A3A32 : p < 0.25f ? 0xD9B7A6 : 0xA8705A;
                set(Blocks.T_GRANITE, x, y, argb(shade(c, vary(0.07f))));
            }
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float v = (float) Math.sin(x * 0.7f + y * 0.35f + Math.sin(y * 0.9f) * 1.6f);
                int c = v > 0.88f ? shade(0xA9A9B4, vary(0.05f)) : shade(0xEDEBE6, vary(0.025f));
                set(Blocks.T_MARBLE, x, y, argb(c));
            }
        for (int y = 0; y < TILE; y++) {
            float band = (y % 4 == 0) ? 0.78f : (y % 4 == 2 ? 1.06f : 0.95f);
            for (int x = 0; x < TILE; x++) set(Blocks.T_SLATE, x, y, argb(shade(0x4D545C, band * vary(0.06f))));
        }
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = (x % 5 == 0) ? 0.7f : vary(0.08f);
                set(Blocks.T_BASALT_SIDE, x, y, argb(shade(0x45464A, f)));
                float ddx = x - 7.5f, ddy = y - 7.5f;
                boolean ring = Math.abs(Math.max(Math.abs(ddx), Math.abs(ddy)) - 5.5f) < 0.6f;
                set(Blocks.T_BASALT_TOP, x, y, argb(shade(0x45464A, ring ? 0.7f : vary(0.08f))));
            }
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = vary(0.04f);
                if ((x + 2 * y) % 13 == 0 || (2 * x - y + 32) % 17 == 0) f *= 1.15f;
                set(Blocks.T_ICE, x, y, argb(shade(0x9CC8F0, f)));
            }
        noisy(Blocks.T_MUD, 0x4A3A2E, 0.12f);
        noisy(Blocks.T_RED_SAND, 0xC0703A, 0.07f);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int c = r.nextFloat() < 0.08f ? shade(0x5E2E8A, vary(0.15f)) : shade(0x17121F, vary(0.2f));
                set(Blocks.T_OBSIDIAN, x, y, argb(c));
            }

        // ---------------- wood
        logSide(Blocks.T_LIGHT_LOG_SIDE, 0xD8D2C4, true);
        logTop(Blocks.T_LIGHT_LOG_TOP, 0xD8D2C4, 0xE2CC96, 0xCDB57E);
        planks(Blocks.T_LIGHT_PLANKS, 0xD9C38E);
        logSide(Blocks.T_DARK_LOG_SIDE, 0x3A2A1C, false);
        logTop(Blocks.T_DARK_LOG_TOP, 0x3A2A1C, 0x6A4A2E, 0x58402A);
        planks(Blocks.T_DARK_PLANKS, 0x5C4028);
        leaves(Blocks.T_AUTUMN_LEAVES, 0xD0702A, 0.16f, 0xE8B23A);
        leaves(Blocks.T_BLOSSOM_LEAVES, 0xEFA3C4, 0.14f, 0xFFFFFF);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = (x % 3 == 0) ? 0.85f : vary(0.08f);
                if (y == 3 || y == 12) f = 0.6f;
                set(Blocks.T_HAY_SIDE, x, y, argb(shade(0xD6B341, f)));
                set(Blocks.T_HAY_TOP, x, y, argb(shade(0xD6B341, ((x + y) % 4 == 0 ? 0.8f : vary(0.1f)))));
            }
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                boolean frame = x <= 1 || y <= 1 || x >= 14 || y >= 14;
                boolean brace = Math.abs(x - y) <= 1;
                int c = frame ? shade(0x7A5A34, vary(0.06f) * (x == 0 || y == 0 ? 1.15f : 1f))
                        : brace ? shade(0x8E6A3E, vary(0.06f)) : shade(0xB08A55, vary(0.07f) * (y % 4 == 0 ? 0.85f : 1f));
                set(Blocks.T_CRATE, x, y, argb(c));
            }
        {
            int[] books = {0x9C2A2A, 0x2A4F9C, 0x2F7A3A, 0xA8762A, 0x6A3A8A, 0x2A7A7A};
            for (int y = 0; y < TILE; y++)
                for (int x = 0; x < TILE; x++) {
                    boolean shelf = y == 0 || y == 7 || y == 8 || y == 15 || x == 0 || x == 15;
                    int c;
                    if (shelf) c = shade(0xB08A55, vary(0.06f) * (y == 7 ? 0.7f : 1f));
                    else {
                        int book = (x - 1) / 2 + (y > 7 ? 3 : 0);
                        int bc = books[(book * 5 + (y > 7 ? 2 : 0)) % books.length];
                        boolean gap = (y < 7 ? ((x * 3 + 1) % 7 == 0 && y < 3) : ((x * 5) % 9 == 0 && y < 11));
                        c = gap ? 0x2A1E12 : shade(bc, (x % 2 == 0 ? 1f : 0.82f) * vary(0.05f));
                    }
                    set(Blocks.T_BOOKSHELF, x, y, argb(c));
                }
        }

        // ---------------- building
        bricks(Blocks.T_STONE_BRICKS, 0x8A8A8A, 0x5A5A5A, 8, 8, 0.06f, 0);
        bricks(Blocks.T_MOSSY_BRICKS, 0x8A8A8A, 0x5A5A5A, 8, 8, 0.06f, 0x4F7A32);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = vary(0.02f);
                if (x == 0 || y == 0) f *= 1.15f;
                else if (x == 15 || y == 15) f *= 0.75f;
                set(Blocks.T_POLISHED_STONE, x, y, argb(shade(0xA0A0A0, f)));
            }
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float band = y < 3 ? 1.06f : (y == 3 || y == 12 ? 0.85f : (y > 12 ? 0.95f : 1f));
                set(Blocks.T_SANDSTONE_SIDE, x, y, argb(shade(0xDCCB8E, band * vary(0.04f))));
                set(Blocks.T_SANDSTONE_TOP, x, y, argb(shade(0xE2D29A, vary(0.04f))));
            }
        noisy(Blocks.T_TERRACOTTA, 0xA45E44, 0.04f);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int row = y / 4, off = (row % 2) * 2;
                float t = ((x + off) % 4) / 3f;
                float f = (y % 4 == 3) ? 0.6f : 1.1f - t * 0.25f - (y % 4) * 0.04f;
                set(Blocks.T_ROOF_TILES, x, y, argb(shade(0x8E3A2A, f * vary(0.03f))));
            }
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                boolean dark = ((x / 4) + (y / 4)) % 2 == 0;
                set(Blocks.T_CHECKER, x, y, argb(shade(dark ? 0x2E2E34 : 0xE8E6E0, vary(0.02f))));
            }
        metal(Blocks.T_METAL, 0x9AA2AC, true);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                boolean frame = x <= 1 || y <= 1 || x >= 14 || y >= 14;
                boolean bar = x == 7 || x == 8 || y == 7 || y == 8;
                float ddx = x - 7.5f, ddy = y - 7.5f;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                int c = frame ? shade(0x5A4630, vary(0.06f)) : bar ? shade(0xE8C468, 0.9f)
                        : shade(0xFFF2B0, 1.05f - d * 0.03f);
                set(Blocks.T_LAMP, x, y, argb(c));
            }

        // ---------------- minerals
        ore(Blocks.T_COAL_ORE, 0x222222, 6);
        ore(Blocks.T_COPPER_ORE, 0xD07A48, 5);
        ore(Blocks.T_GOLD_ORE, 0xF2CE3A, 5);
        ore(Blocks.T_CRYSTAL_ORE, 0x6FE6E8, 4);
        metal(Blocks.T_COPPER_BLOCK, 0xC8754A, false);
        metal(Blocks.T_GOLD_BLOCK, 0xF2C83A, false);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float f = 0.85f + 0.25f * (float) Math.abs(Math.sin((x * 0.9f + y * 0.4f))) + r.nextFloat() * 0.04f;
                if ((x + y) % 8 == 0) f = 1.3f;
                if (x == 0 || y == 0) f *= 1.15f;
                else if (x == 15 || y == 15) f *= 0.7f;
                set(Blocks.T_CRYSTAL_BLOCK, x, y, argb(shade(0x5CCFE0, f)));
            }

        // ---------------- extra colors
        for (int i = 0; i < Wood.ALL.length; i++) paintWood(Blocks.T_WOOD0 + i * 6, Wood.ALL[i], i);

        int[] more = {0xF29BB8, 0x8CD63C, 0x2AA6A6, 0x7DB8F0, 0x7A4E2E, 0x6A6A70, 0xAEAEB4, 0xC03CB0};
        for (int i = 0; i < more.length; i++) colorBlock(Blocks.T_COLOR8 + i, more[i], false);

        int[] out = px;
        px = null;
        r = null;
        return out;
    }

    private TextureAtlas() {
    }
}
