package com.blockbuilder;

import java.util.Arrays;
import java.util.Random;

/**
 * A themed village planned from the world seed: a list of block placements around a
 * central square. Chunks apply the placements that fall inside them.
 */
final class Village {
    static final int MEDIEVAL = 0, FANTASY = 1, DARWIN = 2;
    static final String[] THEME_NAMES = {"Medieval Village", "Fantasy Village", "Darwin Village"};
    static final int REGION = 384;
    /** Placement modes. */
    static final int SET = 0, GROUND = 1, FILL_DOWN = 2;

    final int theme, x, y, z, radius;
    int minX, maxX, minZ, maxZ;
    private int n;
    private int[] ops = new int[6 * 2048];

    private Village(int theme, int x, int y, int z, int radius) {
        this.theme = theme;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        minX = maxX = x;
        minZ = maxZ = z;
    }

    String name() {
        return THEME_NAMES[theme];
    }

    private void op(int px, int py, int pz, int b, int meta, int mode) {
        if (py < 1 || py >= World.SY) return;
        if (n * 6 + 6 > ops.length) ops = Arrays.copyOf(ops, ops.length * 2);
        int o = n * 6;
        ops[o] = px; ops[o + 1] = py; ops[o + 2] = pz; ops[o + 3] = b; ops[o + 4] = meta; ops[o + 5] = mode;
        n++;
        if (px < minX) minX = px;
        if (px > maxX) maxX = px;
        if (pz < minZ) minZ = pz;
        if (pz > maxZ) maxZ = pz;
    }

    boolean overlaps(int x0, int z0, int x1, int z1) {
        return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }

    /** Is this column part of the village (so trees should not grow there)? */
    boolean covers(int px, int pz, int margin) {
        int dx = px - x, dz = pz - z;
        return dx * dx + dz * dz <= (radius + margin) * (radius + margin);
    }

    /** Writes this village's blocks that fall inside a chunk. */
    void apply(Chunk c) {
        int x0 = c.cx * 16, z0 = c.cz * 16;
        if (!overlaps(x0, z0, x0 + 15, z0 + 15)) return;
        for (int i = 0; i < n; i++) {
            int o = i * 6;
            int px = ops[o] - x0, pz = ops[o + 2] - z0;
            if (px < 0 || pz < 0 || px > 15 || pz > 15) continue;
            int py = ops[o + 1], b = ops[o + 3], meta = ops[o + 4], mode = ops[o + 5];
            int idx = Chunk.idx(px, py, pz);
            if (mode == GROUND) {
                int cur = c.blocks[idx];
                if (!natural(cur)) continue;
                c.blocks[idx] = (char) b;
                c.meta[idx] = 0;
                // nothing growing on a path
                if (py + 1 < World.SY && Blocks.shape(c.blocks[idx + 1]) == Shapes.CROSS) c.blocks[idx + 1] = 0;
            } else if (mode == FILL_DOWN) {
                for (int yy = py; yy > 0 && py - yy < 14; yy--) {
                    int j = Chunk.idx(px, yy, pz);
                    int cur = c.blocks[j];
                    if (cur != Blocks.AIR && !Blocks.isWater(cur) && Blocks.isSolid(cur) && yy != py) break;
                    c.blocks[j] = (char) b;
                    c.meta[j] = 0;
                }
            } else {
                c.blocks[idx] = (char) b;
                c.meta[idx] = (byte) meta;
            }
        }
    }

    private static boolean natural(int b) {
        return b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.SAND || b == Blocks.FOREST_FLOOR
                || b == Blocks.COARSE_DIRT || b == Blocks.GRAVEL || b == Blocks.MUD || b == Blocks.SNOW
                || b == Blocks.RED_SAND || b == Blocks.CLAY || b == Blocks.STONE;
    }

    // ------------------------------------------------------------------ planning

    private static final Object CACHE_LOCK = new Object();
    private static final java.util.LinkedHashMap<Long, Village[]> CACHE = new java.util.LinkedHashMap<Long, Village[]>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Village[]> e) {
            return size() > 48;
        }
    };

    /** The village of a region, or null. Planned once and cached. */
    static Village forRegion(Generator g, int rx, int rz) {
        long key = (((long) rx) << 32) ^ (rz & 0xFFFFFFFFL) ^ (g.seed * 31);
        synchronized (CACHE_LOCK) {
            Village[] v = CACHE.get(key);
            if (v != null) return v[0];
        }
        Village v = plan(g, rx, rz);
        synchronized (CACHE_LOCK) {
            CACHE.put(key, new Village[]{v});
        }
        return v;
    }

    static void clearCache() {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
        }
    }

    private static Village plan(Generator g, int rx, int rz) {
        if (g.flat) return null;
        Random r = new Random(g.seed * 7919L + rx * 341873128712L + rz * 132897987541L);
        if (r.nextFloat() > 0.6f) return null;
        int cx = rx * REGION + 64 + r.nextInt(REGION - 128);
        int cz = rz * REGION + 64 + r.nextInt(REGION - 128);
        Generator.Column col = g.column(cx, cz, new Generator.Column());
        if (col.channel || col.bank || col.height <= World.SEA + 1 || col.height > 52) return null;
        Biome biome = Biome.ALL.get(col.biomeA);
        if (biome.swamp) return null;
        int theme;
        float t = r.nextFloat();
        if (biome.climate == Biome.TROPICAL || biome.climate == Biome.WARM) theme = t < 0.75f ? DARWIN : FANTASY;
        else if (biome.name.startsWith("Blossom") || biome.name.contains("Cherry")) theme = FANTASY;
        else theme = t < 0.65f ? MEDIEVAL : FANTASY;

        Village v = new Village(theme, cx, col.height, cz, 26);
        v.build(g, r);
        return v;
    }

    // ------------------------------------------------------------------ building helpers

    /** Local frame for one structure: origin, rotation (0-3, clockwise) and floor height. */
    private int ox, oy, oz, rot;

    private void frame(int x, int y, int z, int rot) {
        ox = x; oy = y; oz = z; this.rot = rot;
    }

    private int wx(int lx, int lz) {
        switch (rot) {
            case 1: return ox - lz;
            case 2: return ox - lx;
            case 3: return ox + lz;
            default: return ox + lx;
        }
    }

    private int wz(int lx, int lz) {
        switch (rot) {
            case 1: return oz + lx;
            case 2: return oz - lz;
            case 3: return oz - lx;
            default: return oz + lz;
        }
    }

    /** Rotates the facing bits of stairs / doors / trapdoors along with the structure. */
    private int rmeta(int b, int meta) {
        int s = Blocks.shape(b);
        if (s == Shapes.STAIRS || s == Shapes.DOOR || s == Shapes.TRAPDOOR) return (meta & ~3) | (((meta & 3) + rot) & 3);
        return meta;
    }

    private void put(int lx, int ly, int lz, int b) {
        put(lx, ly, lz, b, 0);
    }

    private void put(int lx, int ly, int lz, int b, int meta) {
        op(wx(lx, lz), oy + ly, wz(lx, lz), b, rmeta(b, meta), SET);
    }

    private void foundation(int lx, int lz, int b) {
        op(wx(lx, lz), oy, wz(lx, lz), b, 0, FILL_DOWN);
    }

    private static int stone(String name, int piece) {
        for (int i = 0; i < Stone.ALL.length; i++) {
            if (Stone.ALL[i].name.equals(name)) {
                return piece == 0 ? Blocks.STONE_FULL[i] : piece == 1 ? Blocks.STONE_STAIRS[i] : Blocks.STONE_SLAB[i];
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static int wood(String species, int piece) {
        for (int i = 0; i < Wood.ALL.length; i++) if (Wood.ALL[i].name.equals(species)) return Blocks.wood(i, piece);
        throw new IllegalArgumentException(species);
    }

    /** Rotation that makes a structure's front (local +z) face toward (tx, tz). */
    private static int faceToward(int fx, int fz, int tx, int tz) {
        int dx = tx - fx, dz = tz - fz;
        if (Math.abs(dz) >= Math.abs(dx)) return dz > 0 ? 0 : 2;
        return dx > 0 ? 3 : 1;
    }

    // ------------------------------------------------------------------ layout

    private void build(Generator g, Random r) {
        Generator.Column col = new Generator.Column();
        int pathA, pathB;
        if (theme == MEDIEVAL) { pathA = Blocks.GRAVEL; pathB = Blocks.COARSE_DIRT; }
        else if (theme == FANTASY) { pathA = Blocks.MOSSY_COBBLE; pathB = Blocks.MOSS_BLOCK; }
        else { pathA = Blocks.RED_SAND; pathB = Blocks.COARSE_DIRT; }

        // Central square
        frame(x, y, z, 0);
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz > 40) continue;
                foundation(dx, dz, Blocks.DIRT);
                put(dx, 0, dz, r.nextFloat() < 0.6f ? pathA : pathB);
                for (int k = 1; k <= 10; k++) put(dx, k, dz, Blocks.AIR);
            }
        centrepiece(r);

        // Houses in a ring, doors facing the square
        int count = 5 + r.nextInt(4);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = a0 + i * Math.PI * 2 / count + (r.nextDouble() - 0.5) * 0.3;
            int dist = 15 + r.nextInt(5) + (i % 2) * 2;
            int hx = x + (int) Math.round(Math.cos(a) * dist), hz = z + (int) Math.round(Math.sin(a) * dist);
            g.column(hx, hz, col);
            if (col.channel || col.height < World.SEA) continue;
            int hy = Math.max(col.height, World.SEA + 1);
            int rotation = faceToward(hx, hz, x, z);
            int[] door = house(hx, hy, hz, rotation, r, i == 0);
            path(g, door[0], door[1], x, z, pathA, pathB, r);
        }
    }

    /** An L-shaped path between a door and the square. */
    private void path(Generator g, int fx, int fz, int tx, int tz, int a, int b, Random r) {
        Generator.Column col = new Generator.Column();
        int px = fx, pz = fz;
        int guard = 0;
        while ((px != tx || pz != tz) && guard++ < 80) {
            int dx = tx - px, dz = tz - pz;
            if ((dx - dz) * (dx - dz) <= 36 && dx * dx + dz * dz < 30) break; // reached the square
            for (int w = 0; w < 2; w++) {
                int qx = px + (Math.abs(dx) > Math.abs(dz) ? 0 : w), qz = pz + (Math.abs(dx) > Math.abs(dz) ? w : 0);
                g.column(qx, qz, col);
                op(qx, col.height, qz, r.nextFloat() < 0.65f ? a : b, 0, GROUND);
            }
            if (Math.abs(dx) > Math.abs(dz)) px += Integer.signum(dx); else pz += Integer.signum(dz);
        }
    }

    private void centrepiece(Random r) {
        if (theme == MEDIEVAL) {
            // Well with a little roof
            for (int dx = -2; dx <= 1; dx++)
                for (int dz = -2; dz <= 1; dz++) {
                    boolean rim = dx == -2 || dx == 1 || dz == -2 || dz == 1;
                    put(dx, 0, dz, rim ? Blocks.COBBLE : Blocks.WATER);
                    put(dx, -1, dz, rim ? Blocks.COBBLE : Blocks.WATER);
                    if (rim) put(dx, 1, dz, Blocks.COBBLE);
                }
            int post = wood("White Oak", Blocks.W_LOG);
            for (int[] p : new int[][]{{-2, -2}, {1, -2}, {-2, 1}, {1, 1}}) {
                put(p[0], 2, p[1], post);
                put(p[0], 3, p[1], post);
            }
            int slab = wood("Black Walnut", Blocks.W_SLAB);
            for (int dx = -2; dx <= 1; dx++) for (int dz = -2; dz <= 1; dz++) put(dx, 4, dz, slab);
        } else if (theme == FANTASY) {
            // Crystal fountain
            int rim = stone("Quartzite", 0);
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    int d = dx * dx + dz * dz;
                    if (d > 10) continue;
                    put(dx, 0, dz, d >= 6 ? rim : Blocks.WATER);
                    if (d >= 6) put(dx, 1, dz, stone("Quartzite", 2));
                    else put(dx, -1, dz, rim);
                }
            for (int k = 0; k < 4; k++) put(0, k, 0, Blocks.CRYSTAL_BLOCK);
            put(0, 4, 0, Blocks.LAMP);
            for (int[] p : new int[][]{{-4, 0}, {4, 0}, {0, -4}, {0, 4}}) put(p[0], 1, p[1], Blocks.LAMP);
        } else {
            // Shady market: posts, a metal roof and some crates
            int post = wood("Merbau", Blocks.W_LOG);
            for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) for (int k = 1; k <= 3; k++) put(p[0], k, p[1], post);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) put(dx, 4, dz, Blocks.METAL);
            put(-1, 1, -1, Blocks.CRATE); put(0, 1, -1, Blocks.HAY); put(1, 1, 1, Blocks.CRATE); put(1, 2, 1, Blocks.CRATE);
        }
    }

    /** Builds one house; returns the world x,z just outside its door. */
    private int[] house(int cx, int cy, int cz, int rotation, Random r, boolean special) {
        if (theme == MEDIEVAL) return special ? church(cx, cy, cz, rotation) : medieval(cx, cy, cz, rotation, r);
        if (theme == FANTASY) return fantasy(cx, cy, cz, rotation, r);
        return darwin(cx, cy, cz, rotation, r);
    }

    private int[] doorFront(int lx, int lz) {
        return new int[]{wx(lx, lz + 1), wz(lx, lz + 1)};
    }

    private void clearBox(int x0, int x1, int z0, int z1, int y0, int y1) {
        for (int lx = x0; lx <= x1; lx++) for (int lz = z0; lz <= z1; lz++) for (int ly = y0; ly <= y1; ly++) put(lx, ly, lz, Blocks.AIR);
    }

    private int[] medieval(int cx, int cy, int cz, int rotation, Random r) {
        int W = 7 + 2 * r.nextInt(2), D = 6 + r.nextInt(2);
        frame(cx, cy, cz, rotation);
        ox = wx(-W / 2, -D / 2); oz = wz(-W / 2, -D / 2);
        int beam = wood(r.nextBoolean() ? "Black Walnut" : "White Oak", Blocks.W_LOG);
        int plank = wood("White Oak", Blocks.W_PLANKS);
        int door = wood(r.nextBoolean() ? "White Oak" : "English Walnut", Blocks.W_DOOR);
        int roof = r.nextBoolean() ? stone("Slate", 1) : wood("Black Walnut", Blocks.W_STAIRS);
        int roofSlab = roof == stone("Slate", 1) ? stone("Slate", 2) : wood("Black Walnut", Blocks.W_SLAB);
        int wallH = 4;
        clearBox(-1, W, -1, D, 1, wallH + D / 2 + 3);
        for (int lx = 0; lx < W; lx++)
            for (int lz = 0; lz < D; lz++) {
                foundation(lx, lz, Blocks.COBBLE);
                boolean edge = lx == 0 || lz == 0 || lx == W - 1 || lz == D - 1;
                put(lx, 0, lz, edge ? Blocks.COBBLE : plank);
                if (!edge) continue;
                boolean corner = (lx == 0 || lx == W - 1) && (lz == 0 || lz == D - 1);
                for (int ly = 1; ly <= wallH; ly++) {
                    int b = (corner || ly == 3) ? beam : Blocks.WHITE;
                    if (ly == 2 && !corner && ((lx + lz) % 2 == 0)) b = Blocks.GLASS;
                    put(lx, ly, lz, b);
                }
            }
        int dl = W / 2;
        put(dl, 1, D - 1, door, 2);
        put(dl, 2, D - 1, door, 2 | Shapes.UPPER);
        put(dl, 3, D - 1, beam);
        put(1, wallH, 1, Blocks.LAMP);
        put(W - 2, 1, 1, Blocks.BOOKSHELF);
        put(1, 1, D - 2, Blocks.CRATE);
        // Gable roof along x: the south slope's stairs face north and vice versa.
        int base = wallH + 1;
        for (int k = 0; ; k++) {
            int zs = D - k, zn = -1 + k;
            if (zn > zs) break;
            for (int lx = -1; lx <= W; lx++) {
                if (zn == zs) put(lx, base + k, zn, roofSlab);
                else {
                    put(lx, base + k, zs, roof, 0);
                    put(lx, base + k, zn, roof, 2);
                }
            }
            // gable walls
            if (k > 0) for (int lz = zn; lz <= zs; lz++) {
                if (lz <= zn || lz >= zs) continue;
                put(0, base + k - 1, lz, plank);
                put(W - 1, base + k - 1, lz, plank);
            }
        }
        for (int ly = 1; ly <= base + D / 2 + 1; ly++) put(1, ly, 1, Blocks.COBBLE); // chimney
        return doorFront(dl, D - 1);
    }

    private int[] church(int cx, int cy, int cz, int rotation) {
        int W = 7, D = 9, H = 7;
        frame(cx, cy, cz, rotation);
        ox = wx(-W / 2, -D / 2); oz = wz(-W / 2, -D / 2);
        int wall = Blocks.STONE_BRICKS, roof = stone("Slate", 1);
        int door = wood("English Walnut", Blocks.W_DOOR);
        clearBox(-1, W, -1, D, 1, H + 14);
        for (int lx = 0; lx < W; lx++)
            for (int lz = 0; lz < D; lz++) {
                foundation(lx, lz, Blocks.STONE_BRICKS);
                put(lx, 0, lz, Blocks.POLISHED_STONE);
                boolean edge = lx == 0 || lz == 0 || lx == W - 1 || lz == D - 1;
                if (!edge) continue;
                for (int ly = 1; ly <= H; ly++) {
                    int b = (ly >= 3 && ly <= 5 && lz % 2 == 1 && (lx == 0 || lx == W - 1)) ? Blocks.GLASS : wall;
                    if (lz == 0 && ly <= H) b = wall;
                    put(lx, ly, lz, b);
                }
            }
        put(3, 1, D - 1, door, 2); put(3, 2, D - 1, door, 2 | Shapes.UPPER);
        put(3, H - 1, 1, Blocks.LAMP);
        // Pitched roof
        for (int k = 0; k <= 3; k++)
            for (int lz = -1; lz <= D; lz++) {
                if (k == 3) { put(3, H + 1 + k, lz, stone("Slate", 2)); continue; }
                put(k, H + 1 + k, lz, roof, 1);
                put(W - 1 - k, H + 1 + k, lz, roof, 3);
            }
        // Bell tower at the back
        for (int ly = H + 1; ly <= H + 9; ly++)
            for (int lx = 2; lx <= 4; lx++)
                for (int lz = 0; lz <= 2; lz++) {
                    boolean open = ly >= H + 6 && ly <= H + 7 && lx == 3;
                    put(lx, ly, lz, open ? Blocks.AIR : wall);
                }
        put(3, H + 6, 1, Blocks.GOLD_BLOCK);
        for (int k = 0; k <= 2; k++) put(3, H + 10 + k, 1, k == 2 ? Blocks.GOLD_BLOCK : stone("Slate", 0));
        return doorFront(3, D - 1);
    }

    private int[] fantasy(int cx, int cy, int cz, int rotation, Random r) {
        int R = 2 + r.nextInt(2);
        frame(cx, cy, cz, rotation);
        int[] walls = {Blocks.MOSSY_BRICKS, stone("Quartzite", 0), stone("Alabaster", 0), Blocks.STONE_BRICKS};
        int[] roofs = {Blocks.PURPLE, Blocks.BLUE, Blocks.MAGENTA, Blocks.CYAN, Blocks.RED};
        int wall = walls[r.nextInt(walls.length)], roofC = roofs[r.nextInt(roofs.length)];
        int door = wood("Wild Cherry", Blocks.W_DOOR);
        int H = 5 + r.nextInt(3);
        float rr = R + 0.5f;
        clearBox(-R - 2, R + 2, -R - 2, R + 2, 1, H + R + 8);
        for (int lx = -R - 1; lx <= R + 1; lx++)
            for (int lz = -R - 1; lz <= R + 1; lz++) {
                float d = (float) Math.sqrt(lx * lx + lz * lz);
                if (d > rr + 0.2f) continue;
                foundation(lx, lz, wall);
                put(lx, 0, lz, Blocks.POLISHED_STONE);
                boolean shell = d > rr - 1.1f;
                if (!shell) continue;
                for (int ly = 1; ly <= H; ly++) {
                    int b = wall;
                    if ((ly == 2 || ly == H - 1) && (lx == 0 || lz == 0) && lz != R + 1 && Math.abs(lx) + Math.abs(lz) > 1) b = Blocks.GLASS;
                    put(lx, ly, lz, b);
                }
            }
        // Steep cone roof
        for (int k = 0; ; k++) {
            float rad = rr + 1f - k * 0.7f;
            if (rad < 0.3f) {
                put(0, H + 1 + k, 0, Blocks.CRYSTAL_BLOCK);
                put(0, H + 2 + k, 0, Blocks.GOLD_BLOCK);
                break;
            }
            int ir = (int) Math.ceil(rad);
            for (int lx = -ir; lx <= ir; lx++)
                for (int lz = -ir; lz <= ir; lz++)
                    if (lx * lx + lz * lz <= rad * rad) put(lx, H + 1 + k, lz, roofC);
        }
        int dz = (int) Math.floor(rr);
        put(0, 1, dz, door, 2);
        put(0, 2, dz, door, 2 | Shapes.UPPER);
        put(0, 3, dz + 1, Blocks.LAMP);
        put(0, H, 0, Blocks.LAMP);
        // Flower beds by the door
        put(-2, 1, dz + 1, Blocks.RED_FLOWER + r.nextInt(4));
        put(2, 1, dz + 1, Blocks.RED_FLOWER + r.nextInt(4));
        return doorFront(0, dz);
    }

    private int[] darwin(int cx, int cy, int cz, int rotation, Random r) {
        int W = 7 + 2 * r.nextInt(2), D = 6, V = 2;   // V = veranda depth at the front
        frame(cx, cy, cz, rotation);
        ox = wx(-W / 2, -(D + V) / 2); oz = wz(-W / 2, -(D + V) / 2);
        int post = wood(r.nextBoolean() ? "Merbau" : "Iroko", Blocks.W_LOG);
        int floor = wood("Teak", Blocks.W_PLANKS);
        int[] wallChoices = {wood("Balsa", Blocks.W_PLANKS), Blocks.WHITE, Blocks.LIGHT_BLUE, Blocks.YELLOW,
                wood("Silver Birch", Blocks.W_PLANKS)};
        int wall = wallChoices[r.nextInt(wallChoices.length)];
        int door = wood("Teak", Blocks.W_DOOR);
        int shutter = wood("Teak", Blocks.W_TRAPDOOR);
        int stairs = wood("Teak", Blocks.W_STAIRS);
        int S = 3; // stilt height: the floor is raised off the ground
        clearBox(-1, W, -1, D + V + 2, 1, S + 9);
        // Stilts
        for (int lx = 0; lx < W; lx += 3)
            for (int lz = 0; lz < D + V; lz += 3) {
                foundation(lx, lz, post);
                for (int ly = 1; ly < S; ly++) put(lx, ly, lz, post);
            }
        for (int lz = 0; lz < D + V; lz += 3) { foundation(W - 1, lz, post); for (int ly = 1; ly < S; ly++) put(W - 1, ly, lz, post); }
        for (int lx = 0; lx < W; lx += 3) { foundation(lx, D + V - 1, post); for (int ly = 1; ly < S; ly++) put(lx, ly, D + V - 1, post); }
        foundation(W - 1, D + V - 1, post);
        for (int ly = 1; ly < S; ly++) put(W - 1, ly, D + V - 1, post);
        // Raised floor
        for (int lx = 0; lx < W; lx++) for (int lz = 0; lz < D + V; lz++) put(lx, S, lz, floor);
        // Walls with louvred windows
        for (int lx = 0; lx < W; lx++)
            for (int lz = 0; lz < D; lz++) {
                boolean edge = lx == 0 || lz == 0 || lx == W - 1 || lz == D - 1;
                if (!edge) continue;
                for (int ly = S + 1; ly <= S + 3; ly++) {
                    boolean window = ly == S + 2 && (lx % 3 == 1 || lz % 3 == 1) && !(lx == W / 2 && lz == D - 1);
                    put(lx, ly, lz, window ? Blocks.GLASS : wall);
                }
            }
        // Veranda railing (open trapdoors stand up like rails) and corner posts
        for (int lx = 0; lx < W; lx++) {
            if (lx == W / 2) continue;
            put(lx, S + 1, D + V - 1, shutter, Shapes.OPEN | 2);
        }
        for (int lz = D; lz < D + V; lz++) {
            put(0, S + 1, lz, shutter, Shapes.OPEN | 3);
            put(W - 1, S + 1, lz, shutter, Shapes.OPEN | 1);
        }
        put(0, S + 2, D + V - 1, post); put(0, S + 3, D + V - 1, post);
        put(W - 1, S + 2, D + V - 1, post); put(W - 1, S + 3, D + V - 1, post);
        // Door, steps down from the veranda
        put(W / 2, S + 1, D - 1, door, 2);
        put(W / 2, S + 2, D - 1, door, 2 | Shapes.UPPER);
        for (int k = 0; k < S; k++) {
            int lz = D + V + k;
            put(W / 2, S - k, lz, stairs, 0);
            foundation(W / 2, lz, Blocks.DIRT);
        }
        put(W / 2, S + 3, D - 2, Blocks.LAMP);
        // Low-pitched metal roof with overhang (a hip: a second, smaller layer on top)
        for (int lx = -1; lx <= W; lx++) for (int lz = -1; lz <= D + V; lz++) put(lx, S + 4, lz, Blocks.METAL);
        for (int lx = 1; lx <= W - 2; lx++) for (int lz = 1; lz <= D - 2; lz++) put(lx, S + 5, lz, Blocks.METAL);
        // A palm beside the house
        Biome.Tree palm = new Biome.Tree(Blocks.LIGHT_LOG, Blocks.LEAVES, 5, Biome.PALM, 1);
        final int baseY = oy;
        final int px = wx(-3, D / 2), pz = wz(-3, D / 2);
        Trees.grow(new Trees.Sink() {
            @Override
            public int get(int x, int y, int z) {
                return Blocks.AIR;
            }

            @Override
            public void set(int x, int y, int z, int b, int meta) {
                op(x, y, z, b, meta, SET);
            }
        }, palm, px, baseY + 1, pz, r);
        return new int[]{wx(W / 2, D + V + S), wz(W / 2, D + V + S)};
    }
}
