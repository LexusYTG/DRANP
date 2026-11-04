package javax.microedition.media;

import android.media.MediaPlayer;
import com.diamondrush.port.game.Res;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.media.control.VolumeControl;

/**
 * Player respaldado por android.media.MediaPlayer. Los datos se vuelcan a un archivo temporal
 * en el cache de la app (con extension segun el tipo, para que MIDI se reconozca).
 * Si no se puede crear el temporal, el player queda mudo pero no rompe al juego.
 */
class AndroidPlayer implements Player {
    private MediaPlayer mp;
    private File file;
    private final List<PlayerListener> listeners = new ArrayList<PlayerListener>();
    private int state = UNREALIZED;
    private int loopCount = 1;
    private int remaining = 1;
    private int vol = 100;
    private boolean muted = false;
    private boolean atEnd = false;
    private boolean pausedByApp = false;
    private final String type;

    AndroidPlayer(InputStream is, String type) {
        this.type = type;
        try {
            File dir = Res.getCacheDir();
            file = File.createTempFile("j2me_", extFor(type), dir);
            FileOutputStream fos = new FileOutputStream(file);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            } finally {
                fos.close();
            }
            state = REALIZED;
        } catch (Exception e) {
            e.printStackTrace();
            if (file != null) { file.delete(); file = null; }
            state = REALIZED;
        }
        Manager.register(this);
    }

    private static String extFor(String type) {
        if (type == null) return ".bin";
        String t = type.toLowerCase();
        if (t.contains("mid")) return ".mid";
        if (t.contains("mpeg") || t.contains("mp3")) return ".mp3";
        if (t.contains("wav")) return ".wav";
        if (t.contains("amr")) return ".amr";
        if (t.contains("ogg")) return ".ogg";
        return ".bin";
    }

    private void fire(String event) {
        PlayerListener[] ls;
        synchronized (listeners) { ls = listeners.toArray(new PlayerListener[0]); }
        for (PlayerListener l : ls) {
            try { l.playerUpdate(this, event, null); } catch (RuntimeException ignored) {}
        }
    }

    private synchronized void ensure() {
        if (mp != null || file == null) return;
        MediaPlayer m = new MediaPlayer();
        try {
            m.setDataSource(file.getAbsolutePath());
            m.prepare();
            float v = muted ? 0f : vol / 100f;
            m.setVolume(v, v);
            m.setLooping(loopCount == -1);
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer p) { onComplete(p); }
            });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer p, int what, int extra) {
                    fire(PlayerListener.ERROR);
                    return true;
                }
            });
            mp = m;
        } catch (Exception e) {
            e.printStackTrace();
            try { m.release(); } catch (Exception ignored) {}
            mp = null;
        }
    }

    private void onComplete(MediaPlayer p) {
        boolean again;
        synchronized (this) {
            if (mp != p) return;
            again = remaining > 1;
            if (again) {
                remaining--;
                try { p.seekTo(0); p.start(); } catch (Exception e) { again = false; }
            }
            if (!again) {
                atEnd = true;
                if (state == STARTED) state = PREFETCHED;
            }
        }
        if (!again) fire(PlayerListener.END_OF_MEDIA);
    }

    public synchronized int getState() { return state; }

    public synchronized void realize() { if (state < REALIZED) state = REALIZED; }

    public void prefetch() {
        ensure();
        synchronized (this) { if (state < PREFETCHED && state != CLOSED) state = PREFETCHED; }
    }

    public void start() {
        ensure();
        synchronized (this) {
            if (mp == null || state == CLOSED) return;
            try {
                if (state == STARTED && mp.isPlaying()) return;
                if (atEnd) { mp.seekTo(0); atEnd = false; }
                if (state != STARTED && !pausedByApp) remaining = loopCount;
                pausedByApp = false;
                mp.start();
                state = STARTED;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        fire(PlayerListener.STARTED);
    }

    public void stop() {
        boolean wasStarted;
        synchronized (this) {
            wasStarted = state == STARTED;
            if (mp != null) { try { mp.pause(); } catch (Exception ignored) {} }
            if (state == STARTED) state = PREFETCHED;
            pausedByApp = false;
        }
        if (wasStarted) fire(PlayerListener.STOPPED);
    }

    public synchronized void deallocate() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
        atEnd = false;
        pausedByApp = false;
        if (state != CLOSED) state = REALIZED;
    }

    public void close() {
        deallocate();
        synchronized (this) {
            if (state == CLOSED) return;
            state = CLOSED;
            if (file != null) { file.delete(); file = null; }
        }
        Manager.unregister(this);
        fire(PlayerListener.CLOSED);
    }

    public synchronized void setLoopCount(int c) {
        if (c == 0) throw new IllegalArgumentException("loopCount 0");
        loopCount = c < -1 ? -1 : c;
        if (state != STARTED) remaining = loopCount;
        if (mp != null) { try { mp.setLooping(loopCount == -1); } catch (Exception ignored) {} }
    }

    public synchronized long setMediaTime(long t) {
        if (mp != null) { try { mp.seekTo((int) (t / 1000L)); atEnd = false; } catch (Exception ignored) {} }
        return t;
    }

    /** MIDP usa microsegundos. */
    public synchronized long getMediaTime() {
        if (mp == null) return TIME_UNKNOWN;
        try { return mp.getCurrentPosition() * 1000L; } catch (Exception e) { return TIME_UNKNOWN; }
    }

    public synchronized long getDuration() {
        if (mp == null) return TIME_UNKNOWN;
        try {
            int d = mp.getDuration();
            return d < 0 ? TIME_UNKNOWN : d * 1000L;
        } catch (Exception e) { return TIME_UNKNOWN; }
    }

    public String getContentType() { return type != null ? type : "audio/*"; }

    public void addPlayerListener(PlayerListener l) {
        if (l == null) return;
        synchronized (listeners) { if (!listeners.contains(l)) listeners.add(l); }
    }

    public void removePlayerListener(PlayerListener l) {
        synchronized (listeners) { listeners.remove(l); }
    }

    public Control getControl(String t) {
        if (t == null) return null;
        if (t.equals("VolumeControl") || t.endsWith(".VolumeControl")) return new VolumeControlImpl(this);
        return null;
    }

    public Control[] getControls() { return new Control[] { new VolumeControlImpl(this) }; }

    // --- llamado por el ciclo de vida de la app (Manager.pauseAll/resumeAll) ---

    synchronized void pauseForApp() {
        if (mp != null && state == STARTED) {
            try {
                if (mp.isPlaying()) { mp.pause(); pausedByApp = true; }
            } catch (Exception ignored) {}
        }
    }

    synchronized void resumeForApp() {
        if (mp != null && pausedByApp && state == STARTED) {
            try { mp.start(); } catch (Exception ignored) {}
        }
        pausedByApp = false;
    }

    private void applyVolume() {
        if (mp != null) {
            float v = muted ? 0f : vol / 100f;
            try { mp.setVolume(v, v); } catch (Exception ignored) {}
        }
    }

    synchronized void setVolume(int v) { vol = v < 0 ? 0 : (v > 100 ? 100 : v); applyVolume(); }
    synchronized int getVolume() { return vol; }
    synchronized void setMute(boolean m) { muted = m; applyVolume(); }
    synchronized boolean isMute() { return muted; }
}
