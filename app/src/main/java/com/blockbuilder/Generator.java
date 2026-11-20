package com.blockbuilder;

import java.util.Random;

/**
 * Deterministic terrain generation for one chunk at a time. Every function here is a pure
 * function of the seed and coordinates, so chunks can be generated in any order, on any
 * thread, and always line up with their neighbours.
 */
final class Generator {
    final long seed;
    final boolean flat;
    private final Noise hills, mountains, river, warp, temp, floor, fields, flowers, swampNoise;
    /** Size of a biome cell (blocks). */
    static final int CELL = 176;

    Generator(long seed, boolean flat) {
        this.seed = seed;
        this.flat = flat;
        hills = new Noise(seed);
        mountains = new Noise(seed * 31 + 7);
        river = new Noise(seed * 101 + 13);
        warp = new Noise(seed * 59 + 3);
        temp = new Noise(seed * 977 + 41);
        floor = new Noise(seed * 17 + 3);
        fields = new Noise(seed * 7 + 5);
        flowers = new Noise(seed * 13 + 11);
        swampNoise = new Noise(seed * 23 + 19);
    }

    /** Everything about one column of terrain. */
    static final class Column {
        int height;
        boolean channel, bank;
        /** Main and neighbouring biome, and how strongly the main one applies (0.5 .. 1). */
        int biomeA, biomeB;
        float weightA;
        float tr, tg, tb;
    }

    // ------------------------------------------------------------------ biomes

    private int biomeOfCell(long gx, long gz) {
        double cx = (gx + 0.5) * CELL, cz = (gz + 0.5) * CELL;
        float t = temp.fbm(cx / 1400.0, cz / 1400.0, 2);
        int climate = t < 0.43f ? Biome.COLD : t < 0.53f ? Biome.TEMPERATE : t < 0.6f ? Biome.WARM : Biome.TROPICAL;
        java.util.List<Integer> list = Biome.BY_CLIMATE.get(climate);
        float h = warp.hash(gx * 3 + 1, gz * 5 + 2);
        return list.get(Math.min(list.size() - 1, (int) (h * list.size())));
    }

    private void biomes(int x, int z, Column out) {
        double wx = x + (warp.fbm(x / 70.0, z / 70.0, 2) - 0.5) * 90;
        double wz = z + (warp.fbm(x / 70.0 + 40, z / 70.0 - 40, 2) - 0.5) * 90;
        long gx = (long) Math.floor(wx / CELL), gz = (long) Math.floor(wz / CELL);
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        long c1x = 0, c1z = 0, c2x = 0, c2z = 0;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                long cx = gx + i, cz = gz + j;
                double px = (cx + 0.15 + 0.7 * warp.hash(cx, cz)) * CELL;
                double pz = (cz + 0.15 + 0.7 * warp.hash(cz + 7, cx - 3)) * CELL;
                double d = Math.sqrt((px - wx) * (px - wx) + (pz - wz) * (pz - wz));
                if (d < d1) { d2 = d1; c2x = c1x; c2z = c1z; d1 = d; c1x = cx; c1z = cz; }
                else if (d < d2) { d2 = d; c2x = cx; c2z = cz; }
            }
        out.biomeA = biomeOfCell(c1x, c1z);
        out.biomeB = biomeOfCell(c2x, c2z);
        float t = (float) Math.min(1.0, (d2 - d1) / 48.0);
        out.weightA = out.biomeA == out.biomeB ? 1f : 0.5f + 0.5f * t;
    }

    /** Fills in height, rivers, biomes and grass colour for a column. */
    Column column(int x, int z, Column out) {
        if (flat) {
            out.height = World.FLAT_TOP;
            out.channel = out.bank = false;
            out.biomeA = out.biomeB = Biome.ALL.size() - 3; // Meadow
            out.weightA = 1;
            out.tr = out.tg = out.tb = 1;
            return out;
        }
        biomes(x, z, out);
        Biome a = Biome.ALL.get(out.biomeA), b = Biome.ALL.get(out.biomeB);
        float wa = out.weightA, wb = 1 - wa;
        float base = a.base * wa + b.base * wb;
        float hill = a.hills * wa + b.hills * wb;
        float mnt = a.mountains * wa + b.mountains * wb;
        out.tr = a.tr * wa + b.tr * wb;
        out.tg = a.tg * wa + b.tg * wb;
        out.tb = a.tb * wa + b.tb * wb;

        float h1 = hills.fbm(x / 48.0, z / 48.0, 4);
        float m = mountains.fbm(x / 120.0, z / 120.0, 3);
        float mount = Math.max(0f, m - 0.46f) * 2.4f;
        mount *= mount;
        float h = base + h1 * hill + mount * mnt;
        // Swamps sit right at the water line, with puddles.
        float swampW = (a.swamp ? wa : 0) + (b.swamp ? wb : 0);
        if (swampW > 0) {
            float s = World.SEA - 0.5f + (swampNoise.fbm(x / 9.0, z / 9.0, 2) - 0.5f) * 5f;
            h = h * (1 - swampW) + s * swampW;
        }
        int hi = (int) h;
        if (hi < 3) hi = 3;
        if (hi > World.SY - 14) hi = World.SY - 14;

        out.channel = out.bank = false;
        float rd = Math.abs(river.fbm(x / 110.0, z / 110.0, 3) - 0.5f);
        if (rd < 0.05f && hi >= World.SEA - 1) {
            float t = rd / 0.05f;
            if (t < 0.45f) {
                hi = Math.min(hi, World.SEA - 2 - (int) ((0.45f - t) / 0.45f * 2.5f));
                out.channel = true;
            } else {
                hi = Math.min(hi, World.SEA + (int) ((t - 0.45f) / 0.55f * 4f));
                out.bank = true;
            }
        }
        out.height = hi;
        return out;
    }

    String biomeName(int x, int z) {
        Column c = column(x, z, new Column());
        return Biome.ALL.get(c.biomeA).name;
    }

    /** Village whose square is within reach of a column, if any. */
    Village villageNear(int x, int z, int margin) {
        int rx = Generator.floorDiv(x, Village.REGION), rz = Generator.floorDiv(z, Village.REGION);
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                Village v = Village.forRegion(this, rx + i, rz + j);
                if (v != null && v.covers(x, z, margin)) return v;
            }
        return null;
    }

    /** A dry spot near the origin to start on. */
    int[] findSpawn() {
        Column c = new Column();
        for (int r = 0; r < 4000; r += 3) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                int x = (int) (Math.cos(a) * r), z = (int) (Math.sin(a) * r);
                column(x, z, c);
                if (!c.channel && !c.bank && c.height > World.SEA + 1 && c.height < 50) return new int[]{x, c.height + 1, z};
            }
        }
        return new int[]{0, World.SY - 10, 0};
    }

    // ------------------------------------------------------------------ chunks

    Chunk generate(int cx, int cz) {
        Chunk ch = new Chunk(cx, cz);
        int x0 = cx * 16, z0 = cz * 16;
        Random rnd = new Random(seed ^ (cx * 341873128712L) ^ (cz * 132897987541L));
        Column col = new Column();
        int[] heights = new int[256];
        boolean[] channelCol = new boolean[256];

        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                column(x, z, col);
                int h = col.height;
                heights[lx * 16 + lz] = h;
                channelCol[lx * 16 + lz] = col.channel;
                Biome biome = Biome.ALL.get(col.biomeA);
                ch.biome[lx * 16 + lz] = (short) col.biomeA;
                ch.tint[lx * 16 + lz] = pack(col.tr, col.tg, col.tb);
                if (flat) {
                    for (int y = 0; y <= h; y++) {
                        int b = y == 0 ? Blocks.BEDROCK : y < h - 3 ? Blocks.STONE : y < h ? Blocks.DIRT : Blocks.GRASS;
                        ch.blocks[Chunk.idx(lx, y, lz)] = (char) b;
                    }
                    continue;
                }
                boolean beach = h <= World.SEA + 1 && !col.bank && !biome.swamp;
                boolean rocky = h >= 54 || (biome.snowy && h >= 48);
                boolean snowTop = h >= 58 || (biome.snowy && h >= 34) || (biome.climate == Biome.COLD && h >= 46);
                float f = floor.value(x / 9.0, z / 9.0);
                int bed = Blocks.SAND;
                if (col.channel) bed = f < 0.4f ? Blocks.COBBLE : f < 0.7f ? Blocks.GRAVEL : Blocks.MOSSY_COBBLE;
                else if (h < World.SEA - 1) bed = f < 0.33f ? Blocks.GRAVEL : f > 0.7f ? Blocks.CLAY : Blocks.SAND;
                int bankTop = f < 0.35f ? Blocks.FOREST_FLOOR : f < 0.55f ? Blocks.COARSE_DIRT : Blocks.GRASS;
                int top = biome.ground;
                if (top == Blocks.FOREST_FLOOR && f > 0.55f) top = Blocks.GRASS;
                if (biome.swamp && f < 0.3f) top = Blocks.MUD;
                for (int y = 0; y <= h; y++) {
                    int b;
                    if (y == 0) b = Blocks.BEDROCK;
                    else if (y < h - 3) b = ore(y, rnd);
                    else if (col.channel) b = y >= h - 1 ? bed : Blocks.STONE;
                    else if (col.bank && y == h) b = bankTop;
                    else if (col.bank) b = Blocks.DIRT;
                    else if (beach) b = bed;
                    else if (rocky) b = (y == h && snowTop) ? Blocks.SNOW : Blocks.STONE;
                    else b = (y == h) ? (snowTop ? Blocks.SNOW : top) : Blocks.DIRT;
                    ch.blocks[Chunk.idx(lx, y, lz)] = (char) b;
                }
                for (int y = h + 1; y <= World.SEA; y++) ch.blocks[Chunk.idx(lx, y, lz)] = (char) Blocks.WATER;
                if (h < World.SEA && biome.climate == Biome.COLD && !col.channel)
                    ch.blocks[Chunk.idx(lx, World.SEA, lz)] = (char) Blocks.ICE;   // frozen lakes
            }
        if (flat) {
            ch.recomputeMaxY();
            return ch;
        }

        // Villages (planned per region; apply the parts inside this chunk)
        int rx = Generator.floorDiv(x0, Village.REGION), rz = Generator.floorDiv(z0, Village.REGION);
        Village[] near = new Village[9];
        int nv = 0;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                Village v = Village.forRegion(this, rx + i, rz + j);
                if (v != null && v.overlaps(x0 - 8, z0 - 8, x0 + 23, z0 + 23)) near[nv++] = v;
            }
        for (int i = 0; i < nv; i++) near[i].apply(ch);

        // Trees: replay the trees of this chunk and its neighbours, clipped to this chunk.
        Trees.Sink sink = new Trees.Sink() {
            @Override
            public int get(int x, int y, int z) {
                int lx = x - x0, lz = z - z0;
                if (lx < 0 || lz < 0 || lx > 15 || lz > 15 || y < 0 || y >= World.SY) return Blocks.AIR;
                return ch.blocks[Chunk.idx(lx, y, lz)];
            }

            @Override
            public void set(int x, int y, int z, int b, int meta) {
                int lx = x - x0, lz = z - z0;
                if (lx < 0 || lz < 0 || lx > 15 || lz > 15 || y < 0 || y >= World.SY) return;
                int i = Chunk.idx(lx, y, lz);
                ch.blocks[i] = (char) b;
                ch.meta[i] = (byte) meta;
            }
        };
        for (int ncx = cx - 1; ncx <= cx + 1; ncx++)
            for (int ncz = cz - 1; ncz <= cz + 1; ncz++)
                treesOf(ncx, ncz, sink, near, nv);

        decorate(ch, heights, channelCol, rnd);
        ch.recomputeMaxY();
        return ch;
    }

    /** Floor division (Math.floorDiv needs Android 7). */
    static int floorDiv(int a, int b) {
        int q = a / b;
        if ((a % b != 0) && ((a ^ b) < 0)) q--;
        return q;
    }

    /** Biome and grass colour for every column of a chunk loaded from disk. */
    void fillColumns(Chunk ch) {
        Column col = new Column();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                column(ch.cx * 16 + lx, ch.cz * 16 + lz, col);
                ch.biome[lx * 16 + lz] = (short) col.biomeA;
                ch.tint[lx * 16 + lz] = pack(col.tr, col.tg, col.tb);
            }
    }

    private static int pack(float r, float g, float b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(float v) {
        int i = Math.round(v * 128f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Grows the trees whose trunks stand in chunk (cx, cz). */
    private void treesOf(int cx, int cz, Trees.Sink sink, Village[] near, int nv) {
        Random r = new Random(seed * 0x5DEECE66DL ^ (cx * 73428767L) ^ (cz * 912931L) ^ 0x1234567L);
        Column col = new Column();
        int x0 = cx * 16, z0 = cz * 16;
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                float p = r.nextFloat(), pick = r.nextFloat();
                long treeSeed = r.nextLong();
                if (p >= 0.05f) continue;
                int x = x0 + lx, z = z0 + lz;
                column(x, z, col);
                Biome biome = Biome.ALL.get(col.weightA > 0.75f || r.nextBoolean() ? col.biomeA : col.biomeB);
                if (p >= biome.treeChance + biome.shrubChance) continue;
                if (col.channel || col.bank) continue;
                boolean inWater = col.height < World.SEA;
                if (inWater && !biome.swamp) continue;
                if (col.height >= 56) continue;
                if (villageNear(x, z, 4) != null) continue;   // same answer from every chunk
                Biome.Tree t;
                if (p < biome.treeChance) t = biome.pick(pick);
                else t = new Biome.Tree(0, biome.trees.isEmpty() ? Blocks.LEAVES : biome.trees.get(0).leaves,
                        biome.trees.isEmpty() ? 0 : biome.trees.get(0).leafTint, Biome.SHRUB, 1);
                if (t == null) continue;
                if (inWater && t.shape != Biome.CYPRESS && t.shape != Biome.SHRUB) continue;
                Trees.grow(sink, t, x, col.height + 1, z, new Random(treeSeed));
            }
    }

    private void decorate(Chunk ch, int[] heights, boolean[] channel, Random rnd) {
        int x0 = ch.cx * 16, z0 = ch.cz * 16;
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int h = heights[lx * 16 + lz];
                if (h + 2 >= World.SY) continue;
                int x = x0 + lx, z = z0 + lz;
                Biome biome = Biome.ALL.get(ch.biome[lx * 16 + lz]);
                int top = ch.get(lx, h, lz);
                int above = ch.get(lx, h + 1, lz);
                float r = rnd.nextFloat(), q = rnd.nextFloat();
                if (above == Blocks.WATER) {
                    if (h + 2 <= World.SEA && r < 0.3f) ch.blocks[Chunk.idx(lx, h + 1, lz)] = (char) Blocks.SEAGRASS;
                    continue;
                }
                if (above != Blocks.AIR) continue;
                boolean shaded = false;
                for (int y = h + 2; y < Math.min(World.SY, h + 12); y++) {
                    if (Blocks.isLeaves(ch.get(lx, y, lz))) { shaded = true; break; }
                }
                int plant = 0;
                if ((top == Blocks.GRASS || top == Blocks.FOREST_FLOOR || top == Blocks.MUD) && shaded) {
                    if (top == Blocks.GRASS && r < 0.35f) ch.blocks[Chunk.idx(lx, h, lz)] = (char) Blocks.FOREST_FLOOR;
                    plant = q < 0.2f ? Blocks.LEAF_LITTER : q < 0.28f ? Blocks.MOSS_CARPET
                            : q < 0.28f + biome.ferns * 2 ? Blocks.FERN : q < 0.55f ? Blocks.GRASS_TUFT : 0;
                } else if (top == Blocks.GRASS) {
                    if (biome.climate == Biome.TEMPERATE && fields.value(x / 14.0, z / 14.0) > 0.78f) {
                        if (r < 0.85f) plant = Blocks.WHEAT;
                    } else if (biome.flowers > 0.03f || flowers.value(x / 9.0, z / 9.0) > 0.7f) {
                        if (r < biome.flowers * 6) {
                            float k = flowers.value(x / 23.0 + 50, z / 23.0);
                            plant = k < 0.3f ? Blocks.RED_FLOWER : k < 0.5f ? Blocks.YELLOW_FLOWER
                                    : k < 0.7f ? Blocks.WHITE_FLOWER : Blocks.BLUE_FLOWER;
                        } else if (r < biome.flowers * 6 + biome.grass) plant = Blocks.GRASS_TUFT;
                    } else if (r < biome.grass) plant = Blocks.GRASS_TUFT;
                    else if (r < biome.grass + biome.ferns) plant = Blocks.FERN;
                    else if (r < biome.grass + biome.ferns + biome.flowers) plant = Blocks.RED_FLOWER + rnd.nextInt(4);
                } else if (top == Blocks.FOREST_FLOOR || top == Blocks.COARSE_DIRT || top == Blocks.MUD) {
                    plant = r < 0.12f ? Blocks.GRASS_TUFT : r < 0.12f + biome.ferns ? Blocks.FERN
                            : r < 0.3f ? Blocks.LEAF_LITTER : r < 0.36f ? Blocks.MOSS_CARPET : 0;
                }
                if (plant != 0) ch.blocks[Chunk.idx(lx, h + 1, lz)] = (char) plant;
            }
        // Vines under leaves
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++)
                for (int y = 3; y < World.SY; y++) {
                    int b = ch.get(lx, y, lz);
                    if (b != Blocks.LEAVES && b != Blocks.AUTUMN_LEAVES) continue;
                    if (ch.get(lx, y - 1, lz) != Blocks.AIR || rnd.nextFloat() > 0.07f) continue;
                    int len = 1 + rnd.nextInt(3);
                    for (int k = 1; k <= len && y - k > 1 && ch.get(lx, y - k, lz) == Blocks.AIR
                            && ch.get(lx, y - k - 1, lz) == Blocks.AIR; k++)
                        ch.blocks[Chunk.idx(lx, y - k, lz)] = (char) Blocks.VINES;
                }
    }

    /** Stone with the occasional ore; rarer ores only appear deeper down. */
    private static int ore(int y, Random rnd) {
        float p = rnd.nextFloat();
        if (p < 0.012f) return Blocks.COAL_ORE;
        if (p < 0.020f && y < 34) return Blocks.COPPER_ORE;
        if (p < 0.024f && y < 20) return Blocks.GOLD_ORE;
        if (p < 0.0258f && y < 12) return Blocks.CRYSTAL_ORE;
        return Blocks.STONE;
    }
}
