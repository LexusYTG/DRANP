package javax.microedition.media;
import javax.microedition.media.control.VolumeControl;
class VolumeControlImpl implements VolumeControl {
    private final AndroidPlayer p;
    private boolean mute = false;
    private int level = 100;
    VolumeControlImpl() { this.p = null; }
    VolumeControlImpl(AndroidPlayer p) { this.p = p; }
    public void setMute(boolean m) { if (p != null) p.setMute(m); else mute = m; }
    public boolean isMuted() { return p != null ? p.isMute() : mute; }
    public int setLevel(int l) { if (p != null) p.setVolume(l); else level = l; return l; }
    public int getLevel() { return p != null ? p.getVolume() : level; }
}
