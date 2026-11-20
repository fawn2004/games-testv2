package com.blockbuilder;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements MenuView.Listener {
    private GLSurfaceView glView;   // non-null while a world is being played
    private World world;
    private Player player;
    private InputState input;
    private WorldStore.Meta current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            win.setAttributes(lp);
        }
        WorldStore.migrateLegacy(this);
        showMenu();
    }

    private void showMenu() {
        setContentView(new MenuView(this, this));
        hideSystemUi();
    }

    @Override
    public void onPlay(WorldStore.Meta meta) {
        if (glView != null) return;
        current = meta;
        world = new World();
        player = new Player();
        input = new InputState();
        if (SaveManager.load(WorldStore.dataFile(this, meta), world, player, input)) {
            meta.seed = world.seed;
        } else {
            world.generate(meta.seed, meta.flat);
            player.spawn(world);
        }
        meta.lastPlayed = System.currentTimeMillis();
        WorldStore.saveMeta(this, meta);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setPreserveEGLContextOnPause(true);
        glView.setRenderer(new GameRenderer(world, player, input));

        HudView hud = new HudView(this, input, this::exitToMenu);
        FrameLayout root = new FrameLayout(this);
        root.addView(glView);
        root.addView(hud);
        setContentView(root);
        hideSystemUi();
    }

    private void saveGame() {
        if (glView == null || current == null) return;
        glView.onPause(); // blocks until the GL thread has paused, so the world is safe to read
        SaveManager.save(WorldStore.dataFile(this, current), world, player, input);
        current.lastPlayed = System.currentTimeMillis();
        WorldStore.saveMeta(this, current);
    }

    private void exitToMenu() {
        if (glView == null) return;
        saveGame();
        glView = null;
        world = null;
        player = null;
        input = null;
        current = null;
        showMenu();
    }

    @Override
    public void onBackPressed() {
        if (glView != null) exitToMenu();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveGame();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // The app went to the background: save and go back to the world list,
        // so opening the app again always starts at the menu.
        if (glView != null) exitToMenu();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
