package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

public abstract class Canvas extends Displayable {
    public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53, KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57;
    public static final int KEY_STAR = 42, KEY_POUND = 35;
    public static final int UP = 1, DOWN = 6, LEFT = 2, RIGHT = 5, FIRE = 8;
    public static final int GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;

    /** Codigos "softkey" estilo Nokia/Siemens que usa el juego (serializeKey: 1..7 tras abs()). */
    public static final int SOFTKEY_UP = -1, SOFTKEY_DOWN = -2, SOFTKEY_LEFT = -3, SOFTKEY_RIGHT = -4;
    public static final int SOFTKEY_FIRE = -5, SOFTKEY_LEFT_SOFT = -6, SOFTKEY_RIGHT_SOFT = -7;

    /** Avisa a la vista que hay un frame nuevo para dibujar. */
    public interface FrameListener { void onFrame(); }

    // ---- Buffer frontal (lo que ve el usuario) en anillo de 3 bitmaps ----
    // El hilo del juego dibuja en su back buffer y al hacer flush copia aca; el hilo de UI
    // solo lee el ultimo bitmap publicado. Asi no se ve nunca un frame a medio dibujar.
    private final Object frameLock = new Object();
    private final Bitmap[] ring = new Bitmap[3];
    private final android.graphics.Canvas[] ringCanvas = new android.graphics.Canvas[3];
    private int ringIdx = 0;
    private volatile Bitmap published;
    private final Paint copyPaint = new Paint();
    private volatile FrameListener frameListener;

    private int keyStates = 0;
    private Image paintBuffer; // solo para Canvas "puros" que implementan paint()

    protected Canvas() {
        for (int i = 0; i < ring.length; i++) {
            ring[i] = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            ring[i].eraseColor(0xFF000000);
            ringCanvas[i] = new android.graphics.Canvas(ring[i]);
        }
        published = ring[0];
        copyPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        copyPaint.setFilterBitmap(false);
        copyPaint.setAntiAlias(false);
    }

    // ---------------- API MIDP ----------------

    protected void paint(Graphics g) {}
    public void showNotify() {}
    public void hideNotify() {}
    public void keyPressed(int keyCode) {}
    public void keyReleased(int keyCode) {}
    public void keyRepeated(int keyCode) {}
    protected void pointerPressed(int x, int y) {}
    protected void pointerReleased(int x, int y) {}
    protected void pointerDragged(int x, int y) {}
    protected void sizeChanged(int w, int h) {}

    /** Pide repintar. En un GameCanvas el juego dibuja solo, asi que no hace nada. */
    public final void repaint() { requestPaint(); }
    public final void repaint(int x, int y, int w, int h) { requestPaint(); }
    public final void serviceRepaints() { requestPaint(); }

    private void requestPaint() {
        if (this instanceof javax.microedition.lcdui.game.GameCanvas) return;
        synchronized (frameLock) {
            if (paintBuffer == null) paintBuffer = Image.createImage(getWidth(), getHeight());
            Graphics g = paintBuffer.getGraphics();
            g.setClip(0, 0, getWidth(), getHeight());
            paint(g);
            present(paintBuffer.bmp);
        }
    }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case SOFTKEY_UP: case KEY_NUM2: return UP;
            case SOFTKEY_DOWN: case KEY_NUM8: return DOWN;
            case SOFTKEY_LEFT: case KEY_NUM4: return LEFT;
            case SOFTKEY_RIGHT: case KEY_NUM6: return RIGHT;
            case SOFTKEY_FIRE: case KEY_NUM5: return FIRE;
            case KEY_NUM1: return GAME_A;
            case KEY_NUM3: return GAME_B;
            case KEY_NUM7: return GAME_C;
            case KEY_NUM9: return GAME_D;
            default: return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP: return SOFTKEY_UP;
            case DOWN: return SOFTKEY_DOWN;
            case LEFT: return SOFTKEY_LEFT;
            case RIGHT: return SOFTKEY_RIGHT;
            case FIRE: return SOFTKEY_FIRE;
            case GAME_A: return KEY_NUM1;
            case GAME_B: return KEY_NUM3;
            case GAME_C: return KEY_NUM7;
            case GAME_D: return KEY_NUM9;
            default: throw new IllegalArgumentException("gameAction");
        }
    }

    public String getKeyName(int keyCode) {
        if (keyCode >= KEY_NUM0 && keyCode <= KEY_NUM9) return String.valueOf((char) keyCode);
        switch (keyCode) {
            case KEY_STAR: return "*";
            case KEY_POUND: return "#";
            case SOFTKEY_UP: return "UP";
            case SOFTKEY_DOWN: return "DOWN";
            case SOFTKEY_LEFT: return "LEFT";
            case SOFTKEY_RIGHT: return "RIGHT";
            case SOFTKEY_FIRE: return "SELECT";
            case SOFTKEY_LEFT_SOFT: return "SOFT1";
            case SOFTKEY_RIGHT_SOFT: return "SOFT2";
            default: return "KEY " + keyCode;
        }
    }

    /** Estado de teclas de juego (mascaras de GameCanvas.*_PRESSED). */
    public int getKeyStates() { return keyStates; }

    public boolean isDoubleBuffered() { return true; }

    // ---------------- Integracion con Android ----------------

    public final void setFrameListener(FrameListener l) { frameListener = l; }

    /** Ultimo frame completo publicado. Lo llama el hilo de UI. */
    public final Bitmap getFrontBitmap() { return published; }

    /** Copia src al siguiente bitmap del anillo, lo publica y avisa a la vista. */
    protected final void present(Bitmap src) {
        if (src == null) return;
        synchronized (frameLock) {
            ringIdx = (ringIdx + 1) % ring.length;
            ringCanvas[ringIdx].drawBitmap(src, 0, 0, copyPaint);
            published = ring[ringIdx];
        }
        FrameListener l = frameListener;
        if (l != null) l.onFrame();
    }

    /** Entrada: el hilo de UI llama a estos metodos; mantienen keyStates y despachan al juego. */
    public final void dispatchKeyPressed(int keyCode) {
        updateKeyState(keyCode, true);
        keyPressed(keyCode);
    }

    public final void dispatchKeyReleased(int keyCode) {
        updateKeyState(keyCode, false);
        keyReleased(keyCode);
    }

    public final void dispatchPointerPressed(int x, int y) { pointerPressed(x, y); }
    public final void dispatchPointerDragged(int x, int y) { pointerDragged(x, y); }
    public final void dispatchPointerReleased(int x, int y) { pointerReleased(x, y); }

    private void updateKeyState(int keyCode, boolean down) {
        int action = getGameAction(keyCode);
        int mask;
        switch (action) {
            case UP: mask = 1 << 1; break;
            case LEFT: mask = 1 << 2; break;
            case RIGHT: mask = 1 << 5; break;
            case DOWN: mask = 1 << 6; break;
            case FIRE: mask = 1 << 8; break;
            case GAME_A: mask = 1 << 9; break;
            case GAME_B: mask = 1 << 10; break;
            case GAME_C: mask = 1 << 11; break;
            case GAME_D: mask = 1 << 12; break;
            default: return;
        }
        if (down) keyStates |= mask; else keyStates &= ~mask;
    }

    /** Llamado por Display cuando este Canvas pasa a ser (o deja de ser) el visible. */
    final void onShown(boolean shown) {
        boolean was = isShown();
        setShown(shown);
        if (shown && !was) showNotify();
        else if (!shown && was) hideNotify();
    }
}
