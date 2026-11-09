package com.dan.lituya.tools;

import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.lituya.app.LituyaApp;
import com.dan.lituya.gl.PolarlichtGl;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.SceneClock;

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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Werkbuch Welle W12: die Bedienung des Blocks POLARLICHT im echten Fenster, ohne den Bildschirm zu steuern (wie
 * {@link MalweiseFenster}). Startet die App im selben Prozess (ohne Startbild und Datenbank), wartet auf Bedienfeld und
 * Himmel und bedient die Swing-Komponenten des Blocks so, wie ein Klick es täte: „Winternacht“ ({@code doClick}: die Uhr
 * steht danach auf dem 20.12.2026, 23 Uhr, das Polarlicht ist an), „Blick zum Bogen“ (die Kamera schaut zum hellsten Bogen),
 * der Kp-Regler, die Presets ({@code setSelectedItem}, löst dieselben ActionListener aus). Nach jeder Wahl prüft es, ob
 * der Renderer die Wahl übernommen hat und sein Status Bogen und kR nennt, holt das Bild aus dem Fenster
 * ({@code Renderer.grabRequest}) und malt den Block daneben. Ergebnis: {@code docs/bild/W12/Fenster_*.png} und eine Zeile
 * je Prüfung; Rückgabe 0, wenn alles stimmt.
 * <pre>java -cp … com.dan.lituya.tools.PolarlichtFenster</pre>
 */
public final class PolarlichtFenster {
    private PolarlichtFenster() { }

    static int fehler;

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        System.setProperty("lituya.splash", "false");
        System.setProperty("lituya.db", "false");
        System.setProperty("lituya.ohneSpeicher", "true");
        File ziel = new File("docs/bild/W12");
        ziel.mkdirs();
        long t0 = System.nanoTime();
        LituyaApp.main(new String[0]);
        warten("Bedienfeld", () -> LituyaApp.controls != null && LituyaApp.scene != null, 120);
        Renderer r = LituyaApp.scene.renderer();
        warten("Himmel", () -> r.licht.aktiv(), 120);
        System.out.printf(Locale.GERMAN, "App und Himmel bereit nach %.0f s%n", (System.nanoTime() - t0) / 1e9);

        FCollapsiblePanel block = (FCollapsiblePanel) finde(LituyaApp.controls, c -> c instanceof FCollapsiblePanel p && "POLARLICHT".equals(p.getTitle()));
        pruefe(block != null, "Block POLARLICHT im Bedienfeld");
        FComboBox art = (FComboBox) finde(block, c -> c instanceof FComboBox cb && cb.getItemCount() > 0 && cb.getItemAt(0) instanceof PolarlichtGl.Art);
        AbstractButton winter = (AbstractButton) finde(block, c -> c instanceof AbstractButton b && b.getText() != null && b.getText().startsWith("Winternacht"));
        AbstractButton nord = (AbstractButton) finde(block, c -> c instanceof AbstractButton b && b.getText() != null && b.getText().startsWith("Blick zum Bogen"));
        FSlider kp = (FSlider) finde(block, c -> c instanceof FSlider s && s.getMaximum() == 90);
        pruefe(art != null && winter != null && nord != null && kp != null, "Auswahl, „Winternacht“, „Blick zum Bogen“ und Kp-Regler gefunden");
        boolean warOffen = block.isExpanded();
        SwingUtilities.invokeAndWait(() -> block.setExpanded(true, false));

        // Winternacht: Uhr auf den 20.12.2026, 23 Uhr, Polarlicht an
        SwingUtilities.invokeAndWait(winter::doClick);
        Thread.sleep(500);
        pruefe(r.clock.date().getYear() == 2026 && r.clock.date().getMonthValue() == 12 && r.clock.date().getDayOfMonth() == 20 && Math.abs(r.clock.hour() - 23) < 0.05
                && r.clock.zeitstand == SceneClock.Zeitstand.HEUTE, "„Winternacht“: Uhr %s, Zeitstand %s", r.clock.label(), r.clock.zeitstand);
        pruefe(r.polarlicht.art == PolarlichtGl.Art.AKTIV, "„Winternacht“ schaltet das Polarlicht ein: %s", r.polarlicht.art);
        // Blick zum Bogen: die Kamera schaut in das Azimut des hellsten Bogens, 15° hinauf
        Thread.sleep(1500);   // das Polarlicht ist nach „Winternacht“ gebaut
        SwingUtilities.invokeAndWait(nord::doClick);
        Thread.sleep(800);
        double[] fwd = r.camera.fwd.clone();
        double azS = Math.toRadians(r.polarlicht.aurora().azimutHauptbogen());
        double passt = fwd[0] * Math.sin(azS) + fwd[2] * -Math.cos(azS);
        pruefe(passt > 0.9 && fwd[1] > 0.1, "„Blick zum Bogen“ (Azimut %.0f°), 15° hinauf: Blick (%.2f, %.2f, %.2f)", Math.toDegrees(azS), fwd[0], fwd[1], fwd[2]);
        // Kp-Regler
        SwingUtilities.invokeAndWait(() -> kp.setValue(60));
        Thread.sleep(300);
        pruefe(Math.abs(r.polarlicht.kp - 6) < 1e-9, "Kp-Regler 6,0 kommt im Renderer an: %.1f", r.polarlicht.kp);

        for (PolarlichtGl.Art wahl : new PolarlichtGl.Art[]{PolarlichtGl.Art.AKTIV, PolarlichtGl.Art.RUHIG, PolarlichtGl.Art.AUFBRUCH, PolarlichtGl.Art.ROT, PolarlichtGl.Art.KP}) {
            SwingUtilities.invokeAndWait(() -> art.setSelectedItem(wahl));
            pruefe(r.polarlicht.art == wahl && (Double.isNaN(wahl.kp()) || Math.abs(r.polarlicht.kp - wahl.kp()) < 1e-9), "Auswahl „%s“ kommt im Renderer an (Kp %.1f)", wahl.label, r.polarlicht.kp);
            Thread.sleep(3000);   // neu gebaut, Belichtung setzt sich
            String st = r.polarlicht.status();
            BufferedImage bild = holen(r);
            BufferedImage feld = malen(block);
            String kopf = st.substring(0, Math.min(16, st.length()));
            JLabel status = (JLabel) finde(block, c -> c instanceof JLabel l && l.getText() != null && l.getText().replaceAll("<[^>]+>", "").startsWith(kopf));
            pruefe(bild != null && status != null && st.contains("Bogen") && st.contains("kR") && !st.contains("Fehler"), "„%s“: Bild %s, Status: %s", wahl.label,
                    bild == null ? "fehlt" : bild.getWidth() + " × " + bild.getHeight(), status == null ? "(Status fehlt im Block; Renderer: " + st + ")" : st);
            if (bild != null) ImageIO.write(tafel(bild, feld, wahl.label), "png", new File(ziel, "Fenster_" + wahl.name() + ".png"));
        }
        // der Sommerabend 1958: die Sonne steht zu hoch, der Status sagt es
        SwingUtilities.invokeAndWait(() -> art.setSelectedItem(PolarlichtGl.Art.AKTIV));
        r.clock.set(SceneClock.Zeitstand.VOR);
        Thread.sleep(2500);
        String st = r.polarlicht.status();
        pruefe(st.contains("Dämmerung"), "Sommerabend 1958: %s", st);
        BufferedImage bild = holen(r);
        if (bild != null) ImageIO.write(tafel(bild, malen(block), "Sommerabend 1958"), "png", new File(ziel, "Fenster_SOMMER_1958.png"));
        // zurück in die Winternacht: das Polarlicht leuchtet wieder, kein Hinweis auf Dämmerung
        SwingUtilities.invokeAndWait(winter::doClick);
        Thread.sleep(2500);
        st = r.polarlicht.status();
        pruefe(r.polarlicht.an() && !st.contains("Dämmerung"), "Winternacht wieder: %s", st);

        SwingUtilities.invokeAndWait(() -> art.setSelectedItem(PolarlichtGl.Art.AUS));
        if (!warOffen) SwingUtilities.invokeAndWait(() -> block.setExpanded(false, false));
        System.out.println(fehler == 0 ? "Fenster: alles in Ordnung." : "Fenster: " + fehler + " Fehler.");
        System.exit(fehler == 0 ? 0 : 1);
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

    static BufferedImage tafel(BufferedImage bild, BufferedImage feld, String titel) {
        int h = Math.max(bild.getHeight(), feld.getHeight()) + 40, w = bild.getWidth() + feld.getWidth() + 30;
        BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = t.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(220, 226, 228));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("LituyaBay1958 · Bedienfeld POLARLICHT · „" + titel + "“ (gewählt im Fenster, Bild aus dem GLCanvas)", 10, 26);
        g.drawImage(bild, 10, 36, null);
        g.drawImage(feld, bild.getWidth() + 20, 36, null);
        g.dispose();
        return t;
    }
}
