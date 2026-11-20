package com.blockbuilder;

import java.util.Random;

/** One animal in the world: position, physics and a simple wander / flee brain. */
final class Mob {
    static final float GRAVITY = 26f;

    final Animal kind;
    /** Placed with a spawn egg: kept forever and saved with the world. */
    final boolean persistent;
    /** Model variant (e.g. a stag with antlers). */
    final boolean variant;
    double x, y, z;
    float vx, vy, vz, yaw, targetYaw;
    boolean onGround, inWater, hitWall, moving;
    float walkPhase, hopTimer, thinkTimer, fleeTimer;
    /** Lighting at the mob, refreshed now and then: direct sun 0..1 and open sky 0..1. */
    float sun = 1f, sky = 1f;
    int lightAge = 1000;
    private final Random r;

    Mob(Animal kind, double x, double y, double z, float yaw, boolean persistent, long seed) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.targetYaw = yaw;
        this.persistent = persistent;
        this.r = new Random(seed);
        this.variant = r.nextBoolean();
        thinkTimer = r.nextFloat() * 2f;
    }

    private static int fl(double v) {
        return (int) Math.floor(v);
    }

    void update(float dt, World w, double px, double pz) {
        int bx = fl(x), bz = fl(z);
        inWater = Blocks.isWater(w.get(bx, fl(y + kind.height * 0.4), bz));

        // ---- think
        thinkTimer -= dt;
        fleeTimer -= dt;
        double dxp = x - px, dzp = z - pz;
        if (kind.shy && dxp * dxp + dzp * dzp < 25) {          // the player came close: run!
            fleeTimer = 2.5f;
            targetYaw = (float) Math.atan2(dxp, -dzp);
            moving = true;
        } else if (thinkTimer <= 0) {
            if (r.nextFloat() < 0.55f) {
                moving = true;
                targetYaw = yaw + (r.nextFloat() - 0.5f) * 3.5f;
                thinkTimer = 2f + r.nextFloat() * 3f;
            } else {
                moving = false;
                thinkTimer = 2f + r.nextFloat() * 4f;
            }
        }
        // turn smoothly toward where it wants to go
        float diff = (float) Math.atan2(Math.sin(targetYaw - yaw), Math.cos(targetYaw - yaw));
        yaw += diff * Math.min(1f, dt * 5f);

        float speed = moving ? kind.speed * (fleeTimer > 0 ? 1.8f : 1f) : 0f;
        if (inWater && !kind.swims) speed *= 0.6f;
        float fx = (float) Math.sin(yaw), fz = -(float) Math.cos(yaw);

        // ---- don't walk into water (unless it swims) or off cliffs: turn around instead
        if (moving && onGround) {
            int ax = fl(x + fx * (kind.halfWidth + 0.6)), az = fl(z + fz * (kind.halfWidth + 0.6));
            int ay = fl(y + 0.01);
            boolean drop = !w.solidAt(ax, ay - 1, az) && !w.solidAt(ax, ay - 2, az) && !w.solidAt(ax, ay - 3, az)
                    && !w.solidAt(ax, ay, az);
            boolean water = !kind.swims && Blocks.isWater(w.get(ax, ay, az)) || (!kind.swims && Blocks.isWater(w.get(ax, ay - 1, az))
                    && !w.solidAt(ax, ay - 1, az));
            if (drop || water) {
                targetYaw = yaw + (float) Math.PI * (0.7f + r.nextFloat() * 0.6f);
                speed = 0;
            }
        }

        float k = Math.min(1f, dt * 10f);
        vx += (fx * speed - vx) * k;
        vz += (fz * speed - vz) * k;
        if (kind.hops && speed > 0 && !onGround && !inWater) {
            // keep momentum while airborne
        } else if (kind.hops && speed > 0 && onGround) {
            hopTimer -= dt;
            if (hopTimer <= 0) {
                vy = kind == Animal.ALL.get(Animal.KANGAROO) ? 7.5f : 5.5f;
                hopTimer = 0.25f;
            }
        }

        if (inWater) {
            vy -= GRAVITY * 0.2f * dt;
            vy = Math.min(vy + 14f * dt, kind.swims ? 1.6f : 2.4f);   // paddle to the surface
            if (vy < -2f) vy = -2f;
        } else {
            vy -= GRAVITY * dt;
            if (vy < -40f) vy = -40f;
        }

        boolean wasOnGround = onGround;
        onGround = false;
        move(w, vx * dt, vy * dt, vz * dt, wasOnGround);

        // hop up single-block ledges
        if (hitWall && (wasOnGround || onGround) && speed > 0) {
            int ax = fl(x + fx * (kind.halfWidth + 0.4)), az = fl(z + fz * (kind.halfWidth + 0.4)), ay = fl(y + 0.01);
            if (w.solidAt(ax, ay, az) && !w.solidAt(ax, ay + 1, az) && !w.solidAt(ax, ay + 2, az)) vy = 7.6f;
            else targetYaw = yaw + (float) Math.PI * (0.5f + r.nextFloat());
        }

        float hs = (float) Math.sqrt(vx * vx + vz * vz);
        walkPhase += hs * dt * 6f;
        if (y < -20) y = World.SY;
    }

    /** Lighting sample used to shade the model. */
    void relight(World w) {
        int bx = fl(x), by = fl(y + kind.height * 0.6), bz = fl(z);
        float t = 1f;
        for (int yy = by + 1; yy < World.SY && t > 0.02f; yy++) t *= World.transmit(w.get(bx, yy, bz));
        sky = t;
        float s = 1f;
        float sx = bx + 0.5f, sz = bz + 0.5f;
        for (int k = 1; k <= World.SHADOW_STEPS && by + k < World.SY && s > 0.03f; k++) {
            sx += World.SUN_DX;
            sz += World.SUN_DZ;
            s *= World.transmit(w.get(fl(sx), by + k, fl(sz)));
        }
        sun = s < 0.03f ? 0 : s;
        lightAge = 0;
    }

    /** Distance along a ray to this mob's box, or -1. */
    double rayHit(double ox, double oy, double oz, float dx, float dy, float dz, double max) {
        double hw = kind.halfWidth;
        double[] lo = {x - hw, y, z - hw}, hi = {x + hw, y + kind.height, z + hw};
        double[] o = {ox, oy, oz};
        float[] d = {dx, dy, dz};
        double tn = -1e18, tf = 1e18;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-9) {
                if (o[a] < lo[a] || o[a] > hi[a]) return -1;
                continue;
            }
            double t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tn = Math.max(tn, t1);
            tf = Math.min(tf, t2);
            if (tn > tf) return -1;
        }
        if (tf < 0 || tn > max) return -1;
        return Math.max(0, tn);
    }

    // ------------------------------------------------------------ collision (same approach as the player)

    private double[] boxes = new double[6 * 128];
    private int nBoxes;

    private void gather(World w, double dx, double dy, double dz) {
        double hw = kind.halfWidth, h = kind.height;
        int x0 = fl(Math.min(x - hw, x - hw + dx)) - 1, x1 = fl(Math.max(x + hw, x + hw + dx)) + 1;
        int y0 = fl(Math.min(y, y + dy)) - 1, y1 = fl(Math.max(y + h, y + h + dy)) + 1;
        int z0 = fl(Math.min(z - hw, z - hw + dz)) - 1, z1 = fl(Math.max(z + hw, z + hw + dz)) + 1;
        nBoxes = 0;
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                for (int bz = z0; bz <= z1; bz++) {
                    if (boxes.length < (nBoxes + 4) * 6) boxes = java.util.Arrays.copyOf(boxes, boxes.length * 2);
                    nBoxes = w.collisionBoxes(bx, by, bz, boxes, nBoxes);
                }
    }

    private double clip(int axis, double d) {
        double hw = kind.halfWidth, h = kind.height;
        double px0 = x - hw, px1 = x + hw, py0 = y, py1 = y + h, pz0 = z - hw, pz1 = z + hw;
        final double e = 1e-5;
        for (int k = 0; k < nBoxes; k++) {
            int o = k * 6;
            double bx0 = boxes[o], by0 = boxes[o + 1], bz0 = boxes[o + 2], bx1 = boxes[o + 3], by1 = boxes[o + 4], bz1 = boxes[o + 5];
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

    private void move(World w, double dx, double dy, double dz, boolean canStep) {
        gather(w, dx, dy + (canStep ? 0.6 : 0), dz);
        double sx = x, sy = y, sz = z;
        double cy = clip(1, dy);
        y += cy;
        double cx = clip(0, dx);
        x += cx;
        double cz = clip(2, dz);
        z += cz;
        boolean blocked = cx != dx || cz != dz;
        boolean landed = cy != dy && dy < 0;
        boolean bumped = cy != dy && dy > 0;
        if (blocked && canStep) {   // step onto slabs and stairs
            double ax = x, ay = y, az = z;
            x = sx; y = sy; z = sz;
            double up = clip(1, 0.6);
            y += up;
            double qx = clip(0, dx);
            x += qx;
            double qz = clip(2, dz);
            z += qz;
            double want = -up + Math.min(0, dy);
            double down = clip(1, want);
            y += down;
            if (qx * qx + qz * qz > cx * cx + cz * cz + 1e-6) {
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
