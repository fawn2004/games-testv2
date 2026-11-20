package com.blockbuilder;

/** First-person player with AABB collision against the voxel grid. */
final class Player {
    static final float HW = 0.3f, H = 1.8f, EYE = 1.62f;
    static final float GRAVITY = 26f, JUMP_V = 8.4f, WALK = 4.5f, FLY = 11f, SWIM = 2.8f;

    float x, y, z, vx, vy, vz, yaw, pitch;
    boolean onGround, inWater, headInWater;
    private boolean hitWall;
    private final float[] look = new float[2];

    void spawn(World w) {
        int cx = World.SX / 2, cz = World.SZ / 2;
        // Spiral outward looking for dry land.
        for (int r = 0; r < World.SX / 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int px = cx + dx, pz = cz + dz;
                    int top = w.topY(px, pz);
                    int b = w.get(px, top, pz);
                    if (b == Blocks.GRASS || b == Blocks.SAND) {
                        place(px, top + 1, pz);
                        return;
                    }
                }
            }
        }
        place(cx, w.topY(cx, cz) + 1, cz);
    }

    private void place(int bx, int by, int bz) {
        x = bx + 0.5f;
        y = by;
        z = bz + 0.5f;
        vx = vy = vz = 0;
        yaw = 0.6f;
        pitch = -0.15f;
    }

    float eyeY() {
        return y + EYE;
    }

    void lookDir(float[] out) {
        float cp = (float) Math.cos(pitch);
        out[0] = (float) Math.sin(yaw) * cp;
        out[1] = (float) Math.sin(pitch);
        out[2] = -(float) Math.cos(yaw) * cp;
    }

    /** True if the block cell overlaps the player's body. */
    boolean intersects(int bx, int by, int bz) {
        return bx + 1 > x - HW && bx < x + HW && by + 1 > y && by < y + H && bz + 1 > z - HW && bz < z + HW;
    }

    void update(float dt, World w, InputState in) {
        in.consumeLook(look);
        yaw += look[0];
        pitch -= look[1];
        if (pitch > 1.55f) pitch = 1.55f;
        if (pitch < -1.55f) pitch = -1.55f;

        inWater = Blocks.isWater(w.get(fl(x), fl(y + 0.4f), fl(z)));
        headInWater = Blocks.isWater(w.get(fl(x), fl(eyeY()), fl(z)));

        float fx = (float) Math.sin(yaw), fz = -(float) Math.cos(yaw);
        float rx = -fz, rz = fx;
        float mx = in.moveX, my = in.moveY;
        float wx = fx * my + rx * mx, wz = fz * my + rz * mx;

        boolean flying = in.flying;
        float speed = flying ? FLY : (inWater ? SWIM : WALK);
        float k = Math.min(1f, dt * (flying ? 8f : 14f));
        vx += (wx * speed - vx) * k;
        vz += (wz * speed - vz) * k;

        if (flying) {
            float vert = (in.jump ? 1f : 0f) - (in.down ? 1f : 0f);
            vy += (vert * FLY * 0.8f - vy) * k;
        } else if (inWater) {
            vy -= GRAVITY * 0.3f * dt;
            if (in.jump) vy = Math.min(vy + 20f * dt, 3.5f);
            if (vy < -3f) vy = -3f;
        } else {
            vy -= GRAVITY * dt;
            if (vy < -50f) vy = -50f;
            if (in.jump && onGround) vy = JUMP_V;
        }

        hitWall = false;
        boolean wasOnGround = onGround;
        onGround = false;
        float dx = vx * dt, dy = vy * dt, dz = vz * dt;
        move(w, dx, dy, dz, wasOnGround && !flying);

        // Auto-jump onto single block ledges while walking.
        if (!flying && hitWall && (wasOnGround || onGround) && (mx * mx + my * my) > 0.04f) {
            float len = (float) Math.sqrt(wx * wx + wz * wz);
            if (len > 0.01f) {
                int bx = fl(x + wx / len * 0.6f), bz = fl(z + wz / len * 0.6f), by = fl(y + 0.01f);
                if (w.solidAt(bx, by, bz) && !w.solidAt(bx, by + 1, bz) && !w.solidAt(bx, by + 2, bz)
                        && !w.solidAt(fl(x), fl(y + H + 0.5f), fl(z))) {
                    vy = inWater ? 5.5f : JUMP_V;
                }
            }
        }

        if (y < -20) spawn(w);
    }

    private static int fl(float v) {
        return (int) Math.floor(v);
    }

    // ------------------------------------------------------------ collision

    private float[] boxes = new float[6 * 256];
    private int nBoxes;
    private static final float STEP = 0.6f;

    /** Collects the boxes of every cell the player could touch while moving by (dx,dy,dz). */
    private void gather(World w, float dx, float dy, float dz) {
        int x0 = fl(Math.min(x - HW, x - HW + dx)) - 1, x1 = fl(Math.max(x + HW, x + HW + dx)) + 1;
        int y0 = fl(Math.min(y, y + dy)) - 1, y1 = fl(Math.max(y + H, y + H + dy)) + 1;
        int z0 = fl(Math.min(z - HW, z - HW + dz)) - 1, z1 = fl(Math.max(z + HW, z + HW + dz)) + 1;
        nBoxes = 0;
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                for (int bz = z0; bz <= z1; bz++) {
                    if (boxes.length < (nBoxes + 4) * 6) {
                        float[] nb = new float[boxes.length * 2];
                        System.arraycopy(boxes, 0, nb, 0, nBoxes * 6);
                        boxes = nb;
                    }
                    nBoxes = w.collisionBoxes(bx, by, bz, boxes, nBoxes);
                }
    }

    /** Shortens a move along one axis so the player's box stops at the first box in the way. */
    private float clip(int axis, float d) {
        float px0 = x - HW, px1 = x + HW, py0 = y, py1 = y + H, pz0 = z - HW, pz1 = z + HW;
        final float e = 1e-5f;
        for (int k = 0; k < nBoxes; k++) {
            int o = k * 6;
            float bx0 = boxes[o], by0 = boxes[o + 1], bz0 = boxes[o + 2];
            float bx1 = boxes[o + 3], by1 = boxes[o + 4], bz1 = boxes[o + 5];
            if (axis == 0) {
                if (by1 <= py0 + e || by0 >= py1 - e || bz1 <= pz0 + e || bz0 >= pz1 - e) continue;
                if (d > 0 && bx0 >= px1 - e) d = Math.min(d, bx0 - px1);
                else if (d < 0 && bx1 <= px0 + e) d = Math.max(d, bx1 - px0);
            } else if (axis == 1) {
                if (bx1 <= px0 + e || bx0 >= px1 - e || bz1 <= pz0 + e || bz0 >= pz1 - e) continue;
                if (d > 0 && by0 >= py1 - e) d = Math.min(d, by0 - py1);
                else if (d < 0 && by1 <= py0 + e) d = Math.max(d, by1 - py0);
            } else {
                if (bx1 <= px0 + e || bx0 >= px1 - e || by1 <= py0 + e || by0 >= py1 - e) continue;
                if (d > 0 && bz0 >= pz1 - e) d = Math.min(d, bz0 - pz1);
                else if (d < 0 && bz1 <= pz0 + e) d = Math.max(d, bz1 - pz0);
            }
        }
        return d;
    }

    private void move(World w, float dx, float dy, float dz, boolean canStep) {
        gather(w, dx, dy + (canStep ? STEP : 0), dz);
        float sx = x, sy = y, sz = z;

        float cy = clip(1, dy);
        y += cy;
        float cx = clip(0, dx);
        x += cx;
        float cz = clip(2, dz);
        z += cz;
        boolean blocked = cx != dx || cz != dz;

        boolean landed = cy != dy && dy < 0;
        boolean bumped = cy != dy && dy > 0;

        // Step up onto slabs, stairs and other low edges.
        if (blocked && canStep) {
            float ax = x, ay = y, az = z;
            x = sx; y = sy; z = sz;
            float up = clip(1, STEP);
            y += up;
            float qx = clip(0, dx);
            x += qx;
            float qz = clip(2, dz);
            z += qz;
            float want = -up + Math.min(0, dy);
            float down = clip(1, want);
            y += down;
            if (qx * qx + qz * qz > cx * cx + cz * cz + 1e-6f) {
                cx = qx; cz = qz;
                landed = down != want;
                bumped = false;
                blocked = qx != dx || qz != dz;
            } else {
                x = ax; y = ay; z = az;
            }
        }

        if (cx != dx) vx = 0;
        if (cz != dz) vz = 0;
        if (landed) { onGround = true; vy = 0; }
        if (bumped) vy = 0;
        hitWall = blocked;
    }
}
