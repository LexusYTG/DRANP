package javax.microedition.lcdui;

public abstract class Displayable {
    /** Resolucion logica del MIDlet original (240x320). */
    public static final int LOGICAL_WIDTH = 240;
    public static final int LOGICAL_HEIGHT = 320;

    private volatile boolean shown = false;
    private boolean fullScreen = false;

    public boolean isShown() { return shown; }
    void setShown(boolean s) { shown = s; }

    public int getWidth() { return LOGICAL_WIDTH; }
    public int getHeight() { return LOGICAL_HEIGHT; }

    public void setFullScreenMode(boolean b) { fullScreen = b; }
    public boolean isFullScreenMode() { return fullScreen; }

    public boolean hasPointerEvents() { return true; }
    public boolean hasPointerMotionEvents() { return true; }
    public boolean hasRepeatEvents() { return false; }
}
