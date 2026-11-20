package com.blockbuilder;

/**
 * Geometry of non-cube blocks. A shape is a list of boxes in block-local coordinates
 * {x0, y0, z0, x1, y1, z1}, used for drawing, collision and aiming.
 *
 * Meta bits: 0-1 facing (0 north/-Z, 1 east/+X, 2 south/+Z, 3 west/-X), 2 top/upside-down,
 * 3 open, 4 hinge on the right, 5 upper door half.
 */
final class Shapes {
    static final int CUBE = 0, STAIRS = 1, SLAB = 2, DOOR = 3, TRAPDOOR = 4;
    static final int TOP = 4, OPEN = 8, HINGE_RIGHT = 16, UPPER = 32;
    private static final float T = 3f / 16f;

    static int facing(int meta) {
        return meta & 3;
    }

    /** Writes the boxes of a block into out (6 floats each) and returns how many there are. */
    static int boxes(int block, int meta, float[] out) {
        int shape = Blocks.shape(block);
        boolean top = (meta & TOP) != 0;
        int f = meta & 3;
        switch (shape) {
            case SLAB:
                return put(out, 0, 0, top ? 0.5f : 0, 0, 1, top ? 1 : 0.5f, 1);
            case STAIRS: {
                put(out, 0, 0, top ? 0.5f : 0, 0, 1, top ? 1 : 0.5f, 1);
                put(out, 1, 0, top ? 0 : 0.5f, 0, 1, top ? 0.5f : 1, 0.5f);
                rotate(out, 1, f);
                return 2;
            }
            case DOOR: {
                boolean open = (meta & OPEN) != 0;
                if (!open) put(out, 0, 0, 0, 0, 1, 1, T);
                else if ((meta & HINGE_RIGHT) != 0) put(out, 0, 1 - T, 0, 0, 1, 1, 1);
                else put(out, 0, 0, 0, 0, T, 1, 1);
                rotate(out, 0, f);
                return 1;
            }
            case TRAPDOOR: {
                if ((meta & OPEN) != 0) put(out, 0, 0, 0, 0, 1, 1, T);
                else if (top) put(out, 0, 0, 1 - T, 0, 1, 1, 1);
                else put(out, 0, 0, 0, 0, 1, T, 1);
                rotate(out, 0, f);
                return 1;
            }
            default:
                return put(out, 0, 0, 0, 0, 1, 1, 1);
        }
    }

    private static int put(float[] o, int i, float x0, float y0, float z0, float x1, float y1, float z1) {
        int k = i * 6;
        o[k] = x0; o[k + 1] = y0; o[k + 2] = z0; o[k + 3] = x1; o[k + 4] = y1; o[k + 5] = z1;
        return i + 1;
    }

    /** Rotates box i around the block centre from "facing north" to the given facing. */
    private static void rotate(float[] o, int i, int f) {
        if (f == 0) return;
        int k = i * 6;
        float ax = o[k], az = o[k + 2], bx = o[k + 3], bz = o[k + 5];
        float nax, naz, nbx, nbz;
        if (f == 1) { nax = 1 - az; naz = ax; nbx = 1 - bz; nbz = bx; }
        else if (f == 2) { nax = 1 - ax; naz = 1 - az; nbx = 1 - bx; nbz = 1 - bz; }
        else { nax = az; naz = 1 - ax; nbx = bz; nbz = 1 - bx; }
        o[k] = Math.min(nax, nbx); o[k + 3] = Math.max(nax, nbx);
        o[k + 2] = Math.min(naz, nbz); o[k + 5] = Math.max(naz, nbz);
    }

    /** Facing (0..3) from a horizontal look direction. */
    static int facingFromLook(float dx, float dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz < 0 ? 0 : 2;
    }

    private Shapes() {
    }
}
