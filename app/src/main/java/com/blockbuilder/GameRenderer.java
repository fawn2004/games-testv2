package com.blockbuilder;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Runs the game loop and draws the world on the GL thread. */
final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";

    /** Sky / haze model in linear colour (usable in vertex or fragment shaders). */
    private static final String SKY_FN =
            "uniform vec3 uSun; uniform float uUnder; uniform float uFogStart; uniform float uFogDen;\n" +
            "vec3 skyColor(vec3 d, float glow) {\n" +
            "  if (uUnder > 0.5) return vec3(0.015, 0.06, 0.11);\n" +
            "  float t = clamp(d.y, 0.0, 1.0);\n" +
            "  vec3 zen = vec3(0.06, 0.17, 0.52);\n" +
            "  vec3 hor = vec3(0.40, 0.53, 0.74);\n" +
            "  vec3 c = mix(hor, zen, pow(t, 0.5));\n" +
            "  if (d.y < 0.0) c = mix(hor, vec3(0.26, 0.32, 0.40), clamp(-d.y * 2.5, 0.0, 1.0));\n" +
            "  float s = max(dot(d, uSun), 0.0);\n" +
            "  c += vec3(1.0, 0.82, 0.58) * (pow(s, 6.0) * 0.30 + pow(s, 48.0) * 0.45) * glow;\n" +
            "  return c;\n" +
            "}\n" +
            "float fogAmount(float d) {\n" +
            "  float k = max(d - uFogStart, 0.0) * uFogDen;\n" +
            "  return 1.0 - exp(-k * k);\n" +
            "}\n";

    /** Exposure, slight desaturation and filmic tone mapping, output in display gamma. */
    private static final String TONE =
            "uniform float uExposure;\n" +
            "vec3 tonemap(vec3 x) {\n" +
            "  x *= uExposure;\n" +
            "  float l = dot(x, vec3(0.2126, 0.7152, 0.0722));\n" +
            "  x = mix(vec3(l), x, 1.0);\n" +
            "  x = max(x, 0.0);\n" +
            "  x = clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);\n" +
            "  return sqrt(x);\n" +
            "}\n";

    private static final String VS = SKY_FN +
            "uniform mat4 uMVP; uniform vec3 uOffset; uniform vec2 uWorldBase; uniform float uTimeV;\n" +
            "attribute vec3 aPos; attribute vec2 aUV; attribute vec3 aColor; attribute float aFlags;\n" +
            "varying vec2 vUV; varying vec3 vLight; varying vec3 vFogCol; varying float vFog;\n" +
            "varying vec3 vWorld; varying float vUnder;\n" +
            "void main(){\n" +
            "  float under = step(1.5, aFlags);\n" +
            "  float sway = aFlags - 2.0 * under;\n" +
            "  vec3 p = aPos;\n" +
            "  if (sway > 0.0) {\n" +                                  // wind
            "    vec2 w = aPos.xz + uWorldBase;\n" +
            "    float ph = uTimeV * 1.7 + w.x * 0.55 + w.y * 0.35;\n" +
            "    p.x += (sin(ph) * 0.06 + sin(ph * 2.3 + w.y) * 0.025) * sway;\n" +
            "    p.z += (cos(ph * 0.8 + w.x) * 0.05) * sway;\n" +
            "  }\n" +
            "  gl_Position = uMVP * vec4(p + uOffset, 1.0);\n" +
            "  vUV = aUV; vLight = aColor; vUnder = under;\n" +
            "  vWorld = vec3(aPos.x + uWorldBase.x, aPos.y, aPos.z + uWorldBase.y);\n" +
            "  vec3 toV = aPos + uOffset;\n" +
            "  float d = length(toV);\n" +
            "  vFog = fogAmount(d);\n" +
            "  vFogCol = skyColor(toV / max(d, 0.001), 0.6);\n" +
            "}\n";
    private static final String FS = PRECISION + TONE +
            "uniform sampler2D uTex; uniform float uAlphaCut; uniform float uTime;\n" +
            "varying vec2 vUV; varying vec3 vLight; varying vec3 vFogCol; varying float vFog;\n" +
            "varying vec3 vWorld; varying float vUnder;\n" +
            "float caustic(vec2 p, float t) {\n" +
            "  vec2 q = p * 1.35;\n" +
            "  float a = sin(q.x * 2.1 + t * 1.2 + sin(q.y * 1.7 + t * 0.9) * 1.6);\n" +
            "  float b = sin(q.y * 2.4 - t * 1.0 + sin(q.x * 1.9 - t * 0.7) * 1.6);\n" +
            "  float v = 1.0 - abs((a + b) * 0.5);\n" +
            "  return v * v * v * v;\n" +
            "}\n" +
            "void main(){\n" +
            "  vec4 c = texture2D(uTex, vUV);\n" +
            "  if (c.a < uAlphaCut) discard;\n" +
            "  vec3 col = c.rgb * c.rgb * vLight;\n" +                     // to linear, light
            "  if (vUnder > 0.5) {\n" +                                    // light dancing on the bed
            "    float k = caustic(vWorld.xz + vWorld.y * 0.3, uTime);\n" +
            "    col *= vec3(0.62, 0.86, 0.95) * (0.8 + 1.4 * k);\n" +
            "  }\n" +
            "  col = mix(col, vFogCol, vFog);\n" +
            "  gl_FragColor = vec4(tonemap(col), c.a);\n" +
            "}\n";

    private static final String WATER_VS =
            "uniform mat4 uMVP; uniform vec3 uOffset; uniform vec2 uWorldBase;\n" +
            "attribute vec3 aPos; attribute vec2 aUV; attribute vec3 aColor;\n" +
            "varying vec3 vPos; varying vec3 vRel; varying vec3 vInfo;\n" +
            "void main(){\n" +
            "  gl_Position = uMVP * vec4(aPos + uOffset, 1.0);\n" +
            "  vPos = vec3(aPos.x + uWorldBase.x, aPos.y, aPos.z + uWorldBase.y); vRel = aPos + uOffset; vInfo = aColor;\n" +
            "}\n";
    private static final String WATER_FS = PRECISION + SKY_FN + TONE +
            "uniform float uTime;\n" +
            "varying vec3 vPos; varying vec3 vRel; varying vec3 vInfo;\n" +   // info: top-face flag, sky, sun
            "void main(){\n" +
            "  vec3 toFrag = vRel;\n" +
            "  float dist = length(toFrag);\n" +
            "  vec3 V = toFrag / dist;\n" +
            "  vec3 n;\n" +
            "  if (vInfo.r > 0.5) {\n" +
            "    vec2 p = vPos.xz; float t = uTime;\n" +
            "    float dx = cos(p.x * 1.7 + t * 1.3) * 0.5 + cos((p.x + p.y) * 2.9 - t * 1.7) * 0.35 + cos(p.x * 5.3 - p.y * 3.1 + t * 2.3) * 0.15;\n" +
            "    float dz = cos(p.y * 1.9 - t * 1.1) * 0.5 + cos((p.x - p.y) * 3.3 + t * 1.5) * 0.35 + cos(p.y * 6.1 + p.x * 2.2 - t * 2.7) * 0.15;\n" +
            "    n = normalize(vec3(dx * 0.09, 1.0, dz * 0.09));\n" +
            "  } else {\n" +
            "    n = normalize(vec3(-V.x, 0.0, -V.z));\n" +
            "  }\n" +
            "  if (dot(n, V) > 0.0) n = -n;\n" +                  // seen from below
            "  float cosT = max(dot(-V, n), 0.0);\n" +
            "  float fres = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);\n" +
            "  vec3 R = reflect(V, n); R.y = abs(R.y);\n" +
            "  vec3 refl = skyColor(R, 1.0) * (0.3 + 0.7 * vInfo.g);\n" +
            "  float spec = pow(max(dot(R, uSun), 0.0), 240.0) * vInfo.b;\n" +
            "  vec3 deep = vec3(0.03, 0.11, 0.16) * (0.35 + 0.6 * vInfo.g + 0.9 * vInfo.b);\n" +
            "  vec3 col = mix(deep, refl, fres) + vec3(1.0, 0.88, 0.65) * spec * 8.0;\n" +
            "  col = mix(col, skyColor(V, 0.6), fogAmount(dist));\n" +
            "  float a = mix(0.42, 0.95, fres);\n" +
            "  gl_FragColor = vec4(tonemap(col), a);\n" +
            "}\n";

    private static final String SKY_VS =
            "uniform mat4 uInvVP; uniform vec3 uCam;\n" +
            "attribute vec2 aClip;\n" +
            "varying vec3 vDir;\n" +
            "void main(){\n" +
            "  vec4 p = uInvVP * vec4(aClip, 1.0, 1.0);\n" +
            "  vDir = p.xyz / p.w - uCam;\n" +
            "  gl_Position = vec4(aClip, 0.999, 1.0);\n" +
            "}\n";
    private static final String SKY_FS = PRECISION + SKY_FN + TONE +
            "varying vec3 vDir;\n" +
            "void main(){\n" +
            "  vec3 d = normalize(vDir);\n" +
            "  vec3 c = skyColor(d, 1.0);\n" +
            "  float s = dot(d, uSun);\n" +
            "  if (uUnder < 0.5) c += vec3(14.0, 12.0, 9.0) * smoothstep(0.9991, 0.9996, s);\n" +
            "  gl_FragColor = vec4(tonemap(c), 1.0);\n" +
            "}\n";

    private static final String LINE_VS =
            "uniform mat4 uMVP; attribute vec3 aPos;\n" +
            "void main(){ gl_Position = uMVP * vec4(aPos, 1.0); }\n";
    private static final String LINE_FS =
            "precision mediump float; uniform vec4 uColor;\n" +
            "void main(){ gl_FragColor = uColor; }\n";

    /** Chunks drawn around the player, loaded (for neighbours), and kept before unloading. */
    static final int RENDER_R = 7, LOAD_R = 9, UNLOAD_R = 11;
    private static final float EXPOSURE = 0.62f;
    private static final int STRIDE_BYTES = FloatBuilder.STRIDE * 4;
    private static final float FOG_START = 44f, FOG_DEN = 0.027f;

    private final World world;
    private final Player player;
    private final InputState input;
    private final File dir;

    // ---- background work
    private static ThreadFactory factory(final String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        };
    }

    private final ExecutorService genPool = Executors.newFixedThreadPool(2, factory("bb-gen"));
    private final ExecutorService meshPool = Executors.newFixedThreadPool(2, factory("bb-mesh"));
    private final ExecutorService nearPool = Executors.newSingleThreadExecutor(factory("bb-mesh-near"));
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor(factory("bb-io"));
    private final Set<Long> pendingGen = java.util.Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final AtomicInteger meshesInFlight = new AtomicInteger();
    private final ConcurrentLinkedQueue<MeshResult> results = new ConcurrentLinkedQueue<>();
    // (ThreadLocal.withInitial needs Android 8, so subclass instead.)
    private final ThreadLocal<ChunkMesher> meshers = new ThreadLocal<ChunkMesher>() {
        @Override
        protected ChunkMesher initialValue() {
            return new ChunkMesher();
        }
    };
    private final ThreadLocal<FloatBuilder[]> builders = new ThreadLocal<FloatBuilder[]>() {
        @Override
        protected FloatBuilder[] initialValue() {
            return new FloatBuilder[]{new FloatBuilder(), new FloatBuilder()};
        }
    };
    private volatile boolean shutDown;

    private static final class MeshResult {
        final Chunk chunk;
        final int version;
        final float[] solid, water;

        MeshResult(Chunk chunk, int version, float[] solid, float[] water) {
            this.chunk = chunk;
            this.version = version;
            this.solid = solid;
            this.water = water;
        }
    }

    /** GPU buffers of one chunk (GL thread only). */
    private static final class GLChunk {
        final int cx, cz;
        int vboSolid, vboWater, nSolid, nWater, version = -1;

        GLChunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    private final HashMap<Long, GLChunk> gl = new HashMap<>();
    /** Latest mesh request per chunk (GL thread only). */
    private final HashMap<Long, Integer> meshVersion = new HashMap<>();
    /** Chunk offsets sorted by distance, for loading nearest first. */
    private static final int[][] OFFSETS;

    static {
        java.util.ArrayList<int[]> list = new java.util.ArrayList<>();
        for (int dx = -LOAD_R; dx <= LOAD_R; dx++)
            for (int dz = -LOAD_R; dz <= LOAD_R; dz++)
                if (dx * dx + dz * dz <= LOAD_R * LOAD_R + 1) list.add(new int[]{dx, dz});
        java.util.Collections.sort(list, (a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        OFFSETS = list.toArray(new int[0][]);
    }

    // ---- GL state
    private int prog, aPos, aUV, aColor, aFlags, uMVP, uOffset, uBase, uTex, uAlphaCut;
    private int waterProg, waPos, waUV, waColor, wuMVP, wuOffset, wuBase, wuTime;
    private int skyProg, sClip, sInvVP, sCam;
    private int lineProg, laPos, luMVP, luColor;
    private int tex;
    private final float[] invVP = new float[16];
    private final FloatBuffer quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1});
    private final long startNanos = System.nanoTime();
    private final float[] proj = new float[16], view = new float[16], mvp = new float[16];
    private final float[] dir3 = new float[3];
    private final int[] hit = new int[7];
    private final double[] hitPos = new double[3];
    private final float[] shapeBoxes = new float[6 * 4];
    private FloatBuffer upload;
    private final FloatBuffer lineBuf = ByteBuffer.allocateDirect(24 * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private long lastNanos;
    private int frame;

    GameRenderer(World world, Player player, InputState input, File dir) {
        this.world = world;
        this.player = player;
        this.input = input;
        this.dir = dir;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        prog = link(VS, FS);
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aUV = GLES20.glGetAttribLocation(prog, "aUV");
        aColor = GLES20.glGetAttribLocation(prog, "aColor");
        aFlags = GLES20.glGetAttribLocation(prog, "aFlags");
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
        uOffset = GLES20.glGetUniformLocation(prog, "uOffset");
        uBase = GLES20.glGetUniformLocation(prog, "uWorldBase");
        uTex = GLES20.glGetUniformLocation(prog, "uTex");
        uAlphaCut = GLES20.glGetUniformLocation(prog, "uAlphaCut");

        waterProg = link(WATER_VS, WATER_FS);
        waPos = GLES20.glGetAttribLocation(waterProg, "aPos");
        waUV = GLES20.glGetAttribLocation(waterProg, "aUV");
        waColor = GLES20.glGetAttribLocation(waterProg, "aColor");
        wuMVP = GLES20.glGetUniformLocation(waterProg, "uMVP");
        wuOffset = GLES20.glGetUniformLocation(waterProg, "uOffset");
        wuBase = GLES20.glGetUniformLocation(waterProg, "uWorldBase");
        wuTime = GLES20.glGetUniformLocation(waterProg, "uTime");

        skyProg = link(SKY_VS, SKY_FS);
        sClip = GLES20.glGetAttribLocation(skyProg, "aClip");
        sInvVP = GLES20.glGetUniformLocation(skyProg, "uInvVP");
        sCam = GLES20.glGetUniformLocation(skyProg, "uCam");
        quad.position(0);

        lineProg = link(LINE_VS, LINE_FS);
        laPos = GLES20.glGetAttribLocation(lineProg, "aPos");
        luMVP = GLES20.glGetUniformLocation(lineProg, "uMVP");
        luColor = GLES20.glGetUniformLocation(lineProg, "uColor");

        tex = TextureAtlas.upload();

        // Context was (re)created: every GPU buffer is gone.
        gl.clear();
        for (Chunk c : world.chunks.values()) c.dirty.set(true);

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
        Matrix.perspectiveM(proj, 0, 70f, w / (float) Math.max(1, h), 0.05f, 300f);
    }

    // ------------------------------------------------------------------ streaming

    private static int floorDiv16(double v) {
        return (int) Math.floor(v / 16.0);
    }

    private void streamChunks() {
        int pcx = floorDiv16(player.x), pcz = floorDiv16(player.z);
        // Load nearest first, a few at a time.
        for (int[] o : OFFSETS) {
            if (pendingGen.size() >= 6) break;
            final int cx = pcx + o[0], cz = pcz + o[1];
            if (!World.insideXZ(cx * 16, cz * 16)) continue;
            long key = Chunk.key(cx, cz);
            if (world.chunks.containsKey(key) || !pendingGen.add(key)) continue;
            genPool.execute(() -> {
                try {
                    if (shutDown) return;
                    int px = floorDiv16(player.x), pz = floorDiv16(player.z);
                    if (Math.abs(cx - px) > UNLOAD_R || Math.abs(cz - pz) > UNLOAD_R) return; // walked away
                    Chunk c = dir != null ? SaveManager.loadChunk(dir, world.gen, cx, cz) : null;
                    if (c == null) c = world.gen.generate(cx, cz);
                    world.addChunk(c);
                } catch (Throwable t) {
                    Log.e("BlockBuilder", "chunk generation failed " + cx + "," + cz, t);
                } finally {
                    pendingGen.remove(key);
                }
            });
        }
        // Unload far chunks now and then.
        if (frame % 30 == 0) {
            for (Chunk c : world.chunks.values()) {
                if (Math.abs(c.cx - pcx) <= UNLOAD_R && Math.abs(c.cz - pcz) <= UNLOAD_R) continue;
                world.removeChunk(c);
                meshVersion.remove(c.key());
                if (c.modified && dir != null) ioPool.execute(() -> SaveManager.saveChunk(dir, c));
            }
            java.util.Iterator<java.util.Map.Entry<Long, GLChunk>> it = gl.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Long, GLChunk> e = it.next();
                if (world.chunks.containsKey(e.getKey())) continue;
                freeGL(e.getValue());
                it.remove();
            }
        }
    }

    private boolean neighboursLoaded(Chunk c) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (world.chunk(c.cx + dx, c.cz + dz) == null) return false;
        return true;
    }

    private void scheduleMeshes() {
        int pcx = floorDiv16(player.x), pcz = floorDiv16(player.z);
        for (int[] o : OFFSETS) {
            if (o[0] * o[0] + o[1] * o[1] > (RENDER_R + 1) * (RENDER_R + 1)) break;
            Chunk c = world.chunk(pcx + o[0], pcz + o[1]);
            if (c == null || c.meshing || !c.dirty.get() || !neighboursLoaded(c)) continue;
            boolean near = Math.abs(o[0]) <= 1 && Math.abs(o[1]) <= 1;
            if (!near && meshesInFlight.get() >= 4) continue;
            c.dirty.set(false);
            c.meshing = true;
            Integer prev = meshVersion.get(c.key());
            final int version = prev == null ? 1 : prev + 1;
            meshVersion.put(c.key(), version);
            meshesInFlight.incrementAndGet();
            (near ? nearPool : meshPool).execute(() -> {
                try {
                    if (shutDown) return;
                    FloatBuilder[] fb = builders.get();
                    meshers.get().build(world, c, fb[0], fb[1]);
                    results.add(new MeshResult(c, version, Arrays.copyOf(fb[0].data, fb[0].size),
                            Arrays.copyOf(fb[1].data, fb[1].size)));
                } catch (Throwable t) {
                    Log.e("BlockBuilder", "meshing failed", t);
                    c.dirty.set(true);
                } finally {
                    c.meshing = false;
                    meshesInFlight.decrementAndGet();
                }
            });
        }
    }

    private void uploadResults() {
        long start = System.nanoTime();
        MeshResult r;
        while ((r = results.poll()) != null) {
            Chunk c = r.chunk;
            if (world.chunk(c.cx, c.cz) != c) continue; // unloaded meanwhile
            GLChunk g = gl.get(c.key());
            if (g == null) {
                g = new GLChunk(c.cx, c.cz);
                gl.put(c.key(), g);
            }
            if (r.version < g.version) continue;
            g.version = r.version;
            g.vboSolid = uploadMesh(g.vboSolid, r.solid);
            g.nSolid = r.solid.length / FloatBuilder.STRIDE;
            g.vboWater = uploadMesh(g.vboWater, r.water);
            g.nWater = r.water.length / FloatBuilder.STRIDE;
            if (System.nanoTime() - start > 6_000_000L) break;
        }
    }

    private void freeGL(GLChunk g) {
        int[] ids = {g.vboSolid, g.vboWater};
        GLES20.glDeleteBuffers(2, ids, 0);
    }

    /** Saves the player and every changed chunk (call while the GL thread is paused). */
    void saveAll() {
        if (dir == null) return;
        for (Chunk c : world.chunks.values()) if (c.modified) SaveManager.saveChunk(dir, c);
        SaveManager.savePlayer(dir, player, input);
    }

    void shutdown() {
        shutDown = true;
        genPool.shutdownNow();
        meshPool.shutdownNow();
        nearPool.shutdownNow();
        ioPool.shutdown();
        try {
            ioPool.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        frame++;

        streamChunks();
        int bx = (int) Math.floor(player.x), bz = (int) Math.floor(player.z);
        boolean ready = world.isLoaded(bx, bz);
        input.loading = !ready;
        player.lookDir(dir3);
        if (ready) {
            processActions();
            player.update(dt, world, input);
        }
        scheduleMeshes();
        uploadResults();
        if (frame % 20 == 0) {
            Village v = world.gen.villageNear(bx, bz, 0);
            input.place = v != null ? v.name() : world.gen.biomeName(bx, bz);
        }

        double px = player.x, py = player.eyeY(), pz = player.z;
        player.lookDir(dir3);
        // The camera sits at the origin; everything is drawn relative to it.
        Matrix.setLookAtM(view, 0, 0, 0, 0, dir3[0], dir3[1], dir3[2], 0, 1, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);

        boolean under = player.headInWater;
        float fogStart = under ? 0f : FOG_START, fogDen = under ? 0.11f : FOG_DEN;
        float fogFar = RENDER_R * 16f;
        float time = ((System.nanoTime() - startNanos) / 1e9f) % 628.3185f;
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        // Sky
        Matrix.invertM(invVP, 0, mvp, 0);
        GLES20.glUseProgram(skyProg);
        common(skyProg, under, fogStart, fogDen);
        GLES20.glUniformMatrix4fv(sInvVP, 1, false, invVP, 0);
        GLES20.glUniform3f(sCam, 0, 0, 0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glEnableVertexAttribArray(sClip);
        GLES20.glVertexAttribPointer(sClip, 2, GLES20.GL_FLOAT, false, 8, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(sClip);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Opaque + cut-out pass
        GLES20.glUseProgram(prog);
        common(prog, under, fogStart, fogDen);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniform1f(uAlphaCut, 0.5f);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uTime"), time);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uTimeV"), time);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glEnableVertexAttribArray(aFlags);
        for (GLChunk g : gl.values()) {
            if (g.nSolid == 0 || !visible(g, px, pz, fogFar)) continue;
            chunkUniforms(uOffset, uBase, g, px, py, pz);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.vboSolid);
            GLES20.glVertexAttribPointer(aFlags, 1, GLES20.GL_FLOAT, false, STRIDE_BYTES, 32);
            draw(g.vboSolid, g.nSolid, aPos, aUV, aColor);
        }
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
        GLES20.glDisableVertexAttribArray(aColor);
        GLES20.glDisableVertexAttribArray(aFlags);

        // Water pass
        GLES20.glUseProgram(waterProg);
        common(waterProg, under, fogStart, fogDen);
        GLES20.glUniformMatrix4fv(wuMVP, 1, false, mvp, 0);
        GLES20.glUniform1f(wuTime, time);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnableVertexAttribArray(waPos);
        if (waUV >= 0) GLES20.glEnableVertexAttribArray(waUV);
        GLES20.glEnableVertexAttribArray(waColor);
        for (GLChunk g : gl.values()) {
            if (g.nWater == 0 || !visible(g, px, pz, fogFar)) continue;
            chunkUniforms(wuOffset, wuBase, g, px, py, pz);
            draw(g.vboWater, g.nWater, waPos, waUV, waColor);
        }
        GLES20.glDisableVertexAttribArray(waPos);
        if (waUV >= 0) GLES20.glDisableVertexAttribArray(waUV);
        GLES20.glDisableVertexAttribArray(waColor);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Selection outline
        if (ready && world.raycast(px, py, pz, dir3[0], dir3[1], dir3[2], 6f, hit, hitPos)) {
            drawOutline(hit[0], hit[1], hit[2], px, py, pz);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** Chunk position relative to the camera, plus a wrapped world position for animation. */
    private void chunkUniforms(int offsetLoc, int baseLoc, GLChunk g, double px, double py, double pz) {
        double ox = g.cx * 16.0, oz = g.cz * 16.0;
        GLES20.glUniform3f(offsetLoc, (float) (ox - px), (float) -py, (float) (oz - pz));
        GLES20.glUniform2f(baseLoc, (float) (((g.cx * 16) % 4096 + 4096) % 4096), (float) (((g.cz * 16) % 4096 + 4096) % 4096));
    }

    private boolean visible(GLChunk g, double px, double pz, float fogFar) {
        float dx = (float) (g.cx * 16.0 + 8 - px), dz = (float) (g.cz * 16.0 + 8 - pz);
        float d2 = dx * dx + dz * dz;
        float lim = fogFar + 12;
        if (d2 > lim * lim) return false;
        float fx = dir3[0], fz = dir3[2];
        float fl = (float) Math.sqrt(fx * fx + fz * fz);
        if (fl < 0.2f) return true;
        return (dx * fx + dz * fz) / fl > -16 * 1.2f;
    }

    private void draw(int vbo, int count, int pos, int uv, int color) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0);
        if (uv >= 0) GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, 12);
        GLES20.glVertexAttribPointer(color, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 20);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
    }

    /** Uniforms shared by the sky, terrain and water shaders. */
    private void common(int program, boolean under, float fogStart, float fogDen) {
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uSun"),
                ChunkMesher.SUN_X, ChunkMesher.SUN_Y, ChunkMesher.SUN_Z);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uUnder"), under ? 1f : 0f);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFogStart"), fogStart);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFogDen"), fogDen);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uExposure"), EXPOSURE);
    }

    // ------------------------------------------------------------------ building

    private void processActions() {
        double ex = player.x, ey = player.eyeY(), ez = player.z;
        Integer a;
        while ((a = input.actions.poll()) != null) {
            if (!world.raycast(ex, ey, ez, dir3[0], dir3[1], dir3[2], 6f, hit, hitPos)) continue;
            int hx = hit[0], hy = hit[1], hz = hit[2];
            int hb = world.get(hx, hy, hz), hm = world.getMeta(hx, hy, hz);
            int hShape = Blocks.shape(hb);
            if (a == InputState.ACT_BREAK) {
                if (hb == Blocks.BEDROCK) continue;
                world.set(hx, hy, hz, hb == Blocks.SEAGRASS ? Blocks.WATER : Blocks.AIR);
                if (hShape == Shapes.DOOR) {   // remove the other half too
                    int oy = (hm & Shapes.UPPER) != 0 ? hy - 1 : hy + 1;
                    if (world.get(hx, oy, hz) == hb) world.set(hx, oy, hz, Blocks.AIR);
                }
                // Plants and carpets resting on top go with it; so do vines hanging below.
                int up = world.get(hx, hy + 1, hz), upShape = Blocks.shape(up);
                if ((upShape == Shapes.CROSS && up != Blocks.VINES) || upShape == Shapes.CARPET)
                    world.set(hx, hy + 1, hz, up == Blocks.SEAGRASS ? Blocks.WATER : Blocks.AIR);
                for (int vy = hy - 1; vy > 0 && world.get(hx, vy, hz) == Blocks.VINES; vy--)
                    world.set(hx, vy, hz, Blocks.AIR);
            } else if (a == InputState.ACT_PLACE) {
                if (hShape == Shapes.DOOR || hShape == Shapes.TRAPDOOR) {
                    toggle(hx, hy, hz, hb, hm);
                    continue;
                }
                int b = input.selectedBlock();
                int shape = Blocks.shape(b);
                int face = hit[6];
                // Two matching slabs make a full block.
                if (shape == Shapes.SLAB && hb == b) {
                    boolean top = (hm & Shapes.TOP) != 0;
                    if ((!top && face == 2) || (top && face == 3)) {
                        world.set(hx, hy, hz, Blocks.fullOf(b));
                        continue;
                    }
                }
                int px = hit[3], py = hit[4], pz = hit[5];
                if (hShape == Shapes.CROSS && hb != b) {   // building onto a plant replaces it
                    px = hx; py = hy; pz = hz;
                }
                if (!World.insideY(py) || !World.insideXZ(px, pz)) continue;
                int cur = world.get(px, py, pz);
                boolean replaceable = cur == Blocks.AIR || cur == Blocks.WATER || Blocks.shape(cur) == Shapes.CROSS;
                if (!replaceable) {
                    if (shape == Shapes.SLAB && cur == b) world.set(px, py, pz, Blocks.fullOf(b));
                    continue;
                }
                if (Blocks.isSolid(b) && player.intersects(px, py, pz)) continue;

                int facing = Shapes.facingFromLook(dir3[0], dir3[2]);
                boolean upperHalf = face == 3 || (face != 2 && hitPos[1] - Math.floor(hitPos[1]) > 0.5);
                int meta = 0;
                if (shape == Shapes.STAIRS || shape == Shapes.TRAPDOOR) {
                    meta = facing | (upperHalf ? Shapes.TOP : 0);
                } else if (shape == Shapes.SLAB) {
                    meta = upperHalf ? Shapes.TOP : 0;
                } else if (shape == Shapes.DOOR) {
                    int above = world.get(px, py + 1, pz);
                    if (!World.insideY(py + 1) || (above != Blocks.AIR && !Blocks.isWater(above)
                            && Blocks.shape(above) != Shapes.CROSS)) continue;
                    if (player.intersects(px, py + 1, pz)) continue;
                    world.set(px, py, pz, b, facing);
                    world.set(px, py + 1, pz, b, facing | Shapes.UPPER);
                    continue;
                }
                world.set(px, py, pz, b, meta);
            }
        }
    }

    /** Opens or closes a door (both halves) or trapdoor. */
    private void toggle(int x, int y, int z, int b, int m) {
        world.set(x, y, z, b, m ^ Shapes.OPEN);
        if (Blocks.shape(b) == Shapes.DOOR) {
            int oy = (m & Shapes.UPPER) != 0 ? y - 1 : y + 1;
            if (world.get(x, oy, z) == b) world.set(x, oy, z, b, world.getMeta(x, oy, z) ^ Shapes.OPEN);
        }
    }

    private int uploadMesh(int vbo, float[] data) {
        if (data.length == 0) return vbo;
        if (vbo == 0) {
            int[] id = new int[1];
            GLES20.glGenBuffers(1, id, 0);
            vbo = id[0];
        }
        if (upload == null || upload.capacity() < data.length) {
            upload = ByteBuffer.allocateDirect(Math.max(data.length, 1 << 16) * 4 * 2)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        upload.clear();
        upload.put(data, 0, data.length);
        upload.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, upload, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return vbo;
    }

    private void drawOutline(int x, int y, int z, double px, double py, double pz) {
        float e = 0.003f;
        int n = Shapes.boxes(world.get(x, y, z), world.getMeta(x, y, z), shapeBoxes);
        float bx0 = 1, by0 = 1, bz0 = 1, bx1 = 0, by1 = 0, bz1 = 0;
        for (int k = 0; k < n; k++) {
            int o = k * 6;
            bx0 = Math.min(bx0, shapeBoxes[o]); by0 = Math.min(by0, shapeBoxes[o + 1]); bz0 = Math.min(bz0, shapeBoxes[o + 2]);
            bx1 = Math.max(bx1, shapeBoxes[o + 3]); by1 = Math.max(by1, shapeBoxes[o + 4]); bz1 = Math.max(bz1, shapeBoxes[o + 5]);
        }
        float rx = (float) (x - px), ry = (float) (y - py), rz = (float) (z - pz);
        float x0 = rx + bx0 - e, y0 = ry + by0 - e, z0 = rz + bz0 - e, x1 = rx + bx1 + e, y1 = ry + by1 + e, z1 = rz + bz1 + e;
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
