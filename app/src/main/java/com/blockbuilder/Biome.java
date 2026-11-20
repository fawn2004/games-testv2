package com.blockbuilder;

import java.util.ArrayList;
import java.util.List;

/** A biome: climate, terrain shape, ground colours, plants and the trees that grow there. */
final class Biome {
    static final int COLD = 0, TEMPERATE = 1, WARM = 2, TROPICAL = 3;

    /** Tree shapes. */
    static final int ROUND = 0, BROAD = 1, SLENDER = 2, CONE = 3, PINE = 4, GIANT = 5, TROPICAL_TREE = 6,
            SAVANNA = 7, CEDAR = 8, CYPRESS = 9, SHRUB = 10, PALM = 11;

    /** Leaf colour palettes (stored in the leaves block's meta). */
    static final float[][] LEAF_TINT = {
            {1.00f, 1.00f, 1.00f}, // 0 default
            {1.28f, 1.18f, 0.78f}, // 1 light yellow-green (birch, poplar)
            {0.82f, 0.90f, 0.82f}, // 2 deep green (oak, walnut)
            {0.85f, 0.98f, 1.10f}, // 3 blue-green (spruce, fir)
            {1.12f, 1.02f, 0.72f}, // 4 olive (teak, hickory, ash)
            {0.92f, 1.16f, 0.82f}, // 5 lush tropical
            {1.06f, 1.12f, 1.02f}, // 6 silvery green (linden, sycamore)
            {1.35f, 0.72f, 0.62f}, // 7 copper (copper beech)
            {1.20f, 1.25f, 0.70f}, // 8 bright larch green
    };

    static final class Tree {
        final int log, leaves, leafTint, shape;
        final float weight;

        Tree(int log, int leaves, int leafTint, int shape, float weight) {
            this.log = log;
            this.leaves = leaves;
            this.leafTint = leafTint;
            this.shape = shape;
            this.weight = weight;
        }
    }

    final String name;
    final int climate;
    /** Terrain: base height, hill amplitude, mountain amplitude. */
    final float base, hills, mountains;
    /** Grass colour multiplier. */
    final float tr, tg, tb;
    /** Chance per column of a tree / shrub, and of plants. */
    float treeChance = 0.012f, shrubChance = 0.004f, grass = 0.28f, ferns = 0.03f, flowers = 0.012f;
    int ground = Blocks.GRASS;
    boolean swamp, snowy;
    final List<Tree> trees = new ArrayList<>();
    float totalWeight;

    Biome(String name, int climate, float base, float hills, float mountains, float tr, float tg, float tb) {
        this.name = name;
        this.climate = climate;
        this.base = base;
        this.hills = hills;
        this.mountains = mountains;
        this.tr = tr;
        this.tg = tg;
        this.tb = tb;
    }

    Biome tree(int log, int leaves, int tint, int shape, float weight) {
        trees.add(new Tree(log, leaves, tint, shape, weight));
        totalWeight += weight;
        return this;
    }

    Biome density(float trees, float shrubs) {
        treeChance = trees;
        shrubChance = shrubs;
        return this;
    }

    Biome plants(float grass, float ferns, float flowers) {
        this.grass = grass;
        this.ferns = ferns;
        this.flowers = flowers;
        return this;
    }

    Tree pick(float r) {
        float t = r * totalWeight;
        for (Tree tr : trees) {
            t -= tr.weight;
            if (t <= 0) return tr;
        }
        return trees.isEmpty() ? null : trees.get(trees.size() - 1);
    }

    // ------------------------------------------------------------------ registry

    static final List<Biome> ALL = new ArrayList<>();
    /** Biome indices per climate. */
    static final List<List<Integer>> BY_CLIMATE = new ArrayList<>();

    private static int log(String species) {
        return Blocks.wood(idx(species), Blocks.W_LOG);
    }

    private static int idx(String species) {
        for (int i = 0; i < Wood.ALL.length; i++) if (Wood.ALL[i].name.equals(species)) return i;
        throw new IllegalArgumentException(species);
    }

    private static Biome add(Biome b) {
        ALL.add(b);
        return b;
    }

    /** Shape, leaf block, leaf tint, climate and biome naming for each species. */
    private static void speciesBiome(String species, int shape, int leaves, int tint, int climate, String kind) {
        float base = 17, hills = 14, mount = 10;
        float tr = 1, tg = 1, tb = 1;
        if (climate == COLD) { base = 20; hills = 18; mount = 26; tr = 0.86f; tg = 0.96f; tb = 1.02f; }
        if (climate == WARM) { tr = 1.1f; tg = 1.02f; tb = 0.8f; }
        if (climate == TROPICAL) { base = 16; hills = 16; mount = 8; tr = 0.9f; tg = 1.12f; tb = 0.8f; }
        Biome b = add(new Biome(species + " " + kind, climate, base, hills, mount, tr, tg, tb));
        b.tree(log(species), leaves, tint, shape, 1f);
        switch (shape) {
            case CONE: case PINE: case GIANT:
                b.density(shape == GIANT ? 0.018f : 0.022f, 0.004f).plants(0.18f, 0.08f, 0.004f);
                b.ground = Blocks.FOREST_FLOOR;
                break;
            case TROPICAL_TREE:
                b.density(0.03f, 0.03f).plants(0.3f, 0.1f, 0.01f);
                break;
            case SAVANNA:
                b.density(0.005f, 0.01f).plants(0.45f, 0.0f, 0.006f);
                break;
            case SLENDER:
                b.density(0.02f, 0.004f).plants(0.3f, 0.04f, 0.015f);
                break;
            case CYPRESS:
                b.density(0.02f, 0.006f).plants(0.25f, 0.08f, 0.004f);
                b.swamp = true;
                break;
            default:
                b.density(0.014f, 0.006f).plants(0.3f, 0.03f, 0.014f);
        }
    }

    static {
        int L = Blocks.LEAVES, N = Blocks.NEEDLES, A = Blocks.AUTUMN_LEAVES, B = Blocks.BLOSSOM_LEAVES;
        // ---- one biome per wood species
        speciesBiome("White Oak", BROAD, L, 2, TEMPERATE, "Woodland");
        speciesBiome("Red Oak", BROAD, L, 2, TEMPERATE, "Woodland");
        speciesBiome("Common Ash", ROUND, L, 6, TEMPERATE, "Forest");
        speciesBiome("White Ash", ROUND, L, 6, TEMPERATE, "Forest");
        speciesBiome("Green Ash", ROUND, L, 4, TEMPERATE, "Forest");
        speciesBiome("European Beech", ROUND, L, 7, TEMPERATE, "Forest");
        speciesBiome("American Beech", ROUND, L, 0, TEMPERATE, "Forest");
        speciesBiome("Silver Birch", SLENDER, L, 1, COLD, "Grove");
        speciesBiome("Yellow Birch", SLENDER, L, 1, COLD, "Grove");
        speciesBiome("Paper Birch", SLENDER, L, 1, COLD, "Grove");
        speciesBiome("Sugar Maple", ROUND, A, 0, TEMPERATE, "Forest");
        speciesBiome("Red Maple", ROUND, A, 0, TEMPERATE, "Forest");
        speciesBiome("Black Walnut", BROAD, L, 2, TEMPERATE, "Woods");
        speciesBiome("English Walnut", BROAD, L, 2, TEMPERATE, "Woods");
        speciesBiome("Black Cherry", ROUND, L, 0, TEMPERATE, "Orchard");
        speciesBiome("Wild Cherry", ROUND, B, 0, TEMPERATE, "Orchard");
        speciesBiome("Honduran Mahogany", TROPICAL_TREE, L, 5, TROPICAL, "Jungle");
        speciesBiome("African Mahogany", TROPICAL_TREE, L, 5, TROPICAL, "Jungle");
        speciesBiome("Teak", TROPICAL_TREE, L, 4, TROPICAL, "Forest");
        speciesBiome("Balsa", SLENDER, L, 1, TROPICAL, "Grove");
        speciesBiome("Red Alder", SLENDER, L, 2, TEMPERATE, "Grove");
        speciesBiome("Black Alder", SLENDER, L, 2, TEMPERATE, "Grove");
        speciesBiome("Linden", ROUND, L, 6, TEMPERATE, "Forest");
        speciesBiome("Hickory", BROAD, L, 4, TEMPERATE, "Woods");
        speciesBiome("Sycamore", BROAD, L, 6, TEMPERATE, "Woods");
        speciesBiome("Yellow Poplar", SLENDER, L, 1, TEMPERATE, "Forest");
        speciesBiome("Rosewood", TROPICAL_TREE, L, 5, TROPICAL, "Jungle");
        speciesBiome("Iroko", TROPICAL_TREE, L, 5, TROPICAL, "Forest");
        speciesBiome("Sapele", TROPICAL_TREE, L, 5, TROPICAL, "Jungle");
        speciesBiome("Merbau", TROPICAL_TREE, L, 4, TROPICAL, "Forest");
        speciesBiome("Wenge", SAVANNA, L, 4, WARM, "Savanna");
        speciesBiome("Zebrano", SAVANNA, L, 1, WARM, "Savanna");
        speciesBiome("Scots Pine", PINE, N, 0, COLD, "Forest");
        speciesBiome("White Pine", PINE, N, 0, COLD, "Forest");
        speciesBiome("Yellow Pine", PINE, N, 0, WARM, "Barrens");
        speciesBiome("Norway Spruce", CONE, N, 3, COLD, "Taiga");
        speciesBiome("Sitka Spruce", CONE, N, 3, COLD, "Taiga");
        speciesBiome("Douglas Fir", CONE, N, 0, COLD, "Forest");
        speciesBiome("Silver Fir", CONE, N, 3, COLD, "Forest");
        speciesBiome("Western Red Cedar", CONE, N, 0, COLD, "Rainforest");
        speciesBiome("Eastern Red Cedar", CEDAR, N, 0, WARM, "Glade");
        speciesBiome("Bald Cypress", CYPRESS, L, 4, WARM, "Swamp");
        speciesBiome("Redwood", GIANT, N, 0, TEMPERATE, "Forest");
        speciesBiome("European Larch", CONE, N, 8, COLD, "Forest");
        speciesBiome("Siberian Larch", CONE, N, 8, COLD, "Taiga");
        speciesBiome("Hemlock", CONE, N, 3, COLD, "Forest");

        // ---- mixed biomes
        add(new Biome("Mixed Woodland", TEMPERATE, 17, 14, 8, 1, 1, 1))
                .tree(log("White Oak"), L, 2, BROAD, 2).tree(log("Common Ash"), L, 6, ROUND, 2)
                .tree(log("European Beech"), L, 0, ROUND, 2).tree(log("Silver Birch"), L, 1, SLENDER, 1.5f)
                .tree(Blocks.LOG, L, 0, ROUND, 1).tree(0, L, 2, SHRUB, 2)
                .density(0.02f, 0.02f).plants(0.32f, 0.05f, 0.015f);
        add(new Biome("Temperate Rainforest", COLD, 18, 18, 22, 0.85f, 1.02f, 0.95f))
                .tree(log("Douglas Fir"), N, 0, CONE, 2).tree(log("Sitka Spruce"), N, 3, CONE, 2)
                .tree(log("Western Red Cedar"), N, 0, CONE, 1.5f).tree(log("Hemlock"), N, 3, CONE, 1.5f)
                .tree(log("Red Alder"), L, 2, SLENDER, 1).tree(0, L, 2, SHRUB, 1)
                .density(0.03f, 0.01f).plants(0.2f, 0.2f, 0.004f).ground = Blocks.FOREST_FLOOR;
        add(new Biome("Tropical Rainforest", TROPICAL, 16, 18, 10, 0.88f, 1.15f, 0.78f))
                .tree(log("Honduran Mahogany"), L, 5, TROPICAL_TREE, 2).tree(log("Teak"), L, 4, TROPICAL_TREE, 1.5f)
                .tree(log("Rosewood"), L, 5, TROPICAL_TREE, 1.5f).tree(log("Iroko"), L, 5, TROPICAL_TREE, 1)
                .tree(log("Balsa"), L, 1, SLENDER, 1).tree(Blocks.LIGHT_LOG, L, 5, PALM, 0.7f)
                .tree(0, L, 5, SHRUB, 3)
                .density(0.035f, 0.04f).plants(0.35f, 0.15f, 0.01f);
        add(new Biome("Autumn Hills", TEMPERATE, 19, 20, 14, 1.15f, 0.95f, 0.7f))
                .tree(log("Sugar Maple"), A, 0, ROUND, 3).tree(log("Red Maple"), A, 0, ROUND, 2)
                .tree(log("American Beech"), A, 0, ROUND, 1).tree(log("Hickory"), L, 4, BROAD, 1)
                .tree(log("Yellow Birch"), L, 1, SLENDER, 1)
                .density(0.016f, 0.008f).plants(0.25f, 0.02f, 0.008f);
        add(new Biome("Blossom Valley", TEMPERATE, 19, 10, 4, 1.05f, 1.06f, 0.92f))
                .tree(log("Wild Cherry"), B, 0, ROUND, 3).tree(Blocks.DARK_LOG, B, 0, ROUND, 1)
                .tree(log("Black Cherry"), L, 0, ROUND, 1).tree(0, B, 0, SHRUB, 0.5f)
                .density(0.01f, 0.006f).plants(0.3f, 0.01f, 0.06f);
        add(new Biome("Shrubland", WARM, 17, 9, 4, 1.15f, 1.02f, 0.72f))
                .tree(0, L, 4, SHRUB, 6).tree(log("Eastern Red Cedar"), N, 0, CEDAR, 1)
                .tree(log("Zebrano"), L, 1, SAVANNA, 1)
                .density(0.03f, 0.0f).plants(0.4f, 0.0f, 0.01f).ground = Blocks.GRASS;
        add(new Biome("Meadow", TEMPERATE, 18, 8, 3, 1.05f, 1.05f, 0.9f))
                .tree(Blocks.LOG, L, 0, ROUND, 2).tree(Blocks.LIGHT_LOG, L, 1, SLENDER, 1)
                .tree(log("English Walnut"), L, 2, BROAD, 1).tree(0, L, 2, SHRUB, 1)
                .density(0.002f, 0.004f).plants(0.42f, 0.0f, 0.06f);
        Biome peaks = add(new Biome("Snowy Peaks", COLD, 26, 22, 48, 0.8f, 0.95f, 1.05f))
                .tree(log("Norway Spruce"), N, 3, CONE, 2).tree(log("Siberian Larch"), N, 8, CONE, 1)
                .density(0.006f, 0.0f).plants(0.12f, 0.02f, 0.002f);
        peaks.snowy = true;
        Biome swamp = add(new Biome("Tropical Marsh", TROPICAL, 19, 3, 0, 0.85f, 1.05f, 0.75f))
                .tree(log("Bald Cypress"), L, 5, CYPRESS, 1).tree(0, L, 5, SHRUB, 1)
                .density(0.02f, 0.02f).plants(0.3f, 0.12f, 0.004f);
        swamp.swamp = true;

        for (int c = 0; c < 4; c++) BY_CLIMATE.add(new ArrayList<>());
        for (int i = 0; i < ALL.size(); i++) BY_CLIMATE.get(ALL.get(i).climate).add(i);
    }
}
