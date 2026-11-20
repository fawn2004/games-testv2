package com.blockbuilder;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Keeps a list of saved worlds: one ".meta" (name, seed, dates) and one ".dat" (blocks) per world. */
final class WorldStore {
    static final class Meta {
        String id, name;
        long seed, created, lastPlayed;
        boolean flat;
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "worlds");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static File dataFile(Context ctx, Meta m) {
        return new File(dir(ctx), m.id + ".dat");
    }

    private static File metaFile(Context ctx, String id) {
        return new File(dir(ctx), id + ".meta");
    }

    /** All worlds, most recently played first. */
    static List<Meta> list(Context ctx) {
        List<Meta> out = new ArrayList<>();
        File[] files = dir(ctx).listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (!n.endsWith(".meta")) continue;
                Meta m = read(f, n.substring(0, n.length() - 5));
                if (m != null) out.add(m);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        return out;
    }

    private static Meta read(File f, String id) {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
            Meta m = new Meta();
            m.id = id;
            m.name = p.getProperty("name", "World");
            m.seed = Long.parseLong(p.getProperty("seed", "0"));
            m.created = Long.parseLong(p.getProperty("created", "0"));
            m.lastPlayed = Long.parseLong(p.getProperty("lastPlayed", "0"));
            m.flat = Boolean.parseBoolean(p.getProperty("flat", "false"));
            return m;
        } catch (Exception e) {
            Log.e("BlockBuilder", "bad world meta " + f, e);
            return null;
        }
    }

    static void saveMeta(Context ctx, Meta m) {
        Properties p = new Properties();
        p.setProperty("name", m.name);
        p.setProperty("seed", Long.toString(m.seed));
        p.setProperty("created", Long.toString(m.created));
        p.setProperty("lastPlayed", Long.toString(m.lastPlayed));
        p.setProperty("flat", Boolean.toString(m.flat));
        try (FileOutputStream out = new FileOutputStream(metaFile(ctx, m.id))) {
            p.store(out, null);
        } catch (Exception e) {
            Log.e("BlockBuilder", "meta save failed", e);
        }
    }

    static Meta create(Context ctx, String name, long seed, boolean flat) {
        Meta m = new Meta();
        long now = System.currentTimeMillis();
        String id = "w" + now;
        while (metaFile(ctx, id).exists()) id = id + "x";
        m.id = id;
        m.name = name;
        m.seed = seed;
        m.flat = flat;
        m.created = now;
        m.lastPlayed = now;
        saveMeta(ctx, m);
        return m;
    }

    static void delete(Context ctx, Meta m) {
        dataFile(ctx, m).delete();
        new File(dataFile(ctx, m).getPath() + ".tmp").delete();
        metaFile(ctx, m.id).delete();
    }

    /** Moves the single save from version 1.0 into the world list. */
    static void migrateLegacy(Context ctx) {
        File old = new File(ctx.getFilesDir(), "world.dat");
        if (!old.exists()) return;
        Meta m = create(ctx, "My World", 0, false);
        if (!old.renameTo(dataFile(ctx, m))) delete(ctx, m);
    }

    private WorldStore() {
    }
}
