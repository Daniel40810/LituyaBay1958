package com.dan.lituya.scene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Ein Lauf der Echtzeit im Speicher (Phase 13c, „Was wäre wenn“): je Gitter die Bilder des Wasserstands, wie sie die
 * Grafikkarte liefert, gepackt wie in {@link EreignisDaten} (Stufen zu 5 cm, Deflate). Das ist der Rückspulpuffer: die
 * {@link Wiedergabe} spielt ihn wie eine Vorrechnung, vorwärts, rückwärts und in Zeitlupe, soweit er gerechnet ist.
 * Die höchsten Stände je Kachel wachsen mit jedem Bild (für das Wegschneiden).
 */
public final class LiveLauf implements Wiedergabe.Bilder {
    private static int zaehler;
    public final String name;
    public final int nummer;
    public final List<EreignisDaten.Gitter> gitter;
    public final List<Wiedergabe.Spur> spuren = new ArrayList<>();
    private final List<List<byte[]>> bilder = new ArrayList<>();
    private final Deflater def = new Deflater(Deflater.BEST_SPEED);
    private long bytes;
    private Wiedergabe wiedergabe;

    /** Ein leerer Lauf über den Gittern (UTM), Ereigniszeit der Bilder wie in den Dateien. */
    public LiveLauf(String name, List<EreignisDaten.Gitter> gitter) {
        synchronized (LiveLauf.class) { nummer = ++zaehler; }
        this.name = name;
        this.gitter = List.copyOf(gitter);
        for (int k = 0; k < gitter.size(); k++) {
            Wiedergabe.Spur s = new Wiedergabe.Spur(100 + k, Wiedergabe.Art.WELLE, name, k, gitter.get(k), new double[0], this);
            float[] m = new float[s.ntx * s.ntz];
            Arrays.fill(m, Float.NaN);
            s.kachelMax = m;
            if (k > 0) spuren.get(k - 1).feiner = s.szene();
            spuren.add(s);
            bilder.add(new ArrayList<>());
        }
    }

    /** Die Wiedergabe, in deren Vorrat neue Bilder gleich ungepackt gehen. */
    public void wiedergabe(Wiedergabe w) { wiedergabe = w; }

    /** Hängt ein Bild an (Ereigniszeit t, Wasserstand je Zelle, NaN trocken); t muss wachsen. */
    public void bild(int k, double t, float[] eta) {
        Wiedergabe.Spur s = spuren.get(k);
        short[] r = new short[eta.length];
        for (int c = 0; c < r.length; c++)
            r[c] = Float.isNaN(eta[c]) ? EreignisDaten.TROCKEN : (short) Math.max(-32767, Math.min(32767, Math.round(eta[c] / EreignisDaten.STUFE)));
        // höchste Stände je Kachel (vor dem Bild sichtbar machen)
        EreignisDaten.Gitter g = s.g;
        float[] m = s.kachelMax.clone();
        int K = Wiedergabe.KACHEL;
        for (int j = 0; j < g.nz(); j++)
            for (int i = 0; i < g.nx(); i++) {
                short v = r[j * g.nx() + i];
                if (v == EreignisDaten.TROCKEN) continue;
                float h = (float) (v * EreignisDaten.STUFE);
                int ti = Math.min(s.ntx - 1, i / K), tj = Math.min(s.ntz - 1, j / K);
                for (int dj = -1; dj <= 0; dj++)
                    for (int di = -1; di <= 0; di++) {
                        int a = ti + di, b = tj + dj;
                        if (a < 0 || b < 0 || (di == -1 && i % K != 0) || (dj == -1 && j % K != 0)) continue;
                        int c = b * s.ntx + a;
                        if (!(m[c] >= h)) m[c] = h;
                    }
            }
        s.kachelMax = m;
        byte[] z;
        synchronized (this) {
            byte[] raw = new byte[r.length * 2];
            java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(r);
            def.reset();
            def.setInput(raw);
            def.finish();
            byte[] buf = new byte[raw.length / 4 + 1024];
            int n = 0;
            while (!def.finished()) {
                if (n == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
                n += def.deflate(buf, n, buf.length - n);
            }
            z = Arrays.copyOf(buf, n);
            bilder.get(k).add(z);
            bytes += n;
        }
        int b = bilder.get(k).size() - 1;
        Wiedergabe w = wiedergabe;
        if (w != null) w.einlegen(s, b, r);
        double[] alt = s.t, neu = Arrays.copyOf(alt, alt.length + 1);
        neu[alt.length] = t;
        s.t = neu;
    }

    @Override
    public short[] roh(int k, int b) throws IOException {
        byte[] z;
        synchronized (this) { z = bilder.get(k).get(b); }
        EreignisDaten.Gitter g = gitter.get(k);
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
        java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(v);
        return v;
    }

    /** Gepackte Bytes im Puffer. */
    public synchronized long bytes() { return bytes; }

    /** Gerechnet bis (Ereigniszeit des letzten Bilds des gröbsten Gitters, NaN ohne Bild). */
    public double bis() { return spuren.isEmpty() ? Double.NaN : spuren.get(0).t1(); }
}
