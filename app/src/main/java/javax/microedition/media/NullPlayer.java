package javax.microedition.media;
import javax.microedition.media.control.VolumeControl;
import java.util.*;
class NullPlayer implements Player {
    private final List<PlayerListener> listeners = new ArrayList<>();
    private int state = UNREALIZED;
    private int loopCount = 1;
    private long mediaTime = 0;
    private final VolumeControl vc = new VolumeControlImpl();
    public int getState() { return state; }
    public void realize() { state = REALIZED; }
    public void prefetch() { if (state < PREFETCHED) state = PREFETCHED; }
    public void start() {
        state = STARTED;
        for (PlayerListener l : listeners) l.playerUpdate(this, PlayerListener.STARTED, null);
        for (PlayerListener l : listeners) l.playerUpdate(this, PlayerListener.END_OF_MEDIA, null);
    }
    public void stop() { state = PREFETCHED; }
    public void deallocate() { state = REALIZED; }
    public void close() { state = CLOSED; for (PlayerListener l : listeners) l.playerUpdate(this, PlayerListener.CLOSED, null); }
    public void setLoopCount(int c) { loopCount = c; }
    public long setMediaTime(long t) { mediaTime = t; return t; }
    public long getMediaTime() { return mediaTime; }
    public long getDuration() { return -1; }
    public String getContentType() { return "audio/x-null"; }
    public void addPlayerListener(PlayerListener l) { listeners.add(l); }
    public void removePlayerListener(PlayerListener l) { listeners.remove(l); }
    public Control getControl(String type) { return vc; }
    public Control[] getControls() { return new Control[] { vc }; }
}
