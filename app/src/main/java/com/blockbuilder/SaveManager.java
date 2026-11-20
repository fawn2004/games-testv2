package com.blockbuilder;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Persists the world and player to app-private storage. */
final class SaveManager {
    private static final int MAGIC = 0x424C4B42, VERSION = 2;

    static void save(File dst, World w, Player p, InputState in) {
        File tmp = new File(dst.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(tmp))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(World.SX);
            out.writeInt(World.SY);
            out.writeInt(World.SZ);
            out.writeLong(w.seed);
            out.writeFloat(p.x);
            out.writeFloat(p.y);
            out.writeFloat(p.z);
            out.writeFloat(p.yaw);
            out.writeFloat(p.pitch);
            out.writeBoolean(in.flying);
            out.writeBoolean(in.breakMode);
            out.writeInt(in.selected);
            for (int b : in.hotbar) out.writeInt(b);
            // v2: 16-bit block ids, then one meta byte per block
            byte[] ids = new byte[w.blocks.length * 2];
            for (int i = 0; i < w.blocks.length; i++) {
                ids[2 * i] = (byte) (w.blocks[i] >> 8);
                ids[2 * i + 1] = (byte) w.blocks[i];
            }
            out.write(ids);
            out.write(w.meta);
        } catch (Exception e) {
            Log.e("BlockBuilder", "save failed", e);
            return;
        }
        if (!tmp.renameTo(dst)) {
            dst.delete();
            tmp.renameTo(dst);
        }
    }

    static boolean load(File f, World w, Player p, InputState in) {
        if (!f.exists()) return false;
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(f))))) {
            if (din.readInt() != MAGIC) return false;
            int version = din.readInt();
            if (version != 1 && version != 2) return false;
            if (din.readInt() != World.SX || din.readInt() != World.SY || din.readInt() != World.SZ) return false;
            long seed = din.readLong();
            float x = din.readFloat(), y = din.readFloat(), z = din.readFloat();
            float yaw = din.readFloat(), pitch = din.readFloat();
            boolean flying = din.readBoolean(), breakMode = din.readBoolean();
            int sel = din.readInt();
            int[] hb = new int[in.hotbar.length];
            for (int i = 0; i < hb.length; i++) hb[i] = din.readInt();
            int n = w.blocks.length;
            char[] ids = new char[n];
            byte[] meta = new byte[n];
            if (version == 1) {             // 1.0 - 1.3: one byte per block, no meta
                byte[] data = new byte[n];
                din.readFully(data);
                for (int i = 0; i < n; i++) ids[i] = (char) (data[i] & 0xFF);
            } else {
                byte[] data = new byte[n * 2];
                din.readFully(data);
                for (int i = 0; i < n; i++) ids[i] = (char) (((data[2 * i] & 0xFF) << 8) | (data[2 * i + 1] & 0xFF));
                din.readFully(meta);
            }
            for (char b : ids) if (b >= Blocks.COUNT) return false;
            System.arraycopy(ids, 0, w.blocks, 0, n);
            System.arraycopy(meta, 0, w.meta, 0, n);
            w.seed = seed;
            w.recomputeMaxHeight();
            w.markAllDirty();
            p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.pitch = pitch;
            in.flying = flying;
            in.breakMode = breakMode;
            in.selected = Math.max(0, Math.min(in.hotbar.length - 1, sel));
            for (int i = 0; i < hb.length; i++) {
                if (hb[i] > 0 && hb[i] < Blocks.COUNT) in.hotbar[i] = hb[i];
            }
            return true;
        } catch (Exception e) {
            Log.e("BlockBuilder", "load failed", e);
            return false;
        }
    }

    private SaveManager() {
    }
}
