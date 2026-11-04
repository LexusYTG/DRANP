package javax.microedition.rms;

import com.diamondrush.port.game.Res;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementación de RMS sobre archivos propios.
 *
 * Formato (big endian, via DataOutputStream):
 *   int magic, int formato, int version, long lastModified, int nextId, int count,
 *   count x { int id, int len, byte[len] }
 * Se escribe a un .tmp y se renombra, así un corte a mitad de escritura no pisa el save anterior.
 * Los ids nunca se reutilizan (nextId se persiste).
 */
public class RecordStore {
    private static final int MAGIC = 0x52535452; // "RSTR"
    private static final int FORMAT = 1;
    private static final int MAX_NAME = 32;
    private static final int CAPACITY = 1024 * 1024;

    private static final Object LOCK = new Object();
    private static final Map<String, RecordStore> OPEN = new HashMap<String, RecordStore>();

    private final String name;
    private final File file;
    private final TreeMap<Integer, byte[]> records = new TreeMap<Integer, byte[]>();
    private final List<RecordListener> listeners = new ArrayList<RecordListener>();
    private int nextId = 1;
    private int version = 0;
    private long lastModified = 0L;
    private int openCount = 0;
    private boolean closed = false;

    private RecordStore(String name, File file) {
        this.name = name;
        this.file = file;
    }

    // ------------------------------------------------------------------ archivos

    private static File dir() throws RecordStoreException {
        File base = Res.getFilesDir();
        if (base == null) throw new RecordStoreException("Res no inicializado");
        File d = new File(base, "rms");
        if (!d.isDirectory() && !d.mkdirs() && !d.isDirectory()) {
            throw new RecordStoreException("no se pudo crear " + d);
        }
        return d;
    }

    private static String encode(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('.').append(String.format("%04x", (int) c));
            }
        }
        return sb.append(".rs").toString();
    }

    private static String decode(String fileName) {
        if (!fileName.endsWith(".rs")) return null;
        String s = fileName.substring(0, fileName.length() - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (i + 5 > s.length()) return null;
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                } catch (NumberFormatException e) {
                    return null;
                }
                i += 4;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void checkName(String name) {
        if (name == null || name.length() == 0 || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("nombre de record store invalido");
        }
    }

    private void load() throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 8192));
        try {
            if (in.readInt() != MAGIC) throw new IOException("magic invalido");
            if (in.readInt() != FORMAT) throw new IOException("formato desconocido");
            version = in.readInt();
            lastModified = in.readLong();
            nextId = in.readInt();
            int count = in.readInt();
            if (count < 0 || nextId < 1) throw new IOException("cabecera corrupta");
            records.clear();
            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int len = in.readInt();
                if (len < 0 || len > CAPACITY || id < 1 || id >= nextId) throw new IOException("registro corrupto");
                byte[] b = new byte[len];
                in.readFully(b);
                records.put(id, b);
            }
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    private void save() throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        FileOutputStream fos = new FileOutputStream(tmp);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos, 8192));
        boolean ok = false;
        try {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeInt(version);
            out.writeLong(lastModified);
            out.writeInt(nextId);
            out.writeInt(records.size());
            for (Map.Entry<Integer, byte[]> e : records.entrySet()) {
                out.writeInt(e.getKey());
                byte[] b = e.getValue();
                out.writeInt(b.length);
                out.write(b);
            }
            out.flush();
            try { fos.getFD().sync(); } catch (IOException ignored) {}
            ok = true;
        } finally {
            try { out.close(); } catch (IOException e) { ok = false; }
            if (!ok) tmp.delete();
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            if (!tmp.renameTo(file)) {
                tmp.delete();
                throw new IOException("no se pudo renombrar " + tmp);
            }
        }
    }

    private void touch() {
        version++;
        lastModified = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ API estática

    public static RecordStore openRecordStore(String name, boolean createIfNecessary) throws RecordStoreException {
        checkName(name);
        synchronized (LOCK) {
            RecordStore rs = OPEN.get(name);
            if (rs != null) {
                rs.openCount++;
                return rs;
            }
            File f = new File(dir(), encode(name));
            rs = new RecordStore(name, f);
            if (f.isFile()) {
                try {
                    rs.load();
                } catch (IOException e) {
                    // save corrupto: se aparta y se trata como inexistente
                    f.renameTo(new File(f.getPath() + ".bad"));
                    rs.records.clear();
                    rs.nextId = 1;
                    rs.version = 0;
                    if (!createIfNecessary) throw new RecordStoreNotFoundException(name);
                    try {
                        rs.touch();
                        rs.save();
                    } catch (IOException e2) {
                        throw new RecordStoreException("no se pudo crear " + name);
                    }
                }
            } else {
                if (!createIfNecessary) throw new RecordStoreNotFoundException(name);
                try {
                    rs.touch();
                    rs.save();
                } catch (IOException e) {
                    throw new RecordStoreException("no se pudo crear " + name + ": " + e.getMessage());
                }
            }
            rs.openCount = 1;
            OPEN.put(name, rs);
            return rs;
        }
    }

    /** Si el store esta abierto no hace nada (MIDP real lanzaria RecordStoreException). */
    public static void deleteRecordStore(String name) {
        if (name == null) return;
        synchronized (LOCK) {
            if (OPEN.containsKey(name)) return;
            try {
                File f = new File(dir(), encode(name));
                f.delete();
            } catch (RecordStoreException ignored) {}
        }
    }

    public static String[] listRecordStores() {
        synchronized (LOCK) {
            try {
                File[] fs = dir().listFiles();
                if (fs == null) return null;
                List<String> out = new ArrayList<String>();
                for (File f : fs) {
                    String n = decode(f.getName());
                    if (n != null) out.add(n);
                }
                return out.isEmpty() ? null : out.toArray(new String[0]);
            } catch (RecordStoreException e) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void closeRecordStore() {
        synchronized (LOCK) {
            if (closed) return;
            if (--openCount <= 0) {
                closed = true;
                listeners.clear();
                if (OPEN.get(name) == this) OPEN.remove(name);
            }
        }
    }

    private void ensureOpen() throws RecordStoreNotOpenException {
        if (closed) throw new RecordStoreNotOpenException(name);
    }

    // ------------------------------------------------------------------ registros

    public synchronized int addRecord(byte[] data, int off, int len) throws RecordStoreException {
        ensureOpen();
        if (len < 0 || (len > 0 && (data == null || off < 0 || off + len > data.length))) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (getSize() + len > CAPACITY) throw new RecordStoreFullException(name);
        byte[] copy = new byte[len];
        if (len > 0) System.arraycopy(data, off, copy, 0, len);
        int id = nextId;
        int oldVersion = version;
        long oldMod = lastModified;
        records.put(id, copy);
        nextId = id + 1;
        touch();
        try {
            save();
        } catch (IOException e) {
            records.remove(id);
            nextId = id;
            version = oldVersion;
            lastModified = oldMod;
            throw new RecordStoreException("no se pudo guardar: " + e.getMessage());
        }
        fire(0, id);
        return id;
    }

    public synchronized byte[] getRecord(int id) throws RecordStoreException {
        ensureOpen();
        byte[] b = records.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        return b.clone();
    }

    public synchronized int getRecord(int id, byte[] buf, int off) throws RecordStoreException {
        ensureOpen();
        byte[] b = records.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        if (off < 0 || off + b.length > buf.length) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(b, 0, buf, off, b.length);
        return b.length;
    }

    public synchronized int getRecordSize(int id) throws RecordStoreException {
        ensureOpen();
        byte[] b = records.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        return b.length;
    }

    public synchronized void setRecord(int id, byte[] data, int off, int len) throws RecordStoreException {
        ensureOpen();
        byte[] old = records.get(id);
        if (old == null) throw new InvalidRecordIDException("id " + id);
        if (len < 0 || (len > 0 && (data == null || off < 0 || off + len > data.length))) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (getSize() - old.length + len > CAPACITY) throw new RecordStoreFullException(name);
        byte[] copy = new byte[len];
        if (len > 0) System.arraycopy(data, off, copy, 0, len);
        int oldVersion = version;
        long oldMod = lastModified;
        records.put(id, copy);
        touch();
        try {
            save();
        } catch (IOException e) {
            records.put(id, old);
            version = oldVersion;
            lastModified = oldMod;
            throw new RecordStoreException("no se pudo guardar: " + e.getMessage());
        }
        fire(1, id);
    }

    public synchronized void deleteRecord(int id) throws RecordStoreException {
        ensureOpen();
        byte[] old = records.remove(id);
        if (old == null) throw new InvalidRecordIDException("id " + id);
        int oldVersion = version;
        long oldMod = lastModified;
        touch();
        try {
            save();
        } catch (IOException e) {
            records.put(id, old);
            version = oldVersion;
            lastModified = oldMod;
            throw new RecordStoreException("no se pudo guardar: " + e.getMessage());
        }
        fire(2, id);
    }

    // ------------------------------------------------------------------ consultas

    public String getName() { return name; }
    public synchronized int getNumRecords() { return records.size(); }
    public synchronized int getNextRecordID() { return nextId; }

    public synchronized int[] getRecordIDs() {
        int[] r = new int[records.size()];
        int i = 0;
        for (int k : records.keySet()) r[i++] = k;
        return r;
    }

    public synchronized int getSize() {
        int s = 0;
        for (byte[] b : records.values()) s += b.length;
        return s;
    }

    public synchronized int getSizeAvailable() { return Math.max(0, CAPACITY - getSize()); }
    public synchronized long getLastModified() { return lastModified; }
    public synchronized int getVersion() { return version; }

    // ------------------------------------------------------------------ listeners

    public synchronized void addRecordListener(RecordListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public synchronized void removeRecordListener(RecordListener l) {
        listeners.remove(l);
    }

    private void fire(int kind, int id) {
        RecordListener[] ls = listeners.toArray(new RecordListener[0]);
        for (RecordListener l : ls) {
            try {
                if (kind == 0) l.recordAdded(this, id);
                else if (kind == 1) l.recordChanged(this, id);
                else l.recordDeleted(this, id);
            } catch (RuntimeException ignored) {}
        }
    }
}
