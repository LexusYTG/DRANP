package javax.microedition.lcdui;

import android.graphics.Paint;
import android.graphics.Typeface;

public class Font {
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;
    public static final int FONT_STATIC_TEXT = 0, FONT_INPUT_TEXT = 1;

    /** Paint ya configurado con tipografia y tamanio (para pantalla logica de 240x320). */
    public final Paint paint = new Paint();

    private final int face, style, size;
    private final Paint.FontMetrics fm;

    private static Font defaultFont;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        Typeface base;
        switch (face) {
            case FACE_MONOSPACE: base = Typeface.MONOSPACE; break;
            case FACE_PROPORTIONAL: base = Typeface.SANS_SERIF; break;
            default: base = Typeface.DEFAULT; break;
        }
        int tfStyle = Typeface.NORMAL;
        if ((style & STYLE_BOLD) != 0 && (style & STYLE_ITALIC) != 0) tfStyle = Typeface.BOLD_ITALIC;
        else if ((style & STYLE_BOLD) != 0) tfStyle = Typeface.BOLD;
        else if ((style & STYLE_ITALIC) != 0) tfStyle = Typeface.ITALIC;
        paint.setTypeface(Typeface.create(base, tfStyle));
        paint.setTextSize(size == SIZE_SMALL ? 10f : size == SIZE_LARGE ? 16f : 13f);
        paint.setAntiAlias(true);
        paint.setUnderlineText((style & STYLE_UNDERLINED) != 0);
        fm = paint.getFontMetrics();
    }

    public static synchronized Font getDefaultFont() {
        if (defaultFont == null) defaultFont = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
        return defaultFont;
    }

    public static Font getFont(int face, int style, int size) {
        return new Font(face, style, size);
    }

    public static Font getFont(int fontSpecifier) { return getDefaultFont(); }

    public int getFace() { return face; }
    public int getStyle() { return style; }
    public int getSize() { return size; }
    public boolean isPlain() { return style == STYLE_PLAIN; }
    public boolean isBold() { return (style & STYLE_BOLD) != 0; }
    public boolean isItalic() { return (style & STYLE_ITALIC) != 0; }
    public boolean isUnderlined() { return (style & STYLE_UNDERLINED) != 0; }

    public int getHeight() { return (int) Math.ceil(fm.descent - fm.ascent + fm.leading); }
    public int getBaselinePosition() { return (int) Math.ceil(-fm.ascent); }
    public int getDescent() { return (int) Math.ceil(fm.descent); }

    public int charWidth(char ch) { return (int) Math.ceil(paint.measureText(String.valueOf(ch))); }
    public int charsWidth(char[] ch, int off, int len) { return (int) Math.ceil(paint.measureText(ch, off, len)); }
    public int stringWidth(String s) { return s == null ? 0 : (int) Math.ceil(paint.measureText(s)); }
    public int substringWidth(String s, int off, int len) { return (int) Math.ceil(paint.measureText(s, off, off + len)); }
}
