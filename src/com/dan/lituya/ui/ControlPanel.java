package com.dan.lituya.ui;

import com.dan.lituya.app.Bibliothek;
import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Camera;
import com.dan.lituya.data.Verbindung;
import com.dan.lituya.gl.ComputeProbe;
import com.dan.lituya.gl.Quality;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.gl.SceneView;
import com.dan.lituya.netz.Lod;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Sun;
import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Das Bedienfeld rechts als Klappblöcke (aus Sylt und Timmendorf, entschlackt). Die Blöcke stehen nach dem Werkbuch
 * (Abschnitt 12) schon alle da: ANSICHT, ZEIT, EREIGNIS, NETZE, WETTER, REGIEBÜCHER, DATEN, dazu TECHNIK. In Phase 1
 * arbeiten ANSICHT (neun Ansichten, frei fliegen, kreisen), ZEIT (Zeitstand, Weltzeit, Simulationszeit, Zeitraffer,
 * Sonne), DATEN (Verbindung, Weg zum Network Data Model) und TECHNIK (Qualität, Auflösung, Belichtung, HUD, Marken, Foto,
 * Shader, Compute-Test, Physik, Packages); die übrigen sagen, in welcher Phase sie kommen. Offen bleibt, was zuletzt
 * offen war (je Rechner gemerkt). Jeder Regler schreibt direkt in die öffentlichen Felder von Uhr, Kamera, Verdrahtung
 * und Renderer; es gibt keinen zweiten Zustand.
 */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    private final SceneView view;
    private final Renderer r;
    private final SceneClock clock;
    private final JLabel dateLbl = label(""), timeLbl = label(""), simLbl = label(""), sunLbl = small(""), camLbl = small(""), perfLbl = small(""),
            computeLbl = small(""), glLbl = small(""), physLbl = small(""), hudLbl = small(""), scaleLbl = label(""), qualLbl = small(""), pkgLbl = small(""),
            dbLbl = small(""), lodLbl = small(""), datenLbl = small(""), standLbl = small(""), netzLbl = small("");
    private final FSlider time = new FSlider(0, 24 * 60 - 1, (int) (SceneClock.START_HOUR * 60)), scaleSl = new FSlider(50, 100, 85), expSl = new FSlider(-30, 30, 0);
    private final FCheckBox free = new FCheckBox("Frei fliegen (F)"), orbit = new FCheckBox("Von selbst kreisen (R)"),
            marks = new FCheckBox("Marken (UTM-Raster, Kern, Szene, Fern, Orte)"), autoBox = new FCheckBox("Auflösung selbst regeln"),
            meerBox = new FCheckBox("Wasser zeigen (Platzhalter bis Phase 5a)"),
            gelaendeBox = new FCheckBox("Gelände zeigen (clipmap, Farben bis Phase 5)");
    private FComboBox lapseBox, qualBox, hudBox, standBox;
    private boolean fromScene;
    private int ticks;
    private JPanel target = this;
    private static final Preferences PREFS = prefs();
    private final java.util.List<Object[]> bloecke = new java.util.ArrayList<>();
    private final java.util.List<FCollapsiblePanel> zu = new java.util.ArrayList<>();
    private static final double[] LAPSE = {0, 1, 10, 60, 180, 720, 3600};
    private volatile String lodProbe = "Probe läuft …";

    /** Die Reihenfolge der Blöcke (Werkbuch, Abschnitt 12). */
    public static final String[] REIHENFOLGE = {"ANSICHT", "ZEIT", "EREIGNIS", "NETZE", "WETTER", "REGIEBÜCHER", "DATEN", "TECHNIK"};

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/lituya/bedienfeld"); } catch (RuntimeException | Error e) { return null; }
    }

    public ControlPanel(SceneView view) {
        this.view = view;
        this.r = view.renderer();
        this.clock = r.clock;
        setBackground(Theme.bg());
        setOpaque(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 16, 10));

        // ============================================================== ANSICHT
        block("ANSICHT", true);
        put(camLbl);
        JPanel v1 = grid(2), v2 = grid(2), v3 = grid(2), v4 = grid(2), v5 = grid(2);
        JPanel[] rows = {v1, v1, v2, v2, v3, v3, v4, v4, v5};
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            FButton b = new FButton(a.key() + " " + a.name());
            b.setToolTipText(Ansichten.describe(a, Renderer.PHASE));
            b.addActionListener(e -> ansicht(a));
            rows[a.key() - 1].add(b);
            view.setKeyAction(java.awt.event.KeyEvent.VK_0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
            view.setKeyAction(java.awt.event.KeyEvent.VK_NUMPAD0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
        }
        put(v1);
        put(v2);
        put(v3);
        put(v4);
        put(v5);
        free.addActionListener(e -> { view.setMode(free.isSelected() ? Camera.Mode.FREI : Camera.Mode.KREISEN); view.requestFocusInWindow(); });
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        put(free);
        put(orbit);
        view.setModeListener(m -> javax.swing.SwingUtilities.invokeLater(() -> free.setSelected(m == Camera.Mode.FREI)));
        view.setOrbitListener(b -> javax.swing.SwingUtilities.invokeLater(() -> orbit.setSelected(b)));
        JPanel home = grid(1);
        FButton hb = new FButton("Pos1 Grundansicht");
        hb.addActionListener(e -> { view.goHome(); view.requestFocusInWindow(); });
        home.add(hb);
        put(home);
        put(small("<html><body style='width:215px'>Die Ansichten stehen seit Phase 4 auf dem Gelände aus clipmap (vor dem ersten Laden über dem Wasser)."
                + " Fahrten und Presets aus LB_NET_SICHT kommen mit Phase 17, „Mit der Edrie“ mit Phase 15.</body></html>"));

        // ============================================================== ZEIT
        block("ZEIT", true);
        put(label("Zeitstand"));
        standBox = new FComboBox(SceneClock.Zeitstand.values());
        standBox.setSelectedItem(clock.zeitstand);
        standBox.setToolTipText("Springt auf Datum und Uhrzeit des Zeitstands; Gelände, Wasser, Wald und Boote je Stand ab Phase 5a");
        standBox.addActionListener(e -> {
            if (fromScene || !(standBox.getSelectedItem() instanceof SceneClock.Zeitstand z)) return;
            clock.set(z);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(standBox);
        put(standLbl);
        put(dateLbl);
        JPanel days = grid(3);
        FButton minus = new FButton("− Tag"), beben = new FButton("22:16"), plus = new FButton("+ Tag");
        minus.addActionListener(e -> { clock.shiftDays(-1); syncFromScene(); view.requestFocusInWindow(); });
        plus.addActionListener(e -> { clock.shiftDays(1); syncFromScene(); view.requestFocusInWindow(); });
        beben.setToolTipText("Auf den ersten Stoß: 9.7.1958, 22:16 Uhr Ortszeit (10.7. 06:16 UTC)");
        beben.addActionListener(e -> { clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli()); clock.zeitstand = SceneClock.Zeitstand.WELLE; syncFromScene(); view.requestFocusInWindow(); });
        days.add(minus); days.add(beben); days.add(plus);
        put(days);
        put(timeLbl);
        put(time);
        time.addChangeListener(e -> { if (!fromScene) clock.setHour(time.getValue() / 60.0); labels(); });
        put(simLbl);
        put(label("Zeitraffer"));
        lapseBox = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "10-fach", "1 min/s", "3 min/s", "12 min/s (ein Tag in 2 min)", "1 h/s"});
        lapseBox.setSelectedIndex(1);
        lapseBox.addActionListener(e -> { clock.lapse = LAPSE[Math.max(0, lapseBox.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(lapseBox);
        put(sunLbl);
        put(small("<html><body style='width:215px'>Ortszeit: 1958 Pacific Standard Time (UTC−8), heute Alaska-Zeit (im Sommer UTC−8). "
                + "Simulationszeit t ab dem ersten Stoß. Zeitlupe bis 1:20 kommt mit dem Ereignis (Phase 13), 68 Jahre im Zeitraffer mit Phase 14a; "
                + "Himmel und Dämmerung rechnet ab Phase 5a com.dan.sky.</body></html>"));

        // ============================================================== spätere Blöcke
        block("EREIGNIS", false);
        put(small("<html><body style='width:215px'>Kommt mit Phase 13: die Kette Beben → Felssturz → Eintauchen → Welle → Auflauf, "
                + "vorgerechnet abspielen (vorwärts, rückwärts, Zeitlupe) oder in Echtzeit mit Volumen, Ort, Tide und Grund 1940/heute (F10 A). "
                + "Die Packages quake, landslide, impact und wave entstehen in den Phasen 8 bis 11.</body></html>"));
        block("NETZE", false);
        // Phase 7a: der Netzblick (Werkbuch 7.10)
        com.dan.lituya.gl.Netzblick nb = r.netzblick;
        FCheckBox pip = new FCheckBox("Netzblick als Bild im Bild (N)"), szene = new FCheckBox("Netze auf dem Wasser der Szene"),
                ganz = new FCheckBox("Alle Isochronen (ohne Zeit)"), isoBox = new FCheckBox("Isochronen und Einschlaglinie"),
                rayBox = new FCheckBox("Wellenstrahlen zu den Orten"), uferBox = new FCheckBox("Ufer (LRS) und Auflauf");
        pip.setSelected(nb.bildImBild); szene.setSelected(nb.inSzene); ganz.setSelected(nb.ganz);
        isoBox.setSelected(nb.isochronen); rayBox.setSelected(nb.strahlen); uferBox.setSelected(nb.ufer);
        pip.addActionListener(e -> { nb.bildImBild = pip.isSelected(); view.requestFocusInWindow(); });
        szene.addActionListener(e -> { nb.inSzene = szene.isSelected(); view.requestFocusInWindow(); });
        ganz.addActionListener(e -> { nb.ganz = ganz.isSelected(); view.requestFocusInWindow(); });
        isoBox.addActionListener(e -> { nb.isochronen = isoBox.isSelected(); view.requestFocusInWindow(); });
        rayBox.addActionListener(e -> { nb.strahlen = rayBox.isSelected(); view.requestFocusInWindow(); });
        uferBox.addActionListener(e -> { nb.ufer = uferBox.isSelected(); view.requestFocusInWindow(); });
        view.setKeyAction(java.awt.event.KeyEvent.VK_N, () -> javax.swing.SwingUtilities.invokeLater(() -> { pip.setSelected(!pip.isSelected()); nb.bildImBild = pip.isSelected(); }));
        put(pip);
        put(szene);
        put(label("Laufzeit über dem Grund"));
        FComboBox kost = new FComboBox(com.dan.lituya.scene.WellenNetz.KOSTEN_NAME);
        kost.setSelectedIndex(nb.kosten);
        kost.addActionListener(e -> { nb.kosten = Math.max(0, kost.getSelectedIndex()); view.requestFocusInWindow(); });
        put(kost);
        put(ganz);
        put(isoBox);
        put(rayBox);
        put(uferBox);
        JPanel nz = grid(2);
        FButton welleStart = new FButton("Welle: Einschlag"), welleEnde = new FButton("+ 6 min");
        welleStart.setToolTipText("Uhr auf den Einschlag im Gilbert Inlet: 22:16 Uhr + " + Math.round(com.dan.lituya.scene.WellenNetz.START_S) + " s [G]");
        welleStart.addActionListener(e -> { clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + Math.round(com.dan.lituya.scene.WellenNetz.START_S * 1000));
            clock.zeitstand = SceneClock.Zeitstand.WELLE; syncFromScene(); view.requestFocusInWindow(); });
        welleEnde.addActionListener(e -> { clock.setEpochMillis(clock.epochMillis() + 360_000L); syncFromScene(); view.requestFocusInWindow(); });
        nz.add(welleStart); nz.add(welleEnde);
        put(nz);
        put(netzLbl);
        put(small("<html><body style='width:215px'>Die Netze kommen aus daten/netz (F25 A; ant aufbereitung \"-Dschritte=welle,ufer\"), in Oracle als "
                + "LB_NET_WELLE und LB_NET_UFER (ant netzdb, ant netzvergleich). Isochronen alle 10 s, jede Minute kräftig; sie laufen mit der Uhr "
                + "(Zeitraffer im Block ZEIT). Später See, Wasser, Wege, Sicht und Herkunft (Phasen 15a bis 17).</body></html>"));
        block("WETTER", false);
        put(small("<html><body style='width:215px'>Kommt mit Phase 5a und 18: Sommerabend 1958 nach ERA5 und Zeugen, für „heute“ Regen, "
                + "Nebel, Herbststurm, Winter; Polarlicht nur „heute“ (F35 A).</body></html>"));
        block("REGIEBÜCHER", false);
        put(small("<html><body style='width:215px'>Kommt mit Phase 17: „Die Nacht der Welle“, „Zehn Minuten“, „Die drei Boote“, "
                + "„68 Jahre“, „Die Bucht der Wellen“; im Kinomodus aus com.dan.kino.</body></html>"));

        // ============================================================== DATEN
        block("DATEN", false);
        put(dbLbl);
        put(lodLbl);
        put(datenLbl);
        put(small("<html><body style='width:215px'>Phase 1 verbindet nur. Die NDM-Funktionsprobe und die Netze LB_NET_ kommen mit Phase 3, "
                + "Tabellen, Speichern und Laden mit Phase 19 (Fragenblock II). Den Treiber 21 für LOD holt <i>ant lod</i> aus ORACLE_HOME.</body></html>"));

        // ============================================================== TECHNIK
        block("TECHNIK", false);
        put(perfLbl);
        put(label("Qualität"));
        qualBox = new FComboBox(Quality.values());
        qualBox.setSelectedItem(r.quality());
        qualBox.addActionListener(e -> { r.setQuality((Quality) qualBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(qualBox);
        put(qualLbl);
        autoBox.setSelected(r.autoScale);
        autoBox.addActionListener(e -> { r.autoScale = autoBox.isSelected(); if (!r.autoScale) r.renderScale = scaleSl.getValue() / 100.0; view.requestFocusInWindow(); });
        put(autoBox);
        put(scaleLbl);
        put(scaleSl);
        scaleSl.addChangeListener(e -> {
            if (fromScene) return;
            r.autoScale = false;
            autoBox.setSelected(false);
            r.renderScale = scaleSl.getValue() / 100.0;
        });
        put(label("Belichtung (Blenden von Hand)"));
        put(expSl);
        expSl.addChangeListener(e -> { if (!fromScene) r.exposureBias = expSl.getValue() / 10.0; });
        put(label("HUD"));
        hudBox = new FComboBox(new Object[]{"kurz", "voll", "aus"});
        hudBox.addActionListener(e -> {
            int i = hudBox.getSelectedIndex();
            r.showHud = i != 2;
            r.hudFull = i == 1;
            view.requestFocusInWindow();
        });
        put(hudBox);
        marks.setSelected(r.showMarks);
        marks.addActionListener(e -> { r.showMarks = marks.isSelected(); view.requestFocusInWindow(); });
        put(marks);
        meerBox.setSelected(r.showMeer);
        meerBox.addActionListener(e -> { r.showMeer = meerBox.isSelected(); view.requestFocusInWindow(); });
        put(meerBox);
        gelaendeBox.setSelected(r.showGelaende);
        gelaendeBox.addActionListener(e -> { r.showGelaende = gelaendeBox.isSelected(); view.requestFocusInWindow(); });
        put(gelaendeBox);
        JPanel pic = grid(2);
        FButton photo = new FButton("Foto (F12)"), shaders = new FButton("Shader neu (F5)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        shaders.addActionListener(e -> { r.requestShaderReload(); view.requestFocusInWindow(); });
        pic.add(photo); pic.add(shaders);
        put(pic);
        put(small("<html><body style='width:215px'>Fotos gehen nach docs/status. Marken: gold der Kern (24 × 18 km), weiß die Szene "
                + "(66 × 60 km), blau der Fern (260 × 240 km); das UTM-Raster zu 1, 5 und 10 km; die Orte aus GNIS weiß (Buchten), grün (Insel), "
                + "sandfarben (Kaps), blau (Seen, Bäche), eisblau (Gletscher), hell (Gipfel), rot der Sporn mit 524 m [G].</body></html>"));
        sub("Grafikkarte");
        put(computeLbl);
        put(glLbl);
        put(physLbl);
        sub("Packages");
        put(pkgLbl);
        sub("Alle Zeilen des HUD");
        put(hudLbl);

        reihenfolge();
        for (Object[] b : bloecke) ((FCollapsiblePanel) b[0]).setBodyComponent((JComponent) b[1]);
        for (FCollapsiblePanel p : zu) p.setExpanded(false, false);
        add(Box.createVerticalGlue());
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(this::tasten));
        view.setKeyAction(java.awt.event.KeyEvent.VK_SPACE, () -> javax.swing.SwingUtilities.invokeLater(() ->
                lapseBox.setSelectedIndex(clock.lapse == 0 ? 1 : 0)));
        Thread probe = new Thread(() -> { Lod.Probe p = Lod.probe(); lodProbe = (p.ok() ? "" : "") + p.text(); }, "LituyaBay1958-LOD-Probe");
        probe.setDaemon(true);
        probe.start();
        syncFromScene();
        new Timer(500, e -> {
            // Befund aus Glacier: solange eine Auswahlliste offen ist, nichts auffrischen
            if (popupOpen(this) || menuOpen()) return;
            syncFromScene();
            if (ticks++ % 2 == 0) technik();
            nachmessen();
        }).start();
    }

    private void ansicht(Ansichten.Ansicht a) {
        if (r.placeView(a)) {
            free.setSelected(r.camera.mode == Camera.Mode.FREI);
            orbit.setSelected(false);
        }
        view.requestFocusInWindow();
    }

    private void tasten() {
        StringBuilder an = new StringBuilder();
        for (Ansichten.Ansicht a : Ansichten.ALLE) an.append(an.length() > 0 ? ", " : "").append(a.key()).append(' ').append(a.name());
        com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:420px'>"
                + "<b>Szene</b><br>Links ziehen: drehen · rechts ziehen: verschieben · Rad, Bild↑/↓, +/−: zoomen<br>"
                + "Pfeile: drehen · W A S D: gleiten · Q/E: ab und auf (frei) · Umschalt: schneller<br>"
                + "F: frei fliegen · R: von selbst kreisen · Pos1: Grundansicht · H: HUD kurz, voll, aus<br>"
                + "Leertaste: Uhr anhalten und weiter · N: Netzblick an und aus · F12: Foto nach docs/status · F5: Shader neu laden<br>"
                + "Ansichten: " + an + "<br><br>"
                + "<b>Fenster</b><br>F1: diese Übersicht · F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)</body></html>",
                "LituyaBay1958 · Tasten", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
    }

    /** Regler nach der Szene stellen (alle 0,5 s). */
    private void syncFromScene() {
        fromScene = true;
        int m = (int) Math.round(clock.hour() * 60) % (24 * 60);
        if (!time.getValueIsAdjusting()) time.setValue(m);
        if (!scaleSl.getValueIsAdjusting()) scaleSl.setValue((int) Math.round(r.renderScale * 100));
        if (!expSl.getValueIsAdjusting()) expSl.setValue((int) Math.round(r.exposureBias * 10));
        if (standBox.getSelectedItem() != clock.zeitstand) standBox.setSelectedItem(clock.zeitstand);
        fromScene = false;
        labels();
    }

    private void labels() {
        txt(dateLbl, clock.local().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM u", Locale.GERMANY)));
        txt(timeLbl, "Ortszeit " + clock.timeLabelSec());
        txt(simLbl, "Simulationszeit " + clock.simLabel());
        txt(standLbl, "<html><body style='width:215px'>" + esc(clock.zeitstand.text) + "</body></html>");
        double[] s = clock.sun();
        double day = clock.local().toLocalDate().atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double up = Sun.crossing(day, clock.lat, clock.lon, true), down = Sun.crossing(day, clock.lat, clock.lon, false);
        txt(sunLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Sonne %.1f° hoch, aus %03.0f° (%s) · Aufgang %s, Untergang %s</body></html>",
                s[0], s[1], compass(s[1]), hm(up), hm(down)));
        txt(camLbl, "<html><body style='width:215px'>" + esc(r.cameraLine()) + "</body></html>");
        txt(scaleLbl, String.format(Locale.GERMAN, "Auflösung %.0f %% (%s)", r.renderScale * 100, r.autoScale ? "geregelt, " + r.scaleSteps() + "× geändert" : "fest"));
        txt(qualLbl, "<html><body style='width:215px'>" + r.quality().summary() + "</body></html>");
    }

    private void technik() {
        txt(perfLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms<br>%s</body></html>",
                r.fps(), r.gpuMs(), r.cpuMs(), r.passTimes()));
        ComputeProbe.Result c = r.compute();
        txt(computeLbl, "<html><body style='width:215px'>" + (c == null ? "Compute-Test läuft …" : c.line()) + "</body></html>");
        txt(glLbl, "<html><body style='width:215px'>" + r.glInfo() + (r.reversedDepth() ? " · umgekehrte Tiefe" : "") + "</body></html>");
        txt(physLbl, "<html><body style='width:215px'>" + r.physik.describe() + "</body></html>");
        txt(pkgLbl, "<html><body style='width:215px'>" + esc(Bibliothek.kurz().replace("!", "")) + ": " + String.join(", ", Bibliothek.pakete())
                + ".<br>Neu für LituyaBay1958 (im Projekt Renderer): " + esc(Bibliothek.neuStand()) + ".</body></html>");
        txt(dbLbl, "<html><body style='width:215px'>Datenbank: " + esc(Verbindung.stand()) + "</body></html>");
        txt(lodLbl, "<html><body style='width:215px'>Netze (LOD): " + esc(Lod.stand()) + "<br>" + esc(lodProbe) + "</body></html>");
        txt(datenLbl, "<html><body style='width:215px'>" + esc(Startbild.datenZeile()) + "</body></html>");
        txt(netzLbl, "<html><body style='width:215px'>" + esc(r.netzblick.stand()) + "</body></html>");
        StringBuilder h = new StringBuilder("<html><body style='width:215px'>");
        for (String l : r.hudLines()) h.append(esc(l.startsWith("!") ? l.substring(1) : l)).append("<br>");
        txt(hudLbl, h.append("</body></html>").toString());
    }

    private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static String hm(double epochMs) {
        if (Double.isNaN(epochMs)) return "—";
        return String.format("%tR", Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE));
    }

    static String compass(double deg) {
        String[] n = {"Nord", "Nordnordost", "Nordost", "Ostnordost", "Ost", "Ostsüdost", "Südost", "Südsüdost",
                "Süd", "Südsüdwest", "Südwest", "Westsüdwest", "West", "Westnordwest", "Nordwest", "Nordnordwest"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    /** Text nur setzen, wenn er sich ändert (sonst legt Swing den Block bei jedem Takt neu aus). */
    private static void txt(JLabel l, String t) { if (!t.equals(l.getText())) l.setText(t); }

    private void reihenfolge() {
        java.util.Map<String, FCollapsiblePanel> m = new java.util.LinkedHashMap<>();
        for (Object[] b : bloecke) m.putIfAbsent(((FCollapsiblePanel) b[0]).getTitle(), (FCollapsiblePanel) b[0]);
        removeAll();
        boolean erst = true;
        for (String t : REIHENFOLGE) {
            FCollapsiblePanel p = m.remove(t);
            if (p == null) continue;
            if (!erst) add(Box.createVerticalStrut(6));
            add(p);
            erst = false;
        }
        for (FCollapsiblePanel p : m.values()) { add(Box.createVerticalStrut(6)); add(p); }
    }

    /** Für Prüfungen: die Titel der Blöcke, wie sie im Bedienfeld stehen. */
    public java.util.List<String> bloecke() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (Component c : getComponents()) if (c instanceof FCollapsiblePanel p) l.add(p.getTitle());
        return l;
    }

    private final java.util.Map<FCollapsiblePanel, Integer> hoehe = new java.util.HashMap<>();

    /**
     * Wie in Geiranger: FCollapsiblePanel merkt sich die Höhe des Inhalts beim Aufklappen. Wachsen danach Zeilen,
     * schneidet der Block unten ab. Darum alle halbe Sekunde nachmessen und offene Blöcke neu setzen.
     */
    private void nachmessen() {
        boolean changed = false;
        for (Object[] b : bloecke) {
            FCollapsiblePanel p = (FCollapsiblePanel) b[0];
            if (!p.isExpanded()) { hoehe.remove(p); continue; }
            JComponent body = (JComponent) b[1];
            body.invalidate();
            int h = body.getPreferredSize().height;
            Integer old = hoehe.put(p, h);
            if (old == null || old != h) { p.setExpanded(true, false); changed = true; }
        }
        if (changed) { revalidate(); repaint(); }
    }

    /** Ist irgendwo im Bedienfeld eine Auswahlliste aufgeklappt? */
    private static boolean popupOpen(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JComboBox<?> b && b.isPopupVisible()) return true;
            if (k instanceof java.awt.Container cc && popupOpen(cc)) return true;
        }
        return false;
    }

    /** Ist irgendein Aufklappmenü offen (auch die Liste einer Auswahl)? Dann darf sich nichts neu auslegen. */
    public static boolean menuOpen() { return javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0; }

    // ------------------------------------------------------------ Aufbau

    /** Ein Klappblock (FCollapsiblePanel); offen bleibt, was zuletzt offen war. */
    private void block(String title, boolean offen) {
        FCollapsiblePanel p = new FCollapsiblePanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setTitle(title);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel inhalt = new JPanel();
        inhalt.setOpaque(false);
        inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
        inhalt.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 8));
        bloecke.add(new Object[]{p, inhalt});
        String key = "offen." + title;
        boolean auf = PREFS == null ? offen : PREFS.getBoolean(key, offen);
        if (!auf) zu.add(p);
        p.addFCollapsiblePanelListener((panel, expanded) -> {
            if (PREFS != null) try { PREFS.putBoolean(key, expanded); } catch (RuntimeException ex) { /* nicht gemerkt */ }
        });
        if (getComponentCount() > 0) add(Box.createVerticalStrut(6));
        add(p);
        target = inhalt;
    }

    /** Eine Zwischenüberschrift im Block. */
    private void sub(String text) {
        JLabel h = new JLabel(text);
        h.setForeground(Theme.accent());
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        put(h);
    }

    private void put(Component c) { target.add(row(c)); }

    private static JPanel grid(int n) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, n, 6, 0));
        p.setOpaque(false);
        return p;
    }

    /** Eine Zeile, die mit ihrem Inhalt wächst. */
    private static JComponent row(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.ink());
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    static JLabel small(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }

    // ------------------------------------------------------------ Scrollable: nur senkrecht
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle rr, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle rr, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
