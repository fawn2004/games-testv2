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

/**
 * Saves a world as a folder: player.dat (position, hotbar, settings) plus one file per
 * chunk that differs from what the generator would produce.
 */
final class SaveManager {
    private static final int PLAYER_MAGIC = 0x42425031, CHUNK_MAGIC = 0x42424331, LEGACY_MAGIC = 0x424C4B42;

    private static void log(String msg, Exception e) {
        try {
            Log.e("BlockBuilder", msg, e);
        } catch (RuntimeException ignored) {
            // not running on Android (tests)
        }
    }

    private static void replace(File tmp, File dst) {
        if (!tmp.renameTo(dst)) {
            dst.delete();
            tmp.renameTo(dst);
        }
    }

    // ------------------------------------------------------------------ player

    static void savePlayer(File dir, Player p, InputState in) {
        dir.mkdirs();
        File dst = new File(dir, "player.dat"), tmp = new File(dir, "player.tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(PLAYER_MAGIC);
            out.writeInt(1);
            out.writeDouble(p.x);
            out.writeDouble(p.y);
            out.writeDouble(p.z);
            out.writeFloat(p.yaw);
            out.writeFloat(p.pitch);
            out.writeBoolean(in.flying);
            out.writeBoolean(in.breakMode);
            out.writeInt(in.selected);
            out.writeInt(in.hotbar.length);
            for (int b : in.hotbar) out.writeInt(b);
        } catch (Exception e) {
            log("player save failed", e);
            return;
        }
        replace(tmp, dst);
    }

    static boolean loadPlayer(File dir, Player p, InputState in) {
        File f = new File(dir, "player.dat");
        if (!f.exists()) return false;
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (din.readInt() != PLAYER_MAGIC) return false;
            din.readInt();
            p.x = din.readDouble();
            p.y = din.readDouble();
            p.z = din.readDouble();
            p.yaw = din.readFloat();
            p.pitch = din.readFloat();
            in.flying = din.readBoolean();
            in.breakMode = din.readBoolean();
            in.selected = Math.max(0, Math.min(in.hotbar.length - 1, din.readInt()));
            int n = din.readInt();
            for (int i = 0; i < n; i++) {
                int b = din.readInt();
                if (i < in.hotbar.length && ((b > 0 && b < Blocks.COUNT) || Animal.isEgg(b))) in.hotbar[i] = b;
            }
            return true;
        } catch (Exception e) {
            log("player load failed", e);
            return false;
        }
    }

    // ------------------------------------------------------------------ chunks

    private static File chunkFile(File dir, int cx, int cz) {
        return new File(new File(dir, "chunks"), cx + "_" + cz + ".gz");
    }

    static void saveChunk(File dir, Chunk c) {
        File cdir = new File(dir, "chunks");
        cdir.mkdirs();
        File dst = chunkFile(dir, c.cx, c.cz), tmp = new File(cdir, c.cx + "_" + c.cz + ".tmp");
        int n = c.blocks.length;
        byte[] ids = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            ids[2 * i] = (byte) (c.blocks[i] >> 8);
            ids[2 * i + 1] = (byte) c.blocks[i];
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(tmp))))) {
            out.writeInt(CHUNK_MAGIC);
            out.writeInt(1);
            out.writeInt(c.cx);
            out.writeInt(c.cz);
            out.writeInt(World.SY);
            out.write(ids);
            out.write(c.meta);
        } catch (Exception e) {
            log("chunk save failed", e);
            return;
        }
        replace(tmp, dst);
        c.modified = false;
        c.saved = true;
    }

    /** A previously saved chunk, or null if it was never changed. */
    static Chunk loadChunk(File dir, Generator gen, int cx, int cz) {
        File f = chunkFile(dir, cx, cz);
        if (!f.exists()) return null;
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(f))))) {
            if (din.readInt() != CHUNK_MAGIC) return null;
            din.readInt();
            if (din.readInt() != cx || din.readInt() != cz) return null;
            int sy = din.readInt();
            if (sy > World.SY) return null;
            Chunk c = new Chunk(cx, cz);
            int n = 16 * 16 * sy;
            byte[] ids = new byte[n * 2];
            byte[] meta = new byte[n];
            din.readFully(ids);
            din.readFully(meta);
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                    for (int y = 0; y < sy; y++) {
                        int i = (lx * 16 + lz) * sy + y, j = Chunk.idx(lx, y, lz);
                        int b = ((ids[2 * i] & 0xFF) << 8) | (ids[2 * i + 1] & 0xFF);
                        c.blocks[j] = (char) (b < Blocks.COUNT ? b : 0);
                        c.meta[j] = meta[i];
                    }
            gen.fillColumns(c);
            c.recomputeMaxY();
            c.saved = true;
            return c;
        } catch (Exception e) {
            log("chunk load failed " + cx + "," + cz, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ worlds from versions 1.0 - 1.7

    /**
     * Converts an old single-file world (128 x 64 x 128 blocks) into chunk files and a
     * player file. The old area keeps its exact blocks; new land generates around it.
     */
    static boolean migrateLegacy(File legacy, File dir, Generator gen) {
        if (!legacy.exists()) return false;
        final int SX = 128, SY = 64, SZ = 128;
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(legacy))))) {
            if (din.readInt() != LEGACY_MAGIC) return false;
            int version = din.readInt();
            if (din.readInt() != SX || din.readInt() != SY || din.readInt() != SZ) return false;
            din.readLong(); // seed (kept in the world's meta)
            Player p = new Player();
            InputState in = new InputState();
            p.x = din.readFloat(); p.y = din.readFloat(); p.z = din.readFloat();
            p.yaw = din.readFloat(); p.pitch = din.readFloat();
            in.flying = din.readBoolean();
            in.breakMode = din.readBoolean();
            in.selected = Math.max(0, Math.min(8, din.readInt()));
            for (int i = 0; i < 9; i++) {
                int b = din.readInt();
                if (b > 0 && b < Blocks.COUNT) in.hotbar[i] = b;
            }
            int n = SX * SY * SZ;
            char[] ids = new char[n];
            byte[] meta = new byte[n];
            if (version == 1) {
                byte[] data = new byte[n];
                din.readFully(data);
                for (int i = 0; i < n; i++) ids[i] = (char) (data[i] & 0xFF);
            } else {
                byte[] data = new byte[n * 2];
                din.readFully(data);
                for (int i = 0; i < n; i++) ids[i] = (char) (((data[2 * i] & 0xFF) << 8) | (data[2 * i + 1] & 0xFF));
                din.readFully(meta);
            }
            for (int cx = 0; cx < SX / 16; cx++)
                for (int cz = 0; cz < SZ / 16; cz++) {
                    Chunk c = new Chunk(cx, cz);
                    for (int lx = 0; lx < 16; lx++)
                        for (int lz = 0; lz < 16; lz++)
                            for (int y = 0; y < SY; y++) {
                                int i = ((cx * 16 + lx) * SZ + (cz * 16 + lz)) * SY + y;
                                int b = ids[i];
                                c.blocks[Chunk.idx(lx, y, lz)] = (char) (b < Blocks.COUNT ? b : 0);
                                c.meta[Chunk.idx(lx, y, lz)] = meta[i];
                            }
                    saveChunk(dir, c);
                }
            savePlayer(dir, p, in);
        } catch (Exception e) {
            log("legacy migration failed", e);
            return false;
        }
        legacy.renameTo(new File(legacy.getPath() + ".old"));
        return true;
    }

    private SaveManager() {
    }
}
