package com.diamondrush.port.game;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Acceso a recursos del "JAR" original, que en Android viven en assets/.
 *
 * Los MIDlets cargan datos con Class.getResourceAsStream("/archivo"), que en Android NO
 * encuentra nada en assets. Todo acceso a recursos debe pasar por aca.
 */
public final class Res {
    private static Context context;
    private static AssetManager assets;

    public static void init(Context ctx) {
        context = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        assets = context.getAssets();
    }

    public static Context getContext() { return context; }

    public static File getFilesDir() { return context != null ? context.getFilesDir() : null; }

    public static File getCacheDir() { return context != null ? context.getCacheDir() : null; }

    /** Abre un recurso. Devuelve null si no existe (igual que getResourceAsStream). */
    public static InputStream open(String name) {
        if (name == null || assets == null) return null;
        if (name.startsWith("/")) name = name.substring(1);
        try {
            return new FullReadStream(new BufferedInputStream(assets.open(name), 8192));
        } catch (IOException e) {
            return null;
        }
    }

    /** Lee un recurso completo. Devuelve null si no existe. */
    public static byte[] readAll(String name) {
        InputStream in = open(name);
        if (in == null) return null;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(16384);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf, 0, buf.length)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * El codigo J2ME asume que InputStream.read(byte[]) y skip(n) completan lo pedido
     * (en un JAR de telefono se cumple). AssetInputStream puede devolver menos, asi que
     * este wrapper repite hasta completar o llegar a EOF.
     */
    private static final class FullReadStream extends FilterInputStream {
        FullReadStream(InputStream in) { super(in); }

        @Override public int read(byte[] b) throws IOException { return read(b, 0, b.length); }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            int total = 0;
            while (total < len) {
                int n = in.read(b, off + total, len - total);
                if (n < 0) break;
                total += n;
            }
            return total == 0 ? -1 : total;
        }

        @Override public long skip(long n) throws IOException {
            long skipped = 0;
            while (skipped < n) {
                long s = in.skip(n - skipped);
                if (s <= 0) {
                    if (in.read() < 0) break; // EOF
                    s = 1;
                }
                skipped += s;
            }
            return skipped;
        }
    }

    private Res() {}
}
