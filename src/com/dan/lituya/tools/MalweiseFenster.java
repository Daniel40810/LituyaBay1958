package com.dan.lituya.tools;

import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.lituya.app.LituyaApp;
import com.dan.lituya.gl.MalweiseGl;
import com.dan.lituya.gl.Renderer;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Werkbuch Welle W10b: die Bedienung des Blocks MALWEISE im echten Fenster, ohne den Bildschirm zu steuern. Startet die
 * App im selben Prozess (ohne Startbild und Datenbank, ohne Archiv), wartet auf Bedienfeld und Wald und bedient dann die
 * Swing-Komponenten des Blocks so, wie ein Klick es täte: „In den Wald“ ({@code doClick}), die Art der Malweise
 * ({@code setSelectedItem}, das löst dieselben ActionListener aus). Nach jeder Wahl prüft es, ob der Renderer die Wahl
 * übernommen hat, holt das Bild aus dem Fenster ({@code Renderer.grabRequest}, gelesen aus dem Rahmen des GLCanvas) und malt
 * den Block daneben. Ergebnis: {@code docs/bild/W10B/Fenster_*.png} und eine Zeile je Prüfung; Rückgabe 0, wenn alles stimmt.
 * <pre>java -cp … com.dan.lituya.tools.MalweiseFenster</pre>
 */
public final class MalweiseFenster {
    private MalweiseFenster() { }

    static int fehler;

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        System.setProperty("lituya.splash", "false");
        System.setProperty("lituya.db", "false");
        System.setProperty("lituya.ohneSpeicher", "true");
        File ziel = new File("docs/bild/W10B");
        ziel.mkdirs();
        long t0 = System.nanoTime();
        LituyaApp.main(new String[0]);
        warten("Bedienfeld", () -> LituyaApp.controls != null && LituyaApp.scene != null, 120);
        Renderer r = LituyaApp.scene.renderer();
        warten("Wald", () -> r.wald.bereit(), 300);
        System.out.printf(Locale.GERMAN, "App und Wald bereit nach %.0f s%n", (System.nanoTime() - t0) / 1e9);

        // die Komponenten des Blocks MALWEISE
        FCollapsiblePanel block = (FCollapsiblePanel) finde(LituyaApp.controls, c -> c instanceof FCollapsiblePanel p && "MALWEISE".equals(p.getTitle()));
        pruefe(block != null, "Block MALWEISE im Bedienfeld");
        FComboBox art = (FComboBox) finde(block, c -> c instanceof FComboBox cb && cb.getItemCount() > 0 && cb.getItemAt(0) instanceof MalweiseGl.Art);
        AbstractButton inDenWald = (AbstractButton) finde(block, c -> c instanceof AbstractButton b && b.getText() != null && b.getText().startsWith("In den Wald"));
        pruefe(art != null && inDenWald != null, "Auswahl der Art und Knopf „In den Wald“ gefunden");
        boolean warOffen = block.isExpanded();
        SwingUtilities.invokeAndWait(() -> block.setExpanded(true, false));

        // In den Wald: die Kamera steht danach am Boden bei Fish Lake
        SwingUtilities.invokeAndWait(inDenWald::doClick);
        Thread.sleep(500);
        double[] auge = r.camera.eye.clone();
        pruefe(Math.hypot(auge[0] - MalweiseGl.IM_WALD.x(), auge[2] - MalweiseGl.IM_WALD.z()) < 1, "„In den Wald“: Auge bei x %.0f, z %.0f", auge[0], auge[2]);

        for (MalweiseGl.Art wahl : new MalweiseGl.Art[]{MalweiseGl.Art.GERTSCH, MalweiseGl.Art.GERTSCH_STICH, MalweiseGl.Art.STICH, MalweiseGl.Art.AUS}) {
            SwingUtilities.invokeAndWait(() -> art.setSelectedItem(wahl));
            pruefe(r.malweise.art == wahl, "Auswahl „%s“ kommt im Renderer an", wahl.label);
            if (wahl.gertsch()) warten("Ausschnitt", () -> r.malweise.fertig(), 120);
            Thread.sleep(2500);   // Tonstufen und Trägheit des Drucks setzen sich (Histogramm zwei Bilder versetzt)
            BufferedImage bild = holen(r);
            BufferedImage feld = malen(block);
            // die Statuszeile des Blocks zeigt, was der Renderer meldet (sie wird alle 0,5 s nachgeführt)
            String st = r.malweise.status(), kopf = st.substring(0, Math.min(24, st.length()));
            JLabel status = (JLabel) finde(block, c -> c instanceof JLabel l && l.getText() != null && l.getText().replaceAll("<[^>]+>", "").startsWith(kopf));
            if (wahl != MalweiseGl.Art.AUS) pruefe(bild != null && status != null && !st.contains("Fehler"), "„%s“: Bild %s, im Bedienfeld: %s", wahl.label,
                    bild == null ? "fehlt" : bild.getWidth() + " × " + bild.getHeight(), status == null ? "(Status fehlt; Renderer: " + st + ")" : st);
            if (bild != null) ImageIO.write(tafel(bild, feld, wahl.label), "png", new File(ziel, "Fenster_" + wahl.name() + ".png"));
        }
        // W10b: schnelle Fahrt nach Süden durch den Wald, mit und ohne Vorausbauen
        SwingUtilities.invokeAndWait(() -> art.setSelectedItem(MalweiseGl.Art.GERTSCH));
        for (double v : new double[]{10, 30})
            for (boolean voraus : new boolean[]{false, true}) fahrt(r, v, voraus);
        SwingUtilities.invokeAndWait(() -> art.setSelectedItem(MalweiseGl.Art.AUS));
        if (!warOffen) SwingUtilities.invokeAndWait(() -> block.setExpanded(false, false));
        System.out.println(fehler == 0 ? "Fenster: alles in Ordnung." : "Fenster: " + fehler + " Fehler.");
        System.exit(fehler == 0 ? 0 : 1);
    }

    /**
     * Fährt die Kamera v m/s nach Süden (Blick in Fahrtrichtung, 1,8 m über dem Grund der Kultur), 10 s lang, und misst je
     * 20 ms, wie weit der gemalte Wald in Blickrichtung noch reicht. Mit Vorausbauen soll er nicht unter 20 m fallen.
     */
    static void fahrt(Renderer r, double v, boolean voraus) throws InterruptedException {
        r.malweise.voraus = voraus;
        com.dan.forest.ForestCulture.Grid hoehe = r.wald.forest().culture().height;
        double x = MalweiseGl.IM_WALD.x(), z0 = MalweiseGl.IM_WALD.z();
        r.camera.place(x, hoehe.sample(0, (float) x, (float) z0) + 1.8, z0, 180, 3);
        int bauVorher = r.malweise.baue();
        warten("Ausschnitt am Start", () -> r.malweise.baue() > bauVorher && r.malweise.fertig(), 60);
        Thread.sleep(500);
        int baue0 = r.malweise.baue(), n = 0, knapp = 0;
        double min = Double.MAX_VALUE;
        long t0 = System.nanoTime();
        while (true) {
            double t = (System.nanoTime() - t0) / 1e9;
            if (t > 10) break;
            double z = z0 + v * t;
            r.camera.place(x, hoehe.sample(0, (float) x, (float) z) + 1.8, z, 180, 3);
            Thread.sleep(20);
            double vl = r.malweise.vorlauf(r.camera.eye, r.camera.fwd);
            if (Double.isNaN(vl)) continue;
            n++;
            min = Math.min(min, vl);
            if (vl < 20) knapp++;
        }
        boolean ok = !voraus || min >= 20;
        pruefe(ok, "Fahrt %2.0f m/s %s: Wald vor dem Auge mindestens %.0f m, unter 20 m in %.0f %% der Zeit, %d Ausschnitte gebaut", v,
                voraus ? "mit Vorausbauen " : "ohne Vorausbauen", min, 100.0 * knapp / Math.max(1, n), r.malweise.baue() - baue0);
        r.malweise.voraus = true;
    }

    static void pruefe(boolean ok, String was, Object... args) {
        if (!ok) fehler++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + String.format(Locale.GERMAN, was, args));
    }

    static void warten(String was, java.util.function.BooleanSupplier bis, double sekunden) throws InterruptedException {
        long t0 = System.nanoTime();
        while (!bis.getAsBoolean()) {
            if (System.nanoTime() - t0 > sekunden * 1e9) { System.out.println("  FEHLER " + was + " nach " + sekunden + " s nicht bereit"); fehler++; return; }
            Thread.sleep(200);
        }
    }

    /** Das Bild aus dem Fenster, beim nächsten Bild des Animators gelesen. */
    static BufferedImage holen(Renderer r) throws InterruptedException {
        AtomicReference<BufferedImage> b = new AtomicReference<>();
        r.grabRequest = b::set;
        long t0 = System.nanoTime();
        while (b.get() == null && System.nanoTime() - t0 < 10e9) Thread.sleep(50);
        return b.get();
    }

    static Component finde(Container c, Predicate<Component> p) {
        if (c == null) return null;
        for (Component k : c.getComponents()) {
            if (p.test(k)) return k;
            if (k instanceof Container ck) { Component f = finde(ck, p); if (f != null) return f; }
        }
        return null;
    }

    /** Der Block, wie er im Bedienfeld steht (Swing malt ihn in ein Bild; im EDT). */
    static BufferedImage malen(JComponent k) throws Exception {
        AtomicReference<BufferedImage> b = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            int w = Math.max(1, k.getWidth()), h = Math.max(1, k.getHeight());
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            k.paint(g);
            g.dispose();
            b.set(im);
        });
        return b.get();
    }

    /** Fensterbild links, der Block rechts, darüber die Wahl. */
    static BufferedImage tafel(BufferedImage bild, BufferedImage feld, String titel) {
        int h = Math.max(bild.getHeight(), feld.getHeight()) + 40, w = bild.getWidth() + feld.getWidth() + 30;
        BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = t.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(220, 226, 228));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("LituyaBay1958 · Bedienfeld MALWEISE · „" + titel + "“ (gewählt im Fenster, Bild aus dem GLCanvas)", 10, 26);
        g.drawImage(bild, 10, 36, null);
        g.drawImage(feld, bild.getWidth() + 20, 36, null);
        g.dispose();
        return t;
    }

    static List<Component> alle(Container c) {
        List<Component> l = new ArrayList<>();
        for (Component k : c.getComponents()) { l.add(k); if (k instanceof Container ck) l.addAll(alle(ck)); }
        return l;
    }
}
