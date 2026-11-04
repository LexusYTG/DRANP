package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Implementacion MIDP 2.0 de Graphics sobre android.graphics.Canvas.
 *
 * Convenciones MIDP respetadas:
 *  - translate() desplaza el origen; setClip()/clipRect()/getClip*() trabajan en coordenadas
 *    trasladadas (internamente el clip se guarda en coordenadas absolutas del bitmap).
 *  - setClip REEMPLAZA el clip (Android solo permite intersectar, por eso se restaura el estado
 *    del canvas y se vuelve a aplicar).
 *  - drawRect/drawLine incluyen el pixel final; fillRect cubre w x h pixeles.
 *  - Angulos de arcos en grados, antihorario desde las 3 en punto.
 *  - Las 8 transformaciones de drawRegion (Sprite.TRANS_*).
 */
public class Graphics {
    public static final int SOLID = 0, DOTTED = 1;
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8;
    public static final int TOP = 16, BOTTOM = 32, BASELINE = 64;

    public android.graphics.Canvas c;
    public android.graphics.Paint paint;      // formas
    private final Paint imgPaint;             // bitmaps (solo alpha global)
    private final Paint textPaint;            // texto
    private final Bitmap target;              // bitmap destino (para copyArea); puede ser null

    private int color = 0xFFFFFF;
    private int alpha = 255;
    private int tx = 0, ty = 0;
    // clip en coordenadas absolutas del bitmap
    private int clipL, clipT, clipR, clipB;
    private int saveCount;
    private final int surfaceW, surfaceH;
    private Font font;
    private int strokeStyle = SOLID;

    // reutilizables para no generar basura por frame
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();
    private final RectF rectF = new RectF();
    private final Matrix matrix = new Matrix();
    private final float[] mv = new float[9];
    private final Path path = new Path();

    Graphics(android.graphics.Canvas canvas, Bitmap target) {
        this.c = canvas;
        this.target = target;
        this.surfaceW = target != null ? target.getWidth() : canvas.getWidth();
        this.surfaceH = target != null ? target.getHeight() : canvas.getHeight();

        paint = new Paint();
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setFilterBitmap(false);
        paint.setStrokeWidth(1f);
        paint.setStrokeCap(Paint.Cap.BUTT);

        imgPaint = new Paint();
        imgPaint.setAntiAlias(false);
        imgPaint.setDither(false);
        imgPaint.setFilterBitmap(false);

        textPaint = new Paint();
        textPaint.setAntiAlias(true);

        clipL = 0; clipT = 0; clipR = surfaceW; clipB = surfaceH;
        saveCount = c.save();
        applyColor();
    }

    // ------------------------------------------------------------------ color

    private void applyColor() {
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        paint.setColor(argb);
        imgPaint.setAlpha(alpha);
    }

    public void setColor(int rgb) { color = rgb & 0xFFFFFF; applyColor(); }
    public void setColor(int r, int g, int b) {
        if ((r | g | b) < 0 || r > 255 || g > 255 || b > 255) throw new IllegalArgumentException("color");
        setColor((r << 16) | (g << 8) | b);
    }
    public int getColor() { return color; }
    public int getRedComponent() { return (color >> 16) & 0xFF; }
    public int getGreenComponent() { return (color >> 8) & 0xFF; }
    public int getBlueComponent() { return color & 0xFF; }
    public void setGrayScale(int v) {
        if (v < 0 || v > 255) throw new IllegalArgumentException("gray");
        setColor(v, v, v);
    }
    public int getGrayScale() { return (getRedComponent() + getGreenComponent() + getBlueComponent()) / 3; }
    public int getDisplayColor(int c) { return c & 0xFFFFFF; }

    /** Extension (no MIDP): alpha global 0..255 para las operaciones siguientes. */
    public void setAlpha(int a) { alpha = a < 0 ? 0 : (a > 255 ? 255 : a); applyColor(); }
    public int getAlpha() { return alpha; }

    // ------------------------------------------------------------------ estilo

    public void setStrokeStyle(int s) {
        if (s != SOLID && s != DOTTED) throw new IllegalArgumentException("stroke");
        strokeStyle = s;
        paint.setPathEffect(s == DOTTED ? new DashPathEffect(new float[]{2f, 2f}, 0f) : null);
    }
    public int getStrokeStyle() { return strokeStyle; }

    public void setFont(Font f) { font = f; }
    public Font getFont() { return font != null ? font : Font.getDefaultFont(); }

    // ------------------------------------------------------------------ traslacion y clip

    public void translate(int x, int y) { tx += x; ty += y; }
    public int getTranslateX() { return tx; }
    public int getTranslateY() { return ty; }

    public void setClip(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            clipL = clipT = clipR = clipB = 0;
        } else {
            clipL = clampX(x + tx);
            clipT = clampY(y + ty);
            clipR = clampX(x + tx + w);
            clipB = clampY(y + ty + h);
        }
        applyClip();
    }

    public void clipRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            clipL = clipT = clipR = clipB = 0;
        } else {
            clipL = Math.max(clipL, clampX(x + tx));
            clipT = Math.max(clipT, clampY(y + ty));
            clipR = Math.min(clipR, clampX(x + tx + w));
            clipB = Math.min(clipB, clampY(y + ty + h));
        }
        applyClip();
    }

    private int clampX(int v) { return v < 0 ? 0 : (v > surfaceW ? surfaceW : v); }
    private int clampY(int v) { return v < 0 ? 0 : (v > surfaceH ? surfaceH : v); }

    private void applyClip() {
        c.restoreToCount(saveCount);
        saveCount = c.save();
        if (clipR <= clipL || clipB <= clipT) {
            clipL = clipT = clipR = clipB = 0;
            c.clipRect(0, 0, 0, 0);
        } else {
            c.clipRect(clipL, clipT, clipR, clipB);
        }
    }

    public int getClipX() { return clipL - tx; }
    public int getClipY() { return clipT - ty; }
    public int getClipWidth() { return clipR - clipL; }
    public int getClipHeight() { return clipB - clipT; }

    // ------------------------------------------------------------------ primitivas

    public void drawLine(int x1, int y1, int x2, int y2) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE); // incluye el pixel final
        c.drawLine(x1 + tx + 0.5f, y1 + ty + 0.5f, x2 + tx + 0.5f, y2 + ty + 0.5f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(x + tx, y + ty, x + tx + w, y + ty + h, paint);
    }

    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) return;
        paint.setStyle(Paint.Style.STROKE);
        c.drawRect(x + tx + 0.5f, y + ty + 0.5f, x + tx + w + 0.5f, y + ty + h + 0.5f, paint);
    }

    public void fillRoundRect(int x, int y, int w, int h, int arcW, int arcH) {
        if (w <= 0 || h <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        rectF.set(x + tx, y + ty, x + tx + w, y + ty + h);
        c.drawRoundRect(rectF, arcW / 2f, arcH / 2f, paint);
        paint.setAntiAlias(false);
    }

    public void drawRoundRect(int x, int y, int w, int h, int arcW, int arcH) {
        if (w < 0 || h < 0) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        rectF.set(x + tx + 0.5f, y + ty + 0.5f, x + tx + w + 0.5f, y + ty + h + 0.5f);
        c.drawRoundRect(rectF, arcW / 2f, arcH / 2f, paint);
        paint.setAntiAlias(false);
    }

    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w <= 0 || h <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        rectF.set(x + tx, y + ty, x + tx + w, y + ty + h);
        // MIDP: antihorario; Android: horario
        c.drawArc(rectF, -startAngle, -arcAngle, true, paint);
        paint.setAntiAlias(false);
    }

    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w < 0 || h < 0) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        rectF.set(x + tx + 0.5f, y + ty + 0.5f, x + tx + w + 0.5f, y + ty + h + 0.5f);
        c.drawArc(rectF, -startAngle, -arcAngle, false, paint);
        paint.setAntiAlias(false);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        path.rewind();
        path.moveTo(x1 + tx, y1 + ty);
        path.lineTo(x2 + tx, y2 + ty);
        path.lineTo(x3 + tx, y3 + ty);
        path.close();
        c.drawPath(path, paint);
        paint.setAntiAlias(false);
    }

    // ------------------------------------------------------------------ imagenes

    private static int anchorX(int ax, int w, int anchor) {
        if ((anchor & RIGHT) != 0) return ax - w;
        if ((anchor & HCENTER) != 0) return ax - (w >> 1);
        return ax;
    }

    private static int anchorY(int ay, int h, int anchor) {
        if ((anchor & BOTTOM) != 0) return ay - h;
        if ((anchor & VCENTER) != 0) return ay - (h >> 1);
        return ay;
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null || img.bmp == null) return;
        int w = img.bmp.getWidth(), h = img.bmp.getHeight();
        int dx = anchorX(x + tx, w, anchor);
        int dy = anchorY(y + ty, h, anchor);
        c.drawBitmap(img.bmp, dx, dy, imgPaint);
    }

    public void drawRegion(Image img, int sx, int sy, int w, int h, int transform, int dx, int dy, int anchor) {
        if (img == null || img.bmp == null || w <= 0 || h <= 0) return;
        Bitmap bmp = img.bmp;
        if (sx < 0 || sy < 0 || sx + w > bmp.getWidth() || sy + h > bmp.getHeight()) return;
        if (transform < 0 || transform > 7) throw new IllegalArgumentException("transform");

        // las transformaciones 4..7 (rotaciones de 90 y transpuestas) intercambian ancho y alto
        boolean swap = transform >= 4;
        int tw = swap ? h : w;
        int th = swap ? w : h;
        int ax = anchorX(dx + tx, tw, anchor);
        int ay = anchorY(dy + ty, th, anchor);

        if (transform == 0) {
            srcRect.set(sx, sy, sx + w, sy + h);
            dstRect.set(ax, ay, ax + w, ay + h);
            c.drawBitmap(bmp, srcRect, dstRect, imgPaint);
            return;
        }

        // x' = a*x + b*y + e ; y' = c*x + d*y + f   (x,y relativos a la region de origen)
        float a, b, e, cc, d, f;
        switch (transform) {
            case 1:  a = 1;  b = 0;  e = 0; cc = 0;  d = -1; f = h; break; // MIRROR_ROT180 (espejo vertical)
            case 2:  a = -1; b = 0;  e = w; cc = 0;  d = 1;  f = 0; break; // MIRROR (espejo horizontal)
            case 3:  a = -1; b = 0;  e = w; cc = 0;  d = -1; f = h; break; // ROT180
            case 4:  a = 0;  b = 1;  e = 0; cc = 1;  d = 0;  f = 0; break; // MIRROR_ROT270 (transpuesta)
            case 5:  a = 0;  b = -1; e = h; cc = 1;  d = 0;  f = 0; break; // ROT90 horario
            case 6:  a = 0;  b = 1;  e = 0; cc = -1; d = 0;  f = w; break; // ROT270
            default: a = 0;  b = -1; e = h; cc = -1; d = 0;  f = w; break; // 7: MIRROR_ROT90
        }
        mv[0] = a;  mv[1] = b;  mv[2] = e;
        mv[3] = cc; mv[4] = d;  mv[5] = f;
        mv[6] = 0;  mv[7] = 0;  mv[8] = 1;
        matrix.setValues(mv);

        int count = c.save();
        c.translate(ax, ay);
        c.concat(matrix);
        c.translate(-sx, -sy);
        c.clipRect(sx, sy, sx + w, sy + h);
        c.drawBitmap(bmp, 0, 0, imgPaint);
        c.restoreToCount(count);
    }

    @SuppressWarnings("deprecation")
    public void drawRGB(int[] rgb, int offset, int scanlength, int x, int y, int w, int h, boolean processAlpha) {
        if (rgb == null) throw new NullPointerException("rgb");
        if (w <= 0 || h <= 0) return;
        c.drawBitmap(rgb, offset, scanlength, x + tx, y + ty, w, h, processAlpha, imgPaint);
    }

    public void copyArea(int xSrc, int ySrc, int w, int h, int xDest, int yDest, int anchor) {
        if (target == null || w <= 0 || h <= 0) return;
        int sx = xSrc + tx, sy = ySrc + ty;
        if (sx < 0 || sy < 0 || sx + w > target.getWidth() || sy + h > target.getHeight()) {
            throw new IllegalArgumentException("copyArea fuera del buffer");
        }
        Bitmap copy = Bitmap.createBitmap(target, sx, sy, w, h); // copia (el target es mutable)
        int dx = anchorX(xDest + tx, w, anchor);
        int dy = anchorY(yDest + ty, h, anchor);
        c.drawBitmap(copy, dx, dy, imgPaint);
        copy.recycle();
    }

    // ------------------------------------------------------------------ texto

    private void prepareText() {
        Font f = getFont();
        textPaint.setTypeface(f.paint.getTypeface());
        textPaint.setTextSize(f.paint.getTextSize());
        textPaint.setUnderlineText(f.paint.isUnderlineText());
        textPaint.setColor((alpha << 24) | (color & 0xFFFFFF));
    }

    private void drawText(String s, int x, int y, int anchor) {
        if (s == null) throw new NullPointerException("str");
        if (s.length() == 0) return;
        prepareText();
        float w = textPaint.measureText(s);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float px = x + tx;
        if ((anchor & RIGHT) != 0) px -= w;
        else if ((anchor & HCENTER) != 0) px -= w / 2f;
        float baseline = y + ty;
        if ((anchor & BASELINE) != 0) {
            // y ya es la linea base
        } else if ((anchor & BOTTOM) != 0) {
            baseline -= fm.descent;
        } else { // TOP (por defecto)
            baseline -= fm.ascent;
        }
        c.drawText(s, px, baseline, textPaint);
    }

    public void drawString(String str, int x, int y, int anchor) { drawText(str, x, y, anchor); }

    public void drawSubstring(String str, int offset, int length, int x, int y, int anchor) {
        if (str == null) throw new NullPointerException("str");
        if (offset < 0 || length < 0 || offset + length > str.length()) throw new StringIndexOutOfBoundsException();
        drawText(str.substring(offset, offset + length), x, y, anchor);
    }

    public void drawChar(char ch, int x, int y, int anchor) { drawText(String.valueOf(ch), x, y, anchor); }

    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        drawText(new String(data, offset, length), x, y, anchor);
    }
}
