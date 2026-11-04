package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class Sprite extends Layer {
    public static final int TRANS_NONE = 0;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_MIRROR_ROT270 = 4;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR_ROT90 = 7;

    private Image img;
    private int frameW, frameH, rawFrames, cols;
    private int[] sequence;
    private int seqIdx = 0;
    private int transform = TRANS_NONE;
    private int refX = 0, refY = 0;          // pixel de referencia (sin transformar)
    private int collX, collY, collW, collH;  // rectangulo de colision (sin transformar)

    public Sprite(Image image) {
        super(0, 0);
        if (image == null) throw new NullPointerException("image");
        init(image, image.getWidth(), image.getHeight());
    }

    public Sprite(Image image, int frameWidth, int frameHeight) {
        super(0, 0);
        if (image == null) throw new NullPointerException("image");
        init(image, frameWidth, frameHeight);
    }

    public Sprite(Sprite s) {
        super(0, 0);
        if (s == null) throw new NullPointerException("sprite");
        init(s.img, s.frameW, s.frameH);
        this.sequence = s.sequence.clone();
        this.seqIdx = s.seqIdx;
        this.transform = s.transform;
        this.refX = s.refX; this.refY = s.refY;
        this.collX = s.collX; this.collY = s.collY; this.collW = s.collW; this.collH = s.collH;
        this.x = s.x; this.y = s.y;
        this.visible = s.visible;
        updateSize();
    }

    private void init(Image image, int fw, int fh) {
        if (fw <= 0 || fh <= 0 || image.getWidth() % fw != 0 || image.getHeight() % fh != 0) {
            throw new IllegalArgumentException("frame size");
        }
        this.img = image;
        this.frameW = fw;
        this.frameH = fh;
        this.cols = image.getWidth() / fw;
        this.rawFrames = cols * (image.getHeight() / fh);
        this.sequence = new int[rawFrames];
        for (int i = 0; i < rawFrames; i++) sequence[i] = i;
        this.seqIdx = 0;
        this.collX = 0; this.collY = 0; this.collW = fw; this.collH = fh;
        updateSize();
    }

    // ------------------------------------------------------------ frames

    public void setFrame(int f) {
        if (f < 0 || f >= sequence.length) throw new IndexOutOfBoundsException("frame " + f);
        seqIdx = f;
    }
    public final int getFrame() { return seqIdx; }
    public int getRawFrameCount() { return rawFrames; }
    public int getFrameSequenceLength() { return sequence.length; }
    public int getFrameSequenceIndex() { return seqIdx; }
    public void nextFrame() { seqIdx = (seqIdx + 1) % sequence.length; }
    public void prevFrame() { seqIdx = (seqIdx - 1 + sequence.length) % sequence.length; }

    public void setFrameSequence(int[] seq) {
        if (seq == null) { // secuencia por defecto
            sequence = new int[rawFrames];
            for (int i = 0; i < rawFrames; i++) sequence[i] = i;
            seqIdx = 0;
            return;
        }
        if (seq.length < 1) throw new IllegalArgumentException("secuencia vacia");
        for (int v : seq) if (v < 0 || v >= rawFrames) throw new ArrayIndexOutOfBoundsException("frame " + v);
        sequence = seq.clone();
        seqIdx = 0;
    }

    public void setImage(Image image, int fw, int fh) {
        if (image == null) throw new NullPointerException("image");
        if (fw <= 0 || fh <= 0 || image.getWidth() % fw != 0 || image.getHeight() % fh != 0) {
            throw new IllegalArgumentException("frame size");
        }
        int oldRefScreenX = getRefPixelX(), oldRefScreenY = getRefPixelY();
        int oldRaw = rawFrames;
        boolean sameSize = (fw == frameW && fh == frameH);
        this.img = image;
        this.frameW = fw;
        this.frameH = fh;
        this.cols = image.getWidth() / fw;
        this.rawFrames = cols * (image.getHeight() / fh);
        if (rawFrames < oldRaw || !sameSize) {
            // se reinicia la secuencia si hay menos frames o cambia el tamanio
            sequence = new int[rawFrames];
            for (int i = 0; i < rawFrames; i++) sequence[i] = i;
            seqIdx = 0;
        }
        if (!sameSize) {
            collX = 0; collY = 0; collW = fw; collH = fh;
            updateSize();
            setRefPixelPosition(oldRefScreenX, oldRefScreenY);
        }
    }

    // ------------------------------------------------------------ transformacion / referencia

    public void defineReferencePixel(int rx, int ry) {
        refX = rx;
        refY = ry;
    }

    public int getRefPixelX() { return x + transformedRefX(); }
    public int getRefPixelY() { return y + transformedRefY(); }

    public void setRefPixelPosition(int rx, int ry) {
        x = rx - transformedRefX();
        y = ry - transformedRefY();
    }

    public void setTransform(int t) {
        if (t < 0 || t > 7) throw new IllegalArgumentException("transform");
        int sx = getRefPixelX(), sy = getRefPixelY();
        this.transform = t;
        updateSize();
        setRefPixelPosition(sx, sy); // el pixel de referencia no se mueve en pantalla
    }

    public int getTransform() { return transform; }

    private boolean swapsAxes() { return transform >= 4; }

    private void updateSize() {
        setWidth(swapsAxes() ? frameH : frameW);
        setHeight(swapsAxes() ? frameW : frameH);
    }

    /** Aplica la transformacion actual a un punto (px,py) dentro de un marco frameW x frameH. */
    private int tX(int px, int py) {
        switch (transform) {
            case 1: return px;
            case 2: return frameW - 1 - px;
            case 3: return frameW - 1 - px;
            case 4: return py;
            case 5: return frameH - 1 - py;
            case 6: return py;
            case 7: return frameH - 1 - py;
            default: return px;
        }
    }

    private int tY(int px, int py) {
        switch (transform) {
            case 1: return frameH - 1 - py;
            case 2: return py;
            case 3: return frameH - 1 - py;
            case 4: return px;
            case 5: return px;
            case 6: return frameW - 1 - px;
            case 7: return frameW - 1 - px;
            default: return py;
        }
    }

    private int transformedRefX() { return tX(refX, refY); }
    private int transformedRefY() { return tY(refX, refY); }

    // ------------------------------------------------------------ colision

    public void defineCollisionRectangle(int cx, int cy, int cw, int ch) {
        if (cw < 0 || ch < 0) throw new IllegalArgumentException("colision");
        collX = cx; collY = cy; collW = cw; collH = ch;
    }

    /** Rectangulo de colision transformado, en coordenadas absolutas: {x, y, w, h}. */
    private int[] collisionRect() {
        // esquinas (inclusivas) del rectangulo sin transformar
        int x0 = collX, y0 = collY, x1 = collX + collW - 1, y1 = collY + collH - 1;
        int ax = tX(x0, y0), ay = tY(x0, y0);
        int bx = tX(x1, y1), by = tY(x1, y1);
        int minX = Math.min(ax, bx), minY = Math.min(ay, by);
        int maxX = Math.max(ax, bx), maxY = Math.max(ay, by);
        if (collW == 0 || collH == 0) return new int[]{x + minX, y + minY, 0, 0};
        return new int[]{x + minX, y + minY, maxX - minX + 1, maxY - minY + 1};
    }

    /** Alfa del pixel de pantalla (sx, sy) de este sprite; 0 si cae fuera del frame actual. */
    private int alphaAtScreen(int sx, int sy) {
        int lx = sx - x, ly = sy - y; // coordenadas en el sprite ya transformado
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return 0;
        // inversa de la transformacion: se busca (px,py) del frame tal que tX/tY den (lx,ly)
        int px, py;
        switch (transform) {
            case 1: px = lx;               py = frameH - 1 - ly; break;
            case 2: px = frameW - 1 - lx;  py = ly; break;
            case 3: px = frameW - 1 - lx;  py = frameH - 1 - ly; break;
            case 4: px = ly;               py = lx; break;
            case 5: px = ly;               py = frameH - 1 - lx; break;
            case 6: px = frameW - 1 - ly;  py = lx; break;
            case 7: px = frameW - 1 - ly;  py = frameH - 1 - lx; break;
            default: px = lx;              py = ly; break;
        }
        int raw = sequence[seqIdx];
        int ix = (raw % cols) * frameW + px;
        int iy = (raw / cols) * frameH + py;
        return (img.bmp.getPixel(ix, iy) >>> 24);
    }

    private static boolean intersects(int[] a, int[] b) {
        return a[2] > 0 && a[3] > 0 && b[2] > 0 && b[3] > 0
                && a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    public final boolean collidesWith(Sprite s, boolean pixelLevel) {
        if (s == null) throw new NullPointerException("sprite");
        if (!visible || !s.visible) return false;
        int[] a = collisionRect(), b = s.collisionRect();
        if (!intersects(a, b)) return false;
        if (!pixelLevel) return true;
        int l = Math.max(a[0], b[0]), t = Math.max(a[1], b[1]);
        int r = Math.min(a[0] + a[2], b[0] + b[2]), bt = Math.min(a[1] + a[3], b[1] + b[3]);
        for (int py = t; py < bt; py++) {
            for (int px = l; px < r; px++) {
                if (alphaAtScreen(px, py) != 0 && s.alphaAtScreen(px, py) != 0) return true;
            }
        }
        return false;
    }

    public final boolean collidesWith(TiledLayer tl, boolean pixelLevel) {
        if (tl == null) throw new NullPointerException("tiledLayer");
        if (!visible || !tl.isVisible()) return false;
        int[] a = collisionRect();
        if (a[2] <= 0 || a[3] <= 0) return false;
        int tw = tl.getCellWidth(), th = tl.getCellHeight();
        int c0 = Math.max(0, (a[0] - tl.getX()) / tw - (a[0] - tl.getX() < 0 ? 1 : 0));
        int r0 = Math.max(0, (a[1] - tl.getY()) / th - (a[1] - tl.getY() < 0 ? 1 : 0));
        int c1 = Math.min(tl.getColumns() - 1, (a[0] + a[2] - 1 - tl.getX()) / tw);
        int r1 = Math.min(tl.getRows() - 1, (a[1] + a[3] - 1 - tl.getY()) / th);
        if (a[0] + a[2] - 1 - tl.getX() < 0 || a[1] + a[3] - 1 - tl.getY() < 0) return false;
        Image tiles = tl.tileImage();
        int perRow = tl.tileSetColumns();
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int t = tl.staticTileAt(c, r);
                if (t == 0) continue;
                if (!pixelLevel) return true;
                int cellX = tl.getX() + c * tw, cellY = tl.getY() + r * th;
                int l = Math.max(a[0], cellX), top = Math.max(a[1], cellY);
                int rr = Math.min(a[0] + a[2], cellX + tw), bb = Math.min(a[1] + a[3], cellY + th);
                int sx = ((t - 1) % perRow) * tw, sy = ((t - 1) / perRow) * th;
                for (int py = top; py < bb; py++) {
                    for (int px = l; px < rr; px++) {
                        if (alphaAtScreen(px, py) != 0
                                && (tiles.bmp.getPixel(sx + px - cellX, sy + py - cellY) >>> 24) != 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public final boolean collidesWith(Image image, int ix, int iy, boolean pixelLevel) {
        if (image == null) throw new NullPointerException("image");
        if (!visible) return false;
        int[] a = collisionRect();
        int[] b = {ix, iy, image.getWidth(), image.getHeight()};
        if (!intersects(a, b)) return false;
        if (!pixelLevel) return true;
        int l = Math.max(a[0], b[0]), t = Math.max(a[1], b[1]);
        int r = Math.min(a[0] + a[2], b[0] + b[2]), bt = Math.min(a[1] + a[3], b[1] + b[3]);
        for (int py = t; py < bt; py++) {
            for (int px = l; px < r; px++) {
                if (alphaAtScreen(px, py) != 0 && (image.bmp.getPixel(px - ix, py - iy) >>> 24) != 0) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ dibujo

    public final void paint(Graphics g) {
        if (!visible || img == null) return;
        int raw = sequence[seqIdx];
        int sx = (raw % cols) * frameW;
        int sy = (raw / cols) * frameH;
        g.drawRegion(img, sx, sy, frameW, frameH, transform, x, y, Graphics.TOP | Graphics.LEFT);
    }
}
