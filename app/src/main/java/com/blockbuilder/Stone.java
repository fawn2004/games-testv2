package com.blockbuilder;

/** Real-world building and decorative stones. Each gets a full block, stairs and a slab. */
final class Stone {
    // Texture styles
    static final int S_PORES = 0, S_SPECKLE = 1, S_FLOW = 2, S_PLAIN = 3, S_ASHLAR = 4, S_BANDS = 5,
            S_PEBBLES = 6, S_LAMINA = 7, S_FLINT = 8, S_SPARKLE = 9, S_GNEISS = 10, S_SCHIST = 11,
            S_VEINS = 12, S_ONYX = 13, S_FLAGS = 14, S_FOSSIL = 15;

    static final String IGNEOUS = "IGNEOUS  (cooled magma / lava)",
            SEDIMENTARY = "SEDIMENTARY  (compressed sediment)",
            METAMORPHIC = "METAMORPHIC  (changed by heat & pressure)",
            OTHER = "OTHER DECORATIVE / BUILDING STONES";

    final String name, section;
    /** Block id of an existing block reused as this stone's full block, or -1 for a new one. */
    final int existing;
    final int style, base, accent, accent2;

    private Stone(String section, String name, int existing, int style, int base, int accent, int accent2) {
        this.section = section;
        this.name = name;
        this.existing = existing;
        this.style = style;
        this.base = base;
        this.accent = accent;
        this.accent2 = accent2;
    }

    private static Stone old(String section, String name, int id) {
        return new Stone(section, name, id, -1, 0, 0, 0);
    }

    private static Stone s(String section, String name, int style, int base, int accent, int accent2) {
        return new Stone(section, name, -1, style, base, accent, accent2);
    }

    /** Order is permanent: block ids are derived from it. Only append new stones. */
    static final Stone[] ALL = {
            old(IGNEOUS, "Granite", Blocks.GRANITE),
            old(IGNEOUS, "Basalt", Blocks.BASALT),
            old(IGNEOUS, "Obsidian", Blocks.OBSIDIAN),
            s(IGNEOUS, "Pumice", S_PORES, 0xCFCABB, 0x8E897C, 0),
            s(IGNEOUS, "Dolerite", S_SPECKLE, 0x3E4542, 0x262B29, 0x6A726C),
            s(IGNEOUS, "Rhyolite", S_FLOW, 0xC69C8A, 0xA87A6A, 0xDDBBAA),
            s(IGNEOUS, "Diorite", S_SPECKLE, 0xD8D8D2, 0x2A2A2A, 0x8A8A86),

            s(SEDIMENTARY, "Limestone", S_FOSSIL, 0xD6CFB8, 0xB8AF96, 0),
            s(SEDIMENTARY, "Portland Stone", S_ASHLAR, 0xE6E0CC, 0xB8B09A, 0),
            s(SEDIMENTARY, "Chalk", S_PLAIN, 0xF2F0E8, 0xDCD8CC, 0),
            old(SEDIMENTARY, "Sandstone", Blocks.SANDSTONE),
            s(SEDIMENTARY, "Travertine", S_BANDS, 0xD9C6A0, 0xBFA87E, 0x9E8A66),
            s(SEDIMENTARY, "Conglomerate", S_PEBBLES, 0xA08C70, 0x6E6A66, 0xC8B49A),
            s(SEDIMENTARY, "Mudstone", S_PLAIN, 0x7A6A5A, 0x6A5A4C, 0),
            s(SEDIMENTARY, "Shale", S_LAMINA, 0x4E4E52, 0x3A3A3E, 0x5E5A54),
            s(SEDIMENTARY, "Flint", S_FLINT, 0x34343C, 0xEDE9DF, 0x9A968C),

            old(METAMORPHIC, "Marble", Blocks.MARBLE),
            old(METAMORPHIC, "Slate", Blocks.SLATE),
            s(METAMORPHIC, "Quartzite", S_SPARKLE, 0xE8DDD6, 0xFFFFFF, 0xD0BEB4),
            s(METAMORPHIC, "Gneiss", S_GNEISS, 0xC8C0B8, 0x3A3A3E, 0x9A8E86),
            s(METAMORPHIC, "Schist", S_SCHIST, 0x8A8E84, 0xB8BCB2, 0x8A2A2A),
            s(METAMORPHIC, "Soapstone", S_VEINS, 0x7E8C82, 0x96A49A, 0),
            s(METAMORPHIC, "Onyx", S_ONYX, 0xE8D8B0, 0xB88A50, 0xF6EEDC),

            s(OTHER, "Alabaster", S_VEINS, 0xF0ECE4, 0xDCD6CA, 0),
            s(OTHER, "Flagstone", S_FLAGS, 0x8E8476, 0x5A5248, 0),
            s(OTHER, "Tufa", S_PORES, 0xC8B890, 0x8E7E5A, 0),
    };

    /** How many stones need a brand-new full block. */
    static int newCount() {
        int n = 0;
        for (Stone s : ALL) if (s.existing < 0) n++;
        return n;
    }
}
