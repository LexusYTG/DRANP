package javax.microedition.midlet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.diamondrush.port.game.Res;

public abstract class MIDlet {
    /** La Activity registra aca que hacer cuando el MIDlet pide cerrarse. */
    public interface LifecycleListener {
        void onDestroyed();
        void onPaused();
        void onResumeRequested();
    }

    private static volatile LifecycleListener lifecycleListener;

    public static void setLifecycleListener(LifecycleListener l) { lifecycleListener = l; }

    protected MIDlet() {}

    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    /** El MIDlet termino: se cierra la Activity. */
    public final void notifyDestroyed() {
        LifecycleListener l = lifecycleListener;
        if (l != null) l.onDestroyed();
    }

    public final void notifyPaused() {
        LifecycleListener l = lifecycleListener;
        if (l != null) l.onPaused();
    }

    public final void resumeRequest() {
        LifecycleListener l = lifecycleListener;
        if (l != null) l.onResumeRequested();
    }

    /** Equivalente a los atributos del JAD/manifiesto. Las claves que no existen devuelven null. */
    public final String getAppProperty(String key) {
        if (key == null) return null;
        switch (key) {
            case "MIDlet-Version": return versionName();
            case "MIDlet-Name": return "Diamond Rush";
            case "MIDlet-Vendor": return "Gameloft";
            default: return null;
        }
    }

    private static String versionName() {
        Context ctx = Res.getContext();
        if (ctx != null) {
            try {
                String v = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
                if (v != null) return v;
            } catch (Exception ignored) {}
        }
        return "1.0.0";
    }

    /** Abre una URL en el navegador. Devuelve false: el MIDlet no necesita cerrarse. */
    public final boolean platformRequest(String url) throws javax.microedition.io.ConnectionNotFoundException {
        Context ctx = Res.getContext();
        if (ctx == null || url == null) throw new javax.microedition.io.ConnectionNotFoundException();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return false;
        } catch (Exception e) {
            throw new javax.microedition.io.ConnectionNotFoundException();
        }
    }

    public final int checkPermission(String permission) { return 1; }
}
