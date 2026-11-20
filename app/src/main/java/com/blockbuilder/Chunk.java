package com.blockbuilder;

import java.util.concurrent.atomic.AtomicBoolean;

/** A 16 x 96 x 16 column of the world. */
final class Chunk {
    static final int S = 16, H = World.SY;

    final int cx, cz;
    final char[] blocks = new char[S * S * H];
    final byte[] meta = new byte[S * S * H];
    /** Grass / plant colour per column (packed 0xRRGGBB, 128 = neutral per channel). */
    final int[] tint = new int[S * S];
    /** Biome index per column (for the HUD). */
    final short[] biome = new short[S * S];
    /** Changed since generation (or since it was loaded from disk) and must be saved. */
    volatile boolean modified;
    /** Has its own save file (so it is loaded rather than regenerated). */
    volatile boolean saved;
    final AtomicBoolean dirty = new AtomicBoolean(true);
    volatile boolean meshing;
    int maxY;

    Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    static int idx(int lx, int y, int lz) {
        return (lx * S + lz) * H + y;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    long key() {
        return key(cx, cz);
    }

    int get(int lx, int y, int lz) {
        return blocks[idx(lx, y, lz)];
    }

    void recomputeMaxY() {
        int m = 0;
        for (int i = 0; i < blocks.length; i++) if (blocks[i] != 0 && i % H > m) m = i % H;
        maxY = m;
    }
}
