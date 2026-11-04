package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class TiledLayer extends Layer {
    private final int cols, rows;
    private final int[][] cells;       // [fila][columna]; >0 estatico, <0 animado, 0 vacio
    private Image image;
    private int tileW, tileH, tilesPerRow, staticCount;
    private int[] animated = new int[4]; // animated[-idx] = tile estatico actual
    private int animatedCount = 0;

    public TiledLayer(int cols, int rows, Image image, int tileW, int tileH) {
        super(cols * tileW, rows * tileH);
        if (cols <= 0 || rows <= 0 || tileW <= 0 || tileH <= 0) throw new IllegalArgumentException("size");
        this.cols = cols;
        this.rows = rows;
        this.cells = new int[rows][cols];
        setStaticTileSet(image, tileW, tileH);
    }

    public void setStaticTileSet(Image image, int tw, int th) {
        if (image == null) throw new NullPointerException("image");
        if (tw <= 0 || th <= 0 || image.getWidth() % tw != 0 || image.getHeight() % th != 0) {
            throw new IllegalArgumentException("tile size");
        }
        int newCount = (image.getWidth() / tw) * (image.getHeight() / th);
        if (this.image == null || newCount < staticCount) {
            // se descartan celdas y tiles animados si el nuevo set es menor
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) cells[r][c] = 0;
            animatedCount = 0;
        }
        this.image = image;
        this.tileW = tw;
        this.tileH = th;
        this.tilesPerRow = image.getWidth() / tw;
        this.staticCount = newCount;
        setWidth(cols * tw);
        setHeight(rows * th);
    }

    public int createAnimatedTile(int staticTileIndex) {
        checkStatic(staticTileIndex);
        if (animatedCount + 1 >= animated.length) {
            int[] n = new int[animated.length * 2];
            System.arraycopy(animated, 0, n, 0, animated.length);
            animated = n;
        }
        animatedCount++;
        animated[animatedCount] = staticTileIndex;
        return -animatedCount;
    }

    public void setAnimatedTile(int animatedTileIndex, int staticTileIndex) {
        checkStatic(staticTileIndex);
        int i = -animatedTileIndex;
        if (i < 1 || i > animatedCount) throw new IndexOutOfBoundsException("animated tile");
        animated[i] = staticTileIndex;
    }

    public int getAnimatedTile(int animatedTileIndex) {
        int i = -animatedTileIndex;
        if (i < 1 || i > animatedCount) throw new IndexOutOfBoundsException("animated tile");
        return animated[i];
    }

    private void checkStatic(int t) {
        if (t < 0 || t > staticCount) throw new IndexOutOfBoundsException("tile " + t);
    }

    public void setCell(int col, int row, int tileIndex) {
        checkCell(col, row);
        if (tileIndex > staticCount || -tileIndex > animatedCount) throw new IndexOutOfBoundsException("tile " + tileIndex);
        cells[row][col] = tileIndex;
    }

    public int getCell(int col, int row) { checkCell(col, row); return cells[row][col]; }

    public void fillCells(int col, int row, int numCols, int numRows, int tileIndex) {
        if (numCols < 0 || numRows < 0) throw new IllegalArgumentException();
        if (col < 0 || row < 0 || col + numCols > cols || row + numRows > rows) throw new IndexOutOfBoundsException();
        if (tileIndex > staticCount || -tileIndex > animatedCount) throw new IndexOutOfBoundsException("tile " + tileIndex);
        for (int r = row; r < row + numRows; r++) for (int c = col; c < col + numCols; c++) cells[r][c] = tileIndex;
    }

    private void checkCell(int col, int row) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) throw new IndexOutOfBoundsException("celda " + col + "," + row);
    }

    public final int getCellWidth() { return tileW; }
    public final int getCellHeight() { return tileH; }
    public final int getColumns() { return cols; }
    public final int getRows() { return rows; }

    /** Tile estatico (1..n) que corresponde a la celda, 0 si esta vacia. */
    int staticTileAt(int col, int row) {
        int t = cells[row][col];
        return t < 0 ? animated[-t] : t;
    }

    Image tileImage() { return image; }
    int tileSetColumns() { return tilesPerRow; }

    public final void paint(Graphics g) {
        if (!visible) return;
        int cl = g.getClipX(), ct = g.getClipY();
        int cr = cl + g.getClipWidth(), cb = ct + g.getClipHeight();
        // rango de celdas que toca el clip
        int c0 = Math.max(0, (cl - x) / tileW);
        int r0 = Math.max(0, (ct - y) / tileH);
        int c1 = Math.min(cols - 1, (cr - 1 - x) / tileW);
        int r1 = Math.min(rows - 1, (cb - 1 - y) / tileH);
        if (cl - x < 0) c0 = 0;
        if (ct - y < 0) r0 = 0;
        if (cr - 1 - x < 0 || cb - 1 - y < 0) return;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int t = staticTileAt(c, r);
                if (t == 0) continue;
                int idx = t - 1;
                int sx = (idx % tilesPerRow) * tileW;
                int sy = (idx / tilesPerRow) * tileH;
                g.drawRegion(image, sx, sy, tileW, tileH, 0, x + c * tileW, y + r * tileH, Graphics.TOP | Graphics.LEFT);
            }
        }
    }
}
