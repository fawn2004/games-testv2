package com.blockbuilder;

/** Real-world timber species with colors and texture styles for their blocks. */
final class Wood {
    // Grain styles for planks
    static final int G_PLAIN = 0, G_STRONG = 1, G_KNOTS = 2, G_STRIPES = 3, G_RIBBON = 4, G_STREAKS = 5;
    // Bark styles for log sides
    static final int B_FURROWED = 0, B_SMOOTH = 1, B_BIRCH = 2, B_PATCHY = 3, B_FIBROUS = 4,
            B_LENTICEL = 5, B_SHAGGY = 6, B_PLATED = 7;

    final String name, family;
    final boolean softwood;
    final int planks, bark, grain, barkStyle, accent;

    private Wood(String family, String name, boolean softwood, int planks, int bark, int grain, int barkStyle, int accent) {
        this.family = family;
        this.name = name;
        this.softwood = softwood;
        this.planks = planks;
        this.bark = bark;
        this.grain = grain;
        this.barkStyle = barkStyle;
        this.accent = accent;
    }

    private static Wood h(String family, String name, int planks, int bark, int grain, int barkStyle, int accent) {
        return new Wood(family, name, false, planks, bark, grain, barkStyle, accent);
    }

    private static Wood s(String family, String name, int planks, int bark, int grain, int barkStyle, int accent) {
        return new Wood(family, name, true, planks, bark, grain, barkStyle, accent);
    }

    /** Order is permanent: block ids are derived from the index. Only append new species. */
    static final Wood[] ALL = {
            // ---------------- hardwoods
            h("Oak", "White Oak", 0xC4A06A, 0x6E6458, G_STRONG, B_FURROWED, 0),
            h("Oak", "Red Oak", 0xC8946A, 0x5E4E42, G_STRONG, B_FURROWED, 0),
            h("Ash", "Common Ash", 0xD9C9A3, 0x8A8478, G_STRONG, B_FURROWED, 0),
            h("Ash", "White Ash", 0xE0D2AE, 0x7E7A72, G_STRONG, B_FURROWED, 0),
            h("Ash", "Green Ash", 0xD4C49A, 0x75705F, G_STRONG, B_FURROWED, 0),
            h("Beech", "European Beech", 0xD9A877, 0x9A9A94, G_PLAIN, B_SMOOTH, 0),
            h("Beech", "American Beech", 0xD6A57A, 0xA4A49C, G_PLAIN, B_SMOOTH, 0),
            h("Birch", "Silver Birch", 0xE6D3A8, 0xE8E6DE, G_PLAIN, B_BIRCH, 0x2A2826),
            h("Birch", "Yellow Birch", 0xE0C595, 0xC8A86A, G_PLAIN, B_BIRCH, 0x7A5A34),
            h("Birch", "Paper Birch", 0xEAD9B0, 0xF4F2EC, G_PLAIN, B_BIRCH, 0x3A3632),
            h("Maple", "Sugar Maple", 0xEADBB8, 0x6B6052, G_PLAIN, B_FURROWED, 0),
            h("Maple", "Red Maple", 0xE2C9A0, 0x7A746A, G_PLAIN, B_PLATED, 0),
            h("Walnut", "Black Walnut", 0x5C3E2A, 0x3A322C, G_STREAKS, B_FURROWED, 0x3A2618),
            h("Walnut", "English Walnut", 0x8A6440, 0x8A8478, G_STREAKS, B_FURROWED, 0x5A3E26),
            h("Cherry", "Black Cherry", 0xA5583A, 0x3E2E2A, G_PLAIN, B_LENTICEL, 0xA88A70),
            h("Cherry", "Wild Cherry", 0xB86A48, 0x5A3E38, G_PLAIN, B_LENTICEL, 0xB89A80),
            h("Mahogany", "Honduran Mahogany", 0x8E3E26, 0x6A5446, G_RIBBON, B_PLATED, 0x6E2E1A),
            h("Mahogany", "African Mahogany", 0x9C4A2C, 0x6E5A4A, G_RIBBON, B_PLATED, 0x7A3820),
            h("Teak", "Teak", 0xB07A3E, 0x8A7A62, G_STREAKS, B_FURROWED, 0x7A5226),
            h("Balsa", "Balsa", 0xEEE2C4, 0xB8B2A2, G_PLAIN, B_SMOOTH, 0),
            h("Alder", "Red Alder", 0xC98E62, 0x8E8A82, G_PLAIN, B_SMOOTH, 0),
            h("Alder", "Black Alder", 0xC07E56, 0x4E4640, G_PLAIN, B_PLATED, 0),
            h("Linden", "Linden", 0xE8DCC0, 0x6E665A, G_PLAIN, B_FURROWED, 0),
            h("Hickory", "Hickory", 0xC9A27A, 0x6A645C, G_STREAKS, B_SHAGGY, 0x7A5638),
            h("Sycamore", "Sycamore", 0xE6D6B6, 0xC8C2A8, G_PLAIN, B_PATCHY, 0x8E8A70),
            h("Poplar", "Yellow Poplar", 0xD8CFA0, 0x6E6A5E, G_STREAKS, B_FURROWED, 0x9EA070),
            h("Rosewood", "Rosewood", 0x5A2A22, 0x4A3A30, G_STREAKS, B_PLATED, 0x2E1612),
            h("Iroko", "Iroko", 0xA87A3E, 0x7A6A54, G_STRONG, B_PLATED, 0),
            h("Sapele", "Sapele", 0x8A4A2E, 0x5E4A3A, G_RIBBON, B_PLATED, 0x6A341E),
            h("Merbau", "Merbau", 0x7A3E26, 0x6A5444, G_STREAKS, B_SMOOTH, 0x5A2A18),
            h("Wenge", "Wenge", 0x3E2E24, 0x2E2620, G_STRIPES, B_FURROWED, 0x1C1410),
            h("Zebrano", "Zebrano", 0xD8C08A, 0x6A5A48, G_STRIPES, B_FURROWED, 0x4A3420),
            // ---------------- softwoods
            s("Pine", "Scots Pine", 0xE0C08A, 0xA8643A, G_KNOTS, B_PLATED, 0x8A5A2E),
            s("Pine", "White Pine", 0xE8D6A8, 0x5E5650, G_KNOTS, B_FURROWED, 0x9A7A4A),
            s("Pine", "Yellow Pine", 0xDDB574, 0x7A5A3E, G_STRONG, B_PLATED, 0),
            s("Spruce", "Norway Spruce", 0xE6D6AE, 0x6A4E3A, G_KNOTS, B_PLATED, 0x9A7A50),
            s("Spruce", "Sitka Spruce", 0xE2CCA2, 0x7A6E62, G_PLAIN, B_PLATED, 0),
            s("Fir", "Douglas Fir", 0xD8A070, 0x5A4234, G_STRONG, B_FURROWED, 0),
            s("Fir", "Silver Fir", 0xE8DCC0, 0x9A9890, G_PLAIN, B_SMOOTH, 0),
            s("Cedar", "Western Red Cedar", 0xB8704A, 0x8A5A3E, G_STREAKS, B_FIBROUS, 0x8A4A2E),
            s("Cedar", "Eastern Red Cedar", 0xA85A48, 0x7E5A44, G_STREAKS, B_FIBROUS, 0xD8C49A),
            s("Cypress", "Bald Cypress", 0xD0B080, 0x8A6E5A, G_STRONG, B_FIBROUS, 0),
            s("Redwood", "Redwood", 0xA04A32, 0x8E4A30, G_STRONG, B_FIBROUS, 0),
            s("Larch", "European Larch", 0xD08E5A, 0x7A4A3A, G_STRONG, B_PLATED, 0),
            s("Larch", "Siberian Larch", 0xC98A58, 0x6E4434, G_STRONG, B_PLATED, 0),
            s("Hemlock", "Hemlock", 0xD6BE98, 0x5E4234, G_PLAIN, B_FURROWED, 0),
    };

    /** Index of the first softwood in ALL. */
    static int firstSoftwood() {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].softwood) return i;
        return ALL.length;
    }
}
