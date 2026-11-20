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
    static final int COUNT = 66;

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
    }

    /** Inventory tabs. Each category holds at most 18 blocks (two rows of nine). */
    static final String[] CATEGORY_NAMES = {"Natural", "Wood", "Building", "Minerals", "Colors"};
    static final int[][] CATEGORIES = {
            {GRASS, DIRT, STONE, COBBLE, MOSSY_COBBLE, GRAVEL, SAND, RED_SAND, CLAY,
                    MUD, SNOW, ICE, GRANITE, MARBLE, SLATE, BASALT, OBSIDIAN, WATER},
            {LOG, PLANKS, LIGHT_LOG, LIGHT_PLANKS, DARK_LOG, DARK_PLANKS, LEAVES, AUTUMN_LEAVES,
                    BLOSSOM_LEAVES, HAY, CRATE, BOOKSHELF},
            {BRICK, STONE_BRICKS, MOSSY_BRICKS, POLISHED_STONE, SANDSTONE, TERRACOTTA, ROOF_TILES,
                    CHECKER, GLASS, METAL, LAMP},
            {COAL_ORE, COPPER_ORE, GOLD_ORE, CRYSTAL_ORE, COPPER_BLOCK, GOLD_BLOCK, CRYSTAL_BLOCK},
            {RED, ORANGE, YELLOW, LIME, GREEN, CYAN, LIGHT_BLUE, BLUE, PURPLE, MAGENTA, PINK,
                    BROWN, WHITE, LIGHT_GRAY, GRAY, BLACK},
    };

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
        return b != AIR && b != WATER;
    }

    private Blocks() {
    }
}
