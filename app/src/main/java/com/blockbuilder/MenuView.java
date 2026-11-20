package com.blockbuilder;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/** Title screen: list of saved worlds with play / create / delete. */
final class MenuView extends FrameLayout {
    interface Listener {
        void onPlay(WorldStore.Meta world);
    }

    private static final int GREEN = 0xFF4CAF50, GREEN_DARK = 0xFF3B8A3E;
    private static final int RED = 0xFFC94A3B, RED_DARK = 0xFFA33A2D;
    private static final int GREY = 0xFF5B6475, GREY_DARK = 0xFF485060;

    private final Listener listener;
    private final float dp;
    private final LinearLayout list;
    private final Paint paint = new Paint();
    private final Rect src = new Rect(0, 0, 16, 16);
    private final RectF dst = new RectF();
    private final Bitmap grassSide, dirt, grassIcon;
    private final float[] clouds;

    MenuView(Context ctx, Listener listener) {
        super(ctx);
        this.listener = listener;
        dp = ctx.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        paint.setFilterBitmap(false);
        grassSide = BlockIcons.tile(Blocks.T_GRASS_SIDE, false);
        dirt = BlockIcons.tile(Blocks.T_DIRT, false);
        grassIcon = BlockIcons.get(ctx)[Blocks.GRASS];
        Random r = new Random(7);
        clouds = new float[12];
        for (int i = 0; i < clouds.length; i += 3) {
            clouds[i] = r.nextFloat();
            clouds[i + 1] = 0.05f + r.nextFloat() * 0.3f;
            clouds[i + 2] = 0.6f + r.nextFloat() * 0.8f;
        }

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int panelW = (int) Math.min(540 * dp, ctx.getResources().getDisplayMetrics().widthPixels - 32 * dp);
        LayoutParams colLp = new LayoutParams(panelW, LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        colLp.topMargin = (int) (10 * dp);
        colLp.bottomMargin = (int) (12 * dp);
        addView(column, colLp);

        TextView title = new TextView(ctx);
        title.setText("BLOCK BUILDER");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(36);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(0.01f, 3 * dp, 3 * dp, 0xFF2D4A1E);
        if (android.os.Build.VERSION.SDK_INT >= 21) title.setLetterSpacing(0.08f);
        column.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(ctx);
        subtitle.setText("Select a world");
        subtitle.setTextColor(0xEEFFFFFF);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setShadowLayer(4 * dp, 0, dp, 0x88000000);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.bottomMargin = (int) (8 * dp);
        column.addView(subtitle, subLp);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackground(rounded(0xC0202634, 12));
        int pad = (int) (8 * dp);
        scroll.setPadding(pad, pad, pad, pad);
        scroll.setClipToPadding(false);
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        TextView create = button("+  Create New World", GREEN, GREEN_DARK, 17);
        create.setOnClickListener(v -> showCreateDialog());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, (int) (50 * dp));
        cLp.topMargin = (int) (10 * dp);
        column.addView(create, cLp);

        TextView version = new TextView(ctx);
        version.setText("v" + versionName(ctx));
        version.setTextColor(0xCCFFFFFF);
        version.setTextSize(12);
        version.setShadowLayer(3 * dp, 0, dp, 0xAA000000);
        LayoutParams vLp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        vLp.rightMargin = (int) (10 * dp);
        vLp.bottomMargin = (int) (6 * dp);
        addView(version, vLp);

        refresh();
    }

    private static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ---------------------------------------------------------------- world list

    void refresh() {
        list.removeAllViews();
        List<WorldStore.Meta> worlds = WorldStore.list(getContext());
        if (worlds.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No worlds yet.\nTap “Create New World” to start building!");
            empty.setTextColor(0xCCFFFFFF);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            int p = (int) (28 * dp);
            empty.setPadding(p, p, p, p);
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (WorldStore.Meta m : worlds) list.addView(row(m));
    }

    private View row(WorldStore.Meta m) {
        Context ctx = getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(pressable(0xFF343C4D, 0xFF46506A, 8));
        int p = (int) (8 * dp);
        row.setPadding(p, p, p, p);
        row.setOnClickListener(v -> listener.onPlay(m));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = (int) (6 * dp);
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(ctx);
        icon.setImageBitmap(grassIcon);
        int is = (int) (40 * dp);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(is, is);
        iconLp.rightMargin = (int) (10 * dp);
        row.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(ctx);
        name.setText(m.name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);
        TextView info = new TextView(ctx);
        String when = m.lastPlayed > 0
                ? DateUtils.getRelativeTimeSpanString(m.lastPlayed, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString()
                : "never";
        info.setText((m.flat ? "Flat" : "Normal") + "  ·  Played " + when);
        info.setTextColor(0xFFAEB7C8);
        info.setTextSize(13);
        info.setSingleLine(true);
        texts.addView(info);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView play = button("Play", GREEN, GREEN_DARK, 15);
        play.setOnClickListener(v -> listener.onPlay(m));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams((int) (76 * dp), (int) (40 * dp));
        playLp.leftMargin = (int) (8 * dp);
        row.addView(play, playLp);

        TextView del = button("Delete", RED, RED_DARK, 14);
        del.setOnClickListener(v -> confirmDelete(m));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams((int) (76 * dp), (int) (40 * dp));
        delLp.leftMargin = (int) (8 * dp);
        row.addView(del, delLp);
        return row;
    }

    // ---------------------------------------------------------------- dialogs

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void confirmDelete(WorldStore.Meta m) {
        dialog()
                .setTitle("Delete “" + m.name + "”?")
                .setMessage("This world and everything you built in it will be gone forever.")
                .setPositiveButton("Delete", (d, w) -> {
                    WorldStore.delete(getContext(), m);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreateDialog() {
        Context ctx = getContext();
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * dp);
        form.setPadding(p, (int) (8 * dp), p, 0);

        final EditText name = new EditText(ctx);
        name.setHint("World name");
        name.setText("World " + (WorldStore.list(ctx).size() + 1));
        name.setSingleLine(true);
        name.setSelectAllOnFocus(true);
        name.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));

        final EditText seed = new EditText(ctx);
        seed.setHint("Seed (optional, leave blank for random)");
        seed.setSingleLine(true);
        seed.setInputType(InputType.TYPE_CLASS_TEXT);
        seed.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        form.addView(seed, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout types = new LinearLayout(ctx);
        types.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams typesLp = new LinearLayout.LayoutParams(-1, (int) (44 * dp));
        typesLp.topMargin = (int) (12 * dp);
        form.addView(types, typesLp);
        final boolean[] flat = {false};
        final TextView normal = button("Normal terrain", GREEN, GREEN_DARK, 14);
        final TextView flatBtn = button("Flat (for building)", GREY, GREY_DARK, 14);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -1, 1f);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -1, 1f);
        half2.leftMargin = (int) (8 * dp);
        types.addView(normal, half);
        types.addView(flatBtn, half2);
        normal.setOnClickListener(v -> {
            flat[0] = false;
            normal.setBackground(pressable(GREEN, GREEN_DARK, 8));
            flatBtn.setBackground(pressable(GREY, GREY_DARK, 8));
        });
        flatBtn.setOnClickListener(v -> {
            flat[0] = true;
            flatBtn.setBackground(pressable(GREEN, GREEN_DARK, 8));
            normal.setBackground(pressable(GREY, GREY_DARK, 8));
        });

        dialog()
                .setTitle("Create New World")
                .setView(form)
                .setPositiveButton("Create", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = "World";
                    if (n.length() > 40) n = n.substring(0, 40);
                    WorldStore.Meta m = WorldStore.create(getContext(), n, parseSeed(seed.getText().toString()), flat[0]);
                    listener.onPlay(m);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static long parseSeed(String s) {
        s = s.trim();
        if (s.isEmpty()) return new Random().nextLong();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    // ---------------------------------------------------------------- styling

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * dp);
        return g;
    }

    private StateListDrawable pressable(int color, int pressed, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed, radiusDp));
        s.addState(new int[]{}, rounded(color, radiusDp));
        return s;
    }

    private TextView button(String label, int color, int pressed, int sp) {
        TextView b = new TextView(getContext());
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(pressable(color, pressed, 8));
        b.setClickable(true);
        return b;
    }

    // ---------------------------------------------------------------- background

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        Paint sky = new Paint();
        sky.setShader(new LinearGradient(0, 0, 0, h, 0xFF5FA8EC, 0xFFC4E6FF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, sky);

        Paint cloud = new Paint();
        cloud.setColor(0xDDFFFFFF);
        float u = 22 * dp;
        for (int i = 0; i < clouds.length; i += 3) {
            float cx = clouds[i] * w, cy = clouds[i + 1] * h, s = clouds[i + 2];
            c.drawRect(cx, cy, cx + 6 * u * s, cy + u * s, cloud);
            c.drawRect(cx + 1.5f * u * s, cy - 0.8f * u * s, cx + 4.5f * u * s, cy, cloud);
        }

        float t = 40 * dp;
        int cols = (int) Math.ceil(w / t) + 1;
        for (int row = 0; row < 3; row++) {
            float y = h - t * (3 - row);
            Bitmap b = row == 0 ? grassSide : dirt;
            for (int i = 0; i < cols; i++) {
                dst.set(i * t, y, i * t + t, y + t);
                c.drawBitmap(b, src, dst, paint);
            }
        }
    }
}
