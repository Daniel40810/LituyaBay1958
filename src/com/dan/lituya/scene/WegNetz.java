package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/**
 * {@code LB_NET_WEG} als Datei (Phase 16a, Werkbuch 7.5): Wege über Gelände, gerichtet (je Richtung eine Kante, weil
 * Tobler bergauf und bergab verschieden ist), in zwei Ebenen: Ebene 1 das 10-m-Gitter im Kern, Ebene 2 das 50-m-Gitter
 * der Szene; jeder Knoten der Ebene 1 hat den 50-m-Knoten, in dem er liegt, als Elternknoten. Die Ebenen sind nicht
 * durch Kanten verbunden: Fragen im Kern rechnen auf Ebene 1, Fragen über die Szene (Washburn 1934) auf Ebene 2.
 * <p>Weil es Millionen Kanten sind, liegen Knoten und Kanten binär in {@code daten/netz/weg} ({@code knoten.bin},
 * {@code kanten.bin}, Little Endian, Format im Kopf von {@code WEG.txt}); dazu {@code umstieg.tsv}, {@code washburn.tsv},
 * {@code pruefwege.tsv}, {@code zeit_kern_10m.r32} und {@code WEG.txt}.
 */
public final class WegNetz {
    public static final String ORDNER = "netz/weg";
    /** Raster der Ebenen: NW-Ecke, Zelle, Breite, Höhe (wie tools.Klassen.KERN und SZENE). */
    public static final double[][] RASTER = {
            {Site.KERN.eMin(), Site.KERN.nMax(), 10, Math.round(Site.KERN.breite() / 10), Math.round(Site.KERN.hoehe() / 10)},
            {Site.SZENE.eMin(), Site.SZENE.nMax(), 50, Math.round(Site.SZENE.breite() / 50), Math.round(Site.SZENE.hoehe() / 50)}};

    public int n, m;
    public byte[] ebene, klasse;
    public int[] zelle, parent;
    public float[] hoehe;
    public int[] von, nach;
    public float[] cost;
    /** Kanten je Knoten (CSR nach von; die Kanten sind beim Bau nach von sortiert). */
    public int[] aus;
    public String text = "";

    public double ost(int k) { double[] r = RASTER[ebene[k] - 1]; return r[0] + ((zelle[k] % (int) r[3]) + 0.5) * r[2]; }
    public double nord(int k) { double[] r = RASTER[ebene[k] - 1]; return r[1] - ((zelle[k] / (int) r[3]) + 0.5) * r[2]; }

    public void index() {
        aus = new int[n + 1];
        for (int e = 0; e < m; e++) aus[von[e] + 1]++;
        for (int i = 0; i < n; i++) aus[i + 1] += aus[i];
        for (int e = 1; e < m; e++) if (von[e] < von[e - 1]) throw new IllegalStateException("kanten.bin nicht nach START sortiert");
    }

    /** Dijkstra vom Knoten s bis zur Grenze (s); Zeit je Knoten (NaN: nicht erreicht) und der Vorgänger (Kante). */
    public double[] dijkstra(int s, double grenze, int[] vorKante) {
        double[] t = new double[n];
        Arrays.fill(t, Double.POSITIVE_INFINITY);
        if (vorKante != null) Arrays.fill(vorKante, -1);
        t[s] = 0;
        // binärer Heap über Knoten mit Positionen
        int[] heap = new int[n], pos = new int[n];
        Arrays.fill(pos, -1);
        int hn = 0;
        heap[hn] = s; pos[s] = hn++;
        while (hn > 0) {
            int v = heap[0];
            pos[v] = -2;
            int last = heap[--hn];
            if (hn > 0) { heap[0] = last; pos[last] = 0; runter(heap, pos, hn, t, 0); }
            if (t[v] > grenze) { t[v] = Double.POSITIVE_INFINITY; break; }
            for (int e = aus[v]; e < aus[v + 1]; e++) {
                int w = nach[e];
                if (pos[w] == -2) continue;
                double d = t[v] + cost[e];
                if (d < t[w]) {
                    t[w] = d;
                    if (vorKante != null) vorKante[w] = e;
                    if (pos[w] < 0) { heap[hn] = w; pos[w] = hn++; }
                    hoch(heap, pos, t, pos[w]);
                }
            }
        }
        for (int i = 0; i < n; i++) if (Double.isInfinite(t[i]) || t[i] > grenze) t[i] = Double.NaN;
        return t;
    }

    /** Wie dijkstra, aber mit Startzeit t0 (Ereigniszeit) und nur vor der Front je Knoten (NaN: keine); Zeiten als Ereigniszeit. */
    public double[] dijkstraVorFront(int s, double t0, double[] front, double grenze, int[] vorKante) {
        double[] t = new double[n];
        Arrays.fill(t, Double.POSITIVE_INFINITY);
        if (vorKante != null) Arrays.fill(vorKante, -1);
        if (!Double.isNaN(front[s]) && !(t0 < front[s])) { Arrays.fill(t, Double.NaN); return t; }
        t[s] = t0;
        int[] heap = new int[1024], pos = new int[n];
        Arrays.fill(pos, -1);
        int hn = 0;
        heap[hn] = s; pos[s] = hn++;
        while (hn > 0) {
            int v = heap[0];
            pos[v] = -2;
            int last = heap[--hn];
            if (hn > 0) { heap[0] = last; pos[last] = 0; runter(heap, pos, hn, t, 0); }
            if (t[v] - t0 > grenze) { t[v] = Double.POSITIVE_INFINITY; break; }
            for (int e = aus[v]; e < aus[v + 1]; e++) {
                int w = nach[e];
                if (pos[w] == -2) continue;
                double d = t[v] + cost[e];
                if (!Double.isNaN(front[w]) && !(d < front[w])) continue;
                if (d < t[w]) {
                    t[w] = d;
                    if (vorKante != null) vorKante[w] = e;
                    if (pos[w] < 0) { if (hn == heap.length) heap = Arrays.copyOf(heap, hn * 2); heap[hn] = w; pos[w] = hn++; }
                    hoch(heap, pos, t, pos[w]);
                }
            }
        }
        for (int i = 0; i < n; i++) if (Double.isInfinite(t[i]) || t[i] - t0 > grenze) t[i] = Double.NaN;
        return t;
    }

    static void hoch(int[] h, int[] pos, double[] t, int i) {
        int x = h[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (t[h[p]] <= t[x]) break;
            h[i] = h[p]; pos[h[i]] = i; i = p;
        }
        h[i] = x; pos[x] = i;
    }

    static void runter(int[] h, int[] pos, int hn, double[] t, int i) {
        int x = h[i];
        while (true) {
            int l = 2 * i + 1;
            if (l >= hn) break;
            int r = l + 1, c = r < hn && t[h[r]] < t[h[l]] ? r : l;
            if (t[h[c]] >= t[x]) break;
            h[i] = h[c]; pos[h[i]] = i; i = c;
        }
        h[i] = x; pos[x] = i;
    }

    /** Der Weg von s nach z (Knoten), oder null. */
    public int[] weg(int s, int z, double[] zeit) {
        int[] vor = new int[n];
        double[] t = dijkstra(s, Double.MAX_VALUE, vor);
        if (Double.isNaN(t[z])) return null;
        if (zeit != null) zeit[0] = t[z];
        int k = 0;
        for (int v = z; v != s; v = von[vor[v]]) k++;
        int[] r = new int[k + 1];
        int i = k;
        for (int v = z; ; v = von[vor[v]]) { r[i--] = v; if (v == s) break; }
        return r;
    }

    /** Der nächste Knoten der Ebene eb zum Punkt (e, n), dessen Klasse in der Liste ist (leer = jede), bis r m; −1 wenn keiner. */
    public int naechster(int eb, double e, double nn, double r, byte... klassen) { return naechster(eb, e, nn, r, null, -1, klassen); }

    /** Wie oben, nur Knoten der Komponente kn (komp[k] == kn, kn < 0: jede). */
    public int naechster(int eb, double e, double nn, double r, int[] komp, int kn, byte... klassen) {
        double[] g = RASTER[eb - 1];
        int W = (int) g[3], H = (int) g[4];
        int[] idx = zellIndex(eb);
        int i0 = (int) Math.floor((e - g[0]) / g[2]), j0 = (int) Math.floor((g[1] - nn) / g[2]), q = (int) Math.ceil(r / g[2]);
        int best = -1;
        double bd = r;
        for (int j = Math.max(0, j0 - q); j <= Math.min(H - 1, j0 + q); j++)
            for (int i = Math.max(0, i0 - q); i <= Math.min(W - 1, i0 + q); i++) {
                int k = idx[j * W + i];
                if (k < 0) continue;
                if (komp != null && kn >= 0 && komp[k] != kn) continue;
                if (klassen.length > 0) { boolean ok = false; for (byte b : klassen) ok |= klasse[k] == b; if (!ok) continue; }
                double d = Math.hypot(ost(k) - e, nord(k) - nn);
                if (d < bd) { bd = d; best = k; }
            }
        return best;
    }

    /** Zusammenhangskomponenten (Kanten in beiden Richtungen): Nummer je Knoten; groesste[e − 1] = Nummer der größten Komponente der Ebene e, zahl[0] = Anzahl. */
    public int[] komponenten(int[] groesste, int[] zahl) {
        int[] k = new int[n];
        Arrays.fill(k, -1);
        int[] q = new int[n];
        int c = 0;
        int[] best = new int[2], groesse = new int[2];
        best[0] = best[1] = -1;
        for (int s = 0; s < n; s++) {
            if (k[s] >= 0) continue;
            int qh = 0, qt = 0;
            q[qt++] = s;
            k[s] = c;
            while (qh < qt) {
                int v = q[qh++];
                for (int e = aus[v]; e < aus[v + 1]; e++) { int x = nach[e]; if (k[x] < 0) { k[x] = c; q[qt++] = x; } }
            }
            int eb = ebene[s] - 1;
            if (qt > groesse[eb]) { groesse[eb] = qt; best[eb] = c; }
            c++;
        }
        if (groesste != null) { groesste[0] = best[0]; groesste[1] = best[1]; }
        if (zahl != null) zahl[0] = c;
        return k;
    }

    private int[][] zi = new int[2][];

    /** Knoten je Zelle einer Ebene (−1: keiner). */
    public int[] zellIndex(int eb) {
        if (zi[eb - 1] == null) {
            double[] g = RASTER[eb - 1];
            int[] a = new int[(int) g[3] * (int) g[4]];
            Arrays.fill(a, -1);
            for (int k = 0; k < n; k++) if (ebene[k] == eb) a[zelle[k]] = k;
            zi[eb - 1] = a;
        }
        return zi[eb - 1];
    }

    // ------------------------------------------------------------------ Dateien

    public static boolean da(File daten) { return new File(new File(daten, ORDNER), "kanten.bin").isFile(); }

    public void schreiben(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        d.mkdirs();
        try (FileChannel ch = FileChannel.open(new File(d, "knoten.bin").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer b = ByteBuffer.allocate(4 + n * 14).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(n).put(ebene).put(klasse);
            for (int v : zelle) b.putInt(v);
            for (float v : hoehe) b.putFloat(v);
            for (int v : parent) b.putInt(v);
            b.flip();
            while (b.hasRemaining()) ch.write(b);
        }
        try (FileChannel ch = FileChannel.open(new File(d, "kanten.bin").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer k = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(m);
            k.flip();
            ch.write(k);
            for (int[] a : new int[][]{von, nach}) schreibe(ch, a, null);
            schreibe(ch, null, cost);
        }
    }

    static void schreibe(FileChannel ch, int[] a, float[] f) throws IOException {
        int len = a != null ? a.length : f.length, block = 1 << 20;
        ByteBuffer b = ByteBuffer.allocate(4 * block).order(ByteOrder.LITTLE_ENDIAN);
        for (int s = 0; s < len; s += block) {
            b.clear();
            for (int i = s; i < Math.min(len, s + block); i++) if (a != null) b.putInt(a[i]); else b.putFloat(f[i]);
            b.flip();
            while (b.hasRemaining()) ch.write(b);
        }
    }

    public static WegNetz lesen(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        WegNetz w = new WegNetz();
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(new File(d, "knoten.bin").toPath())).order(ByteOrder.LITTLE_ENDIAN);
        w.n = b.getInt();
        w.ebene = new byte[w.n]; w.klasse = new byte[w.n]; w.zelle = new int[w.n]; w.hoehe = new float[w.n]; w.parent = new int[w.n];
        b.get(w.ebene).get(w.klasse);
        b.asIntBuffer().get(w.zelle);
        b.position(b.position() + 4 * w.n);
        b.asFloatBuffer().get(w.hoehe);
        b.position(b.position() + 4 * w.n);
        b.asIntBuffer().get(w.parent);
        try (FileChannel ch = FileChannel.open(new File(d, "kanten.bin").toPath(), StandardOpenOption.READ)) {
            ByteBuffer k = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(k);
            k.flip();
            w.m = k.getInt();
            w.von = new int[w.m]; w.nach = new int[w.m]; w.cost = new float[w.m];
            lies(ch, w.von, null);
            lies(ch, w.nach, null);
            lies(ch, null, w.cost);
        }
        File t = new File(d, "WEG.txt");
        if (t.isFile()) w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
        w.index();
        return w;
    }

    static void lies(FileChannel ch, int[] a, float[] f) throws IOException {
        int len = a != null ? a.length : f.length, block = 1 << 20;
        ByteBuffer b = ByteBuffer.allocate(4 * block).order(ByteOrder.LITTLE_ENDIAN);
        for (int s = 0; s < len; s += block) {
            int z = Math.min(len, s + block) - s;
            b.clear().limit(4 * z);
            while (b.hasRemaining()) if (ch.read(b) < 0) throw new IOException("kanten.bin zu kurz");
            b.flip();
            if (a != null) b.asIntBuffer().get(a, s, z); else b.asFloatBuffer().get(f, s, z);
        }
    }

    /** SHA-256 über knoten.bin und kanten.bin. */
    public static String pruefsumme(File daten) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        File d = new File(daten, ORDNER);
        for (String f : new String[]{"knoten.bin", "kanten.bin"})
            try (var in = Files.newInputStream(new File(d, f).toPath())) {
                byte[] b = new byte[1 << 20];
                for (int r; (r = in.read(b)) > 0; ) md.update(b, 0, r);
            }
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    /** Ein Wert aus WEG.txt oder null. */
    public String wert(String s) {
        for (String z : text.split("\\R")) if (z.startsWith(s + "=")) return z.substring(s.length() + 1).trim();
        return null;
    }

    /** „Wege (LB_NET_WEG): 1 h vom Strand …; Washburn 1934 …“ aus WEG.txt, oder ein Hinweis. */
    public static String zeile(File daten) {
        File t = new File(new File(daten, ORDNER), "WEG.txt");
        if (!t.isFile()) return "Wege: daten/netz/weg fehlt (ant aufbereitung -Dschritte=klassen+weg)";
        try {
            WegNetz w = new WegNetz();
            w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
            String a = w.wert("stunde.badger"), b = w.wert("washburn");
            return "Wege (LB_NET_WEG): 1 h von Anchorage Cove " + (a == null ? "?" : a.split(";")[0]) + "; Washburn 1934 " + (b == null ? "?" : b.split(";")[0]);
        } catch (IOException e) {
            return "Wege: " + e.getMessage();
        }
    }

    /** „Flucht an Land: späteste Abfahrt Edrie +194 s, Badger +254 s“ aus LANDFLUCHT.txt, oder ein Hinweis. */
    public static String landfluchtZeile(File daten) {
        File t = new File(new File(daten, ORDNER), "LANDFLUCHT.txt");
        if (!t.isFile()) return "Flucht an Land: daten/netz/weg/LANDFLUCHT.txt fehlt (ant aufbereitung -Dschritte=landflucht)";
        try {
            WegNetz w = new WegNetz();
            w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
            StringBuilder b = new StringBuilder("Flucht an Land (Boot, Strand, zu Fuß über den Auflauf), späteste Abfahrt nach dem Stoß:");
            for (String bt : new String[]{"edrie", "badger"}) {
                String v = w.wert("landflucht." + bt);
                b.append(' ').append(Character.toUpperCase(bt.charAt(0))).append(bt.substring(1)).append(' ')
                        .append(v == null || v.equals("keine") ? "keine" : String.format(Locale.GERMAN, "%+d s", Integer.parseInt(v))).append(bt.equals("edrie") ? "," : "");
            }
            return b.toString();
        } catch (IOException e) {
            return "Flucht an Land: " + e.getMessage();
        }
    }

    @Override public String toString() {
        int n1 = 0;
        for (byte b : ebene) if (b == 1) n1++;
        return String.format(Locale.GERMAN, "LB_NET_WEG: %,d Knoten (%,d Ebene 1 zu 10 m, %,d Ebene 2 zu 50 m), %,d Kanten gerichtet", n, n1, n - n1, m);
    }
}
