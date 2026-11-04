package com.dan.lituya.scene;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Ein vorgerechneter Lauf des Ereignisses in {@code daten/ereignis/<lauf>/} (Phase 13, Werkbuch 4.4). Je Gitter der
 * Welle ein Stapel von Bildern des Wasserstands und zwei Raster:
 * <ul>
 * <li>{@code EREIGNIS.txt}: Lauf, Quelle, Uhr ({@code start_s}: Ereigniszeit der Rechenzeit 0), Pegel, je Gitter
 * {@code g<k>.e0, .n0} (Südwestecke UTM 8 N), {@code .zelle, .nx, .nz}, Zahl der Bilder.</li>
 * <li>{@code welle_g<k>.bin}: Bilder hintereinander, je Bild {@code double t} (Ereigniszeit, s), {@code int} Länge, dann
 * die mit Deflate gepackten Werte: {@code short} je Zelle, Little Endian, Zeile für Zeile von Süd nach Nord, in der Zeile
 * von West nach Ost; Wasserstand über NAVD88 in Schritten zu {@link #STUFE} m, {@link #TROCKEN} = trocken oder Fels.
 * Ruhiges Wasser packt sich fast zu nichts.</li>
 * <li>{@code geschw_g<k>.bin} (seit Phase 15a, wenn der Lauf sie schreibt): zu jedem Bild die Strömung über die Tiefe
 * gemittelt, gleiche Folge wie die Welle, je Zelle zwei {@code short} {u nach Ost, v nach Nord} in Schritten zu
 * {@link #STUFE_U} m/s.</li>
 * <li>{@code max_g<k>.r32}, {@code ankunft_g<k>.r32}: float32 Little Endian in derselben Ordnung: höchster Stand
 * (NaN: nie nass) und Ereigniszeit, zu der der Stand zuerst mehr als 1 m über dem Pegel war (NaN: nie).</li>
 * </ul>
 */
public final class EreignisDaten {
    public static final double STUFE = 0.05;
    public static final short TROCKEN = Short.MIN_VALUE;
    /** Stufe der Strömung (m/s). */
    public static final double STUFE_U = 0.01;

    /** Ein Gitter: Südwestecke, Zelle, Größe. */
    public record Gitter(double e0, double n0, double zelle, int nx, int nz) {
        public int index(double e, double n) {
            int i = (int) Math.floor((e - e0) / zelle), j = (int) Math.floor((n - n0) / zelle);
            return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
        }

        public boolean enthaelt(double e, double n) { return index(e, n) >= 0; }

        public double e(int i) { return e0 + (i + 0.5) * zelle; }

        public double n(int j) { return n0 + (j + 0.5) * zelle; }
    }

    public static File ordner(File daten, String lauf) { return new File(new File(daten, "ereignis"), lauf); }

    // ------------------------------------------------------------------ Schreiben

    public static final class Schreiber implements AutoCloseable {
        final File dir;
        final List<Gitter> gitter;
        final DataOutputStream[] out;
        final int[] bilder;
        final Deflater def = new Deflater(Deflater.BEST_SPEED);
        long bytes;

        public Schreiber(File dir, List<Gitter> gitter) throws IOException {
            this.dir = dir;
            this.gitter = gitter;
            dir.mkdirs();
            out = new DataOutputStream[gitter.size()];
            bilder = new int[gitter.size()];
            for (int k = 0; k < gitter.size(); k++) out[k] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "welle_g" + k + ".bin")), 1 << 20));
        }

        /** Ein Bild des Gitters k: Wasserstand je Zelle (NaN trocken). */
        public void bild(int k, double t, float[] eta) throws IOException {
            ByteBuffer b = ByteBuffer.allocate(eta.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (float v : eta) b.putShort(Float.isNaN(v) ? TROCKEN : (short) Math.max(-32767, Math.min(32767, Math.round(v / STUFE))));
            def.reset();
            def.setInput(b.array());
            def.finish();
            byte[] buf = new byte[eta.length * 2 + 1024];
            int n = 0;
            while (!def.finished()) {
                if (n == buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                n += def.deflate(buf, n, buf.length - n);
            }
            out[k].writeDouble(t);
            out[k].writeInt(n);
            out[k].write(buf, 0, n);
            bilder[k]++;
            bytes += n + 12;
        }

        private DataOutputStream[] gout;

        /** Die Strömung des Gitters k zum Bild der Zeit t: {u nach Ost, v nach Nord} je Zelle (m/s, Achsen der Bucht). */
        public void geschw(int k, double t, float[][] uw) throws IOException {
            if (gout == null) gout = new DataOutputStream[gitter.size()];
            if (gout[k] == null) gout[k] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "geschw_g" + k + ".bin")), 1 << 20));
            int n = uw[0].length;
            ByteBuffer b = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int c = 0; c < n; c++) {
                b.putShort((short) Math.max(-32767, Math.min(32767, Math.round(uw[0][c] / STUFE_U))));
                b.putShort((short) Math.max(-32767, Math.min(32767, Math.round(uw[1][c] / STUFE_U))));
            }
            def.reset();
            def.setInput(b.array());
            def.finish();
            byte[] buf = new byte[n * 4 + 1024];
            int m = 0;
            while (!def.finished()) {
                if (m == buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                m += def.deflate(buf, m, buf.length - m);
            }
            gout[k].writeDouble(t);
            gout[k].writeInt(m);
            gout[k].write(buf, 0, m);
            bytes += m + 12;
        }

        public void raster(String name, int k, float[] v) throws IOException {
            ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            b.asFloatBuffer().put(v);
            Files.write(new File(dir, name + "_g" + k + ".r32").toPath(), b.array());
            bytes += v.length * 4L;
        }

        /** Schreibt EREIGNIS.txt mit den Gittern und weiteren Zeilen. */
        public void beschreibung(Properties p) throws IOException {
            for (int k = 0; k < gitter.size(); k++) {
                Gitter g = gitter.get(k);
                p.setProperty("g" + k + ".e0", fmt(g.e0()));
                p.setProperty("g" + k + ".n0", fmt(g.n0()));
                p.setProperty("g" + k + ".zelle", fmt(g.zelle()));
                p.setProperty("g" + k + ".nx", Integer.toString(g.nx()));
                p.setProperty("g" + k + ".nz", Integer.toString(g.nz()));
                p.setProperty("g" + k + ".bilder", Integer.toString(bilder[k]));
            }
            p.setProperty("gitter", Integer.toString(gitter.size()));
            p.setProperty("stufe_m", fmt(STUFE));
            try (FileOutputStream f = new FileOutputStream(new File(dir, "EREIGNIS.txt"))) { p.store(f, "Lauf des Ereignisses (LituyaBay1958 Phase 13), siehe com.dan.lituya.scene.EreignisDaten"); }
        }

        public long bytes() { return bytes; }

        @Override public void close() throws IOException {
            for (DataOutputStream o : out) o.close();
            if (gout != null) for (DataOutputStream o : gout) if (o != null) o.close();
            def.end();
        }
    }

    static String fmt(double v) { return String.format(Locale.ROOT, "%.3f", v); }

    // ------------------------------------------------------------------ Lesen

    public static final class Leser implements AutoCloseable {
        public final Properties p = new Properties();
        public final List<Gitter> gitter = new ArrayList<>();
        final List<double[]> zeiten = new ArrayList<>();
        final List<long[]> lagen = new ArrayList<>();
        final RandomAccessFile[] in;
        RandomAccessFile[] gin;
        final List<long[]> glagen = new ArrayList<>();

        public Leser(File dir) throws IOException {
            try (java.io.FileInputStream f = new java.io.FileInputStream(new File(dir, "EREIGNIS.txt"))) { p.load(f); }
            int n = Integer.parseInt(p.getProperty("gitter"));
            in = new RandomAccessFile[n];
            for (int k = 0; k < n; k++) {
                gitter.add(new Gitter(d("g" + k + ".e0"), d("g" + k + ".n0"), d("g" + k + ".zelle"), (int) d("g" + k + ".nx"), (int) d("g" + k + ".nz")));
                in[k] = new RandomAccessFile(new File(dir, "welle_g" + k + ".bin"), "r");
                List<Double> t = new ArrayList<>();
                List<Long> pos = new ArrayList<>();
                long at = 0, len = in[k].length();
                while (at + 12 <= len) {
                    in[k].seek(at);
                    t.add(in[k].readDouble());
                    int size = in[k].readInt();
                    pos.add(at);
                    at += 12 + size;
                }
                zeiten.add(t.stream().mapToDouble(Double::doubleValue).toArray());
                lagen.add(pos.stream().mapToLong(Long::longValue).toArray());
            }
            // Strömung (seit 15a), nur wenn es sie für alle Gitter mit gleich vielen Bildern gibt
            boolean alle = true;
            for (int k = 0; k < n; k++) alle &= new File(dir, "geschw_g" + k + ".bin").isFile();
            if (alle) {
                gin = new RandomAccessFile[n];
                for (int k = 0; k < n; k++) {
                    gin[k] = new RandomAccessFile(new File(dir, "geschw_g" + k + ".bin"), "r");
                    List<Long> pos = new ArrayList<>();
                    long at = 0, len = gin[k].length();
                    while (at + 12 <= len) {
                        gin[k].seek(at + 8);
                        int size = gin[k].readInt();
                        pos.add(at);
                        at += 12 + size;
                    }
                    glagen.add(pos.stream().mapToLong(Long::longValue).toArray());
                    if (pos.size() != zeiten.get(k).length) alle = false;
                }
                if (!alle) { for (RandomAccessFile f : gin) f.close(); gin = null; glagen.clear(); }
            }
        }

        /** Ob der Lauf die Strömung je Bild hat (seit Phase 15a). */
        public boolean hatStroemung() { return gin != null; }

        /** Strömung zu Bild b von Gitter k: {u nach Ost, v nach Nord} je Zelle (m/s); null ohne. */
        public float[][] geschw(int k, int b) throws IOException {
            if (gin == null) return null;
            Gitter g = gitter.get(k);
            byte[] z;
            synchronized (this) {
                gin[k].seek(glagen.get(k)[b] + 8);
                int size = gin[k].readInt();
                z = new byte[size];
                gin[k].readFully(z);
            }
            Inflater inf = new Inflater();
            inf.setInput(z);
            int n = g.nx() * g.nz();
            byte[] raw = new byte[n * 4];
            try {
                int m = 0;
                while (m < raw.length && !inf.finished()) m += inf.inflate(raw, m, raw.length - m);
            } catch (DataFormatException e) {
                throw new IOException(e);
            } finally {
                inf.end();
            }
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            float[] u = new float[n], w = new float[n];
            for (int c = 0; c < n; c++) { u[c] = (float) (bb.getShort() * STUFE_U); w[c] = (float) (bb.getShort() * STUFE_U); }
            return new float[][]{u, w};
        }

        public double d(String key) { return Double.parseDouble(p.getProperty(key).trim()); }

        /** Ereigniszeiten der Bilder von Gitter k. */
        public double[] zeiten(int k) { return zeiten.get(k).clone(); }

        /** Zahl der Bilder von Gitter k. */
        public int bilder(int k) { return zeiten.get(k).length; }

        /** Ereigniszeit von Bild b des Gitters k. */
        public double zeit(int k, int b) { return zeiten.get(k)[b]; }

        /** Bild b von Gitter k als Wasserstand (NaN trocken). */
        public float[] bild(int k, int b) throws IOException {
            short[] r = roh(k, b);
            float[] v = new float[r.length];
            for (int c = 0; c < v.length; c++) v[c] = r[c] == TROCKEN ? Float.NaN : (float) (r[c] * STUFE);
            return v;
        }

        /** Bild b von Gitter k roh: Stufen zu {@link #STUFE} m, {@link #TROCKEN} trocken (Phase 13b, für die Grafikkarte). */
        public short[] roh(int k, int b) throws IOException {
            Gitter g = gitter.get(k);
            byte[] z;
            synchronized (this) {
                in[k].seek(lagen.get(k)[b] + 8);
                int size = in[k].readInt();
                z = new byte[size];
                in[k].readFully(z);
            }
            Inflater inf = new Inflater();
            inf.setInput(z);
            byte[] raw = new byte[g.nx() * g.nz() * 2];
            try {
                int n = 0;
                while (n < raw.length && !inf.finished()) n += inf.inflate(raw, n, raw.length - n);
            } catch (DataFormatException e) {
                throw new IOException(e);
            } finally {
                inf.end();
            }
            short[] v = new short[g.nx() * g.nz()];
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(v);
            return v;
        }

        public float[] raster(File dir, String name, int k) throws IOException {
            byte[] b = Files.readAllBytes(new File(dir, name + "_g" + k + ".r32").toPath());
            float[] v = new float[b.length / 4];
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
            return v;
        }

        @Override public void close() throws IOException {
            for (RandomAccessFile f : in) f.close();
            if (gin != null) for (RandomAccessFile f : gin) f.close();
        }
    }
}
