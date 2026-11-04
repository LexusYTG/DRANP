package javax.microedition.media;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";

    private static final Map<AndroidPlayer, Boolean> PLAYERS = new WeakHashMap<AndroidPlayer, Boolean>();

    static void register(AndroidPlayer p) {
        synchronized (PLAYERS) { PLAYERS.put(p, Boolean.TRUE); }
    }

    static void unregister(AndroidPlayer p) {
        synchronized (PLAYERS) { PLAYERS.remove(p); }
    }

    private static List<AndroidPlayer> snapshot() {
        synchronized (PLAYERS) { return new ArrayList<AndroidPlayer>(PLAYERS.keySet()); }
    }

    /** Pausa todo lo que esta sonando (onPause de la Activity). */
    public static void pauseAll() {
        for (AndroidPlayer p : snapshot()) p.pauseForApp();
    }

    /** Reanuda lo que pauseAll pauso (onResume de la Activity). */
    public static void resumeAll() {
        for (AndroidPlayer p : snapshot()) p.resumeForApp();
    }

    public static Player createPlayer(InputStream s, String type) throws MediaException {
        return new AndroidPlayer(s, type);
    }

    public static Player createPlayer(String locator) throws MediaException {
        return new NullPlayer();
    }

    public static void playTone(final int note, final int dur, final int vol) {
        if (dur <= 0) return;
        new Thread(new Runnable() {
            @Override public void run() {
                android.media.AudioTrack t = null;
                try {
                    int nt = note < 0 ? 0 : (note > 127 ? 127 : note);
                    int vl = vol < 0 ? 0 : (vol > 100 ? 100 : vol);
                    double freq = 440.0 * Math.pow(2.0, (nt - 69) / 12.0);
                    int sr = 22050;
                    int n = (int) ((long) sr * dur / 1000L);
                    if (n <= 0) return;
                    short[] b = new short[n];
                    double amp = 32767.0 * (vl / 100.0);
                    for (int i = 0; i < n; i++) b[i] = (short) (amp * Math.sin(2 * Math.PI * freq * i / sr));
                    t = new android.media.AudioTrack(
                            android.media.AudioManager.STREAM_MUSIC, sr,
                            android.media.AudioFormat.CHANNEL_OUT_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT,
                            n * 2, android.media.AudioTrack.MODE_STATIC);
                    t.write(b, 0, n);
                    t.play();
                    Thread.sleep(dur + 60L);
                } catch (Exception e) {
                    // sin sonido
                } finally {
                    if (t != null) {
                        try { t.stop(); } catch (Exception ignored) {}
                        try { t.release(); } catch (Exception ignored) {}
                    }
                }
            }
        }, "playTone").start();
    }
}
