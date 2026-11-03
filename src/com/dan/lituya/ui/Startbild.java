package com.dan.lituya.ui;

import com.dan.lituya.app.Bibliothek;
import com.dan.lituya.data.Verbindung;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.fbutton.FButton;
import com.dan.store.Db;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.io.FileNotFoundException;

/**
 * Das Startbild vor der Szene (aus Sylt, Phase 1). Es verbindet mit dem Schema über {@code db/db.properties}, fragt
 * nach dem Passwort, wenn keins hinterlegt ist (auch nicht in {@code LituyaBay1958_DB_PASSWORD}), und trägt es nach dem
 * ersten erfolgreichen Verbinden dort ein. Es sagt, ob die aufbereiteten Daten in {@code daten/} liegen – Regel 3.3 aus
 * dem Werkbuch: die App liest nie aus {@code download/}; fehlt {@code daten/}, nennt es das Werkzeug und den Pfad – und
 * wie weit der Weg zum Network Data Model steht ({@code sdonm.jar}, Treiber 21 in {@code lib/lod/}, F26 A). Knöpfe:
 * „Neu“, „Ohne Datenbank“, „Beenden“; „Weiter wie zuletzt“, „Einrichten“ und „Im Kino“ kommen mit Phase 19 und 17. Ist
 * alles bereit, geht es nach 3 s von selbst weiter; solange ein Passwort fehlt, nicht. Jede Eingabe hält an. Ohne
 * Startbild: {@code -Dlituya.splash=false}.
 */
public final class Startbild extends JDialog {
    private static final int SEKUNDEN = 3;

    /** Was gewählt wurde: mit Datenbank (Stand in {@link Verbindung#stand()}) oder ohne, oder beenden. */
    public record Wahl(boolean datenbank, boolean beenden) { }

    private final JLabel dbLine = new JLabel(" "), datenLine = new JLabel(" "), countLine = new JLabel(" ");
    private final JPanel pwRow = new JPanel(new BorderLayout(8, 0));
    private final com.dan.ftextfield.FPasswordfield pw = new com.dan.ftextfield.FPasswordfield();
    private final FButton neu = new FButton("Neu"), ohne = new FButton("Ohne Datenbank"), ende = new FButton("Beenden");
    private Timer timer;
    private int rest = SEKUNDEN;
    private boolean angehalten, fertig, verbunden;
    private AWTEventListener aufmerken;
    private volatile Db.Konfig konfig;
    private Wahl wahl;

    /** Zeigt das Startbild und kehrt zurück, wenn es zu ist (modal). */
    public static Wahl zeigen() {
        Startbild s = new Startbild();
        s.setVisible(true);
        return s.wahl != null ? s.wahl : new Wahl(false, false);
    }

    /** Ohne Startbild: verbinden, wenn es ohne Rückfrage geht. */
    public static Wahl stumm() {
        if ("false".equals(System.getProperty("lituya.db"))) return new Wahl(false, false);
        try {
            Db.Konfig k = Verbindung.konfig();
            if (k.hatPasswort()) {
                Verbindung.stand(Db.pruefen(k));
                Verbindung.verbunden(k);
                return new Wahl(true, false);
            }
        } catch (Exception e) { /* ohne Datenbank */ }
        return new Wahl(false, false);
    }

    /** Statuszeile unten rechts im Fenster. */
    public static String statuszeile() { return "Datenbank: " + Verbindung.stand(); }

    /** Der Ordner der aufbereiteten Daten (-Dlituya.daten, sonst ./daten). */
    public static java.io.File daten() {
        String d = System.getProperty("lituya.daten");
        return new java.io.File(d != null ? d : "daten");
    }

    /** Was über die Daten zu sagen ist (Regel 3.3: die App liest nie aus download/, nur aus daten/). */
    public static String datenZeile() {
        java.io.File d = daten();
        if (new java.io.File(d, "INHALT.txt").isFile()) return "Daten aus " + d.getPath() + " (INHALT.txt).";
        return "Noch keine aufbereiteten Daten in " + d.getPath() + ". Werkzeug: ant aufbereitung (com.dan.lituya.tools.Aufbereitung); es liest das "
                + "Kartenmaterial aus dem Pfad in " + d.getPath() + "/rohdaten.properties (Vorgabe download/) und schreibt Gelände, Grund und Namen nach "
                + d.getPath() + ". Die App selbst liest nie aus download/; ohne daten/gelaende zeigt die Szene nur Wasser, Himmel und Marken.";
    }

    private Startbild() {
        super((java.awt.Frame) null, "LituyaBay1958", true);
        setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout(18, 14)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(24, 32, 46), 0, getHeight(), new Color(9, 13, 18)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.MILCH);
                g2.fillRect(0, 0, getWidth(), 3);
                int y = getHeight() - 6, w = getWidth();
                for (int i = 0; i < Theme.BAND.length; i++) {
                    g2.setColor(Theme.BAND[i]);
                    g2.fillRect((int) (w * Theme.BAND_AT[i]), y, (int) Math.ceil(w * (Theme.BAND_AT[i + 1] - Theme.BAND_AT[i])) + 1, 6);
                }
            }
        };
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(44, 58, 72)), BorderFactory.createEmptyBorder(22, 26, 24, 28)));
        JLabel il = new JLabel(new javax.swing.ImageIcon(AppIcon.load(128)));
        il.setVerticalAlignment(JLabel.TOP);
        root.add(il, BorderLayout.WEST);
        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("LITUYA BAY · 9. JULI 1958");
        title.setFont(new Font("Serif", Font.PLAIN, 34));
        title.setForeground(Theme.EIS);
        JLabel sub = new JLabel("Das Projekt der Riesenwelle und des Netzes · Alaska, am Fuß der Fairweather-Kette, " + Site.dms(Site.LAT, Site.LON).replace(" · ", " "));
        sub.setFont(new Font("SansSerif", Font.PLAIN, 14));
        sub.setForeground(Theme.MILCH);
        SceneClock start = new SceneClock();
        JLabel info = new JLabel("<html><body style='width:560px'>" + "LituyaBay1958 " + Renderer.VERSION + ": die Bucht vor, während und nach der Welle und heute, mit Gelände bis zum Horizont, "
                + "Wald, Booten, Beben, Felssturz, Welle (vorgerechnet und in Echtzeit), Klang, sieben Netzen, Regiebüchern im Kino und dem Archiv (F2). "
                + "F1 zeigt die Hilfe. Die Szene beginnt am " + start.label() + " (" + start.simLabel() + " bis zum ersten Stoß).<br>Renderer.jar: " + Bibliothek.kurz().replace("!", "")
                + "<br>Netze: " + com.dan.lituya.netz.Lod.stand() + "</body></html>");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));
        info.setForeground(new Color(170, 182, 192));
        info.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));
        for (JLabel l : new JLabel[]{dbLine, datenLine, countLine}) {
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        for (JLabel l : new JLabel[]{title, sub, info}) l.setAlignmentX(Component.LEFT_ALIGNMENT);
        dbLine.setForeground(new Color(224, 190, 120));
        datenLine.setForeground(new Color(196, 206, 210));
        datenLine.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        datenLine.setText("<html><body style='width:620px'>" + datenZeile() + "</body></html>");
        countLine.setForeground(Theme.MILCH);
        countLine.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        pwRow.setOpaque(false);
        pwRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel pl = ControlPanel.small("Passwort für das Schema:");
        FButton verb = new FButton("Verbinden");
        verb.addActionListener(e -> mitPasswort());
        pw.addActionListener(e -> mitPasswort());
        pwRow.add(pl, BorderLayout.WEST);
        pwRow.add(pw, BorderLayout.CENTER);
        pwRow.add(verb, BorderLayout.EAST);
        pwRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        pwRow.setMaximumSize(new Dimension(620, 40));
        pwRow.setVisible(false);
        mid.add(title);
        mid.add(sub);
        mid.add(info);
        mid.add(dbLine);
        mid.add(pwRow);
        mid.add(datenLine);
        mid.add(countLine);
        root.add(mid, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.setOpaque(false);
        ohne.setToolTipText("Ohne die Datenbank starten (in Phase 1 braucht die Szene sie noch nicht; die Netze kommen mit Phase 3)");
        neu.setToolTipText("Die Szene neu: " + start.label());
        ohne.addActionListener(e -> { Verbindung.stand("ohne Datenbank"); schliessen(new Wahl(false, false)); });
        neu.addActionListener(e -> schliessen(new Wahl(verbunden, false)));
        ende.addActionListener(e -> schliessen(new Wahl(false, true)));
        south.add(ohne);
        south.add(ende);
        south.add(neu);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        dbLine.setText("Datenbank: verbinde …");
        pack();
        setSize(Math.max(getWidth(), 980), Math.max(getHeight(), 380));
        setLocationRelativeTo(null);
        try { setIconImages(AppIcon.images()); } catch (RuntimeException e) { /* ohne Symbol */ }
        getRootPane().setDefaultButton(neu);
        aufmerken = ev -> {
            if (ev.getSource() instanceof Component c && SwingUtilities.getWindowAncestor(c) == this && !angehalten
                    && (ev.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED || ev.getID() == java.awt.event.KeyEvent.KEY_PRESSED) && !(c instanceof FButton))
                anhalten("Angehalten. „Neu“ startet die Szene.");
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(aufmerken, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        hintergrund(this::pruefen);
    }

    private static void hintergrund(Runnable r) {
        Thread t = new Thread(r, "LituyaBay1958-Startbild");
        t.setDaemon(true);
        t.start();
    }

    private void anhalten(String text) {
        angehalten = true;
        if (timer != null) timer.stop();
        countLine.setText(text);
    }

    /** Verbinden und sagen, wer man ist (im Hintergrund). */
    private void pruefen() {
        String zeile;
        try {
            konfig = Verbindung.konfig();
            if (!konfig.hatPasswort()) {
                SwingUtilities.invokeLater(() -> {
                    dbLine.setText("<html><body style='width:620px'>Datenbank " + konfig.kurz() + ": bitte das Passwort eingeben (es wird nach dem "
                            + "Verbinden in db/db.properties eingetragen; die Datei steht in .gitignore).</body></html>");
                    pwRow.setVisible(true);
                    anhalten("Wartet auf das Passwort oder „Ohne Datenbank“.");
                    pack();
                    pw.requestFocusInWindow();
                });
                return;
            }
            String wer = Db.pruefen(konfig);
            Verbindung.stand(wer);
            Verbindung.verbunden(konfig);
            verbunden = true;
            zeile = "Datenbank: " + wer + " · NDM-Funktionsprobe und Netze LB_NET_ kommen mit Phase 3, die Tabellen LB_ mit Phase 19.";
        } catch (FileNotFoundException e) {
            zeile = "Keine db/db.properties (Vorlage db/db.properties.example). Die Szene läuft auch ohne Datenbank.";
        } catch (Exception e) {
            zeile = "Datenbank " + (konfig != null ? konfig.kurz() : "") + " nicht erreichbar: " + (e instanceof java.sql.SQLException q ? Db.erste(q) : e.getMessage())
                    + ". Die Szene läuft auch ohne Datenbank.";
            Verbindung.stand("nicht erreichbar");
            rest = 6;
        }
        String z = zeile;
        SwingUtilities.invokeLater(() -> {
            dbLine.setText("<html><body style='width:620px'>" + z + "</body></html>");
            pack();
            setSize(Math.max(getWidth(), 980), getHeight());
            if (!angehalten) zaehlen();
        });
    }

    private void mitPasswort() {
        char[] p = pw.getPassword();
        if (p.length == 0 || konfig == null) return;
        Db.Konfig k = konfig.mitPasswort(p);
        dbLine.setText("Datenbank: verbinde …");
        hintergrund(() -> {
            try {
                String wer = Db.pruefen(k);
                try { Db.passwortSpeichern(k, p); } catch (Exception e) { System.err.println("LituyaBay1958: Passwort nicht in db.properties eingetragen: " + e.getMessage()); }
                Verbindung.stand(wer);
                Verbindung.verbunden(k);
                verbunden = true;
                konfig = k;
                SwingUtilities.invokeLater(() -> {
                    pwRow.setVisible(false);
                    dbLine.setText("<html><body style='width:620px'>Datenbank: " + wer + " · Passwort in db/db.properties eingetragen.</body></html>");
                    countLine.setText("Verbunden. „Neu“ startet die Szene.");
                    pack();
                    setSize(Math.max(getWidth(), 980), getHeight());
                });
            } catch (Exception e) {
                String m = e instanceof java.sql.SQLException q ? Db.erste(q) : e.getMessage();
                SwingUtilities.invokeLater(() -> dbLine.setText("<html><body style='width:620px'>Nicht verbunden: " + m + "</body></html>"));
            }
        });
    }

    private void zaehlen() {
        countLine.setText("Die Szene startet in " + rest + " s …");
        timer = new Timer(1000, e -> {
            if (angehalten) return;
            rest--;
            if (rest <= 0) schliessen(new Wahl(verbunden, false));
            else countLine.setText("Die Szene startet in " + rest + " s …");
        });
        timer.start();
    }

    private void schliessen(Wahl w) {
        if (fertig) return;
        fertig = true;
        wahl = w;
        if (timer != null) timer.stop();
        if (aufmerken != null) Toolkit.getDefaultToolkit().removeAWTEventListener(aufmerken);
        dispose();
    }
}
