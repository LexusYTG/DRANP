package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 1 << Canvas.UP;
    public static final int DOWN_PRESSED = 1 << Canvas.DOWN;
    public static final int LEFT_PRESSED = 1 << Canvas.LEFT;
    public static final int RIGHT_PRESSED = 1 << Canvas.RIGHT;
    public static final int FIRE_PRESSED = 1 << Canvas.FIRE;
    public static final int GAME_A_PRESSED = 1 << Canvas.GAME_A;
    public static final int GAME_B_PRESSED = 1 << Canvas.GAME_B;
    public static final int GAME_C_PRESSED = 1 << Canvas.GAME_C;
    public static final int GAME_D_PRESSED = 1 << Canvas.GAME_D;

    private final Image buffer;
    private final Graphics graphics;

    protected GameCanvas() { this(true); }

    protected GameCanvas(boolean suppressKeyEvents) {
        super();
        buffer = Image.createImage(getWidth(), getHeight());
        graphics = buffer.getGraphics();
    }

    /** Back buffer: el juego dibuja aca y publica con flushGraphics(). */
    public Graphics getGraphics() { return graphics; }

    public Image getBuffer() { return buffer; }

    /** Publica el back buffer completo; la vista lo ve recien ahora (sin frames a medias). */
    public void flushGraphics() { present(buffer.bmp); }

    public void flushGraphics(int x, int y, int w, int h) { present(buffer.bmp); }
}
