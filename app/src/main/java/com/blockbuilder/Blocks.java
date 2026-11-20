package com.blockbuilder;

/** Block ids, names, texture tiles, render flags and inventory categories. */
final class Blocks {
    // Ids 0..21 are from version 1.0 and must never change (they are stored in save files).
    static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, COBBLE = 4, PLANKS = 5, LOG = 6,
            LEAVES = 7, SAND = 8, GLASS = 9, BRICK = 10, WATER = 11, BEDROCK = 12, SNOW = 13,
            RED = 14, ORANGE = 15, YELLOW = 16, GREEN = 17, BLUE = 18, PURPLE = 19, WHITE = 20,
            BLACK = 21;
    // Added in 1.3
    static final int GRAVEL = 22, CLAY = 23, MOSSY_COBBLE = 24, GRANITE = 25, MARBLE = 26,
            SLATE = 27, BASALT = 28, ICE = 29, MUD = 30, RED_SAND = 31, OBSIDIAN = 32,
            LIGHT_LOG = 33, LIGHT_PLANKS = 34, DARK_LOG = 35, DARK_PLANKS = 36,
            AUTUMN_LEAVES = 37, BLOSSOM_LEAVES = 38, HAY = 39, CRATE = 40, BOOKSHELF = 41,
            STONE_BRICKS = 42, MOSSY_BRICKS = 43, POLISHED_STONE = 44, SANDSTONE = 45,
            TERRACOTTA = 46, ROOF_TILES = 47, CHECKER = 48, METAL = 49, LAMP = 50,
            COAL_ORE = 51, COPPER_ORE = 52, GOLD_ORE = 53, CRYSTAL_ORE = 54,
            COPPER_BLOCK = 55, GOLD_BLOCK = 56, CRYSTAL_BLOCK = 57,
            PINK = 58, LIME = 59, CYAN = 60, LIGHT_BLUE = 61, BROWN = 62, GRAY = 63,
            LIGHT_GRAY = 64, MAGENTA = 65;
    static final int FIRST_WOOD = 66;
    /** Each wood species gets six consecutive ids in this order. */
    static final int W_LOG = 0, W_PLANKS = 1, W_STAIRS = 2, W_SLAB = 3, W_DOOR = 4, W_TRAPDOOR = 5,
            PIECES = 6;
    static final String[] PIECE_NAMES = {"Log", "Planks", "Stairs", "Slab", "Door", "Trapdoor"};
    static final int FIRST_STONE = FIRST_WOOD + Wood.ALL.length * PIECES;
    /** Added in 1.7: plants, ground cover and forest-floor blocks (ids are permanent). */
    static final int FIRST_EXTRA = 414;
    static final int GRASS_TUFT = 414, FERN = 415, BUSH = 416, RED_FLOWER = 417, YELLOW_FLOWER = 418,
            BLUE_FLOWER = 419, WHITE_FLOWER = 420, WHEAT = 421, VINES = 422, SEAGRASS = 423,
            MOSS_CARPET = 424, LEAF_LITTER = 425, COARSE_DIRT = 426, FOREST_FLOOR = 427, MOSS_BLOCK = 428;
    /** Added in 1.8. */
    static final int NEEDLES = 429;
    static final int COUNT = 430;
    static final int T_GRASS_TUFT = 384, T_FERN = 385, T_BUSH = 386, T_RED_FLOWER = 387,
            T_YELLOW_FLOWER = 388, T_BLUE_FLOWER = 389, T_WHITE_FLOWER = 390, T_WHEAT = 391,
            T_VINES = 392, T_SEAGRASS = 393, T_MOSS = 394, T_LEAF_LITTER = 395, T_COARSE_DIRT = 396,
            T_FOREST_FLOOR = 397, T_NEEDLES = 398;
    /** Tiles for new stones start here (one tile each). */
    static final int T_STONE0 = 80 + Wood.ALL.length * 6 + 4; // right after the wood tiles
    /** Per stone: full block, stairs and slab ids. */
    static final int[] STONE_FULL = new int[Stone.ALL.length], STONE_STAIRS = new int[Stone.ALL.length],
            STONE_SLAB = new int[Stone.ALL.length];
    /** Wood tiles start here; each species uses six tiles: log side, log top, planks, door lower, door upper, trapdoor. */
    static final int T_WOOD0 = 80;

    // Texture tile indices in the 16x16-tile atlas.
    static final int T_GRASS_TOP = 0, T_GRASS_SIDE = 1, T_DIRT = 2, T_STONE = 3, T_COBBLE = 4,
            T_PLANKS = 5, T_LOG_SIDE = 6, T_LOG_TOP = 7, T_LEAVES = 8, T_SAND = 9, T_GLASS = 10,
            T_BRICK = 11, T_WATER = 12, T_BEDROCK = 13, T_SNOW = 14, T_COLOR0 = 16;
    static final int T_GRAVEL = 24, T_CLAY = 25, T_MOSSY_COBBLE = 26, T_GRANITE = 27,
            T_MARBLE = 28, T_SLATE = 29, T_BASALT_SIDE = 30, T_BASALT_TOP = 31, T_ICE = 32,
            T_MUD = 33, T_RED_SAND = 34, T_OBSIDIAN = 35, T_LIGHT_LOG_SIDE = 36,
            T_LIGHT_LOG_TOP = 37, T_LIGHT_PLANKS = 38, T_DARK_LOG_SIDE = 39, T_DARK_LOG_TOP = 40,
            T_DARK_PLANKS = 41, T_AUTUMN_LEAVES = 42, T_BLOSSOM_LEAVES = 43, T_HAY_SIDE = 44,
            T_HAY_TOP = 45, T_CRATE = 46, T_BOOKSHELF = 47, T_STONE_BRICKS = 48,
            T_MOSSY_BRICKS = 49, T_POLISHED_STONE = 50, T_SANDSTONE_SIDE = 51,
            T_SANDSTONE_TOP = 52, T_TERRACOTTA = 53, T_ROOF_TILES = 54, T_CHECKER = 55,
            T_METAL = 56, T_LAMP = 57, T_COAL_ORE = 58, T_COPPER_ORE = 59, T_GOLD_ORE = 60,
            T_CRYSTAL_ORE = 61, T_COPPER_BLOCK = 62, T_GOLD_BLOCK = 63, T_CRYSTAL_BLOCK = 64,
            T_COLOR8 = 65;

    static final String[] NAMES = new String[COUNT];
    /** {top, side, bottom} tile for each block. */
    static final int[][] TILES = new int[COUNT][];
    private static final boolean[] OPAQUE = new boolean[COUNT];
    private static final boolean[] EMISSIVE = new boolean[COUNT];
    private static final int[] SHAPE = new int[COUNT];
    private static final boolean[] SOLID_FLAG = new boolean[COUNT];
    /** How much a block sways in the wind (0 = not at all). */
    private static final float[] SWAY = new float[COUNT];
    /** For wood pieces: the species index, otherwise -1. */
    private static final int[] SPECIES = new int[COUNT];
    /** Full block that two slabs of this block merge into. */
    private static final int[] FULL = new int[COUNT];

    private static final int SOLID = 0, CUTOUT = 1, CLEAR = 2;

    private static void def(int id, String name, int top, int side, int bottom, int kind) {
        NAMES[id] = name;
        TILES[id] = new int[]{top, side, bottom};
        OPAQUE[id] = kind == SOLID;
    }

    private static void def(int id, String name, int tile) {
        def(id, name, tile, tile, tile, SOLID);
    }

    static {
        def(AIR, "Air", 0, 0, 0, CLEAR);
        def(GRASS, "Grass", T_GRASS_TOP, T_GRASS_SIDE, T_DIRT, SOLID);
        def(DIRT, "Dirt", T_DIRT);
        def(STONE, "Stone", T_STONE);
        def(COBBLE, "Cobblestone", T_COBBLE);
        def(PLANKS, "Oak Planks", T_PLANKS);
        def(LOG, "Oak Log", T_LOG_TOP, T_LOG_SIDE, T_LOG_TOP, SOLID);
        def(LEAVES, "Leaves", T_LEAVES, T_LEAVES, T_LEAVES, CUTOUT);
        def(SAND, "Sand", T_SAND);
        def(GLASS, "Glass", T_GLASS, T_GLASS, T_GLASS, CUTOUT);
        def(BRICK, "Brick", T_BRICK);
        def(WATER, "Water", T_WATER, T_WATER, T_WATER, CLEAR);
        def(BEDROCK, "Bedrock", T_BEDROCK);
        def(SNOW, "Snow", T_SNOW);
        String[] colorNames = {"Red", "Orange", "Yellow", "Green", "Blue", "Purple", "White", "Black"};
        for (int i = 0; i < 8; i++) def(RED + i, colorNames[i] + " Block", T_COLOR0 + i);

        def(GRAVEL, "Gravel", T_GRAVEL);
        def(CLAY, "Clay", T_CLAY);
        def(MOSSY_COBBLE, "Mossy Cobblestone", T_MOSSY_COBBLE);
        def(GRANITE, "Granite", T_GRANITE);
        def(MARBLE, "Marble", T_MARBLE);
        def(SLATE, "Slate", T_SLATE);
        def(BASALT, "Basalt", T_BASALT_TOP, T_BASALT_SIDE, T_BASALT_TOP, SOLID);
        def(ICE, "Ice", T_ICE);
        def(MUD, "Mud", T_MUD);
        def(RED_SAND, "Red Sand", T_RED_SAND);
        def(OBSIDIAN, "Obsidian", T_OBSIDIAN);
        def(LIGHT_LOG, "Pale Log", T_LIGHT_LOG_TOP, T_LIGHT_LOG_SIDE, T_LIGHT_LOG_TOP, SOLID);
        def(LIGHT_PLANKS, "Pale Planks", T_LIGHT_PLANKS);
        def(DARK_LOG, "Ebony Log", T_DARK_LOG_TOP, T_DARK_LOG_SIDE, T_DARK_LOG_TOP, SOLID);
        def(DARK_PLANKS, "Ebony Planks", T_DARK_PLANKS);
        def(AUTUMN_LEAVES, "Autumn Leaves", T_AUTUMN_LEAVES, T_AUTUMN_LEAVES, T_AUTUMN_LEAVES, CUTOUT);
        def(BLOSSOM_LEAVES, "Blossom Leaves", T_BLOSSOM_LEAVES, T_BLOSSOM_LEAVES, T_BLOSSOM_LEAVES, CUTOUT);
        def(HAY, "Hay Bale", T_HAY_TOP, T_HAY_SIDE, T_HAY_TOP, SOLID);
        def(CRATE, "Crate", T_CRATE);
        def(BOOKSHELF, "Bookshelf", T_PLANKS, T_BOOKSHELF, T_PLANKS, SOLID);
        def(STONE_BRICKS, "Stone Bricks", T_STONE_BRICKS);
        def(MOSSY_BRICKS, "Mossy Stone Bricks", T_MOSSY_BRICKS);
        def(POLISHED_STONE, "Polished Stone", T_POLISHED_STONE);
        def(SANDSTONE, "Sandstone", T_SANDSTONE_TOP, T_SANDSTONE_SIDE, T_SANDSTONE_TOP, SOLID);
        def(TERRACOTTA, "Terracotta", T_TERRACOTTA);
        def(ROOF_TILES, "Roof Tiles", T_ROOF_TILES);
        def(CHECKER, "Checkered Tile", T_CHECKER);
        def(METAL, "Metal Plating", T_METAL);
        def(LAMP, "Glow Lamp", T_LAMP);
        def(COAL_ORE, "Coal Ore", T_COAL_ORE);
        def(COPPER_ORE, "Copper Ore", T_COPPER_ORE);
        def(GOLD_ORE, "Gold Ore", T_GOLD_ORE);
        def(CRYSTAL_ORE, "Crystal Ore", T_CRYSTAL_ORE);
        def(COPPER_BLOCK, "Copper Block", T_COPPER_BLOCK);
        def(GOLD_BLOCK, "Gold Block", T_GOLD_BLOCK);
        def(CRYSTAL_BLOCK, "Crystal Block", T_CRYSTAL_BLOCK);
        String[] moreColors = {"Pink", "Lime", "Cyan", "Light Blue", "Brown", "Gray", "Light Gray", "Magenta"};
        for (int i = 0; i < 8; i++) def(PINK + i, moreColors[i] + " Block", T_COLOR8 + i);
        EMISSIVE[LAMP] = true;
        def(PLANKS, "Classic Oak Planks", T_PLANKS);
        def(LOG, "Classic Oak Log", T_LOG_TOP, T_LOG_SIDE, T_LOG_TOP, SOLID);

        java.util.Arrays.fill(SPECIES, -1);
        for (int i = 0; i < Wood.ALL.length; i++) {
            String n = Wood.ALL[i].name;
            int id = wood(i, 0), t = T_WOOD0 + i * 6;
            def(id + W_LOG, n + " Log", t + 1, t, t + 1, SOLID);
            def(id + W_PLANKS, n + " Planks", t + 2);
            def(id + W_STAIRS, n + " Stairs", t + 2, t + 2, t + 2, CUTOUT);
            def(id + W_SLAB, n + " Slab", t + 2, t + 2, t + 2, CUTOUT);
            def(id + W_DOOR, n + " Door", t + 3, t + 3, t + 4, CUTOUT);
            def(id + W_TRAPDOOR, n + " Trapdoor", t + 5, t + 5, t + 5, CUTOUT);
            SHAPE[id + W_STAIRS] = Shapes.STAIRS;
            SHAPE[id + W_SLAB] = Shapes.SLAB;
            SHAPE[id + W_DOOR] = Shapes.DOOR;
            SHAPE[id + W_TRAPDOOR] = Shapes.TRAPDOOR;
            for (int k = 0; k < PIECES; k++) SPECIES[id + k] = i;
            FULL[id + W_SLAB] = id + W_PLANKS;
        }

        int next = FIRST_STONE, newTile = T_STONE0;
        for (int i = 0; i < Stone.ALL.length; i++) {
            Stone st = Stone.ALL[i];
            int full = st.existing;
            if (full < 0) {
                full = next++;
                def(full, st.name, newTile++);
            }
            int stairs = next++, slab = next++;
            int[] t = TILES[full];
            def(stairs, st.name + " Stairs", t[0], t[1], t[2], CUTOUT);
            def(slab, st.name + " Slab", t[0], t[1], t[2], CUTOUT);
            SHAPE[stairs] = Shapes.STAIRS;
            SHAPE[slab] = Shapes.SLAB;
            FULL[slab] = full;
            STONE_FULL[i] = full;
            STONE_STAIRS[i] = stairs;
            STONE_SLAB[i] = slab;
        }
        if (next != FIRST_EXTRA) throw new IllegalStateException("block ids moved: " + next);

        plant(GRASS_TUFT, "Grass Tuft", T_GRASS_TUFT, 1f);
        plant(FERN, "Fern", T_FERN, 1f);
        plant(BUSH, "Bush", T_BUSH, 0.6f);
        plant(RED_FLOWER, "Red Wildflower", T_RED_FLOWER, 1f);
        plant(YELLOW_FLOWER, "Buttercups", T_YELLOW_FLOWER, 1f);
        plant(BLUE_FLOWER, "Cornflower", T_BLUE_FLOWER, 1f);
        plant(WHITE_FLOWER, "Daisies", T_WHITE_FLOWER, 1f);
        plant(WHEAT, "Golden Wheat", T_WHEAT, 1f);
        plant(VINES, "Hanging Vines", T_VINES, 1f);
        plant(SEAGRASS, "Seagrass", T_SEAGRASS, 1f);
        def(MOSS_CARPET, "Moss Carpet", T_MOSS, T_MOSS, T_MOSS, CUTOUT);
        SHAPE[MOSS_CARPET] = Shapes.CARPET;
        def(LEAF_LITTER, "Leaf Litter", T_LEAF_LITTER, T_LEAF_LITTER, T_LEAF_LITTER, CUTOUT);
        SHAPE[LEAF_LITTER] = Shapes.CARPET;
        def(COARSE_DIRT, "Coarse Dirt", T_COARSE_DIRT);
        def(FOREST_FLOOR, "Forest Floor", T_FOREST_FLOOR, T_DIRT, T_DIRT, SOLID);
        def(MOSS_BLOCK, "Moss Block", T_MOSS);
        def(NEEDLES, "Conifer Needles", T_NEEDLES, T_NEEDLES, T_NEEDLES, CUTOUT);

        for (int b = 1; b < COUNT; b++) SOLID_FLAG[b] = b != WATER && SHAPE[b] != Shapes.CROSS;
        SWAY[LEAVES] = SWAY[AUTUMN_LEAVES] = SWAY[BLOSSOM_LEAVES] = 0.25f;
        SWAY[NEEDLES] = 0.12f;
    }

    private static void plant(int id, String name, int tile, float sway) {
        def(id, name, tile, tile, tile, CUTOUT);
        SHAPE[id] = Shapes.CROSS;
        SWAY[id] = sway;
    }

    static float sway(int b) {
        return SWAY[b];
    }

    static boolean isLeaves(int b) {
        return b == LEAVES || b == AUTUMN_LEAVES || b == BLOSSOM_LEAVES || b == NEEDLES;
    }

    /** Water, or a block that sits in water (seagrass). */
    static boolean isWater(int b) {
        return b == WATER || b == SEAGRASS;
    }

    /** Block id of a piece of a wood species. */
    static int wood(int species, int piece) {
        return FIRST_WOOD + species * PIECES + piece;
    }

    static int species(int b) {
        return SPECIES[b];
    }

    static int shape(int b) {
        return SHAPE[b];
    }

    /** The full block two slabs of this kind merge into. */
    static int fullOf(int b) {
        return FULL[b] != 0 ? FULL[b] : b;
    }

    /** Door texture: lower half uses the side tile, upper half the "bottom" tile slot. */
    static int doorTile(int b, boolean upper) {
        return upper ? TILES[b][2] : TILES[b][1];
    }

    static final int ANIMALS_TAB = 2, WOOD_TAB = 3, STONE_TAB = 4;

    /** Inventory tabs. Wood and Stone are browsed by group instead (see GROUPS). */
    static final String[] CATEGORY_NAMES = {"Natural", "Plants", "Animals", "Wood", "Stone", "Building", "Minerals", "Colors"};
    static final int[][] CATEGORIES = {
            {GRASS, DIRT, COARSE_DIRT, FOREST_FLOOR, STONE, COBBLE, MOSSY_COBBLE, GRAVEL, SAND,
                    RED_SAND, CLAY, MUD, SNOW, ICE, WATER},
            {GRASS_TUFT, FERN, BUSH, RED_FLOWER, YELLOW_FLOWER, BLUE_FLOWER, WHITE_FLOWER, WHEAT,
                    VINES, SEAGRASS, MOSS_CARPET, LEAF_LITTER, MOSS_BLOCK},
            eggs(),
            // Wood tab: these are shown under "Leaves & More"; species are listed separately.
            {LOG, PLANKS, LIGHT_LOG, LIGHT_PLANKS, DARK_LOG, DARK_PLANKS, LEAVES, AUTUMN_LEAVES,
                    BLOSSOM_LEAVES, NEEDLES, HAY, CRATE, BOOKSHELF},
            {},
            {BRICK, STONE_BRICKS, MOSSY_BRICKS, POLISHED_STONE, TERRACOTTA, ROOF_TILES,
                    CHECKER, GLASS, METAL, LAMP},
            {COAL_ORE, COPPER_ORE, GOLD_ORE, CRYSTAL_ORE, COPPER_BLOCK, GOLD_BLOCK, CRYSTAL_BLOCK},
            {RED, ORANGE, YELLOW, LIME, GREEN, CYAN, LIGHT_BLUE, BLUE, PURPLE, MAGENTA, PINK,
                    BROWN, WHITE, LIGHT_GRAY, GRAY, BLACK},
    };

    /** A named set of blocks shown together in the picker (one wood species, one stone, ...). */
    static final class Group {
        final String name, section, info;
        final int[] items;
        final int icon;

        Group(String name, String section, String info, int[] items, int icon) {
            this.name = name;
            this.section = section;
            this.info = info;
            this.items = items;
            this.icon = icon;
        }
    }

    /** For each tab, its groups, or null when the tab shows a plain grid. */
    static final Group[][] GROUPS = new Group[CATEGORY_NAMES.length][];
    /** Index of the group a block belongs to within its tab, or -1. */
    private static final int[] GROUP_OF = new int[COUNT];

    static {
        java.util.Arrays.fill(GROUP_OF, -1);
        Group[] wood = new Group[Wood.ALL.length + 1];
        wood[0] = new Group("Leaves & More", null, null, CATEGORIES[WOOD_TAB], LEAVES);
        for (int i = 0; i < Wood.ALL.length; i++) {
            Wood w = Wood.ALL[i];
            int[] items = new int[PIECES];
            for (int k = 0; k < PIECES; k++) items[k] = wood(i, k);
            String info = w.name + (w.name.endsWith(w.family) ? "" : "  \u00b7  " + w.family + " family")
                    + "  \u00b7  " + (w.softwood ? "softwood" : "hardwood")
                    + "   (log, planks, stairs, slab, door, trapdoor)";
            wood[i + 1] = new Group(w.name, w.softwood ? "SOFTWOODS (CONIFER)" : "HARDWOODS", info, items,
                    wood(i, W_LOG));
            for (int b : items) GROUP_OF[b] = i + 1;
        }
        GROUPS[WOOD_TAB] = wood;

        Group[] stone = new Group[Stone.ALL.length];
        for (int i = 0; i < Stone.ALL.length; i++) {
            Stone st = Stone.ALL[i];
            int[] items = {STONE_FULL[i], STONE_STAIRS[i], STONE_SLAB[i]};
            String kind = st.section.split(" ")[0].toLowerCase();
            stone[i] = new Group(st.name, st.section, st.name + "  \u00b7  " + kind + "   (block, stairs, slab)",
                    items, STONE_FULL[i]);
            for (int b : items) GROUP_OF[b] = i;
        }
        GROUPS[STONE_TAB] = stone;
    }

    static int groupOf(int b) {
        return GROUP_OF[b];
    }

    private static int[] eggs() {
        int[] e = new int[Animal.ALL.size()];
        for (int i = 0; i < e.length; i++) e[i] = Animal.eggId(i);
        return e;
    }

    /** face: 0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z */
    static int tile(int b, int face) {
        int[] t = TILES[b];
        return face == 2 ? t[0] : face == 3 ? t[2] : t[1];
    }

    /** Fully hides faces behind it and casts ambient occlusion. */
    static boolean isOpaque(int b) {
        return OPAQUE[b];
    }

    /** Glows: drawn at full brightness without shading. */
    static boolean isEmissive(int b) {
        return EMISSIVE[b];
    }

    /** Blocks the player collides with. */
    static boolean isSolid(int b) {
        return SOLID_FLAG[b];
    }

    private Blocks() {
    }
}
