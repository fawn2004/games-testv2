package com.blockbuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** All animals: updating, natural spawning by biome, saving, picking and building their models. */
final class Mobs {
    private static final int MAGIC = 0x42424D31;
    static final int NATURAL_CAP = 18;
    static final double DESPAWN = 100, DRAW_DIST = 72;

    final List<Mob> list = new ArrayList<>();
    private final World world;
    private final Random rnd = new Random();
    private float spawnTimer;
    private int frame;

    Mobs(World world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ simulation

    void update(float dt, double px, double py, double pz) {
        frame++;
        Iterator<Mob> it = list.iterator();
        int i = 0;
        while (it.hasNext()) {
            Mob m = it.next();
            double dx = m.x - px, dz = m.z - pz;
            if (!m.persistent && dx * dx + dz * dz > DESPAWN * DESPAWN) {
                it.remove();
                continue;
            }
            int bx = (int) Math.floor(m.x), bz = (int) Math.floor(m.z);
            // Only simulate animals whose surroundings are loaded.
            if (!world.isLoaded(bx, bz) || !world.isLoaded(bx + 8, bz) || !world.isLoaded(bx - 8, bz)
                    || !world.isLoaded(bx, bz + 8) || !world.isLoaded(bx, bz - 8)) continue;
            m.update(dt, world, px, pz);
            if ((frame + i++) % 30 == 0 || m.lightAge > 60) m.relight(world);
            m.lightAge++;
        }
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = 0.5f;
            trySpawn(px, pz);
        }
    }

    Mob add(Animal a, double x, double y, double z, boolean persistent) {
        Mob m = new Mob(a, x, y, z, rnd.nextFloat() * 6.283f, persistent, rnd.nextLong());
        list.add(m);
        return m;
    }

    private static boolean ground(int b) {
        return b == Blocks.GRASS || b == Blocks.FOREST_FLOOR || b == Blocks.COARSE_DIRT || b == Blocks.SNOW
                || b == Blocks.SAND || b == Blocks.MUD || b == Blocks.DIRT || b == Blocks.RED_SAND;
    }

    /** Top block a mob could stand on at a column, or -1. */
    private int surface(int x, int z) {
        for (int y = World.SY - 2; y > 1; y--) {
            int b = world.get(x, y, z);
            if (b == Blocks.AIR || Blocks.shape(b) == Shapes.CROSS || Blocks.shape(b) == Shapes.CARPET) continue;
            if (Blocks.isLeaves(b)) return -1;    // in a tree canopy
            return y;
        }
        return -1;
    }

    private void trySpawn(double px, double pz) {
        int natural = 0;
        for (Mob m : list) {
            if (m.persistent) continue;
            double dx = m.x - px, dz = m.z - pz;
            if (dx * dx + dz * dz < 80 * 80) natural++;
        }
        if (natural >= NATURAL_CAP) return;
        double a = rnd.nextDouble() * Math.PI * 2, d = 22 + rnd.nextDouble() * 36;
        int x = (int) Math.floor(px + Math.cos(a) * d), z = (int) Math.floor(pz + Math.sin(a) * d);
        Chunk c = world.chunk(x >> 4, z >> 4);
        if (c == null) return;
        int y = surface(x, z);
        if (y < 0) return;
        int top = world.get(x, y, z);
        if (!ground(top) || world.solidAt(x, y + 1, z) || world.solidAt(x, y + 2, z)) return;
        Biome biome = Biome.ALL.get(c.biome[(x & 15) * 16 + (z & 15)]);
        boolean village = world.gen.villageNear(x, z, 10) != null;
        boolean water = false;
        for (int dx = -3; dx <= 3 && !water; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (Blocks.isWater(world.get(x + dx, y, z + dz)) || Blocks.isWater(world.get(x + dx, y + 1, z + dz))) { water = true; break; }
        boolean snowy = top == Blocks.SNOW || biome.snowy;
        boolean forest = biome.treeChance >= 0.012f;

        // Weighted pick of the animals that suit this spot.
        float[] w = new float[Animal.ALL.size()];
        float total = 0;
        for (Animal an : Animal.ALL) {
            float wt = 0;
            if ((an.climates & (1 << biome.climate)) != 0) wt = 1;
            if (an.snowOnly) wt = snowy ? 3 : 0;
            if (an.nearWater) wt = water ? wt * 3 + (an.swampLover && biome.swamp ? 3 : 0) : 0;
            if (an.swampLover && biome.swamp) wt += 2;
            if (an.farm) wt *= village ? 4 : (forest ? 0.4f : 1.2f);
            else if (village) wt *= 0.3f;
            if (an.id == Animal.BEAR || an.id == Animal.DEER || an.id == Animal.FOX) wt *= forest ? 1.5f : 0.4f;
            if (snowy && !an.snowOnly && an.id != Animal.RABBIT && an.id != Animal.FOX) wt *= 0.2f;
            w[an.id] = wt;
            total += wt;
        }
        if (total <= 0) return;
        float t = rnd.nextFloat() * total;
        Animal pick = null;
        for (Animal an : Animal.ALL) {
            t -= w[an.id];
            if (t <= 0) { pick = an; break; }
        }
        if (pick == null) return;
        int n = pick.minGroup + rnd.nextInt(pick.maxGroup - pick.minGroup + 1);
        for (int i = 0; i < n; i++) {
            int gx = x + rnd.nextInt(5) - 2, gz = z + rnd.nextInt(5) - 2;
            int gy = surface(gx, gz);
            if (gy < 0 || world.solidAt(gx, gy + 1, gz) || world.solidAt(gx, gy + 2, gz)) continue;
            if (!ground(world.get(gx, gy, gz))) continue;
            add(pick, gx + 0.5, gy + 1, gz + 0.5, false);
        }
    }

    /** Nearest animal along a ray (closer than maxDist), or null. */
    Mob pick(double ox, double oy, double oz, float dx, float dy, float dz, double maxDist, double[] distOut) {
        Mob best = null;
        double bd = maxDist;
        for (Mob m : list) {
            double d = m.rayHit(ox, oy, oz, dx, dy, dz, bd);
            if (d >= 0 && d < bd) { bd = d; best = m; }
        }
        distOut[0] = bd;
        return best;
    }

    // ------------------------------------------------------------------ saving (egg-placed animals only)

    void save(File dir) {
        if (dir == null) return;
        dir.mkdirs();
        File dst = new File(dir, "animals.dat"), tmp = new File(dir, "animals.tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            int n = 0;
            for (Mob m : list) if (m.persistent) n++;
            out.writeInt(MAGIC);
            out.writeInt(n);
            for (Mob m : list) {
                if (!m.persistent) continue;
                out.writeInt(m.kind.id);
                out.writeDouble(m.x);
                out.writeDouble(m.y);
                out.writeDouble(m.z);
                out.writeFloat(m.yaw);
            }
        } catch (Exception e) {
            return;
        }
        if (!tmp.renameTo(dst)) {
            dst.delete();
            tmp.renameTo(dst);
        }
    }

    void load(File dir) {
        if (dir == null) return;
        File f = new File(dir, "animals.dat");
        if (!f.exists()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC) return;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int kind = in.readInt();
                double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                float yaw = in.readFloat();
                if (kind < 0 || kind >= Animal.ALL.size()) continue;
                Mob m = add(Animal.ALL.get(kind), x, y, z, true);
                m.yaw = m.targetYaw = yaw;
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ models

    private float[] buf = new float[6 * 36 * 40 * 20];
    private int size;
    private final float[] cx = new float[8], cy = new float[8], cz = new float[8];
    private final int[] idx = new int[4];

    /**
     * Builds camera-relative triangles (x, y, z, r, g, b; colour already lit, linear) for
     * every animal near the camera. Returns the number of floats.
     */
    int buildMeshes(double px, double py, double pz, float lookX, float lookZ) {
        size = 0;
        for (Mob m : list) {
            double dx = m.x - px, dz = m.z - pz;
            double d2 = dx * dx + dz * dz;
            if (d2 > DRAW_DIST * DRAW_DIST) continue;
            if (d2 > 9 && dx * lookX + dz * lookZ < -2) continue;   // behind the camera
            model(m, (float) dx, (float) (m.y - py), (float) dz);
        }
        return size;
    }

    float[] data() {
        return buf;
    }

    private void model(Mob m, float ox, float oy, float oz) {
        float sy = (float) Math.sin(m.yaw), cyw = (float) Math.cos(m.yaw);
        // local +z = forward (sin yaw, -cos yaw); local +x = (-cos yaw, -sin yaw) keeps it a proper rotation
        float fX = sy, fZ = -cyw, rX = -cyw, rZ = -sy;
        float swing = (float) Math.sin(m.walkPhase) * Math.min(1f, (float) Math.sqrt(m.vx * m.vx + m.vz * m.vz) / Math.max(0.5f, m.kind.speed)) * 0.7f;
        float hind = m.kind.hops ? (m.onGround ? 0f : -0.7f) : swing;
        float bob = m.moving ? (float) Math.sin(m.walkPhase * 2) * 0.02f : 0f;
        float tail = (float) Math.sin(m.walkPhase * 1.5f + m.x) * 0.03f;
        float sun = m.sun, sky = m.sky;
        List<Animal.Part> parts = (m.variant && m.kind.id == Animal.DEER) ? m.kind.partsAlt : m.kind.parts;
        for (Animal.Part p : parts) {
            float angle = 0, dyo = 0, dxo = 0;
            switch (p.kind) {
                case Animal.LEG_A: angle = swing; break;
                case Animal.LEG_B: angle = -swing; break;
                case Animal.HIND_A: case Animal.HIND_B: angle = hind; break;
                case Animal.HEAD: dyo = bob; break;
                case Animal.TAIL: dxo = tail; break;
                default: break;
            }
            float pivY = p.y1, pivZ = (p.z0 + p.z1) * 0.5f;
            float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
            for (int k = 0; k < 8; k++) {
                float lx = (k & 1) != 0 ? p.x1 : p.x0, ly = (k & 2) != 0 ? p.y1 : p.y0, lz = (k & 4) != 0 ? p.z1 : p.z0;
                if (angle != 0) {
                    float yy = ly - pivY, zz = lz - pivZ;
                    ly = pivY + yy * ca - zz * sa;
                    lz = pivZ + yy * sa + zz * ca;
                }
                lx += dxo;
                ly += dyo;
                cx[k] = ox + lx * rX + lz * fX;
                cy[k] = oy + ly;
                cz[k] = oz + lx * rZ + lz * fZ;
            }
            float cr = ((p.color >> 16) & 255) / 255f, cg = ((p.color >> 8) & 255) / 255f, cb = (p.color & 255) / 255f;
            cr *= cr; cg *= cg; cb *= cb;   // to linear
            for (int f = 0; f < 6; f++) {
                int[] dn = ChunkMesher.DIR[f];
                float nx = dn[0] * rX + dn[2] * fX, ny = dn[1], nz = dn[0] * rZ + dn[2] * fZ;
                float ndl = Math.max(0f, nx * ChunkMesher.SUN_X + ny * ChunkMesher.SUN_Y + nz * ChunkMesher.SUN_Z) * sun;
                float hemi = 0.62f + 0.38f * ny;
                float skyL = hemi * (0.3f + 0.7f * sky);
                float lr = 1.95f * ndl + 0.26f * skyL + 0.03f;
                float lg = 1.55f * ndl + 0.34f * skyL + 0.03f;
                float lb = 1.05f * ndl + 0.52f * skyL + 0.04f;
                int[][] vs = ChunkMesher.VERTS[f];
                int[] idx = this.idx;
                for (int i = 0; i < 4; i++) idx[i] = vs[i][0] | (vs[i][1] << 1) | (vs[i][2] << 2);
                if (size + 36 > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                vert(idx[0], cr * lr, cg * lg, cb * lb); vert(idx[1], cr * lr, cg * lg, cb * lb); vert(idx[2], cr * lr, cg * lg, cb * lb);
                vert(idx[0], cr * lr, cg * lg, cb * lb); vert(idx[2], cr * lr, cg * lg, cb * lb); vert(idx[3], cr * lr, cg * lg, cb * lb);
            }
        }
    }

    private void vert(int k, float r, float g, float b) {
        float[] d = buf;
        int s = size;
        d[s] = cx[k]; d[s + 1] = cy[k]; d[s + 2] = cz[k]; d[s + 3] = r; d[s + 4] = g; d[s + 5] = b;
        size = s + 6;
    }
}
