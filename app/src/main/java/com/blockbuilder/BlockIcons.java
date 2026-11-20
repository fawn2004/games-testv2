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
    private static final Bitmap[] tiles = new Bitmap[256];

    static synchronized Bitmap[] get(Context ctx) {
        if (icons != null) return icons;
        float dp = ctx.getResources().getDisplayMetrics().density;
        int s = (int) (44 * dp);
        icons = new Bitmap[Blocks.COUNT];
        Matrix m = new Matrix();
        Paint p = new Paint();
        p.setFilterBitmap(false);
        Paint left = new Paint(p), right = new Paint(p);
        left.setColorFilter(new LightingColorFilter(0xFFC8C8C8, 0));
        right.setColorFilter(new LightingColorFilter(0xFF9A9A9A, 0));
        for (int b = 1; b < Blocks.COUNT; b++) {
            Bitmap icon = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(icon);
            int[] t = Blocks.TILES[b];
            Bitmap top = tile(t[0], true), side = tile(t[1], true);
            poly(m, s, 0.06f, 0.29f, 0.5f, 0.52f, 0.06f, 0.74f);
            c.drawBitmap(side, m, left);
            poly(m, s, 0.5f, 0.52f, 0.94f, 0.29f, 0.5f, 0.97f);
            c.drawBitmap(side, m, right);
            poly(m, s, 0.06f, 0.29f, 0.5f, 0.06f, 0.5f, 0.52f);
            c.drawBitmap(top, m, p);
            icons[b] = icon;
        }
        return icons;
    }

    /** Maps tile corners (0,0),(16,0),(0,16) to three points of a parallelogram. */
    private static void poly(Matrix m, int s, float ax, float ay, float bx, float by, float cx, float cy) {
        float[] src = {0, 0, 16, 0, 0, 16};
        float[] dst = {ax * s, ay * s, bx * s, by * s, cx * s, cy * s};
        m.setPolyToPoly(src, 0, dst, 0, 3);
    }

    /** A 16x16 tile bitmap. Glass gets a faint tint so it reads as an icon. */
    static synchronized Bitmap tile(int t, boolean tintGlass) {
        if (tiles[t] != null) return tiles[t];
        int[] atlas = TextureAtlas.pixels();
        int[] px = new int[256];
        int ox = (t & 15) * 16, oy = (t >> 4) * 16;
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
