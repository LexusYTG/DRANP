package javax.microedition.lcdui.game;

import java.util.ArrayList;
import javax.microedition.lcdui.Graphics;

public class LayerManager {
    private final ArrayList<Layer> layers = new ArrayList<Layer>(); // 0 = el de mas arriba
    private int vx = 0, vy = 0, vw = Integer.MAX_VALUE, vh = Integer.MAX_VALUE;

    public LayerManager() {}

    public void append(Layer l) { if (l == null) throw new NullPointerException(); layers.remove(l); layers.add(l); }
    public void insert(Layer l, int index) {
        if (l == null) throw new NullPointerException();
        layers.remove(l);
        if (index < 0 || index > layers.size()) throw new IndexOutOfBoundsException();
        layers.add(index, l);
    }
    public Layer getLayerAt(int index) { return layers.get(index); }
    public int getSize() { return layers.size(); }
    public void remove(Layer l) { layers.remove(l); }

    public void setViewWindow(int x, int y, int w, int h) {
        if (w < 0 || h < 0) throw new IllegalArgumentException();
        vx = x; vy = y; vw = w; vh = h;
    }

    public void paint(Graphics g, int x, int y) {
        int tx = g.getTranslateX(), ty = g.getTranslateY();
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.translate(x - vx, y - vy);
        if (vw != Integer.MAX_VALUE) g.clipRect(vx, vy, vw, vh);
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (l.isVisible()) l.paint(g);
        }
        g.translate(tx - g.getTranslateX(), ty - g.getTranslateY());
        g.setClip(cx, cy, cw, ch);
    }
}
