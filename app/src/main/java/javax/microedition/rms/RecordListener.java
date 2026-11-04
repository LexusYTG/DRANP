package javax.microedition.rms;
public interface RecordListener {
    void recordAdded(RecordStore r, int id);
    void recordChanged(RecordStore r, int id);
    void recordDeleted(RecordStore r, int id);
}
