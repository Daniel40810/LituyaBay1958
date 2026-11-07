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
import com.dan.lituya.scene.Site;
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
 * (Abschnitt 12) schon alle da: ANSICHT, ZEIT, EREIGNIS, NETZE, WETTER, REGIEBÜCHER, DATEN, dazu TECHNIK (KLANG seit Phase 18). In Phase 1
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
            dbLbl = small(""), lodLbl = small(""), datenLbl = small(""), standLbl = small(""), netzLbl = small(""), regieLbl = small(""), klangLbl = small(""), speicherLbl = small(""), wetterLbl = small("");
    private final FSlider time = new FSlider(0, 24 * 60 - 1, (int) (SceneClock.START_HOUR * 60)), scaleSl = new FSlider(50, 100, 100), expSl = new FSlider(-30, 30, 0);
    private final FCheckBox free = new FCheckBox("Frei fliegen (F)"), orbit = new FCheckBox("Von selbst kreisen (R)"),
            marks = new FCheckBox("Marken (UTM-Raster, Kern, Szene, Fern, Orte)"), autoBox = new FCheckBox("Auflösung selbst regeln"),
            meerBox = new FCheckBox("Wasser zeigen (Platzhalter bis Phase 5a)"),
            gelaendeBox = new FCheckBox("Gelände zeigen (clipmap, Farben bis Phase 5)");
    private FComboBox lapseBox, qualBox, hudBox, standBox, laufBox, tempoBox;
    private final JLabel ereignisLbl = small(""), volLbl = small(""), tideLbl = small(""), echtzeitLbl = small("");
    private FComboBox ortBox;

    /** Regler 0 … 100 auf 1 … 300 Mio. m³ (logarithmisch). */
    public static double volumen(int x) { return 1e6 * Math.pow(300, x / 100.0); }

    public static int volWert(double v) { return (int) Math.round(100 * Math.log(v / 1e6) / Math.log(300)); }
    /** Tempo des Abspielens (Phase 13b): Szenensekunden je Sekunde, Richtung extra. */
    private static final double[] TEMPO = {1, 0.5, 0.2, 0.1, 0.05};
    private static final Object[] TEMPO_NAME = {"Echtzeit", "Zeitlupe 1:2", "Zeitlupe 1:5", "Zeitlupe 1:10", "Zeitlupe 1:20"};
    private int richtung = 1;
    /** Der Zeitraffer vor dem Anhalten (Leertaste, K). */
    private double letzte = 1;
    private boolean fromScene;
    private int ticks;
    private JPanel target = this;
    private static final Preferences PREFS = prefs();
    private final java.util.List<Object[]> bloecke = new java.util.ArrayList<>();
    private final java.util.List<FCollapsiblePanel> zu = new java.util.ArrayList<>();
    private static final double[] LAPSE = {0, 1, 10, 60, 180, 720, 3600};
    private volatile String lodProbe = "Probe läuft …";

    /** Die Reihenfolge der Blöcke (Werkbuch, Abschnitt 12). */
    public static final String[] REIHENFOLGE = {"ANSICHT", "KAMERA", "ZEIT", "EREIGNIS", "NETZE", "WETTER", "KLANG", "REGIEBÜCHER", "DATEN", "TECHNIK"};

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/lituya/bedienfeld"); } catch (RuntimeException | Error e) { return null; }
    }

    private boolean zeichenGeprueft;

    /** W9c (Lesbarkeit_W09): einmal, wenn das Bedienfeld seine Schriften hat, alle Beschriftungen auf fehlende Zeichen prüfen. */
    @Override public void addNotify() {
        super.addNotify();
        if (zeichenGeprueft) return;
        zeichenGeprueft = true;
        javax.swing.SwingUtilities.invokeLater(() -> Zeichen.lesbar(this));
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
        // Phase 17: Fahrten aus LB_NET_SICHT
        put(label("Fahrten aus LB_NET_SICHT"));
        FComboBox fahrtTempo = new FComboBox(new String[]{"Tempo 1× (Flugzeit)", "Tempo 5×", "Tempo 15×", "Tempo 50×"});
        fahrtTempo.setSelectedIndex(2);
        put(fahrtTempo);
        JPanel fz = grid(2);
        FButton rundB = new FButton("Rundflug"), fahrtB = new FButton("Kamerafahrt");
        rundB.setToolTipText("Geschlossene Rundreise (tsp) über alle Motive ab der Anchorage Cove, die Sichtlinien entlang");
        fahrtB.setToolTipText("Den Spannbaum (mcst) entlang, jede Kante hin und zurück, Äste ohne Motiv weggelassen");
        rundB.addActionListener(e -> { r.sichtFahrt(com.dan.lituya.camera.SichtFahrt.RUNDFLUG, com.dan.lituya.camera.SichtFahrt.TEMPI[Math.max(0, fahrtTempo.getSelectedIndex())]); view.requestFocusInWindow(); });
        fahrtB.addActionListener(e -> { r.sichtFahrt(com.dan.lituya.camera.SichtFahrt.KAMERAFAHRT, com.dan.lituya.camera.SichtFahrt.TEMPI[Math.max(0, fahrtTempo.getSelectedIndex())]); view.requestFocusInWindow(); });
        fz.add(rundB); fz.add(fahrtB);
        put(fz);
        put(small("<html><body style='width:215px'>Die Ansichten stehen seit Phase 4 auf dem Gelände aus clipmap (vor dem ersten Laden über dem Wasser)."
                + " Seit Phase 17 die Fahrten aus LB_NET_SICHT (ant aufbereitung -Dschritte=sicht): Tasten F oder R, Pfeile oder Ziehen beenden sie.</body></html>"));

        // ============================================================== KAMERA (Werkbuch Welle W9a)
        kamera();

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
                + "Simulationszeit t ab dem ersten Stoß. Zeitlupe bis 1:20 und rückwärts im Block EREIGNIS, 68 Jahre im Zeitraffer mit Phase 14a; "
                + "Himmel und Dämmerung rechnet ab Phase 5a com.dan.sky.</body></html>"));

        // ============================================================== spätere Blöcke
        block("EREIGNIS", false);
        // Phase 13b: die Vorrechnung abspielen (daten/ereignis), Schaustücke 2 bis 7
        com.dan.lituya.scene.Wiedergabe wg = r.wiedergabe;
        FCheckBox spielen = new FCheckBox("Vorrechnung abspielen (Welle, Strom, Staub)");
        spielen.setSelected(wg.an());
        spielen.addActionListener(e -> { wg.an(spielen.isSelected()); view.requestFocusInWindow(); });
        put(spielen);
        put(label("Lauf der Welle"));
        java.util.List<String> laeufe = wg.wellen();
        if (laeufe.isEmpty()) laeufe = java.util.List.of(com.dan.lituya.scene.Wiedergabe.WELLEN);
        laufBox = new FComboBox(laeufe.toArray());
        laufBox.setSelectedItem(wg.welle());
        laufBox.setToolTipText("kette: Nahfeld aus impact (Werkbuch 4.4); messwelle: die gemessene Welle am Einlass; _LOW: grobe Läufe");
        laufBox.addActionListener(e -> { if (laufBox.getSelectedItem() != null) wg.welle(laufBox.getSelectedItem().toString()); view.requestFocusInWindow(); });
        put(laufBox);
        JPanel sp = grid(4);
        // W9c (Lesbarkeit_W09): die Zeichen gemalt; ◀ und ▶ fehlen der Schrift der Knöpfe
        FButton rueck = new FButton("J"), halt = new FButton("K"), vor = new FButton("L"), sturz = new FButton("93 s");
        rueck.setFIcon(Zeichen.von(Zeichen.Art.RUECK));
        halt.setFIcon(Zeichen.von(Zeichen.Art.HALT));
        vor.setFIcon(Zeichen.von(Zeichen.Art.VOR));
        rueck.setToolTipText("Rückwärts (J)");
        halt.setToolTipText("Anhalten (K)");
        vor.setToolTipText("Vorwärts (L)");
        sturz.setToolTipText("Uhr auf die Auslösung des Felssturzes: 93 s nach dem ersten Stoß [G]");
        rueck.addActionListener(e -> richtung(-1));
        halt.addActionListener(e -> richtung(0));
        vor.addActionListener(e -> richtung(1));
        sturz.addActionListener(e -> { clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 93_000L); clock.zeitstand = SceneClock.Zeitstand.WELLE;
            wg.an(true); spielen.setSelected(true); syncFromScene(); view.requestFocusInWindow(); });
        sp.add(rueck); sp.add(halt); sp.add(vor); sp.add(sturz);
        put(sp);
        put(label("Tempo (Z)"));
        tempoBox = new FComboBox(TEMPO_NAME);
        tempoBox.setSelectedIndex(0);
        tempoBox.addActionListener(e -> { tempo(); view.requestFocusInWindow(); });
        put(tempoBox);
        put(ereignisLbl);
        sub("Schaustücke");
        JPanel s1 = grid(2), s2 = grid(2), s3 = grid(2), s4 = grid(1), s6 = grid(2), s5 = grid(2);
        for (com.dan.lituya.camera.Schaustuecke.Stueck st : com.dan.lituya.camera.Schaustuecke.ALLE) {
            FButton b = new FButton(st.nr() + " " + st.name());
            b.setToolTipText(com.dan.lituya.camera.Schaustuecke.describe(st));
            b.addActionListener(e -> stueck(st, spielen));
            (st.nr() >= 10 ? s5 : st.nr() >= 8 ? s6 : st.nr() == 7 ? s4 : st.nr() <= 2 ? s1 : st.nr() <= 4 ? s2 : s3).add(b);
        }
        put(s1); put(s2); put(s3); put(s4); put(s6); put(s5);
        // Phase 13c: „Was wäre wenn“ in Echtzeit (wave.Bay auf der Grafikkarte, LOW)
        sub("Was wäre wenn (Echtzeit)");
        com.dan.lituya.gl.Echtzeit ez = r.echtzeit;
        ortBox = new FComboBox(com.dan.lituya.gl.Echtzeit.ORTE.toArray());
        ortBox.setToolTipText("Wo der Hang ins Wasser stürzt; Strg + Klick in die Szene setzt einen eigenen Ort (Richtung aus dem Hang)");
        ortBox.addActionListener(e -> { if (ortBox.getSelectedItem() instanceof com.dan.lituya.gl.Echtzeit.Ort o) ez.ort = o; view.requestFocusInWindow(); });
        put(ortBox);
        FSlider vol = new FSlider(0, 100, volWert(ez.volumen));
        put(volLbl);
        put(vol);
        vol.addChangeListener(e -> { ez.volumen = volumen(vol.getValue()); txt(volLbl, String.format(Locale.GERMAN, "Volumen %.1f Mio. m³ (1958: 30,6)", ez.volumen / 1e6)); });
        txt(volLbl, String.format(Locale.GERMAN, "Volumen %.1f Mio. m³ (1958: 30,6)", ez.volumen / 1e6));
        FSlider tide = new FSlider(-30, 40, 0);
        put(tideLbl);
        put(tide);
        tide.addChangeListener(e -> { ez.pegel = tide.getValue() / 10.0; txt(tideLbl, String.format(Locale.GERMAN, "Tide: Pegel %+.1f m über NAVD88", ez.pegel)); });
        txt(tideLbl, "Tide: Pegel +0,0 m über NAVD88");
        JPanel gz = grid(2);
        FComboBox grundBox = new FComboBox(com.dan.lituya.gl.Echtzeit.Grund.values()), gangBox = new FComboBox(com.dan.lituya.gl.Echtzeit.Ganglinie.values());
        grundBox.setToolTipText("1940: Lotungen und Rückbau des Gilbert Inlet (wie die Vorrechnung); heute: BlueTopo und Gelände von heute, auch in der Szene");
        gangBox.setToolTipText("Nur an der Wand von 1958: die Ganglinie an der Linie, nach dem Volumen skaliert");
        grundBox.addActionListener(e -> { ez.grund = (com.dan.lituya.gl.Echtzeit.Grund) grundBox.getSelectedItem(); view.requestFocusInWindow(); });
        gangBox.addActionListener(e -> { ez.ganglinie = (com.dan.lituya.gl.Echtzeit.Ganglinie) gangBox.getSelectedItem(); view.requestFocusInWindow(); });
        gz.add(grundBox); gz.add(gangBox);
        put(gz);
        JPanel eb = grid(2);
        FButton rechnen = new FButton("Rechnen ab 93 s"), ezAus = new FButton("Zur Vorrechnung");
        rechnen.setToolTipText("Baut die Bucht mit diesen Einstellungen (einige Sekunden), stellt die Uhr auf die Auslösung und rechnet mit der Uhr");
        rechnen.addActionListener(e -> {
            ez.neu();
            r.wiring.pegel = ez.pegel;
            clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 93_000L);
            clock.zeitstand = SceneClock.Zeitstand.WELLE;
            clock.lapse = TEMPO[Math.max(0, tempoBox.getSelectedIndex())];
            wg.an(true);
            spielen.setSelected(true);
            syncFromScene();
            view.requestFocusInWindow();
        });
        ezAus.addActionListener(e -> { ez.aus(); r.wiring.pegel = 0; view.requestFocusInWindow(); });
        eb.add(rechnen); eb.add(ezAus);
        put(eb);
        put(echtzeitLbl);
        view.setCtrlClickListener((x, y, w, h) -> {
            double[] p = r.treffer(x, y, w, h);
            if (p == null) return;
            double[] u = Site.toUtm(p[0], p[1]);
            com.dan.lituya.gl.Echtzeit.Ort o = new com.dan.lituya.gl.Echtzeit.Ort(String.format(Locale.GERMAN, "Strg-Klick E %.0f N %.0f", u[0], u[1]), u[0], u[1], Double.NaN, false);
            ez.ort = o;
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.DefaultComboBoxModel<Object> m = new javax.swing.DefaultComboBoxModel<>(com.dan.lituya.gl.Echtzeit.ORTE.toArray());
                m.addElement(o);
                ortBox.setModel(m);
                ortBox.setSelectedItem(o);
            });
            r.hudMeldung("Ort des Sturzes: " + o.name() + " (Rechnen ab 93 s im Block EREIGNIS)");
        });
        put(small("<html><body style='width:215px'>Die Vorrechnung aus Phase 13 (ant ereignis): Welle in drei Gittern (16, 4, 2 m), Felssturz "
                + "und Staub über dem Grund von 1958; der Rückbau des Gilbert Inlet gilt im Gelände für alle Zeitstände außer „heute“. "
                + "J/K/L: rückwärts, anhalten, vorwärts; Z: Zeitlupe 1:1, 1:2, 1:5, 1:10, 1:20. Fahrten kommen mit Phase 17, "
                + "Geröll und Stämme mit 14. „Was wäre wenn“ (unten) rechnet die Welle in Echtzeit (LOW, 32/8/4 m) und hält sie im Rückspulpuffer: "
                + "rückwärts geht, soweit gerechnet ist.</body></html>"));
        view.setKeyAction(java.awt.event.KeyEvent.VK_J, () -> javax.swing.SwingUtilities.invokeLater(() -> richtung(-1)));
        view.setKeyAction(java.awt.event.KeyEvent.VK_K, () -> javax.swing.SwingUtilities.invokeLater(() -> richtung(0)));
        view.setKeyAction(java.awt.event.KeyEvent.VK_L, () -> javax.swing.SwingUtilities.invokeLater(() -> richtung(1)));
        view.setKeyAction(java.awt.event.KeyEvent.VK_Z, () -> javax.swing.SwingUtilities.invokeLater(() ->
                tempoBox.setSelectedIndex((tempoBox.getSelectedIndex() + 1) % TEMPO.length)));
        // Phase 14a: der Wald, die Folgen der Welle und die Wiederbewaldung bis heute
        sub("Wald und Wiederbewaldung");
        com.dan.lituya.gl.WaldGl wd = r.wald;
        FCheckBox waldBox = new FCheckBox("Wald zeigen (forest, Zustände aus succession)"),
                gezBox = new FCheckBox("Band auf die Belege 1958 gezogen (sonst rein gerechnet)"), tgBox = new FCheckBox("Treibgut am Morgen danach");
        waldBox.setSelected(wd.an); gezBox.setSelected(wd.gezogen); tgBox.setSelected(wd.treibgut);
        waldBox.addActionListener(e -> { wd.an = waldBox.isSelected(); view.requestFocusInWindow(); });
        gezBox.addActionListener(e -> { wd.gezogen = gezBox.isSelected(); r.hudMeldung("Wald wird neu gerechnet (" + (wd.gezogen ? "gezogen" : "rein gerechnet") + ")"); view.requestFocusInWindow(); });
        tgBox.addActionListener(e -> { wd.treibgut = tgBox.isSelected(); view.requestFocusInWindow(); });
        put(waldBox); put(gezBox); put(tgBox);
        put(label("Jahr nach der Welle"));
        jahrSl = new FSlider(0, com.dan.lituya.scene.Wald1958.JAHRE - 1, 0);
        jahrSl.setToolTipText("Springt auf den 10. Juli des Jahrs (Uhrzeit bleibt): 0 = Morgen danach 1958, 68 = 2026");
        jahrSl.addChangeListener(e -> { if (!fromScene && !jahrSl.getValueIsAdjusting()) jahr(jahrSl.getValue()); });
        put(jahrSl);
        JPanel jr = grid(3);
        FButton j58 = new FButton("Morgen danach"), raffer = new FButton("68 Jahre (Y)"), j26 = new FButton("heute");
        j58.setToolTipText("10.7.1958, 6:00 Uhr: der Morgen nach der Welle mit dem Treibgut");
        raffer.setToolTipText("Jahres-Zeitraffer: 68 Jahre in einer Minute, die Uhrzeit bleibt; hält am 9.7.2026 (noch einmal: anhalten)");
        j26.setToolTipText("9.7.2026: der Wald heute");
        j58.addActionListener(e -> { clock.set(SceneClock.Zeitstand.DANACH); r.jahresRaffer = false; syncFromScene(); view.requestFocusInWindow(); });
        raffer.addActionListener(e -> { raffer(); view.requestFocusInWindow(); });
        j26.addActionListener(e -> { clock.set(SceneClock.Zeitstand.HEUTE); r.jahresRaffer = false; syncFromScene(); view.requestFocusInWindow(); });
        jr.add(j58); jr.add(raffer); jr.add(j26);
        put(jr);
        view.setKeyAction(java.awt.event.KeyEvent.VK_Y, () -> javax.swing.SwingUtilities.invokeLater(this::raffer));
        put(small("<html><body style='width:215px'>Bestand heute aus der Kronenhöhe (ArcticDEM − Gelände, ant aufbereitung -Dschritte=wald), "
                + "im Band 1958 aus dem Gelände [G]. Während der Welle bricht der Wald, wo sie ankommt; danach wächst er Jahr für Jahr nach "
                + "(Roterle zuerst, Sitka-Fichte und Hemlocktanne), Wuchs abgeglichen an der Kronenhöhe im Band (WALD.txt). "
                + "Treibgut: ant treibgut1958.</body></html>"));
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
        // Phase 16c: welches Netz (je Netz ein eigener Ausschnitt im Bild im Bild: Bucht, Szene, Kern)
        put(label("Netz"));
        FComboBox netzBox = new FComboBox(new String[]{"Welle (LB_NET_WELLE, Bucht)", "Wasser (LB_NET_WASSER, Szene)", "Weg (LB_NET_WEG, Kern)", "Sicht (LB_NET_SICHT, Szene)"});
        netzBox.setSelectedIndex(nb.netz);
        netzBox.setToolTipText("Was der Netzblick zeigt, im Bild im Bild und in der Szene");
        netzBox.addActionListener(e -> { nb.netz = Math.max(0, netzBox.getSelectedIndex()); view.requestFocusInWindow(); });
        put(netzBox);
        put(label("Laufzeit über dem Grund"));
        FComboBox kost = new FComboBox(com.dan.lituya.scene.WellenNetz.KOSTEN_NAME);
        kost.setSelectedIndex(nb.kosten);
        kost.addActionListener(e -> { nb.kosten = Math.max(0, kost.getSelectedIndex()); view.requestFocusInWindow(); });
        put(kost);
        put(ganz);
        put(isoBox);
        put(rayBox);
        put(uferBox);
        // Phase 16c: Ebenen von LB_NET_WASSER und LB_NET_WEG
        put(label("Wasser"));
        FCheckBox baBox = new FCheckBox("Bäche nach Einzugsfläche"), glBox = new FCheckBox("Fließlinien der Gletscher (ITS_LIVE)"),
                ezBox = new FCheckBox("Einzugsgebiete und Ziele");
        baBox.setSelected(nb.baeche); glBox.setSelected(nb.gletscher); ezBox.setSelected(nb.einzug);
        baBox.addActionListener(e -> { nb.baeche = baBox.isSelected(); view.requestFocusInWindow(); });
        glBox.addActionListener(e -> { nb.gletscher = glBox.isSelected(); view.requestFocusInWindow(); });
        ezBox.addActionListener(e -> { nb.einzug = ezBox.isSelected(); view.requestFocusInWindow(); });
        put(baBox);
        put(glBox);
        put(ezBox);
        com.dan.lituya.gl.BaecheGl ba = r.baeche;
        FCheckBox baSzene = new FCheckBox("Bäche in der Szene (Wasser-Pass)");
        baSzene.setSelected(ba.an);
        baSzene.addActionListener(e -> { ba.an = baSzene.isSelected(); view.requestFocusInWindow(); });
        put(baSzene);
        // W0: der Regler „Regen“ steht seit dem Werkbuch Welle im Block WETTER
        put(label("Weg"));
        FCheckBox stBox = new FCheckBox("1 h vom Strand (Isolinien 15 min)"), wbBox = new FCheckBox("Washburn 1934 mit Wegpunkten"),
                lfBox = new FCheckBox("Flucht an Land mit der Uhr"), klBox = new FCheckBox("Geländeklassen im Bild im Bild");
        stBox.setSelected(nb.stunde); wbBox.setSelected(nb.washburn); lfBox.setSelected(nb.landflucht); klBox.setSelected(nb.klassen);
        stBox.addActionListener(e -> { nb.stunde = stBox.isSelected(); view.requestFocusInWindow(); });
        wbBox.addActionListener(e -> { nb.washburn = wbBox.isSelected(); view.requestFocusInWindow(); });
        lfBox.addActionListener(e -> { nb.landflucht = lfBox.isSelected(); view.requestFocusInWindow(); });
        klBox.addActionListener(e -> { nb.klassen = klBox.isSelected(); view.requestFocusInWindow(); });
        put(stBox);
        put(wbBox);
        put(lfBox);
        put(klBox);
        // Phase 17: Ebenen von LB_NET_SICHT
        put(label("Sicht"));
        FCheckBox slBox = new FCheckBox("Alle Sichtlinien"), rfBox = new FCheckBox("Rundflug (tsp)"), sbBox = new FCheckBox("Spannbaum (mcst)");
        slBox.setSelected(nb.sichtlinien); rfBox.setSelected(nb.rundflug); sbBox.setSelected(nb.spannbaum);
        slBox.addActionListener(e -> { nb.sichtlinien = slBox.isSelected(); view.requestFocusInWindow(); });
        rfBox.addActionListener(e -> { nb.rundflug = rfBox.isSelected(); view.requestFocusInWindow(); });
        sbBox.addActionListener(e -> { nb.spannbaum = sbBox.isSelected(); view.requestFocusInWindow(); });
        put(slBox);
        put(rfBox);
        put(sbBox);
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
                + "(Zeitraffer im Block ZEIT). Seit Phase 16c auch LB_NET_WASSER (Szene) und LB_NET_WEG (Kern; die Flucht an Land wächst mit der Uhr: "
                + "„Welle: Einschlag“ und Zeitraffer), seit Phase 17 LB_NET_SICHT (Szene: Kamerapunkte, Sichtlinien, Rundflug, Spannbaum).</body></html>"));
        block("WETTER", false);
        // Werkbuch Welle W7 „Wetter“: die Lagen der Bucht (5a.1) mit Übergang statt Umschalten; 1958 fest nach den Zeugen (WF13 A)
        com.dan.lituya.wiring.Wetter wet = r.wetter;
        put(label("Wetterlage (heute)"));
        FComboBox lageBox = new FComboBox(com.dan.lituya.scene.Wetterlage.heute());
        lageBox.setSelectedItem(wet.heute);
        lageBox.setToolTipText("Gilt „heute“; am 9.7.1958 und am Morgen danach ist es nach den Zeugen klar und still (WF13 A)");
        JLabel lageLbl = small("");
        lageBox.addActionListener(e -> {
            Object o = lageBox.getSelectedItem();
            if (o instanceof com.dan.lituya.scene.Wetterlage l) {
                wet.waehle(l);
                // eine Lage wählen heißt: heute ansehen (die Uhrzeit bleibt)
                if (clock.zeitstand != SceneClock.Zeitstand.HEUTE) {
                    double hr = clock.hour();
                    clock.set(SceneClock.Zeitstand.HEUTE.date, hr);
                    clock.zeitstand = SceneClock.Zeitstand.HEUTE;
                    r.jahresRaffer = false;
                    syncFromScene();
                }
                txt(lageLbl, "<html><body style='width:215px'>" + esc(l.beschreibung) + "</body></html>");
            }
            view.requestFocusInWindow();
        });
        put(lageBox);
        txt(lageLbl, "<html><body style='width:215px'>" + esc(wet.heute.beschreibung) + "</body></html>");
        put(lageLbl);
        JLabel uebLbl = small(String.format(Locale.GERMAN, "Übergang %.0f min (Szenenzeit)", wet.uebergangMin));
        FSlider uebSl = new FSlider(0, 120, (int) Math.round(wet.uebergangMin));
        uebSl.setToolTipText("So lange dauert ein Wechsel der Lage in Szenenzeit; im Zeitraffer sieht man Wolken aufziehen und Nebel kommen");
        uebSl.addChangeListener(e -> { wet.uebergangMin = uebSl.getValue(); txt(uebLbl, String.format(Locale.GERMAN, "Übergang %d min (Szenenzeit)", uebSl.getValue())); });
        put(uebLbl);
        put(uebSl);
        JPanel wz = grid(2);
        FButton sofort = new FButton("Sofort"), wRaffer = new FButton("Zeitraffer 1 min/s");
        sofort.setToolTipText("Die gewählte Lage ohne Übergang");
        wRaffer.setToolTipText("Uhr mit 60-fachem Tempo: ein Wechsel von 20 min in 20 s");
        sofort.addActionListener(e -> { wet.sofort(); view.requestFocusInWindow(); });
        wRaffer.addActionListener(e -> { clock.lapse = 60; lapseBox.setSelectedIndex(3); view.requestFocusInWindow(); });
        wz.add(sofort); wz.add(wRaffer);
        put(wz);
        FCheckBox wolkenBox = new FCheckBox("Wolken (com.dan.cloud)"), nebelBox = new FCheckBox("Seenebel (com.dan.fog)"), regenBox = new FCheckBox("Regen (com.dan.rain)");
        wolkenBox.setSelected(r.wetterGl.wolkenAn); nebelBox.setSelected(r.wetterGl.nebelAn); regenBox.setSelected(r.wetterGl.regenAn);
        wolkenBox.addActionListener(e -> { r.wetterGl.wolkenAn = wolkenBox.isSelected(); view.requestFocusInWindow(); });
        nebelBox.addActionListener(e -> { r.wetterGl.nebelAn = nebelBox.isSelected(); view.requestFocusInWindow(); });
        regenBox.addActionListener(e -> { r.wetterGl.regenAn = regenBox.isSelected(); view.requestFocusInWindow(); });
        put(wolkenBox);
        put(nebelBox);
        put(regenBox);
        put(wetterLbl);
        // W0: der Regler „Regen“ auf die Bäche; seit W7 kommt der Abfluss der Wetterlage dazu (das Größere gilt)
        put(label("Regen auf die Bäche (von Hand)"));
        JLabel regenLbl = small("");
        FSlider regenSl = new FSlider(0, (int) com.dan.lituya.gl.BaecheGl.REGEN_MAX, (int) Math.round(r.baecheRegenHand));
        regenSl.setToolTipText("Regen in mm/h: Abfluss = Fläche × Regen × Beiwert (Land 0,7, Eis 1,0) über dem Mittelwasser; Breite b = 2,5·Q^0,5 [G]; die Lage bringt ihren eigenen Abfluss");
        put(regenLbl);
        put(regenSl);
        regenSl.addChangeListener(e -> { r.baecheRegenHand = regenSl.getValue(); txt(regenLbl, String.format(Locale.GERMAN, "Regen %d mm/h (0: Mittelwasser)", regenSl.getValue())); });
        txt(regenLbl, String.format(Locale.GERMAN, "Regen %.0f mm/h (0: Mittelwasser)", r.baecheRegenHand));
        put(small("<html><body style='width:215px'><b>Werkbuch Welle W7.</b> Ein Zustand für alles (com.dan.weather): Wind auf Wasser, Wald und "
                + "Gischt, Wolken mit Schatten, Seenebel, Regen mit Ringen, nasser Fels, Bäche und Fälle schwellen an, Klang. Wolken am Gelände: "
                + "Bank am Hang, Kappe am Fairweather, Quellwolken über der Kette (com.dan.cloud seit W7); der Nebel stößt durch den Eingang vor "
                + "(com.dan.fog seit W7). 1958 fest nach den Zeugen (WF13 A): klar am Abend und am Morgen danach. "
                + "Regiebuch: „wetter Lage [Übergang in min]“.</body></html>"));
        block("KLANG", false);
        // Phase 18: der Klang der Bucht am Ohr der Kamera (com.dan.soundscape)
        FCheckBox tonBox = new FCheckBox("Ton an (M)"), echoBox = new FCheckBox("Echo an den Wänden");
        tonBox.setToolTipText("Beben, Sturz, Aufschlag, Brecher, brechende Bäume, Gletscher, Möwen; Laufzeit des Schalls ab Quelle");
        echoBox.setSelected(r.klang.echo);
        tonBox.addActionListener(e -> { r.klang.setOn(tonBox.isSelected()); view.requestFocusInWindow(); });
        echoBox.addActionListener(e -> { r.klang.echo = echoBox.isSelected(); view.requestFocusInWindow(); });
        view.setKeyAction(java.awt.event.KeyEvent.VK_M, () -> javax.swing.SwingUtilities.invokeLater(() -> { tonBox.setSelected(!tonBox.isSelected()); r.klang.setOn(tonBox.isSelected()); }));
        put(tonBox);
        put(echoBox);
        FSlider lautSl = new FSlider(0, 100, (int) Math.round(r.klang.lautstaerke * 100));
        JLabel lautLbl = small(String.format(Locale.GERMAN, "Lautstärke %d %%", lautSl.getValue()));
        lautSl.addChangeListener(e -> { r.klang.lautstaerke = lautSl.getValue() / 100.0; txt(lautLbl, String.format(Locale.GERMAN, "Lautstärke %d %%", lautSl.getValue())); });
        put(lautLbl);
        put(lautSl);
        put(klangLbl);
        put(small("<html><body style='width:215px'>Phase 18: was die Kamera hört, mit der Laufzeit des Schalls (rund 340 m/s: der Sturz ist in der "
                + "Anchorage Cove erst 34 s später zu hören). Im Kino legt sich der Ton des Kinos darüber. Ohne Fenster: ant hoerproben "
                + "(WAV und Spektrogramm nach docs/hoerproben).</body></html>"));
        block("REGIEBÜCHER", false);
        // Phase 17a: die fünf Regiebücher, im Kino oder im Fenster
        java.util.List<com.dan.lituya.scene.Regie.Book> buecher;
        try { buecher = com.dan.lituya.scene.Regie.all(); } catch (RuntimeException ex) { buecher = java.util.List.of(); put(small("Regiebücher nicht lesbar: " + ex.getMessage())); }
        FComboBox buchBox = new FComboBox(buecher.toArray());
        buchBox.setToolTipText("Die fünf Regiebücher (Werkbuch 11.3); der Text liegt in com/dan/lituya/scene/regie");
        put(buchBox);
        JPanel rb = grid(2);
        FButton kinoB = new FButton("Im Kino"), fensterB = new FButton("Im Fenster"), haltB = new FButton("Anhalten");
        kinoB.setToolTipText("Vollbild ohne Bedienfeld, Balken 2,39 : 1, Vor- und Abspann, Untertitel; Esc: zurück ins Fenster, Leertaste: anhalten");
        fensterB.setToolTipText("Das Regiebuch im Fenster mit HUD; am Ende das Blatt mit den Fotos in docs/status");
        kinoB.addActionListener(e -> { if (buchBox.getSelectedItem() instanceof com.dan.lituya.scene.Regie.Book b) r.imBild(() -> r.kinoStart(b)); view.requestFocusInWindow(); });
        fensterB.addActionListener(e -> { if (buchBox.getSelectedItem() instanceof com.dan.lituya.scene.Regie.Book b) r.imBild(() -> r.regieStart(b)); view.requestFocusInWindow(); });
        haltB.addActionListener(e -> { r.imBild(r::regieStop); view.requestFocusInWindow(); });
        rb.add(kinoB); rb.add(fensterB);
        put(rb);
        JPanel rb2 = grid(1);
        rb2.add(haltB);
        put(rb2);
        put(regieLbl);
        put(small("<html><body style='width:215px'>„Zehn Minuten“, „Die drei Boote“, „Die Nacht der Welle“, „68 Jahre“, „Die Bucht der Wellen“ "
                + "(Phase 17a). Fahrten aus LB_NET_SICHT, Mitfahrt auf den Booten, Netzblick im Kino; die früheren Wellen der Bucht in Echtzeit "
                + "nachgerechnet [G]. Ganz ohne Fenster: ant regiebuecher.</body></html>"));

        // ============================================================== DATEN
        block("DATEN", false);
        put(dbLbl);
        put(lodLbl);
        put(datenLbl);
        // Phase 19: Speichern und Laden (Oracle, sonst daten/speicher)
        put(speicherLbl);
        JPanel sp1 = grid(2);
        FButton sichernB = new FButton("Sitzung sichern"), laufB = new FButton("Lauf sichern");
        sichernB.setToolTipText("Kamera, Uhr, Zeitstand und alle Schalter nach LB_SITZUNG (ohne Datenbank nach daten/speicher) [Strg+S]");
        laufB.setToolTipText("Die Einstellungen der Echtzeit (Ort, Volumen, Grund, Ganglinie, Pegel) als Lauf nach LB_EREIGNIS_LAUF, zum Wiederabspielen");
        Runnable sichern = () -> new Thread(() -> { String m = com.dan.lituya.gl.Sitzungen.sichern(r, null); r.hudMeldung(m); datenMeldung = java.time.LocalTime.now().withNano(0) + " " + m; javax.swing.SwingUtilities.invokeLater(this::sitzungenNeu); }, "LituyaBay1958-Sichern").start();
        sichernB.addActionListener(e -> { sichern.run(); view.requestFocusInWindow(); });
        view.speichern = sichern;
        laufB.addActionListener(e -> { new Thread(() -> { String m = laufSichern(); r.hudMeldung(m); datenMeldung = java.time.LocalTime.now().withNano(0) + " " + m; }, "LituyaBay1958-Lauf").start(); view.requestFocusInWindow(); });
        sp1.add(sichernB); sp1.add(laufB);
        put(sp1);
        sitzungBox = new FComboBox(new Object[]{"(keine Sitzung)"});
        sitzungBox.setToolTipText("Gesicherte Sitzungen, neueste zuerst");
        put(sitzungBox);
        JPanel sp2 = grid(2);
        FButton ladenB = new FButton("Sitzung laden"), listeB = new FButton("Liste neu");
        ladenB.addActionListener(e -> {
            if (sitzungBox.getSelectedItem() instanceof Eintrag en) new Thread(() -> {
                try {
                    com.dan.lituya.data.Speicher.Sitzung s = en.sp.sitzung(en.s.id());
                    if (s == null) { r.hudMeldung("Sitzung nicht mehr da"); return; }
                    r.imBild(() -> { int n = com.dan.lituya.gl.Sitzungen.herstellen(r, s); r.hudMeldung("Sitzung „" + s.name() + "“ geladen (" + n + " Werte)"); });
                    javax.swing.SwingUtilities.invokeLater(this::syncFromScene);
                } catch (Exception ex) { r.hudMeldung("Sitzung nicht geladen: " + ex.getMessage()); }
            }, "LituyaBay1958-Laden").start();
            view.requestFocusInWindow();
        });
        listeB.addActionListener(e -> { com.dan.lituya.data.Speicher.neuWaehlen(); sitzungenNeu(); view.requestFocusInWindow(); });
        sp2.add(ladenB); sp2.add(listeB);
        put(sp2);
        JPanel sp3 = grid(2);
        FButton archivB = new FButton("Archiv (F2)"), hilfeB = new FButton("Hilfe (F1)");
        archivB.setToolTipText("Befunde, Zeugen, Läufe, Bilder, Sitzungen und Regiebücher in einem eigenen Fenster");
        archivB.addActionListener(e -> Archiv.zeigen(r));
        hilfeB.addActionListener(e -> Hilfe.zeigen());
        sp3.add(archivB); sp3.add(hilfeB);
        put(sp3);
        put(small("<html><body style='width:215px'>Phase 19: Sitzungen, Läufe, Regiebücher und Bilder (F12, Blätter) in Oracle (db/szene, ant szenedb), "
                + "ohne Datenbank in daten/speicher; ant szenedb übernimmt sie später. Den Treiber 21 für LOD holt <i>ant lod</i> aus ORACLE_HOME.</body></html>"));
        javax.swing.SwingUtilities.invokeLater(this::sitzungenNeu);

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
        // W1: Himmel und Licht aus com.dan.sky, Glühen
        FCheckBox lichtBox = new FCheckBox("Himmel und Licht aus com.dan.sky (W1)");
        lichtBox.setSelected(r.licht.an);
        lichtBox.setToolTipText("Dämmerhimmel, Alpenglühen, Schatten der Kette, Dunst in zwei Schichten; aus: der Platzhalter von Phase 1");
        lichtBox.addActionListener(e -> { r.licht.an = lichtBox.isSelected(); view.requestFocusInWindow(); });
        put(lichtBox);
        // W2: Wasser aus com.dan.ocean, Spiegel der Berge
        FCheckBox meerBox = new FCheckBox("Wasser aus com.dan.ocean (W2)"), spiegelBox = new FCheckBox("Spiegel der Berge im Wasser");
        meerBox.setSelected(r.meer.an);
        spiegelBox.setSelected(r.meer.spiegel);
        meerBox.setToolTipText("Gletschersee mit Grund und Ufer, Gletschermilch zum Inlet; aus: das Wasser von Phase 1");
        spiegelBox.setToolTipText("Gelände und Wald gespiegelt in halber Auflösung (rund 3–5 ms auf der Radeon 680M)");
        meerBox.addActionListener(e -> { r.meer.an = meerBox.isSelected(); view.requestFocusInWindow(); });
        spiegelBox.addActionListener(e -> { r.meer.spiegel = spiegelBox.isSelected(); view.requestFocusInWindow(); });
        put(meerBox);
        put(spiegelBox);
        // W3b: Texturen der Hänge und Gischt aus den Daten der Welle
        FCheckBox texBox = new FCheckBox("Texturen der Hänge aus com.dan.surface (W3b)"), gischtBox = new FCheckBox("Gischt an der Front (W3b)");
        texBox.setSelected(r.texturen);
        gischtBox.setSelected(r.ereignisGl().vorhang);
        texBox.setToolTipText("Fels, Schutt und Kies im abgeschürften Band und auf der Abrissfläche; aus: nur die Großform");
        gischtBox.setToolTipText("Gischtwand, wo die Front ankommt, so hoch, wie die Welle dort aufläuft; verweht in rund 40 s");
        texBox.addActionListener(e -> { r.texturen = texBox.isSelected(); view.requestFocusInWindow(); });
        gischtBox.addActionListener(e -> { r.ereignisGl().vorhang = gischtBox.isSelected(); view.requestFocusInWindow(); });
        put(texBox);
        put(gischtBox);
        // W6: Landschaft aus den Daten (Sentinel-2, Relief, Eis) und Wasserfälle
        FCheckBox landBox = new FCheckBox("Landschaft aus Sentinel-2, Relief und Eis (W6)"), spaltenBox = new FCheckBox("Spalten auf den Gletschern (W6)"),
                faelleBox = new FCheckBox("Wasserfälle über den Steilstufen (W6)");
        landBox.setSelected(r.landschaft.an);
        spaltenBox.setSelected(r.landschaft.spalten);
        faelleBox.setSelected(r.faelle.an);
        landBox.setToolTipText("Farbe der Hänge aus Sentinel-2 (Juli 2020/2023, ohne das Licht der Aufnahme), Grate und Rinnen aus dem 5-m-Gelände, Fels aus cliff, Schnee mit Form; aus: Farben nach Höhe und Hang wie vor W6");
        spaltenBox.setToolTipText("Querspalten längs der Höhenlinien der Eisoberfläche, dichter wo ITS_LIVE Dehnung zeigt; Seracs im Eisfall [G]");
        faelleBox.setToolTipText("Fälle aus com.dan.fall, wo die Bäche aus LB_NET_WASSER über Stufen steiler als 40° fallen; Abfluss mit Schmelze und Regen");
        landBox.addActionListener(e -> { r.landschaft.an = landBox.isSelected(); view.requestFocusInWindow(); });
        spaltenBox.addActionListener(e -> { r.landschaft.spalten = spaltenBox.isSelected(); view.requestFocusInWindow(); });
        faelleBox.addActionListener(e -> { r.faelle.an = faelleBox.isSelected(); view.requestFocusInWindow(); });
        put(landBox);
        put(spaltenBox);
        put(faelleBox);
        FCheckBox bloomBox = new FCheckBox("Glühen um Helles (Bloom)");
        bloomBox.setSelected(r.bloom > 0);
        bloomBox.addActionListener(e -> { r.bloom = bloomBox.isSelected() ? 0.35 : 0; view.requestFocusInWindow(); });
        put(bloomBox);
        put(label("Belichtung (Blenden von Hand)"));
        put(expSl);
        expSl.addChangeListener(e -> { if (!fromScene) r.exposureBias = expSl.getValue() / 10.0; });
        put(label("HUD"));
        hudBox = new FComboBox(new Object[]{"kurz", "voll", "aus"});
        hudBox.setSelectedIndex(!r.showHud ? 2 : (r.hudFull ? 1 : 0));
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
        // Phase 19a: F1 das Hilfefenster, F2 das Archiv; ein Klick auf eine Marke wählt den Befund
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(Hilfe::zeigen));
        view.setKeyAction(java.awt.event.KeyEvent.VK_F2, () -> javax.swing.SwingUtilities.invokeLater(() -> Archiv.zeigen(r)));
        view.setClickListener((x, y, w, h) -> r.archiv.pick(x, y, w, h));
        // seit Phase 13b: die Leertaste hält an und läuft mit dem vorigen Tempo weiter (auch Zeitlupe und rückwärts)
        view.setKeyAction(java.awt.event.KeyEvent.VK_SPACE, () -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (clock.lapse != 0) { letzte = clock.lapse; clock.lapse = 0; } else clock.lapse = letzte == 0 ? 1 : letzte;
        }));
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

    /** Richtung des Abspielens: −1 rückwärts, 0 halt, 1 vorwärts (J, K, L). */
    private void richtung(int d) {
        if (d != 0) richtung = d;
        if (clock.lapse != 0) letzte = clock.lapse;
        clock.lapse = d == 0 ? 0 : d * TEMPO[Math.max(0, tempoBox.getSelectedIndex())];
        view.requestFocusInWindow();
    }

    private void tempo() {
        if (clock.lapse != 0) clock.lapse = Math.signum(clock.lapse) * TEMPO[Math.max(0, tempoBox.getSelectedIndex())];
    }

    private void stueck(com.dan.lituya.camera.Schaustuecke.Stueck st, FCheckBox spielen) {
        r.placeStueck(st);
        spielen.setSelected(true);
        free.setSelected(r.camera.mode == Camera.Mode.FREI);
        orbit.setSelected(false);
        for (int i = 0; i < TEMPO.length; i++) if (Math.abs(TEMPO[i] - st.lapse()) < 1e-9) tempoBox.setSelectedIndex(i);
        richtung = 1;
        syncFromScene();
        view.requestFocusInWindow();
    }

    /** Phase 19a: die letzte Meldung von Sichern und Lauf bleibt im Block DATEN stehen (das HUD zeigt sie nur 5 s). */
    private volatile String datenMeldung = "";

    private void tasten() {
        StringBuilder an = new StringBuilder();
        for (Ansichten.Ansicht a : Ansichten.ALLE) an.append(an.length() > 0 ? ", " : "").append(a.key()).append(' ').append(a.name());
        com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:420px'>"
                + "<b>Szene</b><br>Links ziehen: drehen · rechts ziehen: verschieben · Rad, Bild↑/↓, +/−: zoomen<br>"
                + "Pfeile: drehen · W A S D: gleiten · Q/E: ab und auf (frei) · Umschalt: schneller<br>"
                + "F: frei fliegen · R: von selbst kreisen · Pos1: Grundansicht · H: HUD kurz, voll, aus<br>"
                + "Leertaste: Uhr anhalten und weiter · N: Netzblick an und aus · F12: Foto nach docs/status · F5: Shader neu laden<br>"
                + "Ereignis: J rückwärts · K anhalten · L vorwärts · Z Zeitlupe (1:1 bis 1:20); Schaustücke im Block EREIGNIS<br>"
                + "Strg + Klick: Ort des Sturzes für „Was wäre wenn“ (Echtzeit im Block EREIGNIS)<br>"
                + "Ansichten: " + an + "<br><br>"
                + "<b>Fenster</b><br>F1: diese Übersicht · F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)</body></html>",
                "LituyaBay1958 · Tasten", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
    }

    /** Regler nach der Szene stellen (alle 0,5 s). */
    private FSlider jahrSl;

    /** Springt auf den 10. Juli des Jahrs y nach der Welle (Uhrzeit bleibt). */
    private void jahr(int y) {
        double h = clock.hour();
        clock.set(java.time.LocalDate.of(1958 + y, 7, 10), h);
        clock.zeitstand = y >= com.dan.lituya.scene.Wald1958.JAHRE - 1 ? SceneClock.Zeitstand.HEUTE : SceneClock.Zeitstand.DANACH;
        r.jahresRaffer = false;
        syncFromScene();
    }

    /** Jahres-Zeitraffer an oder aus; vor dem Morgen danach erst dorthin. */
    private void raffer() {
        if (r.jahresRaffer) { r.jahresRaffer = false; r.hudMeldung("Jahres-Zeitraffer angehalten"); return; }
        if (clock.simSeconds() < 8 * 3600 || clock.date().getYear() >= 2026) clock.set(SceneClock.Zeitstand.DANACH);
        clock.zeitstand = SceneClock.Zeitstand.DANACH;
        r.jahresRaffer = true;
        r.hudMeldung(String.format(Locale.GERMAN, "Jahres-Zeitraffer: %.0f Jahre je Minute (Y hält an)", r.jahreJeMinute));
        syncFromScene();
    }

    private void syncFromScene() {
        fromScene = true;
        int m = (int) Math.round(clock.hour() * 60) % (24 * 60);
        if (!time.getValueIsAdjusting()) time.setValue(m);
        if (!scaleSl.getValueIsAdjusting()) scaleSl.setValue((int) Math.round(r.renderScale * 100));
        if (!expSl.getValueIsAdjusting()) expSl.setValue((int) Math.round(r.exposureBias * 10));
        if (standBox.getSelectedItem() != clock.zeitstand) standBox.setSelectedItem(clock.zeitstand);
        if (jahrSl != null && !jahrSl.getValueIsAdjusting()) jahrSl.setValue(Math.min(jahrSl.getMaximum(), com.dan.lituya.scene.Wald1958.jahr(clock.date())));
        fromScene = false;
        labels();
    }

    private void labels() {
        txt(dateLbl, clock.local().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM u", Locale.GERMANY)));
        txt(timeLbl, "Ortszeit " + clock.timeLabelSec());
        txt(simLbl, "Simulationszeit " + clock.simLabel());
        txt(standLbl, "<html><body style='width:215px'>" + esc(clock.zeitstand.text) + "</body></html>");
        txt(echtzeitLbl, "<html><body style='width:215px'>" + esc(r.echtzeit.zeile()) + "</body></html>");
        String ez = r.ereignisZeile();
        txt(ereignisLbl, "<html><body style='width:215px'>" + esc(ez.isEmpty() ? "Abspielen: " + r.tempoText() + " · " + r.wiedergabe.zeile() : ez + " · " + r.wiedergabe.zeile()) + "</body></html>");
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
        txt(regieLbl, "<html><body style='width:215px'>" + esc(r.regieLine()) + "</body></html>");
        txt(klangLbl, "<html><body style='width:215px'>" + esc(r.klang.describe().replace("!", "")) + "</body></html>");
        txt(wetterLbl, "<html><body style='width:215px'>" + esc(r.wetterGl.info()) + "</body></html>");
        com.dan.lituya.data.Speicher spw = com.dan.lituya.data.Speicher.gewaehlt();
        txt(speicherLbl, "<html><body style='width:215px'>Speicher: " + esc(spw == null ? "noch nicht gewählt" : spw.wo()) + (com.dan.lituya.data.Speicher.hinweis().isEmpty() ? "" : " (" + esc(com.dan.lituya.data.Speicher.hinweis()) + ")")
                + (datenMeldung.isEmpty() ? "" : "<br>Zuletzt: " + esc(datenMeldung)) + "</body></html>");
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

    /** Eine Sitzung in der Liste (mit ihrem Speicher). */
    record Eintrag(com.dan.lituya.data.Speicher sp, com.dan.lituya.data.Speicher.Sitzung s) {
        @Override public String toString() { return s.zeile(); }
    }

    private FComboBox sitzungBox;

    /** Liest die gesicherten Sitzungen neu (im Hintergrund, Oracle kann dauern). */
    void sitzungenNeu() {
        new Thread(() -> {
            java.util.List<Object> l = new java.util.ArrayList<>();
            com.dan.lituya.data.Speicher sp = com.dan.lituya.data.Speicher.aktuell();
            try { for (com.dan.lituya.data.Speicher.Sitzung s : sp.sitzungen()) l.add(new Eintrag(sp, s)); } catch (Exception e) { l.add("(" + e.getMessage() + ")"); }
            if (l.isEmpty()) l.add("(keine Sitzung)");
            javax.swing.SwingUtilities.invokeLater(() -> { if (sitzungBox != null) { sitzungBox.removeAllItems(); for (Object o : l) sitzungBox.addItem(o); } });
        }, "LituyaBay1958-Sitzungen").start();
    }

    /** Die Einstellungen der Echtzeit als Lauf (Name aus Ort, Volumen, Grund, Pegel und Zeit). */
    String laufSichern() {
        com.dan.lituya.gl.Echtzeit ez = r.echtzeit;
        String name = String.format(Locale.GERMAN, "Echtzeit %s · %.1f Mio. m³ · %s · %+.1f m · %tF %<tR", ez.ort.name(), ez.volumen / 1e6, ez.grund.name(), ez.pegel, new java.util.Date());
        StringBuilder in = new StringBuilder();
        java.util.Properties p = com.dan.lituya.gl.Sitzungen.erfassen(r, name).zustand();
        for (String k : new java.util.TreeSet<>(p.stringPropertyNames())) if (k.startsWith("echtzeit.")) in.append(k).append('=').append(p.getProperty(k)).append('\n');
        com.dan.lituya.data.Speicher.Lauf l = new com.dan.lituya.data.Speicher.Lauf(name, "Echtzeit", ez.zeile(), ez.ort.name(), ez.volumen, ez.pegel, ez.grund.name(),
                ez.ganglinie.name(), com.dan.lituya.scene.WellenNetz.START_S, null, r.glInfo(), ez.bis(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, in.toString());
        com.dan.lituya.data.Speicher sp = com.dan.lituya.data.Speicher.aktuell();
        try { sp.laufSpeichern(l); return "Lauf gesichert: " + name + " (" + sp.wo() + ")"; }
        catch (Exception e) {
            try { com.dan.lituya.data.Speicher.datei().laufSpeichern(l); return "Lauf in die Dateien gesichert (" + e.getMessage() + ")"; } catch (Exception e2) { return "Lauf nicht gesichert: " + e2.getMessage(); }
        }
    }

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

    /** Blendenreihe in Drittelstufen (f/2 ist die volle Öffnung des Doppel-Gauß; der Weitwinkel kann f/1,9, das Tele ab f/5,4). */
    static final double[] BLENDEN = {2, 2.2, 2.5, 2.8, 3.2, 3.5, 4, 4.5, 5, 5.6, 6.3, 7.1, 8, 9, 10, 11, 13, 14, 16, 18, 20, 22};

    static int blendeIndex(double n) {
        int best = 0;
        for (int i = 1; i < BLENDEN.length; i++) if (Math.abs(Math.log(BLENDEN[i] / n)) < Math.abs(Math.log(BLENDEN[best] / n))) best = i;
        return best;
    }

    /** Fokusregler 0 … 100: 0,5 m bis 1 000 m (logarithmisch), 100 = ∞. */
    static double fokusAus(int x) { return x >= 100 ? 1e5 : 0.5 * Math.pow(2000, x / 100.0); }

    static int fokusWert(double m) { return m >= 1000 ? 100 : (int) Math.round(100 * Math.log(Math.max(0.5, m) / 0.5) / Math.log(2000)); }

    /**
     * Werkbuch Welle W9a: der Block KAMERA. Das Objektiv aus com.dan.lens (Weitwinkel, Doppel-Gauß oder Tele, nach der
     * Brennweite, auf sie gebracht): Brennweite und Blende als Satz, Blende in Drittelstufen, Fokus von Hand oder aus der
     * Bildmitte, Lamellen, die einzelnen Fehler, und die Belichtungszeit des Regens (bis W9c die Belichtung allen Lichts
     * wird). W9b: der Film aus com.dan.film (Material, Kopierlicht, Korn, Halation, Bildstand, Alter der Kopie).
     */
    private void kamera() {
        block("KAMERA", false);
        com.dan.lens.Lens ob = r.objektiv;
        FCheckBox obBox = new FCheckBox("Objektiv statt Lochkamera (im Kino immer)");
        obBox.setSelected(r.objektivAn);
        obBox.setToolTipText("Tiefenschärfe mit Bokeh, Schärfezieher, Reflexe und Stern der Sonne, Verzeichnung, Farbsäume, Randabfall (com.dan.lens)");
        obBox.addActionListener(e -> { r.objektivAn = obBox.isSelected(); view.requestFocusInWindow(); });
        put(obBox);
        JLabel obLbl = small("");
        put(obLbl);
        put(label("Objektiv"));
        String[] presets = {"Auge (23 mm, f/5,6)", "Weitwinkel 18 mm, f/2", "Weitwinkel 24 mm, f/8", "35 mm, f/4", "Normal 50 mm, f/2", "Porträt 85 mm, f/2",
                "120 mm, f/2,8", "Tele 250 mm, f/5,6", "Tele 400 mm, f/8"};
        double[][] pv = {{0, 5.6}, {18, 2}, {24, 8}, {35, 4}, {50, 2}, {85, 2}, {120, 2.8}, {250, 5.6}, {400, 8}};
        FComboBox obPre = new FComboBox(presets);
        obPre.setSelectedIndex(-1);
        obPre.setToolTipText("Brennweite (24 mm Bildhöhe) und Blende als Satz; unter 35 mm rechnet der Weitwinkel (Nakamura, ±38°, bis f/1,9), ab 130 mm das Tele (250 mm, ab f/5,4), dazwischen der Doppel-Gauß (bis f/2)");
        put(obPre);
        JLabel blLbl = small("");
        FSlider blSl = new FSlider(0, BLENDEN.length - 1, blendeIndex(ob.fNumber));
        blSl.setToolTipText("Blende in Drittelstufen: offen wenig Schärfentiefe und das Katzenauge am Rand, abgeblendet ein Stern um die Sonne; die Belichtung bleibt (wie mit Graufilter)");
        blSl.addChangeListener(e -> { ob.fNumber = BLENDEN[blSl.getValue()]; txt(blLbl, String.format(Locale.GERMAN, "Blende f/%.1f", ob.fNumber)); });
        txt(blLbl, String.format(Locale.GERMAN, "Blende f/%.1f", ob.fNumber));
        put(blLbl);
        put(blSl);
        obPre.addActionListener(e -> {
            int i = obPre.getSelectedIndex();
            if (i < 0) return;
            r.brennweite = pv[i][0];
            ob.fNumber = pv[i][1];
            r.objektivAn = true;
            obBox.setSelected(true);
            blSl.setValue(blendeIndex(ob.fNumber));
            view.requestFocusInWindow();
        });
        FCheckBox afBox = new FCheckBox("Autofokus (Bildmitte, mit Trägheit)");
        afBox.setSelected(ob.autoFocus);
        JLabel foLbl = small("");
        FSlider foSl = new FSlider(0, 100, fokusWert(ob.focusM));
        foSl.setToolTipText("Fokus von Hand, 0,5 m bis ∞ (der Schärfezieher führt nach, ohne Überschwingen)");
        afBox.addActionListener(e -> { ob.autoFocus = afBox.isSelected(); r.fokusZiel = null; view.requestFocusInWindow(); });
        foSl.addChangeListener(e -> {
            ob.focusM = fokusAus(foSl.getValue());
            if (foSl.getValueIsAdjusting()) { ob.autoFocus = false; afBox.setSelected(false); r.fokusZiel = null; }
            txt(foLbl, ob.focusM >= 1e4 ? "Fokus ∞" : String.format(Locale.GERMAN, "Fokus %.1f m", ob.focusM));
        });
        txt(foLbl, String.format(Locale.GERMAN, "Fokus %.1f m", ob.focusM));
        put(afBox);
        put(foLbl);
        put(foSl);
        put(label("Blende"));
        String[] lam = {"kreisrund", "5 gerade Lamellen (Stern mit 10)", "6 Lamellen", "7 gerade Lamellen (Stern mit 14)", "8 Lamellen, leicht gerundet", "9 Lamellen, gerundet", "11 Lamellen, gerundet", "16 Lamellen"};
        double[][] lv = {{0, 1}, {5, 0}, {6, 0.15}, {7, 0}, {8, 0.35}, {9, 0.6}, {11, 0.7}, {16, 0.5}};
        FComboBox lamBox = new FComboBox(lam);
        lamBox.setSelectedIndex(4);
        lamBox.addActionListener(e -> {
            int i = lamBox.getSelectedIndex();
            if (i < 0) return;
            ob.aperture.blades = (int) lv[i][0];
            ob.aperture.roundness = lv[i][1];
            view.requestFocusInWindow();
        });
        put(lamBox);
        JPanel f1 = grid(2), f2 = grid(2), f3 = grid(2);
        FCheckBox geist = new FCheckBox("Reflexe"), stern = new FCheckBox("Stern"), verz = new FCheckBox("Verzeichnung"), farb = new FCheckBox("Farbsäume"),
                rand = new FCheckBox("Randabfall"), schl = new FCheckBox("Schleierlicht");
        Object[][] bind = {{geist, (Runnable) () -> ob.ghosts = geist.isSelected(), ob.ghosts}, {stern, (Runnable) () -> ob.star = stern.isSelected(), ob.star},
                {verz, (Runnable) () -> ob.distortion = verz.isSelected(), ob.distortion}, {farb, (Runnable) () -> ob.chromatic = farb.isSelected(), ob.chromatic},
                {rand, (Runnable) () -> ob.vignetting = rand.isSelected(), ob.vignetting}, {schl, (Runnable) () -> ob.glare = schl.isSelected(), ob.glare}};
        for (Object[] b : bind) {
            FCheckBox c = (FCheckBox) b[0];
            c.setSelected((Boolean) b[2]);
            c.addActionListener(e -> { ((Runnable) b[1]).run(); view.requestFocusInWindow(); });
        }
        geist.setToolTipText("45 Geisterbilder der Sonne aus allen Doppelspiegelungen, gefärbt durch die Vergütung (MgF₂, λ/4 bei 550 nm)");
        stern.setToolTipText("Beugung an den geraden Kanten der Lamellen: je Kante ein Strahl, gerade Zahl n Strahlen, ungerade 2n");
        f1.add(geist); f1.add(stern); f2.add(verz); f2.add(farb); f3.add(rand); f3.add(schl);
        put(f1);
        put(f2);
        put(f3);
        // W9c: die Belichtung (com.dan.shutter)
        put(label("Verschluss der Kamera"));
        JLabel vsLbl = small("");
        put(vsLbl);
        String[] ub = {"Umlaufblende 45°", "90°", "144°", "172,8° (Netz 50 Hz bei 25 B/s)", "180° (1/48 s bei 24 B/s)", "270°", "360°"};
        double[] uv = {45, 90, 144, 172.8, 180, 270, 360};
        FComboBox ubBox = new FComboBox(ub);
        ubBox.setSelectedIndex(4);
        ubBox.setToolTipText("Öffnung der Umlaufblende: 180° belichtet die halbe Bildzeit (1/48 s bei 24 B/s); kleiner: schärfere Bewegung, ruckeliger Lauf");
        ubBox.addActionListener(e -> { int i = ubBox.getSelectedIndex(); if (i >= 0) r.verschluss.angleDeg = uv[i]; view.requestFocusInWindow(); });
        put(ubBox);
        String[] br = {"24 B/s (wie vorgeführt)", "48 B/s (Zeitlupe 1/2)", "96 B/s (1/4)", "192 B/s (1/8, am Einschlag)", "1 000 B/s (1/42)"};
        double[] bv = {24, 48, 96, 192, 1000};
        FComboBox brBox = new FComboBox(br);
        brBox.setSelectedIndex(0);
        brBox.setToolTipText("Bildrate der Kamera: über 24 läuft die Szene langsamer (Zeitlupe), die Belichtung wird kürzer; Regiebuch „bildrate 192“");
        brBox.addActionListener(e -> { int i = brBox.getSelectedIndex(); if (i >= 0) r.bildrateStellen(bv[i]); view.requestFocusInWindow(); });
        put(brBox);
        JPanel v1 = grid(2);
        FCheckBox mbBox = new FCheckBox("Bewegungsunschärfe"), ogBox = new FCheckBox("Öffnungsgang");
        mbBox.setSelected(r.bewegungAn);
        mbBox.setToolTipText("Mit dem Film: die Bewegung der Kamera während der Belichtung (McGuire 2012); in der Kino-Stufe aus der Zeit selbst");
        mbBox.addActionListener(e -> { r.bewegungAn = mbBox.isSelected(); view.requestFocusInWindow(); });
        ogBox.setSelected(r.verschluss.rolling);
        ogBox.setToolTipText("Die Kante der Umlaufblende läuft in einigen Millisekunden über das Fenster (Kino-Stufe)");
        ogBox.addActionListener(e -> { r.verschluss.rolling = ogBox.isSelected(); view.requestFocusInWindow(); });
        v1.add(mbBox); v1.add(ogBox);
        put(v1);
        put(label("Verschluss des Auges (Regen ohne Film)"));
        String[] vs = {"1/30 s", "1/48 s", "1/60 s", "1/125 s", "1/250 s (Auge)", "1/500 s", "1/1000 s", "1/2000 s"};
        double[] vv = {1 / 30.0, 1 / 48.0, 1 / 60.0, 1 / 125.0, 1 / 250.0, 1 / 500.0, 1 / 1000.0, 1 / 2000.0};
        FComboBox vsBox = new FComboBox(vs);
        vsBox.setSelectedIndex(4);
        vsBox.setToolTipText("Belichtungszeit der Tropfen ohne Film (W7b, WF26): lang zeigt Spuren, kurz die Form; mit dem Film belichtet die Umlaufblende");
        vsBox.addActionListener(e -> { int i = vsBox.getSelectedIndex(); if (i >= 0) r.regenVerschlussAuge = vv[i]; view.requestFocusInWindow(); });
        put(vsBox);
        // W9b: der Film
        put(label("Film"));
        FCheckBox fiBox = new FCheckBox("Film statt Filmkurve (im Kino immer)");
        fiBox.setSelected(r.filmAn);
        fiBox.setToolTipText("Negativ und Kopie mit Kurven je Schicht, Kopierlicht, Korn nach Selwyn, Halation aus dem Träger, Bildstand, Flackern (com.dan.film)");
        fiBox.addActionListener(e -> { r.filmAn = fiBox.isSelected(); view.requestFocusInWindow(); });
        put(fiBox);
        JLabel fiLbl = small("");
        put(fiLbl);
        String[] mat = {"nach dem Zeitstand (1958: 5248, heute: Vision3)", "Eastman Color 5248 auf 5382 · CinemaScope", "Kodak Vision3 250D auf 2383 · Super 35",
                "Kodak Vision3 500T auf 2383 · Super 35"};
        FComboBox matBox = new FComboBox(mat);
        matBox.setSelectedIndex(0);
        matBox.setToolTipText("1958 drehte Hollywood in CinemaScope auf Eastman Color 5248 (ASA 25, rund 9 Blenden Spielraum, grobes Korn, Halation); "
                + "heute Vision3 (rund 14 Blenden, feines Korn, Rückschicht gegen die Halation)");
        matBox.addActionListener(e -> {
            int i = matBox.getSelectedIndex();
            if (i == 0) r.filmNachZeitstand = true;
            else r.filmMaterial(i == 1 ? Renderer.EC5248 : i == 2 ? Renderer.V3_250D : Renderer.V3_500T);
            r.filmAn = true;
            fiBox.setSelected(true);
            view.requestFocusInWindow();
        });
        put(matBox);
        String[] kl = {"Kopierlicht nach dem Zeitstand", "neutral 25-25-25", "Abend 1958 (warm) 23-25-27", "Die Welle (kühl, dicht) 26-25-24 +1",
                "Morgen danach (fahl) 26-25-23 -1", "Abend heute 24-25-26", "Nacht (blau) 27-25-22 +1"};
        String[] kn = {"zeitstand", "neutral", "abend1958", "welle", "morgen", "heute", "nacht"};
        FComboBox klBox = new FComboBox(kl);
        klBox.setSelectedIndex(0);
        klBox.setToolTipText("Lichtbestimmung im Kopierwerk: Punkte je Farbe zu 0,025 log E; weniger Rot und mehr Blau macht die Kopie wärmer (Regiebuch: „kopierlicht 24 25 26“)");
        klBox.addActionListener(e -> { int i = klBox.getSelectedIndex(); if (i >= 0) { r.kopierlichtStellen(kn[i]); r.filmAn = true; fiBox.setSelected(true); } view.requestFocusInWindow(); });
        put(klBox);
        JLabel koLbl = small("");
        FSlider koSl = new FSlider(0, 200, (int) Math.round(100 * r.film.grainGain));
        koSl.setToolTipText("Korn gegen das Material (100 % wie gemessen: RMS-Körnigkeit nach Selwyn auf die Fläche eines Bildpunkts)");
        koSl.addChangeListener(e -> { r.film.grainGain = koSl.getValue() / 100.0; txt(koLbl, "Korn " + koSl.getValue() + " %"); });
        txt(koLbl, "Korn " + koSl.getValue() + " %");
        put(koLbl);
        put(koSl);
        JLabel alLbl = small("");
        FSlider alSl = new FSlider(0, 70, (int) Math.round(r.film.printAgeYears));
        alSl.setToolTipText("Alter der Kopie: die Farbstoffe der 1950er verblassen (Gelb und Cyan zuerst, die Kopie wird purpur); 2383 hält");
        alSl.addChangeListener(e -> { r.film.printAgeYears = alSl.getValue(); txt(alLbl, alSl.getValue() == 0 ? "Kopie frisch" : "Kopie " + alSl.getValue() + " Jahre alt"); });
        txt(alLbl, "Kopie frisch");
        put(alLbl);
        put(alSl);
        JPanel g1 = grid(2), g2 = grid(2);
        FCheckBox hal = new FCheckBox("Halation"), wv = new FCheckBox("Bildstand"), fl = new FCheckBox("Flackern"), ko = new FCheckBox("Korn");
        Object[][] fb = {{hal, (Runnable) () -> r.film.halation = hal.isSelected(), r.film.halation}, {wv, (Runnable) () -> r.film.weave = wv.isSelected(), r.film.weave},
                {fl, (Runnable) () -> r.film.flicker = fl.isSelected(), r.film.flicker}, {ko, (Runnable) () -> r.film.grain = ko.isSelected(), r.film.grain}};
        for (Object[] b : fb) {
            FCheckBox c = (FCheckBox) b[0];
            c.setSelected((Boolean) b[2]);
            c.addActionListener(e -> { ((Runnable) b[1]).run(); view.requestFocusInWindow(); });
        }
        hal.setToolTipText("Licht aus dem Träger zurück in die rote Schicht: 5248 mit Ring (2·t·tan θ_c), Vision3 mit Rückschicht nur ein Schein");
        wv.setToolTipText("Das Bild wandert im Fenster des Projektors um Hundertstel Millimeter");
        g1.add(ko); g1.add(hal); g2.add(wv); g2.add(fl);
        put(g1);
        put(g2);
        put(small("<html><body style='width:215px'><b>Werkbuch Welle W9a.</b> Gerechnet aus einem Linsenschnitt (unter 35 mm Weitwinkel nach Nakamura, "
                + "bis 130 mm Doppel-Gauß 50 mm f/2 nach Tronnier, darüber Tele 250 mm; Gläser mit Dispersion, Vergütung MgF₂), auf die Brennweite gebracht: Schärfentiefe nach der dünnen Linse, das Bokeh in der Form der "
                + "Blende mit dem Katzenauge der Fassungen zum Rand, hinter dem Fokus helle Ränder und davor ein weicher Kern (sphärische Aberration "
                + "aus echten Strahlen), Farbsäume, Fokusatmung, die Reflexe und der Stern der Sonne. Regiebuch: „objektiv 85 2,8“, „blende 11“, "
                + "„fokus auto“, „fokus 12“, „fokus Ort“. Taste F9: Bedienfeld weg und wieder da.<br><b>W9b.</b> Der Film: „film an“, „film 5248“, "
                + "„kopierlicht 24 25 26 +1“, „kopie 68“.<br><b>W9c.</b> Die Belichtung: „bildrate 192“ (Zeitlupe), „umlaufblende 180“, „verschluss 1/48“; "
                + "die Kino-Stufe für Film und Standbild (ant film -Dteilbilder=32).</body></html>"));
        new javax.swing.Timer(400, e -> {
            if (!isShowing()) return;
            txt(obLbl, "<html><body style='width:215px'>" + esc(r.objektivLine()) + "</body></html>");
            if (obBox.isSelected() != r.objektivAn) obBox.setSelected(r.objektivAn);
            txt(fiLbl, "<html><body style='width:215px'>" + esc(r.filmLine()) + "</body></html>");
            txt(vsLbl, "<html><body style='width:215px'>" + esc(r.verschlussLine()) + "</body></html>");
            if (fiBox.isSelected() != r.filmAn) fiBox.setSelected(r.filmAn);
            if (afBox.isSelected() != ob.autoFocus) afBox.setSelected(ob.autoFocus);
        }).start();
    }

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
