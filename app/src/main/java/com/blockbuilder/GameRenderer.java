package com.blockbuilder;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Runs the game loop and draws the world on the GL thread. */
final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String VS =
            "uniform mat4 uMVP; uniform vec3 uCam; uniform float uFogNear; uniform float uFogFar;\n" +
            "attribute vec3 aPos; attribute vec2 aUV; attribute float aLight;\n" +
            "varying vec2 vUV; varying float vLight; varying float vFog;\n" +
            "void main(){\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "  vUV = aUV; vLight = aLight;\n" +
            "  float d = length(aPos - uCam);\n" +
            "  vFog = clamp((d - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);\n" +
            "}\n";
    private static final String FS =
            "precision mediump float;\n" +
            "uniform sampler2D uTex; uniform vec3 uFogColor; uniform float uAlphaCut;\n" +
            "varying vec2 vUV; varying float vLight; varying float vFog;\n" +
            "void main(){\n" +
            "  vec4 c = texture2D(uTex, vUV);\n" +
            "  if (c.a < uAlphaCut) discard;\n" +
            "  gl_FragColor = vec4(mix(c.rgb * vLight, uFogColor, vFog), c.a);\n" +
            "}\n";
    private static final String LINE_VS =
            "uniform mat4 uMVP; attribute vec3 aPos;\n" +
            "void main(){ gl_Position = uMVP * vec4(aPos, 1.0); }\n";
    private static final String LINE_FS =
            "precision mediump float; uniform vec4 uColor;\n" +
            "void main(){ gl_FragColor = uColor; }\n";

    private static final int NCH = World.CX * World.CZ;

    private final World world;
    private final Player player;
    private final InputState input;
    private final ChunkMesher mesher = new ChunkMesher();
    private final FloatBuilder solidBuf = new FloatBuilder(), waterBuf = new FloatBuilder();
    private final int[] vboSolid = new int[NCH], vboWater = new int[NCH];
    private final int[] cntSolid = new int[NCH], cntWater = new int[NCH];

    private int prog, aPos, aUV, aLight, uMVP, uCam, uFogColor, uFogNear, uFogFar, uTex, uAlphaCut;
    private int lineProg, laPos, luMVP, luColor;
    private int tex;
    private final float[] proj = new float[16], view = new float[16], mvp = new float[16];
    private final float[] dir = new float[3];
    private final int[] hit = new int[6];
    private FloatBuffer upload;
    private final FloatBuffer lineBuf = ByteBuffer.allocateDirect(24 * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private long lastNanos;

    GameRenderer(World world, Player player, InputState input) {
        this.world = world;
        this.player = player;
        this.input = input;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        prog = link(VS, FS);
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aUV = GLES20.glGetAttribLocation(prog, "aUV");
        aLight = GLES20.glGetAttribLocation(prog, "aLight");
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
        uCam = GLES20.glGetUniformLocation(prog, "uCam");
        uFogColor = GLES20.glGetUniformLocation(prog, "uFogColor");
        uFogNear = GLES20.glGetUniformLocation(prog, "uFogNear");
        uFogFar = GLES20.glGetUniformLocation(prog, "uFogFar");
        uTex = GLES20.glGetUniformLocation(prog, "uTex");
        uAlphaCut = GLES20.glGetUniformLocation(prog, "uAlphaCut");

        lineProg = link(LINE_VS, LINE_FS);
        laPos = GLES20.glGetAttribLocation(lineProg, "aPos");
        luMVP = GLES20.glGetUniformLocation(lineProg, "uMVP");
        luColor = GLES20.glGetUniformLocation(lineProg, "uColor");

        tex = TextureAtlas.upload();

        // Context was (re)created: every GPU buffer is gone.
        for (int i = 0; i < NCH; i++) {
            vboSolid[i] = vboWater[i] = 0;
            cntSolid[i] = cntWater[i] = 0;
        }
        world.markAllDirty();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        lastNanos = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        Matrix.perspectiveM(proj, 0, 70f, w / (float) Math.max(1, h), 0.05f, 200f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        float ex = player.x, ey = player.eyeY(), ez = player.z;
        player.lookDir(dir);
        processActions(ex, ey, ez);
        player.update(dt, world, input);
        rebuildChunks();

        ex = player.x; ey = player.eyeY(); ez = player.z;
        player.lookDir(dir);
        Matrix.setLookAtM(view, 0, ex, ey, ez, ex + dir[0], ey + dir[1], ez + dir[2], 0, 1, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);

        float fr, fg, fb, fogNear, fogFar;
        if (player.headInWater) {
            fr = 0.12f; fg = 0.26f; fb = 0.55f; fogNear = 1f; fogFar = 18f;
        } else {
            fr = 0.56f; fg = 0.77f; fb = 0.96f; fogNear = 36f; fogFar = 76f;
        }
        GLES20.glClearColor(fr, fg, fb, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(prog);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniform3f(uCam, ex, ey, ez);
        GLES20.glUniform3f(uFogColor, fr, fg, fb);
        GLES20.glUniform1f(uFogNear, fogNear);
        GLES20.glUniform1f(uFogFar, fogFar);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glEnableVertexAttribArray(aLight);

        // Opaque + cut-out pass
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUniform1f(uAlphaCut, 0.5f);
        for (int i = 0; i < NCH; i++) {
            if (cntSolid[i] > 0 && visible(i, ex, ez, fogFar)) draw(vboSolid[i], cntSolid[i]);
        }

        // Water pass
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glUniform1f(uAlphaCut, 0.01f);
        for (int i = 0; i < NCH; i++) {
            if (cntWater[i] > 0 && visible(i, ex, ez, fogFar)) draw(vboWater[i], cntWater[i]);
        }
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
        GLES20.glDisableVertexAttribArray(aLight);

        // Selection outline
        if (world.raycast(ex, ey, ez, dir[0], dir[1], dir[2], 6f, hit)) {
            drawOutline(hit[0], hit[1], hit[2]);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private boolean visible(int i, float ex, float ez, float fogFar) {
        float cx = (i / World.CZ) * World.CS + World.CS * 0.5f;
        float cz = (i % World.CZ) * World.CS + World.CS * 0.5f;
        float dx = cx - ex, dz = cz - ez;
        float d2 = dx * dx + dz * dz;
        float lim = fogFar + World.CS;
        if (d2 > lim * lim) return false;
        // Skip chunks well behind the camera.
        float fx = dir[0], fz = dir[2];
        float fl = (float) Math.sqrt(fx * fx + fz * fz);
        if (fl < 0.2f) return true; // looking straight up/down
        return (dx * fx + dz * fz) / fl > -World.CS * 1.2f;
    }

    private void draw(int vbo, int count) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 24, 12);
        GLES20.glVertexAttribPointer(aLight, 1, GLES20.GL_FLOAT, false, 24, 20);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
    }

    private void processActions(float ex, float ey, float ez) {
        Integer a;
        while ((a = input.actions.poll()) != null) {
            if (!world.raycast(ex, ey, ez, dir[0], dir[1], dir[2], 6f, hit)) continue;
            if (a == InputState.ACT_BREAK) {
                if (world.get(hit[0], hit[1], hit[2]) != Blocks.BEDROCK) {
                    world.set(hit[0], hit[1], hit[2], Blocks.AIR);
                }
            } else if (a == InputState.ACT_PLACE) {
                int px = hit[3], py = hit[4], pz = hit[5];
                if (!World.inside(px, py, pz)) continue;
                int cur = world.get(px, py, pz);
                if (cur != Blocks.AIR && cur != Blocks.WATER) continue;
                int b = input.selectedBlock();
                if (Blocks.isSolid(b) && player.intersects(px, py, pz)) continue;
                world.set(px, py, pz, b);
            }
        }
    }

    private void rebuildChunks() {
        long start = System.nanoTime();
        int built = 0;
        while (true) {
            // Pick the nearest dirty chunk.
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < NCH; i++) {
                if (!world.chunkDirty[i]) continue;
                float cx = (i / World.CZ) * World.CS + 8 - player.x;
                float cz = (i % World.CZ) * World.CS + 8 - player.z;
                float d = cx * cx + cz * cz;
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best < 0) return;
            world.chunkDirty[best] = false;
            mesher.build(world, best / World.CZ, best % World.CZ, solidBuf, waterBuf);
            cntSolid[best] = uploadMesh(vboSolid, best, solidBuf);
            cntWater[best] = uploadMesh(vboWater, best, waterBuf);
            built++;
            if (built >= 2 && System.nanoTime() - start > 8_000_000L) return;
        }
    }

    private int uploadMesh(int[] vbos, int i, FloatBuilder fb) {
        if (fb.size == 0) return 0;
        if (vbos[i] == 0) {
            int[] id = new int[1];
            GLES20.glGenBuffers(1, id, 0);
            vbos[i] = id[0];
        }
        if (upload == null || upload.capacity() < fb.size) {
            upload = ByteBuffer.allocateDirect(Math.max(fb.size, 1 << 16) * 4 * 2)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        upload.clear();
        upload.put(fb.data, 0, fb.size);
        upload.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[i]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fb.size * 4, upload, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return fb.size / 6;
    }

    private void drawOutline(int x, int y, int z) {
        float e = 0.003f;
        float x0 = x - e, y0 = y - e, z0 = z - e, x1 = x + 1 + e, y1 = y + 1 + e, z1 = z + 1 + e;
        float[] v = {
                x0, y0, z0, x1, y0, z0, x1, y0, z0, x1, y0, z1, x1, y0, z1, x0, y0, z1, x0, y0, z1, x0, y0, z0,
                x0, y1, z0, x1, y1, z0, x1, y1, z0, x1, y1, z1, x1, y1, z1, x0, y1, z1, x0, y1, z1, x0, y1, z0,
                x0, y0, z0, x0, y1, z0, x1, y0, z0, x1, y1, z0, x1, y0, z1, x1, y1, z1, x0, y0, z1, x0, y1, z1,
        };
        lineBuf.clear();
        lineBuf.put(v);
        lineBuf.position(0);
        GLES20.glUseProgram(lineProg);
        GLES20.glUniformMatrix4fv(luMVP, 1, false, mvp, 0);
        GLES20.glUniform4f(luColor, 0.05f, 0.05f, 0.05f, 0.85f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glLineWidth(3f);
        GLES20.glEnableVertexAttribArray(laPos);
        GLES20.glVertexAttribPointer(laPos, 3, GLES20.GL_FLOAT, false, 12, lineBuf);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 24);
        GLES20.glDisableVertexAttribArray(laPos);
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            Log.e("BlockBuilder", "Shader error: " + log);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    private static int link(String vs, String fs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs));
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("Link failed: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }
}
