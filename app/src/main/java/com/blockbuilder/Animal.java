package com.blockbuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Animal species: size, movement, where they live, spawn-egg colours and a box model.
 * Models are made of coloured boxes measured in 1/16 of a block, facing +Z, feet at y = 0.
 */
final class Animal {
    /** Spawn eggs live in the hotbar next to blocks, using ids from here up. */
    static final int EGG0 = 10000;

    /** Part kinds: how a box moves when animated. */
    static final int BODY = 0, HEAD = 1, LEG_A = 2, LEG_B = 3, TAIL = 4, HIND_A = 5, HIND_B = 6;

    static final class Part {
        final float x0, y0, z0, x1, y1, z1;
        final int color, kind;

        Part(float x0, float y0, float z0, float x1, float y1, float z1, int color, int kind) {
            this.x0 = x0 / 16f; this.y0 = y0 / 16f; this.z0 = z0 / 16f;
            this.x1 = x1 / 16f; this.y1 = y1 / 16f; this.z1 = z1 / 16f;
            this.color = color;
            this.kind = kind;
        }
    }

    final int id;
    final String name;
    final float halfWidth, height, speed;
    final boolean hops, shy, swims;
    final int eggBase, eggSpots;
    /** Climates it lives in (bit per Biome climate), group size, and extra habitat rules. */
    final int climates, minGroup, maxGroup;
    boolean nearWater, snowOnly, farm, swampLover;
    final List<Part> parts = new ArrayList<>();
    final List<Part> partsAlt = new ArrayList<>();   // variant (e.g. a stag with antlers)

    private Animal(int id, String name, float hw, float h, float speed, boolean hops, boolean shy, boolean swims,
                   int eggBase, int eggSpots, int climates, int minGroup, int maxGroup) {
        this.id = id;
        this.name = name;
        this.halfWidth = hw;
        this.height = h;
        this.speed = speed;
        this.hops = hops;
        this.shy = shy;
        this.swims = swims;
        this.eggBase = eggBase;
        this.eggSpots = eggSpots;
        this.climates = climates;
        this.minGroup = minGroup;
        this.maxGroup = maxGroup;
    }

    private Animal box(float x0, float y0, float z0, float x1, float y1, float z1, int color, int kind) {
        parts.add(new Part(x0, y0, z0, x1, y1, z1, color, kind));
        partsAlt.add(new Part(x0, y0, z0, x1, y1, z1, color, kind));
        return this;
    }

    private Animal alt(float x0, float y0, float z0, float x1, float y1, float z1, int color, int kind) {
        partsAlt.add(new Part(x0, y0, z0, x1, y1, z1, color, kind));
        return this;
    }

    /** Four legs: front pair at z, back pair at -z. */
    private Animal legs(float x, float w, float zf, float zb, float d, float h, int color) {
        box(x - w, 0, zf - d, x, h, zf, color, LEG_A);
        box(-x, 0, zf - d, -x + w, h, zf, color, LEG_B);
        box(x - w, 0, zb, x, h, zb + d, color, LEG_B);
        box(-x, 0, zb, -x + w, h, zb + d, color, LEG_A);
        return this;
    }

    private Animal eyes(float x, float y, float z) {
        box(-x - 1, y, z, -x, y + 1, z + 0.3f, 0x151515, HEAD);
        box(x, y, z, x + 1, y + 1, z + 0.3f, 0x151515, HEAD);
        return this;
    }

    static int eggId(int animal) {
        return EGG0 + animal;
    }

    static boolean isEgg(int id) {
        return id >= EGG0 && id < EGG0 + ALL.size();
    }

    static Animal ofEgg(int id) {
        return ALL.get(id - EGG0);
    }

    /** Name for anything that can sit in a hotbar slot. */
    static String itemName(int id) {
        if (isEgg(id)) return ofEgg(id).name + " Spawn Egg";
        return id >= 0 && id < Blocks.COUNT ? Blocks.NAMES[id] : "?";
    }

    // ------------------------------------------------------------------ species

    static final List<Animal> ALL = new ArrayList<>();
    private static final int C = 1 << Biome.COLD, T = 1 << Biome.TEMPERATE, W = 1 << Biome.WARM, P = 1 << Biome.TROPICAL;

    private static Animal add(String name, float hw, float h, float speed, boolean hops, boolean shy, boolean swims,
                              int eggBase, int eggSpots, int climates, int minGroup, int maxGroup) {
        Animal a = new Animal(ALL.size(), name, hw, h, speed, hops, shy, swims, eggBase, eggSpots, climates, minGroup, maxGroup);
        ALL.add(a);
        return a;
    }

    static {
        // Cow: white with dark patches
        Animal cow = add("Cow", 0.45f, 1.4f, 1.2f, false, false, false, 0xEDEDED, 0x2A2A2A, T | W, 2, 4)
                .box(-6, 10, -9, 6, 22, 9, 0xEDEDED, BODY)
                .box(6, 13, -5, 6.3f, 19, 2, 0x2A2A2A, BODY).box(-6.3f, 14, 1, -6, 20, 7, 0x2A2A2A, BODY)
                .box(-3, 22, -6, 2, 22.3f, -1, 0x2A2A2A, BODY)
                .box(-4, 15, 9, 4, 23, 15, 0xEDEDED, HEAD).box(-4.3f, 18, 10, -4, 22, 13, 0x2A2A2A, HEAD)
                .box(-3, 15, 15, 3, 18.5f, 16.5f, 0xE6A3A3, HEAD)
                .box(-5.5f, 21, 11, -4, 22.5f, 12, 0xE8DCC0, HEAD).box(4, 21, 11, 5.5f, 22.5f, 12, 0xE8DCC0, HEAD)
                .eyes(2, 19.5f, 15).box(-0.5f, 13, -9.6f, 0.5f, 21, -9, 0xEDEDED, TAIL)
                .legs(6, 3, 8, -8, 3, 10, 0xE0E0E0);
        cow.farm = true;
        // Pig
        Animal pig = add("Pig", 0.42f, 0.95f, 1.1f, false, false, false, 0xF0A8A8, 0xD07880, T | P, 2, 3)
                .box(-5, 6, -7, 5, 14, 7, 0xF0A8A8, BODY)
                .box(-4, 6, 7, 4, 14, 13, 0xF0A8A8, HEAD).box(-2, 7, 13, 2, 10, 14.2f, 0xE07A84, HEAD)
                .box(-4, 13, 9, -2, 15, 11, 0xE89898, HEAD).box(2, 13, 9, 4, 15, 11, 0xE89898, HEAD)
                .eyes(2, 11, 13).box(-0.5f, 11, -7.6f, 0.5f, 13, -7, 0xE89898, TAIL)
                .legs(5, 3, 6, -6, 3, 6, 0xE89A9A);
        pig.farm = true;
        // Sheep: woolly body, dark face and legs
        Animal sheep = add("Sheep", 0.45f, 1.3f, 1.1f, false, false, false, 0xF2EFE6, 0x3A3330, T | C, 2, 4)
                .box(-6, 8, -8, 6, 19, 8, 0xF2EFE6, BODY)
                .box(-3, 12, 8, 3, 19, 13, 0x3A3330, HEAD).box(-3.5f, 17, 7.5f, 3.5f, 20, 11.5f, 0xF2EFE6, HEAD)
                .box(-5, 16, 9, -3, 17, 11, 0x3A3330, HEAD).box(3, 16, 9, 5, 17, 11, 0x3A3330, HEAD)
                .eyes(1.5f, 16, 13).legs(5, 3, 6, -6, 3, 9, 0x3A3330);
        sheep.farm = true;
        // Chicken
        Animal chicken = add("Chicken", 0.22f, 0.7f, 1.0f, false, true, false, 0xF4F2EC, 0xD83A2E, T | W | P, 2, 4)
                .box(-3, 4, -4, 3, 10, 4, 0xF4F2EC, BODY)
                .box(-3.5f, 5, -3, -3, 9, 2, 0xE6E2DA, BODY).box(3, 5, -3, 3.5f, 9, 2, 0xE6E2DA, BODY)
                .box(-2, 8, -5.5f, 2, 12, -4, 0xF4F2EC, TAIL)
                .box(-2, 9, 2, 2, 15, 6, 0xF4F2EC, HEAD).box(-1, 11, 6, 1, 13, 8, 0xF0B030, HEAD)
                .box(-0.5f, 15, 3, 0.5f, 17, 5, 0xD83A2E, HEAD).box(-0.5f, 9, 6, 0.5f, 11, 7, 0xD83A2E, HEAD)
                .eyes(1, 13, 6)
                .box(-2, 0, -0.5f, -1, 4, 0.5f, 0xF0B030, LEG_A).box(1, 0, -0.5f, 2, 4, 0.5f, 0xF0B030, LEG_B);
        chicken.farm = true;
        // Duck: brown body, green head, white collar
        Animal duck = add("Duck", 0.25f, 0.7f, 1.0f, false, true, true, 0x8A6A4A, 0x2E6A3A, T | W | P, 2, 3)
                .box(-3, 3, -5, 3, 8, 5, 0x8A6A4A, BODY).box(-2, 7, -6, 2, 10, -5, 0x5A4636, TAIL)
                .box(-2, 7, 3, 2, 8, 6, 0xF2F2F2, HEAD)
                .box(-2, 8, 3.5f, 2, 12, 7.5f, 0x2E6A3A, HEAD).box(-1, 8.5f, 7.5f, 1, 10, 10, 0xF0A030, HEAD)
                .eyes(1, 10.5f, 7.5f)
                .box(-2, 0, 0, -1, 3, 1, 0xF0A030, LEG_A).box(1, 0, 0, 2, 3, 1, 0xF0A030, LEG_B);
        duck.nearWater = true;
        // Rabbit
        add("Rabbit", 0.2f, 0.55f, 1.6f, true, true, false, 0x9A7A5A, 0xF2EEE6, C | T | W, 1, 3)
                .box(-2.5f, 2, -4, 2.5f, 7, 3, 0x9A7A5A, BODY).box(-1, 4, -5, 1, 6, -4, 0xF2EEE6, TAIL)
                .box(-2, 4, 2, 2, 9, 6, 0x9A7A5A, HEAD).box(-0.7f, 5, 6, 0.7f, 6, 6.4f, 0xE8A0A0, HEAD)
                .box(-1.6f, 9, 3, -0.6f, 14, 4, 0x9A7A5A, HEAD).box(0.6f, 9, 3, 1.6f, 14, 4, 0x9A7A5A, HEAD)
                .eyes(1, 7, 6)
                .box(-2.5f, 0, -3, -1, 2, 1, 0x8A6A4A, HIND_A).box(1, 0, -3, 2.5f, 2, 1, 0x8A6A4A, HIND_B)
                .box(-2, 0, 2, -1, 3, 3, 0x8A6A4A, LEG_A).box(1, 0, 2, 2, 3, 3, 0x8A6A4A, LEG_B);
        // Deer (sometimes a stag with antlers)
        add("Deer", 0.35f, 1.6f, 1.7f, false, true, false, 0xA9744A, 0xF0E6D2, C | T, 1, 3)
                .box(-4, 14, -9, 4, 22, 8, 0xA9744A, BODY).box(-3, 14, -6, 3, 14.5f, 6, 0xE8D8BC, BODY)
                .box(-2, 18, 6, 2, 26, 9, 0xA9744A, HEAD)
                .box(-2.5f, 24, 7, 2.5f, 29, 12, 0xA9744A, HEAD).box(-1.5f, 24, 12, 1.5f, 27, 14, 0x8A5A38, HEAD)
                .box(-0.7f, 26, 14, 0.7f, 27, 14.4f, 0x151515, HEAD)
                .box(-4, 28, 8, -2.5f, 30, 9, 0xA9744A, HEAD).box(2.5f, 28, 8, 4, 30, 9, 0xA9744A, HEAD)
                .eyes(1.5f, 27, 12).box(-1, 19, -9.6f, 1, 22, -9, 0xF0E6D2, TAIL)
                .legs(4, 2, 7, -8, 2, 14, 0x8A5A38)
                .alt(-2, 29, 9, -1, 33, 10, 0xE0D0B0, HEAD).alt(1, 29, 9, 2, 33, 10, 0xE0D0B0, HEAD)
                .alt(-4, 32, 9, -1, 33, 10, 0xE0D0B0, HEAD).alt(1, 32, 9, 4, 33, 10, 0xE0D0B0, HEAD)
                .alt(-4, 33, 9, -3, 35, 10, 0xE0D0B0, HEAD).alt(3, 33, 9, 4, 35, 10, 0xE0D0B0, HEAD);
        // Fox
        add("Fox", 0.3f, 0.75f, 1.6f, false, true, false, 0xD86A28, 0xF4F0E8, C | T, 1, 2)
                .box(-3, 6, -6, 3, 12, 6, 0xD86A28, BODY).box(-2.5f, 5.8f, 0, 2.5f, 6, 6, 0xF4F0E8, BODY)
                .box(-3, 7, 6, 3, 13, 11, 0xD86A28, HEAD).box(-1.5f, 7, 11, 1.5f, 10, 14, 0xF4F0E8, HEAD)
                .box(-0.6f, 9, 14, 0.6f, 10, 14.4f, 0x151515, HEAD)
                .box(-3, 13, 8, -1, 16, 9, 0xD86A28, HEAD).box(1, 13, 8, 3, 16, 9, 0xD86A28, HEAD)
                .box(-3, 15, 8, -1, 16, 9.2f, 0x2A1E16, HEAD).box(1, 15, 8, 3, 16, 9.2f, 0x2A1E16, HEAD)
                .eyes(1.5f, 11, 11)
                .box(-2, 7, -13, 2, 11, -6, 0xD86A28, TAIL).box(-2, 7, -15, 2, 11, -13, 0xF4F0E8, TAIL)
                .legs(3, 1.5f, 5, -5, 2, 6, 0x2A1E16);
        // Brown bear
        add("Bear", 0.7f, 1.5f, 1.3f, false, false, false, 0x4E3424, 0xB08A60, C, 1, 1)
                .box(-8, 10, -12, 8, 25, 12, 0x4E3424, BODY)
                .box(-6, 15, 12, 6, 26, 21, 0x4E3424, HEAD).box(-3, 15, 21, 3, 20, 24, 0xB08A60, HEAD)
                .box(-1, 19, 24, 1, 20, 24.4f, 0x151515, HEAD)
                .box(-6, 25, 15, -3, 28, 17, 0x4E3424, HEAD).box(3, 25, 15, 6, 28, 17, 0x4E3424, HEAD)
                .eyes(2.5f, 22, 21)
                .legs(8, 5, 11, -11, 5, 10, 0x3E2A1C);
        // Kangaroo: upright, hops on big hind legs
        add("Kangaroo", 0.35f, 1.7f, 2.4f, true, true, false, 0xB07A4E, 0xE6CCA8, W | P, 2, 4)
                .box(-4, 9, -4, 4, 22, 4, 0xB07A4E, BODY).box(-3, 10, 3.8f, 3, 20, 4.3f, 0xE6CCA8, BODY)
                .box(-2.5f, 22, -1, 2.5f, 28, 5, 0xB07A4E, HEAD).box(-1.5f, 22, 5, 1.5f, 25, 8, 0xB07A4E, HEAD)
                .box(-0.6f, 24, 8, 0.6f, 25, 8.4f, 0x151515, HEAD)
                .box(-2.5f, 28, 0, -1, 32, 1.5f, 0xB07A4E, HEAD).box(1, 28, 0, 2.5f, 32, 1.5f, 0xB07A4E, HEAD)
                .eyes(1.2f, 26, 5)
                .box(-4, 15, 4, -2, 18, 7, 0xA06A40, LEG_A).box(2, 15, 4, 4, 18, 7, 0xA06A40, LEG_B)
                .box(-5, 2, -4, -2, 10, 3, 0xA06A40, HIND_A).box(2, 2, -4, 5, 10, 3, 0xA06A40, HIND_B)
                .box(-5, 0, -3, -2, 2, 8, 0x8A5A36, HIND_A).box(2, 0, -3, 5, 2, 8, 0x8A5A36, HIND_B)
                .box(-1.5f, 1, -16, 1.5f, 6, -4, 0xB07A4E, TAIL);
        // Frog
        Animal frog = add("Frog", 0.2f, 0.4f, 1.4f, true, false, true, 0x4E9A3A, 0xE8D850, T | W | P, 2, 4)
                .box(-3, 1, -4, 3, 5, 4, 0x4E9A3A, BODY).box(-2.5f, 0.8f, -3, 2.5f, 1, 3, 0xE8D850, BODY)
                .box(-3, 3, 3, 3, 6, 6, 0x4E9A3A, HEAD)
                .box(-3, 5.5f, 3.5f, -1.5f, 7, 5, 0xF2F2E6, HEAD).box(1.5f, 5.5f, 3.5f, 3, 7, 5, 0xF2F2E6, HEAD)
                .box(-2.6f, 6, 5, -1.9f, 6.7f, 5.2f, 0x151515, HEAD).box(1.9f, 6, 5, 2.6f, 6.7f, 5.2f, 0x151515, HEAD)
                .box(-5, 0, -4, -3, 2, 1, 0x3E7E2E, HIND_A).box(3, 0, -4, 5, 2, 1, 0x3E7E2E, HIND_B)
                .box(-4, 0, 2, -3, 2, 4, 0x3E7E2E, LEG_A).box(3, 0, 2, 4, 2, 4, 0x3E7E2E, LEG_B);
        frog.swampLover = true;
        frog.nearWater = true;
        // Penguin
        Animal penguin = add("Penguin", 0.25f, 1.1f, 0.8f, false, false, true, 0x1E2026, 0xF2F2EE, C, 3, 5)
                .box(-3, 1, -3, 3, 14, 3, 0x1E2026, BODY).box(-2.5f, 2, 3, 2.5f, 12, 3.3f, 0xF2F2EE, BODY)
                .box(-2.5f, 13, -2.5f, 2.5f, 18, 2.5f, 0x1E2026, HEAD).box(-1.8f, 14, 2.5f, 1.8f, 16, 2.8f, 0xF2F2EE, HEAD)
                .box(-0.7f, 14.5f, 2.5f, 0.7f, 15.5f, 4.5f, 0xF0A030, HEAD).eyes(1, 16, 2.6f)
                .box(-3.6f, 5, -1, -3, 11, 1, 0x1E2026, LEG_A).box(3, 5, -1, 3.6f, 11, 1, 0x1E2026, LEG_B)
                .box(-2.5f, 0, -1, -0.5f, 1, 3, 0xF0A030, LEG_B).box(0.5f, 0, -1, 2.5f, 1, 3, 0xF0A030, LEG_A);
        penguin.snowOnly = true;
    }

    static final int COW = 0, PIG = 1, SHEEP = 2, CHICKEN = 3, DUCK = 4, RABBIT = 5, DEER = 6, FOX = 7, BEAR = 8,
            KANGAROO = 9, FROG = 10, PENGUIN = 11;
}
