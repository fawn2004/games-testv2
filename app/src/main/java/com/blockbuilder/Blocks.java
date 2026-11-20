package com.blockbuilder;

/** Block ids, names and texture tiles. */
final class Blocks {
    static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, COBBLE = 4, PLANKS = 5, LOG = 6,
            LEAVES = 7, SAND = 8, GLASS = 9, BRICK = 10, WATER = 11, BEDROCK = 12, SNOW = 13,
            RED = 14, ORANGE = 15, YELLOW = 16, GREEN = 17, BLUE = 18, PURPLE = 19, WHITE = 20,
            BLACK = 21;
    static final int COUNT = 22;

    static final String[] NAMES = {
            "Air", "Grass", "Dirt", "Stone", "Cobblestone", "Planks", "Log", "Leaves", "Sand",
            "Glass", "Brick", "Water", "Bedrock", "Snow", "Red Block", "Orange Block",
            "Yellow Block", "Green Block", "Blue Block", "Purple Block", "White Block", "Black Block"
    };

    // Texture tile indices in the 16x16 atlas.
    static final int T_GRASS_TOP = 0, T_GRASS_SIDE = 1, T_DIRT = 2, T_STONE = 3, T_COBBLE = 4,
            T_PLANKS = 5, T_LOG_SIDE = 6, T_LOG_TOP = 7, T_LEAVES = 8, T_SAND = 9, T_GLASS = 10,
            T_BRICK = 11, T_WATER = 12, T_BEDROCK = 13, T_SNOW = 14, T_COLOR0 = 16;

    /** {top, side, bottom} tile for each block. */
    static final int[][] TILES = new int[COUNT][];

    static {
        TILES[AIR] = new int[]{0, 0, 0};
        TILES[GRASS] = new int[]{T_GRASS_TOP, T_GRASS_SIDE, T_DIRT};
        TILES[DIRT] = all(T_DIRT);
        TILES[STONE] = all(T_STONE);
        TILES[COBBLE] = all(T_COBBLE);
        TILES[PLANKS] = all(T_PLANKS);
        TILES[LOG] = new int[]{T_LOG_TOP, T_LOG_SIDE, T_LOG_TOP};
        TILES[LEAVES] = all(T_LEAVES);
        TILES[SAND] = all(T_SAND);
        TILES[GLASS] = all(T_GLASS);
        TILES[BRICK] = all(T_BRICK);
        TILES[WATER] = all(T_WATER);
        TILES[BEDROCK] = all(T_BEDROCK);
        TILES[SNOW] = all(T_SNOW);
        for (int i = RED; i <= BLACK; i++) TILES[i] = all(T_COLOR0 + (i - RED));
    }

    /** Blocks the player can choose from the inventory. */
    static final int[] PLACEABLE = {
            GRASS, DIRT, STONE, COBBLE, PLANKS, LOG, LEAVES, SAND, GLASS, BRICK, SNOW, WATER,
            RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, WHITE, BLACK
    };

    private static int[] all(int t) {
        return new int[]{t, t, t};
    }

    /** face: 0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z */
    static int tile(int b, int face) {
        int[] t = TILES[b];
        return face == 2 ? t[0] : face == 3 ? t[2] : t[1];
    }

    /** Fully hides faces behind it and casts ambient occlusion. */
    static boolean isOpaque(int b) {
        return b != AIR && b != GLASS && b != LEAVES && b != WATER;
    }

    /** Blocks the player collides with. */
    static boolean isSolid(int b) {
        return b != AIR && b != WATER;
    }

    private Blocks() {
    }
}
