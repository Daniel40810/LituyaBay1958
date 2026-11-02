package com.dan.lituya.scene;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Die vorgerechnete Bahn eines Boots (Phase 15a, {@code tools.Boote1958} → {@code daten/ereignis/boote/<lauf>/bahn_<boot>.bin}):
 * je Bild (15 je Sekunde) Ereigniszeit, Schwerpunkt (Szene), Drehung als Quaternion {w, x, y, z}, Wasser im Rumpf (kg),
 * Zustand (Bits: 1 Kette gerissen, 2 gekentert, 4 gesunken, 8 auf Grund, 16 Maschine an), das gerissene Glied und die
 * Knoten der Kette. Alles float32 Little Endian nach einem Kopf {@code "LBB1"}, Bilder, Kettenknoten.
 */
public final class BootBahn {
    public static final int FELDER = 11;
    public static final int GERISSEN = 1, GEKENTERT = 2, GESUNKEN = 4, AUF_GRUND = 8, MASCHINE = 16;

    public final String name;
    public final int knoten;
    final float[] t;
    final float[][] f;      // je Bild: x, y, z, qw, qx, qy, qz, Wasser, Zustand, Glied, (frei)
    final float[][] kette;  // je Bild: 3 · knoten

    BootBahn(String name, int knoten, float[] t, float[][] f, float[][] kette) {
        this.name = name; this.knoten = knoten; this.t = t; this.f = f; this.kette = kette;
    }

    public int bilder() { return t.length; }

    public double anfang() { return t[0]; }

    public double ende() { return t[t.length - 1]; }

    /** Zustand zur Zeit tt (Bilder linear, die Drehung normiert): {x, y, z, qw, qx, qy, qz, Wasser, Zustand, Glied}. */
    public double[] zustand(double tt) {
        int b = index(tt);
        double a = b + 1 < t.length ? Math.max(0, Math.min(1, (tt - t[b]) / Math.max(1e-6, t[b + 1] - t[b]))) : 0;
        float[] p = f[b], q = f[Math.min(t.length - 1, b + 1)];
        double dot = p[3] * q[3] + p[4] * q[4] + p[5] * q[5] + p[6] * q[6], sg = dot < 0 ? -1 : 1;
        double[] r = new double[10];
        for (int i = 0; i < 3; i++) r[i] = p[i] + (q[i] - p[i]) * a;
        double l = 0;
        for (int i = 3; i < 7; i++) { r[i] = p[i] + (sg * q[i] - p[i]) * a; l += r[i] * r[i]; }
        l = Math.sqrt(l);
        for (int i = 3; i < 7; i++) r[i] /= l;
        r[7] = p[7] + (q[7] - p[7]) * a;
        r[8] = a < 0.5 ? p[8] : q[8];
        r[9] = a < 0.5 ? p[9] : q[9];
        return r;
    }

    /** Knoten der Kette zur Zeit tt (Welt), null ohne Kette. */
    public double[][] kette(double tt) {
        if (knoten == 0) return null;
        int b = index(tt);
        double a = b + 1 < t.length ? Math.max(0, Math.min(1, (tt - t[b]) / Math.max(1e-6, t[b + 1] - t[b]))) : 0;
        float[] p = kette[b], q = kette[Math.min(t.length - 1, b + 1)];
        double[][] r = new double[knoten][3];
        for (int i = 0; i < knoten; i++) for (int c = 0; c < 3; c++) r[i][c] = p[i * 3 + c] + (q[i * 3 + c] - p[i * 3 + c]) * a;
        return r;
    }

    int index(double tt) {
        if (tt <= t[0]) return 0;
        if (tt >= t[t.length - 1]) return t.length - 1;
        int b = java.util.Arrays.binarySearch(t, (float) tt);
        if (b < 0) b = -b - 2;
        return Math.max(0, Math.min(t.length - 1, b));
    }

    // ------------------------------------------------------------------ Datei

    public static BootBahn lesen(File file, String name) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        byte[] m = new byte[4];
        b.get(m);
        if (!new String(m, java.nio.charset.StandardCharsets.US_ASCII).equals("LBB1")) throw new IOException(file + ": keine Bahn (LBB1)");
        int n = b.getInt(), k = b.getInt();
        float[] t = new float[n];
        float[][] f = new float[n][FELDER - 1], ke = new float[n][3 * k];
        for (int i = 0; i < n; i++) {
            t[i] = b.getFloat();
            for (int c = 0; c < FELDER - 1; c++) f[i][c] = b.getFloat();
            for (int c = 0; c < 3 * k; c++) ke[i][c] = b.getFloat();
        }
        return new BootBahn(name, k, t, f, ke);
    }

    /** Sammelt Bilder und schreibt sie am Ende. */
    public static final class Schreiber {
        final int knoten;
        final List<float[]> bilder = new ArrayList<>();

        public Schreiber(int knoten) { this.knoten = knoten; }

        /** Ein Bild: t, Lage, Quaternion, Wasser, Zustand, Glied, Knoten (3 · knoten oder null). */
        public void bild(double t, double[] pos, double[] q, double wasser, int zustand, int glied, double[][] kn) {
            float[] v = new float[FELDER + 3 * knoten];
            v[0] = (float) t;
            for (int i = 0; i < 3; i++) v[1 + i] = (float) pos[i];
            for (int i = 0; i < 4; i++) v[4 + i] = (float) q[i];
            v[8] = (float) wasser;
            v[9] = zustand;
            v[10] = glied;
            for (int i = 0; i < knoten && kn != null; i++) for (int c = 0; c < 3; c++) v[FELDER + i * 3 + c] = (float) kn[i][c];
            bilder.add(v);
        }

        public void schreiben(File file) throws IOException {
            file.getParentFile().mkdirs();
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), 1 << 20))) {
                ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
                b.put("LBB1".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(bilder.size()).putInt(knoten);
                o.write(b.array());
                ByteBuffer z = ByteBuffer.allocate((FELDER + 3 * knoten) * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (float[] v : bilder) {
                    z.clear();
                    for (float x : v) z.putFloat(x);
                    o.write(z.array());
                }
            }
        }
    }
}
