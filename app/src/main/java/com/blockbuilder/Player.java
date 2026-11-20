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

        inWater = w.get(fl(x), fl(y + 0.4f), fl(z)) == Blocks.WATER;
        headInWater = w.get(fl(x), fl(eyeY()), fl(z)) == Blocks.WATER;

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
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / 0.3f);
        if (steps < 1) steps = 1;
        for (int i = 0; i < steps; i++) {
            moveX(w, dx / steps);
            moveZ(w, dz / steps);
            moveY(w, dy / steps);
        }

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

    private boolean collides(World w) {
        int x0 = fl(x - HW), x1 = fl(x + HW - 1e-4f);
        int y0 = fl(y), y1 = fl(y + H - 1e-4f);
        int z0 = fl(z - HW), z1 = fl(z + HW - 1e-4f);
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                for (int bz = z0; bz <= z1; bz++)
                    if (w.solidAt(bx, by, bz)) return true;
        return false;
    }

    private void moveX(World w, float d) {
        if (d == 0) return;
        x += d;
        if (collides(w)) {
            x = d > 0 ? fl(x + HW) - HW - 0.001f : fl(x - HW) + 1 + HW + 0.001f;
            vx = 0;
            hitWall = true;
        }
    }

    private void moveZ(World w, float d) {
        if (d == 0) return;
        z += d;
        if (collides(w)) {
            z = d > 0 ? fl(z + HW) - HW - 0.001f : fl(z - HW) + 1 + HW + 0.001f;
            vz = 0;
            hitWall = true;
        }
    }

    private void moveY(World w, float d) {
        if (d == 0) return;
        y += d;
        if (collides(w)) {
            if (d < 0) {
                y = fl(y) + 1;
                onGround = true;
            } else {
                y = fl(y + H) - H - 0.001f;
            }
            vy = 0;
        }
    }
}
