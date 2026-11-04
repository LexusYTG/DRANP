package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;

public abstract class Layer {
    int x, y, width, height;
    boolean visible = true;

    Layer(int w, int h) { this.width = w; this.height = h; }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void move(int dx, int dy) { this.x += dx; this.y += dy; }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final int getWidth() { return width; }
    public final int getHeight() { return height; }
    public void setVisible(boolean v) { this.visible = v; }
    public final boolean isVisible() { return visible; }

    void setWidth(int w) { this.width = w; }
    void setHeight(int h) { this.height = h; }

    /** Dibuja el layer en sus coordenadas (relativas al origen actual de g). */
    public abstract void paint(Graphics g);

    /** Variante auxiliar: dibuja desplazado (x, y). */
    public void paint(Graphics g, int offX, int offY) {
        g.translate(offX, offY);
        try { paint(g); } finally { g.translate(-offX, -offY); }
    }
}
