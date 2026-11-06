package com.diamondrush.port;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseLongArray;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import javax.microedition.lcdui.Canvas;

/**
 * Muestra el ultimo frame publicado por el Canvas del juego y maneja los controles tactiles.
 * Las teclas pasan por pressKey/releaseKey, que garantizan un tiempo minimo de pulsacion
 * (el juego hace polling de keysPressed una vez por frame y un toque corto se perderia).
 */
public class GameView extends View implements Canvas.FrameListener {
    // codigos MIDP que cGame.serializeKey entiende (valor absoluto 1..7)
    public static final int K_UP = -1, K_DOWN = -2, K_LEFT = -3, K_RIGHT = -4, K_FIRE = -5, K_LSH = -6, K_RSH = -7, K_STAR = 42;

    private static final long MIN_HOLD_MS = 80;

    private final Canvas canvas;
    private final Paint paint = new Paint();
    private final Paint ov = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect(), dst = new Rect();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseBooleanArray keyDown = new SparseBooleanArray();
    private final SparseLongArray downAt = new SparseLongArray();
    private final SparseArray<Runnable> pendingUp = new SparseArray<Runnable>();

    private boolean tUp, tDown, tLeft, tRight, tFire, tLsh, tRsh;
    private int insetTop, insetBottom;

    // geometria calculada por layout()
    private int dx, dy, dw, dh;
    private float padR, cx, cy, ar, ax, ay, sr, lx, ly, rx, ry;
    private int stickId = -1;          // puntero que agarro el joystick (-1 = ninguno)
    private float knobX, knobY;        // posicion de la perilla mientras se arrastra
    private boolean tStar;             // boton '*' (reiniciar nivel / autodestruccion)
    private float starX, starY;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF rect = new RectF();

    // layouts de controles: 0 = joystick analogico, 1 = cruceta
    public static final int LAYOUT_STICK = 0, LAYOUT_DPAD = 1, LAYOUT_COUNT = 2;
    private static final String PREFS = "dr_controls", PREF_LAYOUT = "layout";
    private int layoutMode = LAYOUT_STICK;
    private float tgX, tgY, tgR;       // boton de cambio de layout (abajo al centro)
    private int toggleId = -1;         // puntero que esta tocando el boton de cambio

    public GameView(Context ctx, Canvas c) {
        super(ctx);
        canvas = c;
        paint.setFilterBitmap(false);   // vecino mas cercano: pixel art nitido, sin difuminar
        paint.setAntiAlias(false);
        paint.setDither(false);
        ov.setStyle(Paint.Style.FILL);
        label.setColor(0xCCFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int m = sp.getInt(PREF_LAYOUT, LAYOUT_STICK);
            layoutMode = (m >= 0 && m < LAYOUT_COUNT) ? m : LAYOUT_STICK;
        } catch (Exception ignored) { }
    }

    /** Cambia al siguiente layout de controles y lo guarda. */
    private void cycleLayout() {
        layoutMode = (layoutMode + 1) % LAYOUT_COUNT;
        stickId = -1;
        // suelta las direcciones para que no quede ninguna pegada al cambiar
        edge(false, tUp, K_UP);       tUp = false;
        edge(false, tDown, K_DOWN);   tDown = false;
        edge(false, tLeft, K_LEFT);   tLeft = false;
        edge(false, tRight, K_RIGHT); tRight = false;
        try {
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(PREF_LAYOUT, layoutMode).apply();
        } catch (Exception ignored) { }
        invalidate();
    }

    public Canvas getCanvas() { return canvas; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        canvas.setFrameListener(this);
        requestApplyInsets();
    }

    @Override protected void onDetachedFromWindow() {
        canvas.setFrameListener(null);
        releaseAll();
        super.onDetachedFromWindow();
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets w) {
        int t = w.getSystemWindowInsetTop(), b = w.getSystemWindowInsetBottom();
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout dc = w.getDisplayCutout();
            if (dc != null) {
                t = Math.max(t, dc.getSafeInsetTop());
                b = Math.max(b, dc.getSafeInsetBottom());
            }
        }
        insetTop = t;
        insetBottom = b;
        invalidate();
        return super.onApplyWindowInsets(w);
    }

    @Override public void onFrame() { postInvalidateOnAnimation(); }

    // ------------------------------------------------------------------ geometria

    /** Devuelve false si todavia no hay frame. */
    private boolean computeLayout(Bitmap bmp) {
        int vw = getWidth(), vh = getHeight();
        if (bmp == null || vw <= 0 || vh <= 0) return false;
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        boolean portrait = vh > vw;
        int availTop = portrait ? insetTop : 0;
        int availH = vh - availTop;
        float sc = Math.min((float) vw / bw, (float) availH / bh);
        dw = Math.round(bw * sc);
        dh = Math.round(bh * sc);
        dx = (vw - dw) / 2;
        dy = portrait ? availTop : (vh - dh) / 2;

        float margin = Math.min(vw, vh) * 0.04f;
        float zt = dy + dh;
        float zb = vh - insetBottom;
        float fh = zb - zt;
        if (portrait && fh >= vh * 0.25f) {
            // controles en la franja libre de abajo
            padR = Math.min(fh * 0.26f, vw * 0.17f);
            cx = margin + padR * 1.15f;
            cy = zb - margin - padR * 1.1f;
            ar = padR * 0.85f;
            ax = vw - margin - ar * 1.15f;
            ay = cy;
            sr = padR * 0.5f;
            lx = margin + sr * 1.4f;
            ly = zt + margin + sr * 1.2f;
            rx = vw - margin - sr * 1.4f;
            ry = ly;
            starX = vw / 2f;
            starY = ly;
            tgR = sr * 0.55f;
            tgX = vw / 2f;
            tgY = zb - margin - tgR;
        } else {
            // sobre el juego (landscape o pantallas muy anchas)
            padR = Math.min(dh / 5f, dw / 7f);
            cx = dx + padR + 16;
            cy = dy + dh - padR - 16;
            ar = padR * 0.95f;
            ax = dx + dw - padR - 16;
            ay = cy;
            sr = padR * 0.65f;
            lx = ax - padR * 1.35f;
            ly = ay - padR * 0.75f;
            rx = lx;
            ry = ay + padR * 0.75f;
            starX = dx + dw - sr - 16;
            starY = dy + sr + 16;
            tgR = sr * 0.55f;
            tgX = vw / 2f;
            tgY = dy + dh - tgR - 16;
        }
        return true;
    }

    // ------------------------------------------------------------------ dibujo

    @Override protected void onDraw(android.graphics.Canvas c) {
        c.drawARGB(255, 0, 0, 0);
        Bitmap bmp = canvas.getFrontBitmap();
        if (!computeLayout(bmp)) return;
        src.set(0, 0, bmp.getWidth(), bmp.getHeight());
        dst.set(dx, dy, dx + dw, dy + dh);
        c.drawBitmap(bmp, src, dst, paint);
        drawControls(c);
    }

    private void drawControls(android.graphics.Canvas c) {
        boolean any = tUp || tDown || tLeft || tRight;
        if (layoutMode == LAYOUT_DPAD) {
            drawDpad(c);
        } else {
            // base del joystick
            ov.setColor(0x55000000);
            c.drawCircle(cx, cy, padR, ov);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(padR * 0.04f);
            ring.setColor(0x88FFFFFF);
            c.drawCircle(cx, cy, padR, ring);
            drawArrow(c, 0, -1, tUp);
            drawArrow(c, 0, 1, tDown);
            drawArrow(c, -1, 0, tLeft);
            drawArrow(c, 1, 0, tRight);
            // perilla
            float kx = stickId >= 0 ? knobX : cx, ky = stickId >= 0 ? knobY : cy;
            ov.setColor(any ? 0xDD40E070 : 0xAAE0E0E0);
            c.drawCircle(kx, ky, padR * 0.42f, ov);
            ring.setColor(0xCCFFFFFF);
            c.drawCircle(kx, ky, padR * 0.42f, ring);
        }
        // botones
        drawButton(c, ax, ay, ar, tFire, 0xD03030, "OK", ar * 0.55f);
        drawButton(c, lx, ly, sr, tLsh, 0x2090D0, "L", sr * 0.8f);
        drawButton(c, rx, ry, sr, tRsh, 0x2090D0, "R", sr * 0.8f);
        drawButton(c, starX, starY, sr * 0.9f, tStar, 0xE0A020, "*", sr * 1.1f);
        label.setTextSize(sr * 0.42f);
        c.drawText("REINICIAR", starX, starY + sr * 0.9f + sr * 0.6f, label);
        drawToggle(c);
    }

    /** Cruceta: cuatro brazos en forma de + que se iluminan al presionarlos. */
    private void drawDpad(android.graphics.Canvas c) {
        float arm = padR * 0.34f;   // semiancho de cada brazo
        float rad = arm * 0.45f;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(padR * 0.04f);
        // cuerpo de la cruceta (dos rectangulos cruzados)
        ov.setColor(0x66000000);
        rect.set(cx - padR, cy - arm, cx + padR, cy + arm);
        c.drawRoundRect(rect, rad, rad, ov);
        rect.set(cx - arm, cy - padR, cx + arm, cy + padR);
        c.drawRoundRect(rect, rad, rad, ov);
        // brazos activos
        ov.setColor(0xDD40E070);
        if (tUp)    { rect.set(cx - arm, cy - padR, cx + arm, cy - arm * 0.2f); c.drawRoundRect(rect, rad, rad, ov); }
        if (tDown)  { rect.set(cx - arm, cy + arm * 0.2f, cx + arm, cy + padR); c.drawRoundRect(rect, rad, rad, ov); }
        if (tLeft)  { rect.set(cx - padR, cy - arm, cx - arm * 0.2f, cy + arm); c.drawRoundRect(rect, rad, rad, ov); }
        if (tRight) { rect.set(cx + arm * 0.2f, cy - arm, cx + padR, cy + arm); c.drawRoundRect(rect, rad, rad, ov); }
        // contorno en forma de + (un solo trazo)
        ring.setColor(0x88FFFFFF);
        arrow.reset();
        arrow.moveTo(cx - arm, cy - padR);
        arrow.lineTo(cx + arm, cy - padR);
        arrow.lineTo(cx + arm, cy - arm);
        arrow.lineTo(cx + padR, cy - arm);
        arrow.lineTo(cx + padR, cy + arm);
        arrow.lineTo(cx + arm, cy + arm);
        arrow.lineTo(cx + arm, cy + padR);
        arrow.lineTo(cx - arm, cy + padR);
        arrow.lineTo(cx - arm, cy + arm);
        arrow.lineTo(cx - padR, cy + arm);
        arrow.lineTo(cx - padR, cy - arm);
        arrow.lineTo(cx - arm, cy - arm);
        arrow.close();
        c.drawPath(arrow, ring);
        // flechitas
        drawDpadArrow(c, 0, -1, tUp);
        drawDpadArrow(c, 0, 1, tDown);
        drawDpadArrow(c, -1, 0, tLeft);
        drawDpadArrow(c, 1, 0, tRight);
    }

    private void drawDpadArrow(android.graphics.Canvas c, float dirX, float dirY, boolean active) {
        float mid = padR * 0.66f, h = padR * 0.14f, w = padR * 0.16f;
        float px = -dirY, py = dirX;
        float bx = cx + dirX * mid, by = cy + dirY * mid;
        arrow.reset();
        arrow.moveTo(bx + dirX * h, by + dirY * h);
        arrow.lineTo(bx - dirX * h + px * w, by - dirY * h + py * w);
        arrow.lineTo(bx - dirX * h - px * w, by - dirY * h - py * w);
        arrow.close();
        ov.setColor(active ? 0xFFFFFFFF : 0x99FFFFFF);
        c.drawPath(arrow, ov);
    }

    /** Botoncito abajo al centro: muestra un icono del layout al que cambia. */
    private void drawToggle(android.graphics.Canvas c) {
        boolean on = toggleId >= 0;
        ov.setColor(on ? 0xDD606060 : 0x66000000);
        c.drawCircle(tgX, tgY, on ? tgR * 0.94f : tgR, ov);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(tgR * 0.08f);
        ring.setColor(on ? 0xFFFFFFFF : 0x99FFFFFF);
        c.drawCircle(tgX, tgY, on ? tgR * 0.94f : tgR, ring);
        ov.setColor(0xDDFFFFFF);
        if (layoutMode == LAYOUT_STICK) {
            // icono de cruceta (lo que se activa al tocar)
            float a = tgR * 0.22f, l = tgR * 0.62f;
            rect.set(tgX - l, tgY - a, tgX + l, tgY + a);
            c.drawRect(rect, ov);
            rect.set(tgX - a, tgY - l, tgX + a, tgY + l);
            c.drawRect(rect, ov);
        } else {
            // icono de joystick: aro con perilla
            ring.setStrokeWidth(tgR * 0.12f);
            ring.setColor(0xDDFFFFFF);
            c.drawCircle(tgX, tgY, tgR * 0.6f, ring);
            c.drawCircle(tgX, tgY, tgR * 0.24f, ov);
        }
    }

    private void drawArrow(android.graphics.Canvas c, float dirX, float dirY, boolean active) {
        float tipD = padR * 0.82f, baseD = padR * 0.62f, w = padR * 0.12f;
        float px = -dirY, py = dirX;
        arrow.reset();
        arrow.moveTo(cx + dirX * tipD, cy + dirY * tipD);
        arrow.lineTo(cx + dirX * baseD + px * w, cy + dirY * baseD + py * w);
        arrow.lineTo(cx + dirX * baseD - px * w, cy + dirY * baseD - py * w);
        arrow.close();
        ov.setColor(active ? 0xFF40E070 : 0x66FFFFFF);
        c.drawPath(arrow, ov);
    }

    private void drawButton(android.graphics.Canvas c, float x, float y, float r, boolean on,
                            int rgb, String text, float textSize) {
        float rr = on ? r * 0.94f : r;
        ov.setColor(((on ? 0xDD : 0x66) << 24) | rgb);
        c.drawCircle(x, y, rr, ov);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(r * 0.07f);
        ring.setColor(on ? 0xFFFFFFFF : 0x99FFFFFF);
        c.drawCircle(x, y, rr, ring);
        label.setTextSize(textSize);
        c.drawText(text, x, y + textSize * (text.equals("*") ? 0.55f : 0.35f), label);
    }

    // ------------------------------------------------------------------ teclas con latch

    public void pressKey(int code) {
        Runnable p = pendingUp.get(code);
        if (p != null) {
            handler.removeCallbacks(p);
            pendingUp.remove(code);
        }
        keyDown.put(code, true);
        downAt.put(code, SystemClock.uptimeMillis());
        canvas.dispatchKeyPressed(code);
    }

    public void releaseKey(final int code) {
        if (!keyDown.get(code) || pendingUp.get(code) != null) return;
        long held = SystemClock.uptimeMillis() - downAt.get(code, 0L);
        if (held >= MIN_HOLD_MS) {
            keyDown.delete(code);
            canvas.dispatchKeyReleased(code);
            return;
        }
        Runnable r = new Runnable() {
            @Override public void run() {
                pendingUp.remove(code);
                keyDown.delete(code);
                canvas.dispatchKeyReleased(code);
            }
        };
        pendingUp.put(code, r);
        handler.postDelayed(r, MIN_HOLD_MS - held);
    }

    /** Suelta todo (onPause, perdida de foco, detach) para que no queden teclas pegadas. */
    public void releaseAll() {
        for (int i = 0; i < pendingUp.size(); i++) handler.removeCallbacks(pendingUp.valueAt(i));
        pendingUp.clear();
        for (int i = 0; i < keyDown.size(); i++) {
            if (keyDown.valueAt(i)) canvas.dispatchKeyReleased(keyDown.keyAt(i));
        }
        keyDown.clear();
        tUp = tDown = tLeft = tRight = tFire = tLsh = tRsh = false;
        stickId = -1;
        toggleId = -1;
    }

    // ------------------------------------------------------------------ tactil

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!computeLayout(canvas.getFrontBitmap())) return true;
        int action = e.getActionMasked();
        boolean allUp = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        int skip = action == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;

        int actIdx = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float px = e.getX(actIdx), py = e.getY(actIdx);
            if (toggleId < 0 && Math.hypot(px - tgX, py - tgY) < tgR * 1.4f) {
                toggleId = e.getPointerId(actIdx);   // un toque = un cambio de layout
                cycleLayout();
            } else if (stickId < 0 && Math.hypot(px - cx, py - cy) < padR * 1.5f) {
                stickId = e.getPointerId(actIdx);
            }
        }
        if (allUp || (skip >= 0 && e.getPointerId(skip) == toggleId)) toggleId = -1;
        if (allUp || (skip >= 0 && e.getPointerId(skip) == stickId)) stickId = -1;

        boolean nU = false, nD = false, nL = false, nR = false, nF = false, nLsh = false, nRsh = false, nStar = false;
        if (!allUp) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (i == skip) continue;
                float x = e.getX(i), y = e.getY(i);
                if (e.getPointerId(i) == toggleId) continue;
                if (e.getPointerId(i) == stickId) {
                    float ddx = x - cx, ddy = y - cy;
                    float dist = (float) Math.hypot(ddx, ddy);
                    if (dist > padR) { ddx *= padR / dist; ddy *= padR / dist; dist = padR; }
                    knobX = cx + ddx; knobY = cy + ddy;
                    // zona muerta con histeresis: entra a 35%, suelta a 20%
                    float th = layoutMode == LAYOUT_DPAD ? padR * 0.15f
                            : ((tUp || tDown || tLeft || tRight) ? padR * 0.2f : padR * 0.35f);
                    if (dist >= th) {
                        float adx = Math.abs(ddx), ady = Math.abs(ddy);
                        boolean wasH = tLeft || tRight, wasV = tUp || tDown, horiz;
                        if (layoutMode == LAYOUT_DPAD) horiz = adx > ady;   // cruceta: eje dominante directo
                        else if (wasH) horiz = ady <= adx * 1.3f;          // cambia de eje solo si lo supera claro
                        else if (wasV) horiz = adx > ady * 1.3f;
                        else horiz = adx > ady;
                        if (horiz) { if (ddx > 0) nR = true; else nL = true; }
                        else       { if (ddy > 0) nD = true; else nU = true; }
                    }
                    continue;
                }
                if (Math.hypot(x - ax, y - ay) < ar * 1.15f) nF = true;
                if (Math.hypot(x - lx, y - ly) < sr * 1.4f) nLsh = true;
                if (Math.hypot(x - rx, y - ry) < sr * 1.4f) nRsh = true;
                if (Math.hypot(x - starX, y - starY) < sr * 1.3f) nStar = true;
            }
        }
        edge(nU, tUp, K_UP);       tUp = nU;
        edge(nD, tDown, K_DOWN);   tDown = nD;
        edge(nL, tLeft, K_LEFT);   tLeft = nL;
        edge(nR, tRight, K_RIGHT); tRight = nR;
        edge(nF, tFire, K_FIRE);   tFire = nF;
        edge(nLsh, tLsh, K_LSH);   tLsh = nLsh;
        edge(nRsh, tRsh, K_RSH);   tRsh = nRsh;
        edge(nStar, tStar, K_STAR); tStar = nStar;
        invalidate();
        return true;
    }

    private void edge(boolean now, boolean was, int key) {
        if (now == was) return;
        if (now) pressKey(key); else releaseKey(key);
    }
}
