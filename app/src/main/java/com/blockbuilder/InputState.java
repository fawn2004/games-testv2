package com.blockbuilder;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Shared between the UI thread (touch HUD) and the GL thread (game loop). */
final class InputState {
    static final int ACT_BREAK = 1, ACT_PLACE = 2;

    volatile float moveX, moveY;     // joystick: x = strafe right, y = forward
    volatile boolean jump, down;
    volatile boolean flying;
    volatile boolean breakMode;
    volatile int selected;
    final int[] hotbar = {Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.PLANKS, Blocks.LOG,
            Blocks.GLASS, Blocks.BRICK, Blocks.RED, Blocks.BLUE};

    final ConcurrentLinkedQueue<Integer> actions = new ConcurrentLinkedQueue<>();

    private float lookDX, lookDY;

    synchronized void addLook(float dx, float dy) {
        lookDX += dx;
        lookDY += dy;
    }

    /** Returns and resets accumulated look deltas (radians). */
    synchronized void consumeLook(float[] out) {
        out[0] = lookDX;
        out[1] = lookDY;
        lookDX = 0;
        lookDY = 0;
    }

    int selectedBlock() {
        return hotbar[selected];
    }
}
