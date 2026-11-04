package javax.microedition.media;
public interface PlayerListener {
    String STARTED = "started", STOPPED = "stopped", END_OF_MEDIA = "endOfMedia",
           DURATION_UPDATED = "durationUpdated", DEVICE_AVAILABLE = "deviceAvailable",
           DEVICE_UNAVAILABLE = "deviceUnavailable", VOLUME_CHANGED = "volumeChanged",
           ERROR = "error", CLOSED = "closed";
    void playerUpdate(Player p, String event, Object data);
}
