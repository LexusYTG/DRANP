package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import com.diamondrush.port.game.Res;
import java.io.IOException;
import java.io.InputStream;

public class Image {
    public Bitmap bmp;
    private final boolean mutable;
    private Graphics graphics;

    private Image(Bitmap b, boolean mutable) {
        this.bmp = b;
        this.mutable = mutable;
    }

    private static BitmapFactory.Options decodeOptions() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;                       // nunca reescalar por densidad
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return o;
    }

    /** Imagen mutable, inicialmente blanca (como exige MIDP). */
    public static Image createImage(int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("size");
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFFFFFFFF);
        return new Image(b, true);
    }

    public static Image createImage(byte[] data, int off, int len) {
        if (data == null) throw new NullPointerException("data");
        Bitmap b = BitmapFactory.decodeByteArray(data, off, len, decodeOptions());
        if (b == null) throw new IllegalArgumentException("formato de imagen invalido");
        return new Image(b, false);
    }

    /** Copia inmutable de otra imagen. */
    public static Image createImage(Image source) {
        if (source == null) throw new NullPointerException("source");
        if (!source.mutable) return source; // las inmutables se pueden compartir
        return new Image(source.bmp.copy(Bitmap.Config.ARGB_8888, false), false);
    }

    public static Image createImage(String name) throws IOException {
        InputStream is = Res.open(name);
        if (is == null) throw new IOException("recurso no encontrado: " + name);
        try {
            Bitmap b = BitmapFactory.decodeStream(is, null, decodeOptions());
            if (b == null) throw new IOException("no se pudo decodificar: " + name);
            return new Image(b, false);
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }

    public static Image createImage(InputStream stream) throws IOException {
        Bitmap b = BitmapFactory.decodeStream(stream, null, decodeOptions());
        if (b == null) throw new IOException("no se pudo decodificar el stream");
        return new Image(b, false);
    }

    /** Subregion con transformacion (valores Sprite.TRANS_*). */
    public static Image createImage(Image img, int x, int y, int w, int h, int transform) {
        if (img == null) throw new NullPointerException("image");
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > img.getWidth() || y + h > img.getHeight()) {
            throw new IllegalArgumentException("region");
        }
        if (transform < 0 || transform > 7) throw new IllegalArgumentException("transform");
        Matrix m = null;
        if (transform != 0) {
            float[] mv;
            switch (transform) {
                case 1:  mv = new float[]{1, 0, 0, 0, -1, h, 0, 0, 1}; break;
                case 2:  mv = new float[]{-1, 0, w, 0, 1, 0, 0, 0, 1}; break;
                case 3:  mv = new float[]{-1, 0, w, 0, -1, h, 0, 0, 1}; break;
                case 4:  mv = new float[]{0, 1, 0, 1, 0, 0, 0, 0, 1}; break;
                case 5:  mv = new float[]{0, -1, h, 1, 0, 0, 0, 0, 1}; break;
                case 6:  mv = new float[]{0, 1, 0, -1, 0, w, 0, 0, 1}; break;
                default: mv = new float[]{0, -1, h, -1, 0, w, 0, 0, 1}; break;
            }
            m = new Matrix();
            m.setValues(mv);
        }
        // createBitmap con matriz aplica la transformacion sobre la subregion
        Bitmap b = Bitmap.createBitmap(img.bmp, x, y, w, h, m, false);
        if (b == img.bmp) b = img.bmp.copy(Bitmap.Config.ARGB_8888, false);
        return new Image(b, false);
    }

    /**
     * @param processAlpha si es false se ignora el canal alfa de los datos (imagen opaca),
     *                     tal como define MIDP.
     */
    public static Image createRGBImage(int[] rgb, int w, int h, boolean processAlpha) {
        if (rgb == null) throw new NullPointerException("rgb");
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("size");
        if (rgb.length < w * h) throw new ArrayIndexOutOfBoundsException("rgb");
        int[] data = rgb;
        if (!processAlpha) {
            data = new int[w * h];
            for (int i = 0; i < data.length; i++) data[i] = rgb[i] | 0xFF000000;
        }
        Bitmap b = Bitmap.createBitmap(data, w, h, Bitmap.Config.ARGB_8888);
        return new Image(b, false);
    }

    public Graphics getGraphics() {
        if (!mutable) throw new IllegalStateException("imagen inmutable");
        if (graphics == null) graphics = new Graphics(new android.graphics.Canvas(bmp), bmp);
        return graphics;
    }

    public int getWidth() { return bmp.getWidth(); }
    public int getHeight() { return bmp.getHeight(); }
    public boolean isMutable() { return mutable; }

    public void getRGB(int[] rgb, int offset, int scanlength, int x, int y, int w, int h) {
        bmp.getPixels(rgb, offset, scanlength, x, y, w, h);
    }
}
