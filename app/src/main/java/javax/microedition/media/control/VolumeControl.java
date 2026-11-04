package javax.microedition.media.control;
import javax.microedition.media.Control;
public interface VolumeControl extends Control {
    void setMute(boolean m);
    boolean isMuted();
    int setLevel(int l);
    int getLevel();
}
