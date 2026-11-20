package com.blockbuilder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Touch controls and 2D overlay drawn on top of the 3D view. */
final class HudView extends View {
    private static final int SLOTS = 9;

    private final InputState in;
    private final float dp;
    private final Bitmap[] icons;
    private final Runnable onMenu;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint();
    private final RectF rect = new RectF();

    // Layout
    private float menuX, menuY, menuR;
    private float jumpX, jumpY, jumpR, downX, downY, downR, modeX, modeY, modeR, flyX, flyY, flyR;
    private float slot, barX, barY;
    private float invX, invY, invCell, invTabY, invTabH, invTop, invBottom;
    private static final int INV_COLS = 9, INV_ROWS = 2;
    private int invTab;

    // State
    private boolean invOpen;
    private int joyId = -1, lookId = -1, jumpId = -1, downId = -1;
    private float joyCx, joyCy, joyKx, joyKy, joyR;
    private float lookLastX, lookLastY, lookStartX, lookStartY;
    private long lookStartT;
    private boolean lookMoved, holdFired;
    private String toast;
    private long toastUntil;

    private final Runnable holdBreak = new Runnable() {
        @Override
        public void run() {
            if (lookId == -1 || lookMoved) return;
            holdFired = true;
            in.actions.add(InputState.ACT_BREAK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            postDelayed(this, 260);
        }
    };

    HudView(Context ctx, InputState in, Runnable onMenu) {
        super(ctx);
        this.in = in;
        this.onMenu = onMenu;
        icons = BlockIcons.get(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setShadowLayer(3 * dp, 0, 1 * dp, 0xFF000000);
        iconPaint.setFilterBitmap(false);
        showToast("Left: move   Right: look   Tap: build/break   Hold: break", 7000);
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        jumpR = 44 * dp;
        jumpX = w - 88 * dp;
        jumpY = h - 92 * dp;
        downR = 34 * dp;
        downX = jumpX;
        downY = jumpY - jumpR - downR - 14 * dp;
        modeR = 34 * dp;
        modeX = jumpX - jumpR - modeR - 18 * dp;
        modeY = h - 58 * dp;
        flyR = 28 * dp;
        flyX = w - 48 * dp;
        flyY = 48 * dp;
        joyR = 58 * dp;

        int n = SLOTS + 1;
        slot = Math.max(30 * dp, Math.min(52 * dp, (w - 2 * 235 * dp) / n));
        barX = w / 2f - slot * n / 2f;
        float maxRight = modeX - modeR - 10 * dp;
        if (barX + slot * n > maxRight) barX = Math.max(8 * dp, maxRight - slot * n);
        barY = h - slot - 8 * dp;

        invCell = Math.min(62 * dp, Math.min((w - 48 * dp) / INV_COLS, (h - 150 * dp) / INV_ROWS));
        invTabH = 38 * dp;
        float panelH = 40 * dp + invTabH + 10 * dp + invCell * INV_ROWS + 12 * dp;
        invTop = (h - panelH) / 2f;
        invBottom = invTop + panelH;
        invTabY = invTop + 40 * dp;
        invX = w / 2f - invCell * INV_COLS / 2f;
        invY = invTabY + invTabH + 10 * dp;
        menuR = 26 * dp;
        menuX = 46 * dp;
        menuY = 46 * dp;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();

        // Crosshair
        float cx = w / 2f, cy = h / 2f, a = 10 * dp;
        stroke.setColor(0xAA000000);
        stroke.setStrokeWidth(4 * dp);
        c.drawLine(cx - a, cy, cx + a, cy, stroke);
        c.drawLine(cx, cy - a, cx, cy + a, stroke);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(2 * dp);
        c.drawLine(cx - a, cy, cx + a, cy, stroke);
        c.drawLine(cx, cy - a, cx, cy + a, stroke);

        // Joystick
        if (joyId != -1) {
            circle(c, joyCx, joyCy, joyR, 0x33000000, 0x88FFFFFF);
            circle(c, joyKx, joyKy, joyR * 0.45f, 0x88FFFFFF, 0xCCFFFFFF);
        } else {
            circle(c, 100 * dp, h - 100 * dp, joyR, 0x22000000, 0x44FFFFFF);
            circle(c, 100 * dp, h - 100 * dp, joyR * 0.45f, 0x33FFFFFF, 0x55FFFFFF);
        }

        // Buttons
        button(c, jumpX, jumpY, jumpR, in.flying ? "UP" : "JUMP", jumpId != -1);
        if (in.flying) button(c, downX, downY, downR, "DOWN", downId != -1);
        button(c, modeX, modeY, modeR, in.breakMode ? "BREAK" : "BUILD", in.breakMode);
        button(c, flyX, flyY, flyR, "FLY", in.flying);
        button(c, menuX, menuY, menuR, "MENU", false);

        // Hotbar
        for (int i = 0; i <= SLOTS; i++) {
            float x = barX + i * slot;
            rect.set(x + 2 * dp, barY + 2 * dp, x + slot - 2 * dp, barY + slot - 2 * dp);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x77000000);
            c.drawRoundRect(rect, 6 * dp, 6 * dp, fill);
            if (i < SLOTS) {
                drawIcon(c, in.hotbar[i], rect);
                if (i == in.selected) {
                    stroke.setColor(0xFFFFFFFF);
                    stroke.setStrokeWidth(3 * dp);
                    c.drawRoundRect(rect, 6 * dp, 6 * dp, stroke);
                }
            } else {
                text.setTextSize(slot * 0.45f);
                c.drawText("+", rect.centerX(), rect.centerY() + slot * 0.16f, text);
            }
        }

        // Toast
        long now = System.currentTimeMillis();
        if (toast != null && now < toastUntil) {
            text.setTextSize(16 * dp);
            c.drawText(toast, w / 2f, barY - 14 * dp, text);
        }

        if (invOpen) drawInventory(c, w, h, now);
    }

    private void drawInventory(Canvas c, int w, int h, long now) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xAA000000);
        c.drawRect(0, 0, w, h, fill);
        float left = invX - 12 * dp, right = invX + invCell * INV_COLS + 12 * dp;
        rect.set(left, invTop, right, invBottom);
        fill.setColor(0xEE2A2F3A);
        c.drawRoundRect(rect, 12 * dp, 12 * dp, fill);
        text.setTextSize(17 * dp);
        c.drawText("Slot " + (in.selected + 1) + ": " + Blocks.NAMES[in.hotbar[in.selected]],
                w / 2f, invTop + 27 * dp, text);

        // Tabs
        int nt = Blocks.CATEGORY_NAMES.length;
        float tw = (right - left - 16 * dp) / nt;
        text.setTextSize(Math.min(14 * dp, tw * 0.16f));
        for (int t = 0; t < nt; t++) {
            float x = left + 8 * dp + t * tw;
            rect.set(x + 2 * dp, invTabY, x + tw - 2 * dp, invTabY + invTabH);
            fill.setColor(t == invTab ? 0xFF4CAF50 : 0xFF3A4150);
            c.drawRoundRect(rect, 8 * dp, 8 * dp, fill);
            c.drawText(Blocks.CATEGORY_NAMES[t], rect.centerX(), rect.centerY() + text.getTextSize() * 0.35f, text);
        }

        // Blocks of the current tab
        int[] cat = Blocks.CATEGORIES[invTab];
        for (int i = 0; i < cat.length; i++) {
            float x = invX + (i % INV_COLS) * invCell, y = invY + (i / INV_COLS) * invCell;
            rect.set(x + 3 * dp, y + 3 * dp, x + invCell - 3 * dp, y + invCell - 3 * dp);
            fill.setColor(in.hotbar[in.selected] == cat[i] ? 0xFF56607A : 0xFF3A4150);
            c.drawRoundRect(rect, 6 * dp, 6 * dp, fill);
            drawIcon(c, cat[i], rect);
        }
    }

    private void drawIcon(Canvas c, int block, RectF r) {
        Bitmap b = icons[block];
        if (b == null) return;
        float pad = r.width() * 0.1f;
        rect2.set(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad);
        c.drawBitmap(b, null, rect2, iconPaint);
    }

    private final RectF rect2 = new RectF();

    private void circle(Canvas c, float x, float y, float r, int fillColor, int strokeColor) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        c.drawCircle(x, y, r, fill);
        stroke.setColor(strokeColor);
        stroke.setStrokeWidth(2 * dp);
        c.drawCircle(x, y, r, stroke);
    }

    private void button(Canvas c, float x, float y, float r, String label, boolean active) {
        circle(c, x, y, r, active ? 0x99FFFFFF : 0x55000000, 0xAAFFFFFF);
        text.setTextSize(Math.min(16 * dp, r * 0.42f));
        text.setColor(active ? 0xFF202020 : 0xFFFFFFFF);
        if (active) text.setShadowLayer(0, 0, 0, 0);
        c.drawText(label, x, y + text.getTextSize() * 0.35f, text);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(3 * dp, 0, 1 * dp, 0xFF000000);
    }

    private void showToast(String msg, long ms) {
        toast = msg;
        toastUntil = System.currentTimeMillis() + ms;
        postInvalidateDelayed(ms + 50);
    }

    // ------------------------------------------------------------------ touch

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r * 1.3f;
    }

    private int hotbarSlotAt(float x, float y) {
        if (y < barY || y > barY + slot || x < barX || x > barX + slot * (SLOTS + 1)) return -1;
        return (int) ((x - barX) / slot);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int ai = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerDown(e.getPointerId(ai), e.getX(ai), e.getY(ai));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) pointerMove(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(e.getPointerId(ai), false);
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) pointerUp(e.getPointerId(i), true);
                break;
        }
        invalidate();
        return true;
    }

    private void pointerDown(int id, float x, float y) {
        if (invOpen) {
            inventoryTap(x, y);
            return;
        }
        int s = hotbarSlotAt(x, y);
        if (s >= 0) {
            if (s == SLOTS) {
                invOpen = true;
                releaseMovement();
            } else {
                in.selected = s;
                showToast(Blocks.NAMES[in.hotbar[s]], 1500);
            }
            haptic();
        } else if (inCircle(x, y, menuX, menuY, menuR)) {
            releaseMovement();
            haptic();
            post(onMenu);
        } else if (inCircle(x, y, jumpX, jumpY, jumpR)) {
            jumpId = id;
            in.jump = true;
        } else if (in.flying && inCircle(x, y, downX, downY, downR)) {
            downId = id;
            in.down = true;
        } else if (inCircle(x, y, modeX, modeY, modeR)) {
            in.breakMode = !in.breakMode;
            showToast(in.breakMode ? "Break mode: tap to break blocks" : "Build mode: tap to place blocks", 1800);
            haptic();
        } else if (inCircle(x, y, flyX, flyY, flyR)) {
            in.flying = !in.flying;
            showToast(in.flying ? "Flying on" : "Flying off", 1200);
            haptic();
        } else if (x < getWidth() * 0.45f && joyId == -1) {
            joyId = id;
            joyCx = joyKx = x;
            joyCy = joyKy = y;
        } else if (lookId == -1) {
            lookId = id;
            lookLastX = lookStartX = x;
            lookLastY = lookStartY = y;
            lookStartT = System.currentTimeMillis();
            lookMoved = false;
            holdFired = false;
            postDelayed(holdBreak, 420);
        }
    }

    private void pointerMove(int id, float x, float y) {
        if (id == joyId) {
            float dx = x - joyCx, dy = y - joyCy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > joyR) {
                dx = dx / len * joyR;
                dy = dy / len * joyR;
            }
            joyKx = joyCx + dx;
            joyKy = joyCy + dy;
            in.moveX = dx / joyR;
            in.moveY = -dy / joyR;
        } else if (id == lookId) {
            float sens = 0.0055f / dp;
            in.addLook((x - lookLastX) * sens, (y - lookLastY) * sens);
            lookLastX = x;
            lookLastY = y;
            float tx = x - lookStartX, ty = y - lookStartY;
            if (tx * tx + ty * ty > (12 * dp) * (12 * dp)) lookMoved = true;
        }
    }

    private void pointerUp(int id, boolean cancel) {
        if (id == joyId) {
            joyId = -1;
            in.moveX = 0;
            in.moveY = 0;
        } else if (id == lookId) {
            lookId = -1;
            removeCallbacks(holdBreak);
            if (!cancel && !lookMoved && !holdFired && System.currentTimeMillis() - lookStartT < 400) {
                in.actions.add(in.breakMode ? InputState.ACT_BREAK : InputState.ACT_PLACE);
                haptic();
            }
        } else if (id == jumpId) {
            jumpId = -1;
            in.jump = false;
        } else if (id == downId) {
            downId = -1;
            in.down = false;
        }
    }

    private void releaseMovement() {
        joyId = lookId = jumpId = downId = -1;
        in.moveX = in.moveY = 0;
        in.jump = in.down = false;
        removeCallbacks(holdBreak);
    }

    private void inventoryTap(float x, float y) {
        float left = invX - 12 * dp, right = invX + invCell * INV_COLS + 12 * dp;
        if (x < left || x > right || y < invTop || y > invBottom) {
            invOpen = false; // tapped outside the panel
            return;
        }
        if (y >= invTabY && y <= invTabY + invTabH) {
            int nt = Blocks.CATEGORY_NAMES.length;
            float tw = (right - left - 16 * dp) / nt;
            int t = (int) Math.floor((x - left - 8 * dp) / tw);
            if (t >= 0 && t < nt) {
                invTab = t;
                haptic();
            }
            return;
        }
        int col = (int) Math.floor((x - invX) / invCell), row = (int) Math.floor((y - invY) / invCell);
        if (x >= invX && col < INV_COLS && y >= invY && row >= 0 && row < INV_ROWS) {
            int[] cat = Blocks.CATEGORIES[invTab];
            int i = row * INV_COLS + col;
            if (i < cat.length) {
                in.hotbar[in.selected] = cat[i];
                showToast(Blocks.NAMES[cat[i]], 1500);
                haptic();
                invOpen = false;
            }
        }
    }

    private void haptic() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
