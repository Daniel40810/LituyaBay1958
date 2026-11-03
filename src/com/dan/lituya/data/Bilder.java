package com.dan.lituya.data;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Bilder für {@link Speicher} (Phase 19): Vorschau 320 px und ein Bild im Hintergrund sichern, ohne den Bildlauf aufzuhalten. */
public final class Bilder {
    private Bilder() { }

    /** Vorschau 320 px breit als PNG. */
    public static byte[] vorschau(BufferedImage im) throws IOException {
        int w = 320, h = Math.max(1, (int) Math.round(im.getHeight() * 320.0 / im.getWidth()));
        BufferedImage v = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = v.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(im, 0, 0, w, h, null);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(v, "png", o);
        return o.toByteArray();
    }

    /**
     * Sichert die geschriebene PNG-Datei f als Bild (Art Foto oder Blatt) im eigenen Faden: erst Oracle, dann die Dateien.
     * Kopf: Weltzeit, Simulationszeit, Zeitstand, Kamera (UTM, Höhe, Richtung, Neigung), Regiebuch, Bildrate, Notiz.
     */
    public static void sichernSpaeter(String art, File f, String weltzeit, double zeitS, String zeitstand, double e, double n, double h, double richtung, double neigung,
                                      String regiebuch, double bildrate, String notiz, java.util.function.Consumer<String> meldung) {
        Thread t = new Thread(() -> {
            try {
                byte[] png = Files.readAllBytes(f.toPath());
                BufferedImage im = ImageIO.read(f);
                String datei = f.getPath().replace('\\', '/');
                int i = datei.indexOf("docs/");
                if (i > 0) datei = datei.substring(i);
                Speicher.Bild b = new Speicher.Bild(art, datei, png, vorschau(im), im.getWidth(), im.getHeight(), weltzeit, zeitS, zeitstand, e, n, h, richtung, neigung,
                        regiebuch, bildrate, notiz);
                Speicher sp = Speicher.aktuell();
                try { sp.bildSpeichern(b); }
                catch (IOException ex) { Speicher.datei().bildSpeichern(b); sp = Speicher.datei(); }
                if (meldung != null) meldung.accept(art + " gesichert (" + sp.wo() + ")");
            } catch (Exception ex) {
                if (meldung != null) meldung.accept(art + " nicht gesichert: " + ex.getMessage());
            }
        }, "LituyaBay1958-Bild");
        t.setDaemon(false);
        t.start();
    }
}
