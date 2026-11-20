package com.blockbuilder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

/** Isometric block icons and raw tile bitmaps built from the texture atlas. */
final class BlockIcons {
    private static Bitmap[] icons;
    private static final Bitmap[] tiles = new Bitmap[TextureAtlas.PER_ROW * TextureAtlas.PER_ROW];
    private static final Matrix m = new Matrix();
    private static final Paint top = new Paint(), left = new Paint(), right = new Paint();
    private static final float[] boxes = new float[6 * 4];

    static {
        top.setFilterBitmap(false);
        left.setFilterBitmap(false);
        right.setFilterBitmap(false);
        left.setColorFilter(new LightingColorFilter(0xFFC8C8C8, 0));
        right.setColorFilter(new LightingColorFilter(0xFF9A9A9A, 0));
    }

    /** The icon for a block, created on first use. */
    static synchronized Bitmap icon(Context ctx, int b) {
        if (icons == null) icons = new Bitmap[Blocks.COUNT];
        if (b <= 0 || b >= Blocks.COUNT) return null;
        if (icons[b] != null) return icons[b];
        float dp = ctx.getResources().getDisplayMetrics().density;
        int s = (int) (40 * dp);
        Bitmap icon = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(icon);
        int shape = Blocks.shape(b);
        if (shape == Shapes.DOOR) {
            // Flat door: lower and upper halves stacked.
            android.graphics.RectF r = new android.graphics.RectF(s * 0.28f, s * 0.5f, s * 0.72f, s * 0.98f);
            c.drawBitmap(tile(Blocks.doorTile(b, false), false), null, r, top);
            r.set(s * 0.28f, s * 0.02f, s * 0.72f, s * 0.5f);
            c.drawBitmap(tile(Blocks.doorTile(b, true), false), null, r, top);
        } else {
            int n = Shapes.boxes(b, 0, boxes);
            // Painter's order: boxes further from the viewer first.
            Integer[] order = new Integer[n];
            for (int k = 0; k < n; k++) order[k] = k;
            java.util.Arrays.sort(order, (p, q) -> Float.compare(depth(p), depth(q)));
            int[] t = Blocks.TILES[b];
            for (int k : order) drawBox(c, s, k * 6, tile(t[0], true), tile(t[1], true));
        }
        icons[b] = icon;
        return icon;
    }

    private static float depth(int k) {
        int o = k * 6;
        return (boxes[o] + boxes[o + 3]) + (boxes[o + 1] + boxes[o + 4]) + (boxes[o + 2] + boxes[o + 5]);
    }

    /** Isometric projection of a block-local point, in icon pixels. */
    private static float sx(int s, float x, float z) {
        return (0.5f + 0.44f * (x - z)) * s;
    }

    private static float sy(int s, float x, float y, float z) {
        return (0.06f + 0.23f * (x + z) + 0.45f * (1 - y)) * s;
    }

    private static void drawBox(Canvas c, int s, int o, Bitmap topTile, Bitmap sideTile) {
        float x0 = boxes[o], y0 = boxes[o + 1], z0 = boxes[o + 2];
        float x1 = boxes[o + 3], y1 = boxes[o + 4], z1 = boxes[o + 5];
        // +Z face (left): u = x, v = 1 - y
        face(c, sideTile, x0, 1 - y1, x1, 1 - y0,
                sx(s, x0, z1), sy(s, x0, y1, z1), sx(s, x1, z1), sy(s, x1, y1, z1), sx(s, x0, z1), sy(s, x0, y0, z1), left);
        // +X face (right): u = z, v = 1 - y
        face(c, sideTile, z0, 1 - y1, z1, 1 - y0,
                sx(s, x1, z0), sy(s, x1, y1, z0), sx(s, x1, z1), sy(s, x1, y1, z1), sx(s, x1, z0), sy(s, x1, y0, z0), right);
        // +Y face (top): u = x, v = z
        face(c, topTile, x0, z0, x1, z1,
                sx(s, x0, z0), sy(s, x0, y1, z0), sx(s, x1, z0), sy(s, x1, y1, z0), sx(s, x0, z1), sy(s, x0, y1, z1), top);
    }

    /** Draws the tile sub-rectangle (u0,v0)-(u1,v1) (block units) onto a parallelogram. */
    private static void face(Canvas c, Bitmap tile, float u0, float v0, float u1, float v1,
                             float ax, float ay, float bx, float by, float cx, float cy, Paint p) {
        int px0 = Math.round(u0 * 16), py0 = Math.round(v0 * 16);
        int pw = Math.max(1, Math.round((u1 - u0) * 16)), ph = Math.max(1, Math.round((v1 - v0) * 16));
        Bitmap sub = (px0 == 0 && py0 == 0 && pw == 16 && ph == 16) ? tile : Bitmap.createBitmap(tile, px0, py0, pw, ph);
        float[] src = {0, 0, pw, 0, 0, ph};
        float[] dst = {ax, ay, bx, by, cx, cy};
        m.setPolyToPoly(src, 0, dst, 0, 3);
        c.drawBitmap(sub, m, p);
    }

    /** A 16x16 tile bitmap. Glass gets a faint tint so it reads as an icon. */
    static synchronized Bitmap tile(int t, boolean tintGlass) {
        if (tiles[t] != null) return tiles[t];
        int[] atlas = TextureAtlas.pixels();
        int[] px = new int[256];
        int ox = (t % TextureAtlas.PER_ROW) * 16, oy = (t / TextureAtlas.PER_ROW) * 16;
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int c = atlas[(oy + y) * TextureAtlas.SIZE + ox + x];
                if (tintGlass && t == Blocks.T_GLASS && (c >>> 24) == 0) c = 0x40CFE8F2;
                px[y * 16 + x] = c;
            }
        Bitmap b = Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888);
        tiles[t] = b;
        return b;
    }

    private BlockIcons() {
    }
}
