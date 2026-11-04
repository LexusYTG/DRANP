package javax.microedition.lcdui;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import com.diamondrush.port.game.Res;

public class Display {
    /** La Activity se entera de que hay un nuevo Displayable visible. */
    public interface Listener {
        void onSetCurrent(Displayable d);
    }

    private static Display instance;
    private static Listener listener;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Displayable current;

    private Display() {}

    public static synchronized Display getDisplay(javax.microedition.midlet.MIDlet m) {
        if (instance == null) instance = new Display();
        return instance;
    }

    public static void setListener(Listener l) {
        listener = l;
        // si ya hay algo en pantalla, re-notificar (Activity recreada)
        Display d = instance;
        if (l != null && d != null && d.current != null) l.onSetCurrent(d.current);
    }

    public void setCurrent(Displayable d) {
        Displayable old = this.current;
        if (old == d) return;
        if (old instanceof Canvas) ((Canvas) old).onShown(false);
        this.current = d;
        Listener l = listener;
        if (l != null) l.onSetCurrent(d);
        if (d instanceof Canvas) ((Canvas) d).onShown(true);
    }

    public Displayable getCurrent() { return current; }

    /** Ejecuta r en el hilo de UI (equivale al hilo de eventos del MIDP). */
    public void callSerially(Runnable r) {
        if (r != null) mainHandler.post(r);
    }

    public boolean isColor() { return true; }
    public int numColors() { return 1 << 24; }
    public int numAlphaLevels() { return 256; }
    public int getColor(int colorSpecifier) { return 0xFFFFFF; }
    public int getBorderStyle(boolean highlighted) { return Graphics.SOLID; }

    public boolean vibrate(int ms) {
        Context ctx = Res.getContext();
        if (ctx == null) return false;
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return false;
            if (ms <= 0) { v.cancel(); return true; }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
            return true;
        } catch (SecurityException e) {
            return false; // falta el permiso VIBRATE
        }
    }

    /** No hay forma portable de controlar la luz de fondo; se reporta como no soportado. */
    public boolean flashBacklight(int ms) { return false; }
}
