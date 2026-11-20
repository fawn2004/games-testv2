package com.blockbuilder;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Procedurally painted 16x16-pixel tiles packed into a 256x256 atlas. */
final class TextureAtlas {
    static final int SIZE = 256, TILE = 16;
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
        px[((tile >> 4) * TILE + y) * SIZE + (tile & 15) * TILE + x] = argb;
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

    private static int[] paint() {
        px = new int[SIZE * SIZE];
        r = new Random(1337);

        // Grass top
        noisy(Blocks.T_GRASS_TOP, 0x5FA13A, 0.14f);
        // Dirt
        noisy(Blocks.T_DIRT, 0x866043, 0.14f);
        for (int i = 0; i < 18; i++)
            set(Blocks.T_DIRT, r.nextInt(16), r.nextInt(16), 0xFF000000 | shade(0x866043, 0.7f));
        // Grass side: dirt with a ragged green top
        for (int x = 0; x < TILE; x++) {
            int depth = 3 + r.nextInt(2) + (r.nextInt(5) == 0 ? 2 : 0);
            for (int y = 0; y < TILE; y++) {
                int c = y < depth ? shade(0x5A9A36, vary(0.12f)) : shade(0x866043, vary(0.14f));
                set(Blocks.T_GRASS_SIDE, x, y, 0xFF000000 | c);
            }
        }
        // Stone
        noisy(Blocks.T_STONE, 0x7F7F7F, 0.1f);
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(15), y = r.nextInt(16);
            set(Blocks.T_STONE, x, y, 0xFF000000 | shade(0x7F7F7F, 0.72f));
            set(Blocks.T_STONE, x + 1, y, 0xFF000000 | shade(0x7F7F7F, 0.78f));
        }
        // Cobblestone: voronoi cells with dark mortar
        {
            int n = 9;
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
                    int c = (d2 - d1 < 1.1f) ? shade(0x4E4E4E, vary(0.1f))
                            : shade(0x8C8C8C, cs[best] * vary(0.08f) * (1.1f - d1 * 0.04f));
                    set(Blocks.T_COBBLE, x, y, 0xFF000000 | c);
                }
            }
        }
        // Planks
        for (int y = 0; y < TILE; y++) {
            int row = y / 4;
            int seam = (row * 7 + 3) % 16;
            float rowShade = 0.92f + (row % 2) * 0.08f;
            for (int x = 0; x < TILE; x++) {
                int c;
                if (y % 4 == 3 || x == seam) c = shade(0xB08A55, 0.62f);
                else c = shade(0xB08A55, rowShade * vary(0.06f) * ((x + row) % 5 == 0 ? 0.92f : 1f));
                set(Blocks.T_PLANKS, x, y, 0xFF000000 | c);
            }
        }
        // Log side: vertical bark stripes
        for (int x = 0; x < TILE; x++) {
            float stripe = (x % 4 == 0) ? 0.72f : (x % 4 == 2 ? 0.9f : 1f);
            for (int y = 0; y < TILE; y++)
                set(Blocks.T_LOG_SIDE, x, y, 0xFF000000 | shade(0x6B4F2F, stripe * vary(0.1f)));
        }
        // Log top: rings
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float ddx = x - 7.5f, ddy = y - 7.5f;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                int c;
                if (d > 6.8f) c = shade(0x6B4F2F, vary(0.1f));
                else c = shade(((int) d) % 2 == 0 ? 0xBC9862 : 0xA07E4C, vary(0.05f));
                set(Blocks.T_LOG_TOP, x, y, 0xFF000000 | c);
            }
        }
        // Leaves (with holes for cut-out rendering)
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int c = 0xFF000000 | shade(0x3B8A2A, vary(0.25f));
                set(Blocks.T_LEAVES, x, y, r.nextFloat() < 0.16f ? 0 : c);
            }
        // Sand
        noisy(Blocks.T_SAND, 0xDBCF8E, 0.07f);
        // Glass: frame + highlights, clear center
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int c = 0;
                if (x == 0 || y == 0 || x == 15 || y == 15) c = 0xFFCFE8F2;
                else if ((x + y == 7 && x >= 2 && x <= 5) || (x + y == 10 && x >= 3 && x <= 6)) c = 0xFFF4FBFF;
                set(Blocks.T_GLASS, x, y, c);
            }
        // Brick
        for (int y = 0; y < TILE; y++) {
            int row = y / 4;
            int off = (row % 2) * 4;
            for (int x = 0; x < TILE; x++) {
                int c = (y % 4 == 3 || (x + off) % 8 == 7) ? shade(0xBDB5A6, vary(0.05f))
                        : shade(0x9C4A35, vary(0.1f));
                set(Blocks.T_BRICK, x, y, 0xFF000000 | c);
            }
        }
        // Water (translucent)
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                float w = 0.92f + 0.1f * (float) Math.sin((x + y * 0.5f) * 0.8f) + r.nextFloat() * 0.06f;
                set(Blocks.T_WATER, x, y, 0xB0000000 | shade(0x3466D6, w));
            }
        // Bedrock
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int[] levels = {0x2E2E2E, 0x555555, 0x7A7A7A};
                set(Blocks.T_BEDROCK, x, y, 0xFF000000 | levels[r.nextInt(3)]);
            }
        // Snow
        noisy(Blocks.T_SNOW, 0xF2F6FA, 0.03f);
        // Colored building blocks with a soft bevel
        int[] colors = {0xD83A2E, 0xF08A24, 0xF5D33A, 0x4CB84A, 0x2F6FE0, 0x8E44C4, 0xEDEDED, 0x2C2C30};
        for (int i = 0; i < colors.length; i++) {
            for (int y = 0; y < TILE; y++)
                for (int x = 0; x < TILE; x++) {
                    float f = vary(0.03f);
                    if (x == 0 || y == 0) f *= 1.18f;
                    else if (x == 15 || y == 15) f *= 0.75f;
                    else if (x == 1 || y == 1) f *= 1.07f;
                    else if (x == 14 || y == 14) f *= 0.88f;
                    int base = colors[i];
                    if (i == 7 && (x == 0 || y == 0)) base = 0x55555C;
                    set(Blocks.T_COLOR0 + i, x, y, 0xFF000000 | shade(base, f));
                }
        }
        int[] out = px;
        px = null;
        r = null;
        return out;
    }

    private TextureAtlas() {
    }
}
