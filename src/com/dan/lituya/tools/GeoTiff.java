package com.dan.lituya.tools;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Liest GeoTIFF in Kacheln, ohne GDAL (Phase 2, neu nach dem Vorbild von Timmendorf {@code tools.GeoTiff}, das nur
 * unkomprimierte Dateien las). Was das Kartenmaterial von Lituya Bay braucht:
 * <ul>
 * <li>klassisches TIFF und BigTIFF, Byte-Folge beider Art;</li>
 * <li>unkomprimiert, LZW (5) und Deflate (8, 32946), mit Prädiktor 1, 2 (waagerecht) und 3 (Gleitkomma);</li>
 * <li>Kacheln oder Streifen, Kanäle nebeneinander (PlanarConfiguration 1), 8, 16 und 32 Bit, ganzzahlig oder float32;</li>
 * <li>die Lage aus ModelTiepoint und ModelPixelScale oder ModelTransformation, PixelIsArea oder PixelIsPoint
 * (GTRasterTypeGeoKey), die Abbildung aus den GeoKeys (EPSG-Code oder benutzerdefinierte Albers-Abbildung).</li>
 * </ul>
 * So liegen die Daten vor: Lidar 2019 und IfSAR 2012 (LZW, Prädiktor 3, Kacheln 256²), 3DEP (LZW, Kacheln 512²),
 * Copernicus, BlueTopo und USACE (Deflate), GEBCO (16 Bit, Streifen). Gelesen wird je Kachel mit positionsgebundenem
 * Zugriff auf den Kanal, also aus mehreren Fäden zugleich; ein kleiner Speicher hält die zuletzt gelesenen Kacheln.
 */
public final class GeoTiff implements Closeable {
    public final File file;
    public final int width, height, samples, bits, format;
    /** Ecke des linken oberen Pixels (x, y in der Abbildung) und Pixelgröße; y nimmt nach unten ab. */
    public final double x0, y0, sizeX, sizeY;
    /** EPSG-Code der Abbildung (32767: benutzerdefiniert, dann {@link #albers}). */
    public final int epsg;
    /** Für benutzerdefinierte Albers-Abbildungen: {lat0, lon0, lat1, lat2, falseEast, falseNorth}, sonst null. */
    public final double[] albers;
    /** Wert für „kein Wert“ aus GDAL_NODATA (NaN, wenn keiner angegeben ist). */
    public final double nodata;
    public final int compression, predictor;
    final ByteOrder order;
    final boolean tiled;
    final int tileW, tileH, across, down;
    private final long[] offsets, counts;
    private final FileChannel ch;
    private final int id = NUMMER.incrementAndGet();
    private static final java.util.concurrent.atomic.AtomicInteger NUMMER = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * Ein Speicher der gelesenen Kacheln für alle Dateien zusammen, begrenzt auf {@code -Dlituya.cache.mb} (Vorgabe
     * 1 500 MB): Mit einem Speicher je Datei hielten 312 Lidar-Kacheln zusammen Gigabytes.
     */
    private static final long BUDGET = Long.getLong("lituya.cache.mb", 1500) * 1024 * 1024 / 4;
    private static long belegt;
    private static final Map<Long, float[]> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) {
            if (belegt > BUDGET) { belegt -= e.getValue().length; return true; }
            return false;
        }
    };

    private GeoTiff(File f, FileChannel ch, ByteOrder bo, Map<Integer, long[]> t, Map<Integer, double[]> d, Map<Integer, String> s) throws IOException {
        this.file = f;
        this.ch = ch;
        this.order = bo;
        width = (int) one(t, 256);
        height = (int) one(t, 257);
        bits = (int) t.getOrDefault(258, new long[]{1})[0];
        samples = (int) opt(t, 277, 1);
        format = (int) t.getOrDefault(339, new long[]{1})[0];
        compression = (int) opt(t, 259, 1);
        predictor = (int) opt(t, 317, 1);
        int planar = (int) opt(t, 284, 1);
        if (compression != 1 && compression != 5 && compression != 8 && compression != 32946)
            throw new IOException(f.getName() + ": Kompression " + compression + " wird nicht gelesen");
        if (planar != 1 && samples > 1) throw new IOException(f.getName() + ": Kanäle getrennt abgelegt (PlanarConfiguration 2) wird nicht gelesen");
        if (bits != 8 && bits != 16 && bits != 32) throw new IOException(f.getName() + ": " + bits + " Bit werden nicht gelesen");
        tiled = t.containsKey(322);
        tileW = tiled ? (int) one(t, 322) : width;
        tileH = tiled ? (int) one(t, 323) : (int) Math.min(opt(t, 278, height), height);
        across = (width + tileW - 1) / tileW;
        down = (height + tileH - 1) / tileH;
        offsets = tiled ? t.get(324) : t.get(273);
        counts = tiled ? t.get(325) : t.get(279);
        double nd = Double.NaN;
        String ns = s.get(42113);
        if (ns != null) try { nd = Double.parseDouble(ns.trim()); } catch (NumberFormatException e) { /* keiner */ }
        nodata = nd;
        // GeoKeys
        long[] gk = t.get(34735);
        double[] gd = d.getOrDefault(34736, new double[0]);
        Map<Integer, Integer> keys = new HashMap<>();
        Map<Integer, Double> dkeys = new HashMap<>();
        if (gk != null)
            for (int i = 4; i + 3 < gk.length; i += 4) {
                int id = (int) gk[i], loc = (int) gk[i + 1], cnt = (int) gk[i + 2], val = (int) gk[i + 3];
                if (loc == 0) keys.put(id, val);
                else if (loc == 34736 && val < gd.length && cnt >= 1) dkeys.put(id, gd[val]);
            }
        int code = keys.getOrDefault(3072, keys.getOrDefault(2048, 0));
        epsg = code;
        if (code == 32767 && keys.getOrDefault(3075, 0) == 11) {
            double lon0 = first(dkeys, 3084, 3088, 3080), lat0 = first(dkeys, 3085, 3089, 3081);
            albers = new double[]{lat0, lon0, first(dkeys, 3078), first(dkeys, 3079), first(dkeys, 3086, 3082), first(dkeys, 3087, 3083)};
        } else albers = null;
        boolean point = keys.getOrDefault(1025, 1) == 2;
        double[] tie = d.get(33922), scale = d.get(33550), mt = d.get(34264);
        if (scale != null && tie != null) {
            sizeX = scale[0];
            sizeY = scale[1];
            x0 = tie[3] - tie[0] * sizeX - (point ? sizeX / 2 : 0);
            y0 = tie[4] + tie[1] * sizeY + (point ? sizeY / 2 : 0);
        } else if (mt != null) {
            sizeX = mt[0];
            sizeY = -mt[5];
            x0 = mt[3] - (point ? sizeX / 2 : 0);
            y0 = mt[7] + (point ? sizeY / 2 : 0);
        } else throw new IOException(f.getName() + ": keine Lage (ModelTiepoint/ModelPixelScale)");
    }

    private static double first(Map<Integer, Double> m, int... ids) {
        for (int i : ids) if (m.containsKey(i)) return m.get(i);
        return 0;
    }

    private static long one(Map<Integer, long[]> t, int tag) throws IOException {
        long[] v = t.get(tag);
        if (v == null || v.length == 0) throw new IOException("TIFF-Tag " + tag + " fehlt");
        return v[0];
    }

    private static long opt(Map<Integer, long[]> t, int tag, long dflt) {
        long[] v = t.get(tag);
        return v == null || v.length == 0 ? dflt : v[0];
    }

    /** Öffnet eine Datei und liest den Kopf (erstes Bild). */
    public static GeoTiff open(File f) throws IOException {
        FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        try {
            ByteBuffer h = read(ch, 0, 16, ByteOrder.LITTLE_ENDIAN);
            ByteOrder bo = h.get(0) == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            h.order(bo);
            int magic = h.getShort(2) & 0xffff;
            boolean big = magic == 43;
            if (magic != 42 && !big) throw new IOException(f.getName() + ": kein TIFF");
            long ifd = big ? h.getLong(8) : h.getInt(4) & 0xffffffffL;
            ByteBuffer c = read(ch, ifd, big ? 8 : 2, bo);
            long n = big ? c.getLong(0) : c.getShort(0) & 0xffff;
            int es = big ? 20 : 12;
            ByteBuffer e = read(ch, ifd + (big ? 8 : 2), (int) (n * es), bo);
            Map<Integer, long[]> t = new HashMap<>();
            Map<Integer, double[]> d = new HashMap<>();
            Map<Integer, String> s = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int p = i * es;
                int tag = e.getShort(p) & 0xffff, type = e.getShort(p + 2) & 0xffff;
                long cnt = big ? e.getLong(p + 4) : e.getInt(p + 4) & 0xffffffffL;
                int size = switch (type) { case 1, 2, 6, 7 -> 1; case 3, 8 -> 2; case 4, 9, 11 -> 4; case 5, 10, 12, 16, 17, 18 -> 8; default -> 1; };
                long bytes = size * cnt;
                ByteBuffer v;
                int inl = big ? 8 : 4;
                if (bytes <= inl) {
                    v = ByteBuffer.allocate(8).order(bo);
                    for (int k = 0; k < inl; k++) v.put(k, e.get(p + (big ? 12 : 8) + k));
                } else {
                    long off = big ? e.getLong(p + 12) : e.getInt(p + 8) & 0xffffffffL;
                    v = read(ch, off, (int) bytes, bo);
                }
                int cn = (int) cnt;
                if (type == 2) {
                    byte[] b = new byte[cn];
                    for (int k = 0; k < cn; k++) b[k] = v.get(k);
                    s.put(tag, new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).replace("\0", ""));
                } else if (type == 11 || type == 12) {
                    double[] dv = new double[cn];
                    for (int k = 0; k < cn; k++) dv[k] = type == 11 ? v.getFloat(k * 4) : v.getDouble(k * 8);
                    d.put(tag, dv);
                } else {
                    long[] lv = new long[cn];
                    for (int k = 0; k < cn; k++)
                        lv[k] = switch (type) {
                            case 1, 7 -> v.get(k) & 0xff;
                            case 6 -> v.get(k);
                            case 3 -> v.getShort(k * 2) & 0xffff;
                            case 8 -> v.getShort(k * 2);
                            case 4 -> v.getInt(k * 4) & 0xffffffffL;
                            case 9 -> v.getInt(k * 4);
                            case 16, 17, 18 -> v.getLong(k * 8);
                            default -> v.get(k) & 0xff;
                        };
                    t.put(tag, lv);
                }
            }
            return new GeoTiff(f, ch, bo, t, d, s);
        } catch (IOException | RuntimeException ex) {
            ch.close();
            throw ex instanceof IOException io ? io : new IOException(f.getName() + ": " + ex, ex);
        }
    }

    private static ByteBuffer read(FileChannel ch, long pos, int len, ByteOrder bo) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(len).order(bo);
        while (b.hasRemaining()) {
            int r = ch.read(b, pos + b.position());
            if (r < 0) throw new IOException("Dateiende bei " + (pos + b.position()));
        }
        b.flip();
        return b;
    }

    /** Pixelmitte (x, y in der Abbildung) der Spalte i, Zeile j. */
    public double cx(double i) { return x0 + (i + 0.5) * sizeX; }
    public double cy(double j) { return y0 - (j + 0.5) * sizeY; }

    /** Spalte und Zeile (mit Bruchteil, Pixelmitte = ganze Zahl) zu x, y in der Abbildung. */
    public double col(double x) { return (x - x0) / sizeX - 0.5; }
    public double row(double y) { return (y0 - y) / sizeY - 0.5; }

    /** Rechte untere Ecke. */
    public double x1() { return x0 + width * sizeX; }
    public double y1() { return y0 - height * sizeY; }

    /** Ein Wert (Kanal band) am Pixel (i, j); NaN außerhalb und für „kein Wert“. */
    public float get(int i, int j, int band) throws IOException {
        if (i < 0 || j < 0 || i >= width || j >= height) return Float.NaN;
        int tx = i / tileW, ty = j / tileH;
        float[] t = tile(tx, ty);
        return t[((j - ty * tileH) * tileW + (i - tx * tileW)) * samples + band];
    }

    /** Eine Kachel (oder ein Streifen) als float, Kanäle nebeneinander, „kein Wert“ als NaN. */
    public float[] tile(int tx, int ty) throws IOException {
        int idx = ty * across + tx;
        long key = ((long) id << 32) | idx;
        synchronized (CACHE) {
            float[] c = CACHE.get(key);
            if (c != null) return c;
        }
        float[] v = decode(idx);
        synchronized (CACHE) {
            if (CACHE.put(key, v) == null) belegt += v.length;
        }
        return v;
    }

    private float[] decode(int idx) throws IOException {
        int n = tileW * tileH * samples, bps = bits / 8;
        float[] out = new float[n];
        if (idx >= offsets.length || counts[idx] == 0) { java.util.Arrays.fill(out, Float.NaN); return out; }
        ByteBuffer raw = read(ch, offsets[idx], (int) counts[idx], order);
        byte[] src = new byte[raw.remaining()];
        raw.get(src);
        int rows = tileH;
        int rowBytes = tileW * samples * bps;
        byte[] b = switch (compression) {
            case 5 -> lzw(src, rows * rowBytes);
            case 8, 32946 -> inflate(src, rows * rowBytes);
            default -> src;
        };
        if (b.length < rows * rowBytes) b = java.util.Arrays.copyOf(b, rows * rowBytes);
        ByteBuffer bb;
        if (predictor == 3) {
            byte[] o = new byte[rows * rowBytes];
            int wc = tileW * samples;
            for (int r = 0; r < rows; r++) {
                int base = r * rowBytes;
                for (int k = samples; k < rowBytes; k++) b[base + k] = (byte) (b[base + k] + b[base + k - samples]);
                for (int c = 0; c < wc; c++)
                    for (int byt = 0; byt < bps; byt++) o[base + c * bps + byt] = b[base + byt * wc + c];
            }
            bb = ByteBuffer.wrap(o).order(ByteOrder.BIG_ENDIAN);
        } else {
            bb = ByteBuffer.wrap(b).order(order);
            if (predictor == 2) {
                for (int r = 0; r < rows; r++)
                    for (int c = samples; c < tileW * samples; c++) {
                        int p = r * rowBytes + c * bps, q = p - samples * bps;
                        switch (bps) {
                            case 1 -> b[p] = (byte) (b[p] + b[q]);
                            case 2 -> bb.putShort(p, (short) (bb.getShort(p) + bb.getShort(q)));
                            default -> bb.putInt(p, bb.getInt(p) + bb.getInt(q));
                        }
                    }
            }
        }
        float nd = (float) nodata;
        boolean hasNd = !Double.isNaN(nodata);
        for (int k = 0; k < n; k++) {
            int p = k * bps;
            float v;
            if (bits == 32 && format == 3) v = bb.getFloat(p);
            else if (bits == 32) v = format == 2 ? bb.getInt(p) : (float) (bb.getInt(p) & 0xffffffffL);
            else if (bits == 16) v = format == 2 ? bb.getShort(p) : bb.getShort(p) & 0xffff;
            else v = format == 2 ? bb.get(p) : bb.get(p) & 0xff;
            if (hasNd && (v == nd || Math.abs(v - nd) <= Math.abs(nd) * 1e-6f)) v = Float.NaN;
            out[k] = v;
        }
        return out;
    }

    static byte[] inflate(byte[] src, int expect) throws IOException {
        Inflater inf = new Inflater();
        inf.setInput(src);
        byte[] out = new byte[Math.max(expect, 16)];
        int n = 0;
        try {
            while (!inf.finished() && n < out.length) {
                int r = inf.inflate(out, n, out.length - n);
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += r;
            }
        } catch (DataFormatException e) {
            throw new IOException("Deflate: " + e.getMessage(), e);
        } finally {
            inf.end();
        }
        return out;
    }

    /** TIFF-LZW (höchstwertiges Bit zuerst, Codebreite wechselt einen Code früher). */
    static byte[] lzw(byte[] src, int expect) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(expect);
        byte[][] dict = new byte[4096][];
        for (int i = 0; i < 256; i++) dict[i] = new byte[]{(byte) i};
        int next = 258, width = 9;
        long bitbuf = 0;
        int bitcnt = 0, pos = 0;
        byte[] prev = null;
        while (true) {
            while (bitcnt < width && pos < src.length) {
                bitbuf = (bitbuf << 8) | (src[pos++] & 0xff);
                bitcnt += 8;
            }
            if (bitcnt < width) break;
            int code = (int) ((bitbuf >>> (bitcnt - width)) & ((1 << width) - 1));
            bitcnt -= width;
            if (code == 257) break;
            if (code == 256) {
                next = 258;
                width = 9;
                prev = null;
                continue;
            }
            byte[] entry;
            if (code < next && dict[code] != null) entry = dict[code];
            else if (code == next && prev != null) {
                entry = java.util.Arrays.copyOf(prev, prev.length + 1);
                entry[prev.length] = prev[0];
            } else break;
            out.write(entry, 0, entry.length);
            if (prev != null && next < 4096) {
                byte[] ne = java.util.Arrays.copyOf(prev, prev.length + 1);
                ne[prev.length] = entry[0];
                dict[next++] = ne;
                if (next + 1 >= (1 << width) && width < 12) width++;
            }
            prev = entry;
            if (out.size() >= expect) break;
        }
        return out.toByteArray();
    }

    @Override public void close() throws IOException { ch.close(); }

    @Override public String toString() {
        return String.format(java.util.Locale.ROOT, "%s %d×%d×%d %d Bit (Format %d), Kompression %d/%d, %s %d×%d, EPSG %d%s, Pixel %.6g×%.6g, Ecke %.6f %.6f",
                file.getName(), width, height, samples, bits, format, compression, predictor, tiled ? "Kacheln" : "Streifen", tileW, tileH, epsg,
                albers != null ? " (Albers)" : "", sizeX, sizeY, x0, y0);
    }
}
