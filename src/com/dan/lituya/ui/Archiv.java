package com.dan.lituya.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fframe.FFrame;
import com.dan.ftabpane.FTabPane;
import com.dan.ftable.FTable;
import com.dan.lituya.data.Speicher;
import com.dan.lituya.gl.ArchivSzene;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.Regie;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Das Archiv (Phase 19a, Werkbuch 12 und 13.1, F51–F53): ein eigenes Fenster mit sechs Reitern über dem {@link Speicher}
 * (Oracle oder {@code daten/speicher}).
 * <ul>
 * <li><b>Befunde</b>: Liste mit Art, Wert, Ort und Quellen; als Marken in der Szene (Farbe nach Art), ein Klick auf eine
 * Marke wählt die Zeile; „Kamera zum Ort“, „Uhr auf die Zeit“.</li>
 * <li><b>Zeugen</b>: die Aussagen nach der Zeit; als Untertitel, solange die Uhr in ihrer Zeit steht; „Uhr hierhin“.</li>
 * <li><b>Läufe</b>: die Kennzahlen nebeneinander und als Balken gegen den Beleg; „Abspielen“ (vorgerechnet: die
 * Wiedergabe; Echtzeit: die Eingaben neu rechnen).</li>
 * <li><b>Bilder</b>: Vorschauen der Fotos und Blätter; „Kamera hierhin“ stellt Kamera und Uhr wie beim Foto.</li>
 * <li><b>Sitzungen</b>: laden, löschen, sichern.</li>
 * <li><b>Regiebücher</b>: die mitgelieferten und eigenen; Editor mit Prüfung, speichern, im Fenster oder im Kino
 * abspielen.</li>
 * </ul>
 */
public final class Archiv {
    static final Color BG = new Color(16, 22, 27), INK = new Color(222, 230, 234), MUTED = new Color(150, 165, 172), GOLD = new Color(232, 178, 92);

    final Renderer r;
    final FFrame frame;
    final JLabel status = new JLabel(" ");
    private static Archiv offen;

    /** Öffnet das Archiv (oder holt es nach vorn). */
    public static synchronized void zeigen(Renderer r) {
        if (offen == null) offen = new Archiv(r);
        offen.frame.setVisible(true);
        offen.frame.toFront();
        offen.neu();
    }

    /** Für Bildproben (Schau): das offene Archiv. */
    static Archiv offenFuerSchau() { return offen; }

    Speicher.BildKopf bildWahlErster() { return bildWahl != null ? bildWahl : bilderListeErster; }
    volatile Speicher.BildKopf bilderListeErster;

    /** Für den Klick in der Szene: die Zeile des Befunds wählen (wenn das Archiv offen ist). */
    public static void waehle(Speicher.Befund b) {
        Archiv a = offen;
        if (a != null) SwingUtilities.invokeLater(() -> a.befundWaehlen(b));
    }

    // ------------------------------------------------------------------ Modelle

    /** Ein Tabellenmodell über eine Liste mit Spalten als Funktionen. */
    static final class Modell<T> extends AbstractTableModel {
        final String[] namen;
        final List<Function<T, Object>> spalten;
        List<T> zeilen = new ArrayList<>();
        @SafeVarargs Modell(String[] namen, Function<T, Object>... sp) { this.namen = namen; this.spalten = List.of(sp); }
        void setzen(List<T> l) { zeilen = new ArrayList<>(l); fireTableDataChanged(); }
        T zeile(int i) { return i < 0 || i >= zeilen.size() ? null : zeilen.get(i); }
        @Override public int getRowCount() { return zeilen.size(); }
        @Override public int getColumnCount() { return namen.length; }
        @Override public String getColumnName(int c) { return namen[c]; }
        @Override public Object getValueAt(int r, int c) { Object v = spalten.get(c).apply(zeilen.get(r)); return v == null ? "" : v; }
    }

    static String z(double v, String fmt) { return Double.isNaN(v) ? "–" : String.format(Locale.GERMAN, fmt, v); }

    /** „+5:22“ aus Sekunden nach dem Stoß. */
    static String zeit(double s) {
        if (Double.isNaN(s)) return "";
        int a = (int) Math.round(Math.abs(s));
        return (s < 0 ? "−" : "+") + a / 60 + ":" + String.format("%02d", a % 60);
    }

    final Modell<Speicher.Befund> befunde = new Modell<>(new String[]{"Thema", "Aussage", "Wert", "Einheit", "Art", "Ort", "Zeit", "Quellen"},
            Speicher.Befund::thema, Speicher.Befund::aussage, b -> Double.isNaN(b.wert()) ? "" : ArchivSzeneZahl.zahl(b.wert()), Speicher.Befund::einheit, Speicher.Befund::art,
            Speicher.Befund::ortName, b -> zeit(b.zeitS()), Speicher.Befund::quellen);
    final Modell<Speicher.Zeuge> zeugen = new Modell<>(new String[]{"von", "bis", "Name", "Rolle", "Boot", "Aussage", "Art", "Quelle"},
            g -> zeit(g.vonS()), g -> zeit(g.bisS()), Speicher.Zeuge::name, Speicher.Zeuge::rolle, Speicher.Zeuge::boot, Speicher.Zeuge::aussage, Speicher.Zeuge::art, Speicher.Zeuge::quelle);
    final Modell<Speicher.Lauf> laeufe = new Modell<>(new String[]{"Lauf", "Art", "Sporn (m)", "größter Stand (m)", "Cenotaph (s)", "Anchorage Cove (s)", "Eingang (s)", "bis (s)"},
            Speicher.Lauf::name, Speicher.Lauf::art, l -> z(l.auflaufSporn(), "%.0f"), l -> z(l.auflaufMax(), "%.0f"), l -> z(l.ankunftCenotaph(), "%.0f"),
            l -> z(l.ankunftAnchorage(), "%.0f"), l -> z(l.ankunftEingang(), "%.0f"), l -> z(l.dauerS(), "%.0f"));
    final Modell<Speicher.Sitzung> sitzungen = new Modell<>(new String[]{"Name", "gesichert", "Zeitstand", "Rechner", "Lauf", "Regiebuch"},
            Speicher.Sitzung::name, Speicher.Sitzung::gespeichert, Speicher.Sitzung::zeitstand, Speicher.Sitzung::rechner, Speicher.Sitzung::lauf, Speicher.Sitzung::regiebuch);
    final FTable befundTab = new FTable(befunde), zeugeTab = new FTable(zeugen), laufTab = new FTable(laeufe), sitzungTab = new FTable(sitzungen);
    final LaufBalken balken = new LaufBalken();
    final JPanel bilderGitter = new JPanel(new GridLayout(0, 2, 10, 12));
    final JLabel bildGross = new JLabel("", JLabel.CENTER), bildText = new JLabel(" ");
    volatile Speicher.BildKopf bildWahl;
    final DefaultListModel<Speicher.Buch> buecher = new DefaultListModel<>();
    final JList<Speicher.Buch> buchListe = new JList<>(buecher);
    final com.dan.fcodeeditor.FCodeEditor editor = new com.dan.fcodeeditor.FCodeEditor();
    final JLabel pruefLbl = new JLabel(" ");
    List<Speicher.Befund> alleBefunde = List.of();

    /** ArchivSzene.zahl ist paketintern: hier dieselbe Darstellung. */
    static final class ArchivSzeneZahl {
        static String zahl(double v) {
            if (Math.abs(v) >= 1e6) return String.format(Locale.GERMAN, "%.1f Mio.", v / 1e6);
            if (Math.abs(v - Math.rint(v)) < 1e-9) return String.format(Locale.GERMAN, "%,.0f", v);
            return String.format(Locale.GERMAN, "%.2f", v);
        }
    }

    Archiv(Renderer r) {
        this.r = r;
        frame = new FFrame("LituyaBay1958 · Archiv");
        JPanel root = frame.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        FTabPane tabs = new FTabPane();
        tabs.addTab("Befunde", befundReiter());
        tabs.addTab("Zeugen", zeugenReiter());
        tabs.addTab("Läufe", laufReiter());
        tabs.addTab("Bilder", bilderReiter());
        tabs.addTab("Sitzungen", sitzungReiter());
        tabs.addTab("Regiebücher", buchReiter());
        tabs.setCloseable(false);
        root.add(tabs, BorderLayout.CENTER);
        status.setForeground(MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        root.add(status, BorderLayout.SOUTH);
        frame.setPreferredFrameSize(new Dimension(1280, 760));
        frame.setSize(1280, 760);
        frame.setLocationRelativeTo(null);
        r.archiv.onPick = Archiv::waehle;
    }

    static JPanel reihe(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    static JLabel text(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(INK);
        return l;
    }

    /** Phase 20: Bildlauf im F-Stil ({@code com.dan.fscrollpane.FScrollPane}) statt der hellen Leisten von Swing. */
    static JScrollPane scroll(Component c) {
        com.dan.fscrollpane.FScrollPane s = new com.dan.fscrollpane.FScrollPane(c);
        s.setBodyColor(BG);
        s.getViewport().setBackground(BG);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getVerticalScrollBar().setUnitIncrement(24);
        s.getHorizontalScrollBar().setUnitIncrement(24);
        return s;
    }

    /** Phase 20: ein dunkler, schmaler Teiler statt des hellen von Swing. */
    static JSplitPane teiler(JSplitPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setDividerSize(6);
        sp.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(new Color(26, 34, 40));
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(new Color(60, 110, 118));
                        if (getWidth() < getHeight()) g.fillRect(getWidth() / 2 - 1, getHeight() / 2 - 12, 2, 24);
                        else g.fillRect(getWidth() / 2 - 12, getHeight() / 2 - 1, 24, 2);
                    }
                };
            }
        });
        sp.setBorder(BorderFactory.createEmptyBorder());
        return sp;
    }

    static JPanel panel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        return p;
    }

    /** Im Hintergrund lesen, dann im Swing-Faden setzen; Fehler in die Statuszeile. */
    interface Lies<T> { T lies(Speicher s) throws Exception; }

    <T> void lesen(Lies<T> l, java.util.function.Consumer<T> setzen) {
        new Thread(() -> {
            Speicher sp = Speicher.aktuell();
            try {
                T v = l.lies(sp);
                SwingUtilities.invokeLater(() -> { setzen.accept(v); status.setText("Speicher: " + sp.wo() + (Speicher.hinweis().isEmpty() ? "" : " (" + Speicher.hinweis() + ")")); });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> status.setText("Lesen fehlgeschlagen (" + sp.wo() + "): " + e.getMessage()));
            }
        }, "LituyaBay1958-Archiv").start();
    }

    /** Alles neu lesen. */
    void neu() {
        lesen(Speicher::befunde, l -> { alleBefunde = l; filtern(); r.archiv.setzen(l, r.archiv.zeugen()); });
        lesen(Speicher::zeugen, l -> { zeugen.setzen(l); r.archiv.setzen(r.archiv.befunde(), l); });
        lesen(Speicher::laeufe, l -> { laeufe.setzen(l); balken.setzen(l); });
        lesen(Speicher::bilderListe, this::bilderSetzen);
        lesen(Speicher::sitzungen, sitzungen::setzen);
        lesen(sp -> {
            Map<String, Speicher.Buch> m = new LinkedHashMap<>();
            for (Regie.Book b : Regie.all()) m.put(b.name(), new Speicher.Buch(b.name(), b.text(), true, null));
            for (Speicher.Buch b : sp.regiebuecher()) if (!(b.mitgeliefert() && m.containsKey(b.name()))) m.put(b.name(), b);
            return new ArrayList<>(m.values());
        }, l -> { buecher.clear(); for (Speicher.Buch b : l) buecher.addElement(b); });
    }

    // ------------------------------------------------------------------ Befunde

    final Set<String> arten = new HashSet<>(Set.of("belegt", "gemessen", "gerechnet", "gesetzt"));
    final com.dan.ftextfield.FTextfield suche = new com.dan.ftextfield.FTextfield();

    JComponent befundReiter() {
        JPanel p = panel();
        FCheckBox marken = new FCheckBox("Marken in der Szene");
        marken.setSelected(r.archiv.marken);
        marken.addActionListener(e -> r.archiv.marken = marken.isSelected());
        List<Component> kopf = new ArrayList<>(List.of(marken, Box.createHorizontalStrut(16)));
        for (String a : List.of("belegt", "gemessen", "gerechnet", "gesetzt")) {
            FCheckBox c = new FCheckBox(a);
            c.setSelected(true);
            c.setForeground(ArchivSzene.farbe(a));
            c.addActionListener(e -> { if (c.isSelected()) arten.add(a); else arten.remove(a); r.archiv.arten = Set.copyOf(arten); filtern(); });
            kopf.add(c);
        }
        suche.setColumns(18);
        suche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
        });
        kopf.add(Box.createHorizontalStrut(16));
        kopf.add(text("Suche"));
        kopf.add(suche);
        p.add(reihe(kopf.toArray(new Component[0])), BorderLayout.NORTH);
        befundTab.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        befundTab.getColumnModel().getColumn(1).setPreferredWidth(520);
        befundTab.getSelectionModel().addListSelectionListener(e -> {
            Speicher.Befund b = befunde.zeile(befundTab.getSelectedRow());
            r.archiv.gewaehlt = b == null ? null : b.schluessel();
        });
        p.add(scroll(befundTab), BorderLayout.CENTER);
        FButton hin = new FButton("Kamera zum Ort"), uhr = new FButton("Uhr auf die Zeit");
        hin.addActionListener(e -> { Speicher.Befund b = befunde.zeile(befundTab.getSelectedRow()); if (b != null && !Double.isNaN(b.e())) kameraZu(b.e(), b.n()); });
        uhr.addActionListener(e -> { Speicher.Befund b = befunde.zeile(befundTab.getSelectedRow()); if (b != null && !Double.isNaN(b.zeitS())) uhrAuf(b.zeitS() - 3); });
        JLabel hilfe = new JLabel("Farbe der Marke nach Art · Klick auf eine Marke in der Szene wählt die Zeile");
        hilfe.setForeground(MUTED);
        p.add(reihe(hin, uhr, hilfe), BorderLayout.SOUTH);
        return p;
    }

    void filtern() {
        String q = suche.getText() == null ? "" : suche.getText().toLowerCase(Locale.ROOT).strip();
        List<Speicher.Befund> l = new ArrayList<>();
        for (Speicher.Befund b : alleBefunde)
            if (arten.contains(b.art()) && (q.isEmpty() || (b.thema() + " " + b.aussage() + " " + b.quellen() + " " + b.ortName()).toLowerCase(Locale.ROOT).contains(q))) l.add(b);
        befunde.setzen(l);
    }

    void befundWaehlen(Speicher.Befund b) {
        for (int i = 0; i < befunde.zeilen.size(); i++)
            if (befunde.zeilen.get(i).schluessel().equals(b.schluessel())) {
                befundTab.setRowSelectionInterval(i, i);
                befundTab.scrollRectToVisible(befundTab.getCellRect(i, 0, true));
            }
    }

    /** Kamera schräg von Südosten auf einen Ort (UTM), 700 m entfernt, 260 m über dem Grund. */
    void kameraZu(double e, double n) {
        r.imBild(() -> {
            double[] p = Site.fromUtm(e, n);
            double g = Math.max(0, r.stapelHoehe(p[0], p[1]));
            if (Double.isNaN(g)) g = 0;
            double ex = p[0] + 500, ez = p[1] + 500, ey = g + 260;
            double h = (Math.toDegrees(Math.atan2(p[0] - ex, -(p[1] - ez))) + 360) % 360, ne = -Math.toDegrees(Math.atan2(ey - g, Math.hypot(500, 500)));
            r.camera.stopFahrt();
            r.camera.stopMitfahrt();
            r.camera.place(ex, ey, ez, h, ne);
            r.hudMeldung("Kamera zu E " + Math.round(e) + " N " + Math.round(n));
        });
    }

    /** Uhr auf s nach dem Stoß (Nacht von 1958), Zeitraffer 1. */
    void uhrAuf(double s) {
        r.imBild(() -> {
            r.clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + Math.round(s * 1000));
            r.clock.zeitstand = s < 0 ? SceneClock.Zeitstand.VOR : SceneClock.Zeitstand.WELLE;
            r.clock.lapse = 1;
            r.wiedergabe.setzen(r.clock.simSeconds(), 1);
            r.hudMeldung("Uhr auf " + zeit(s) + " nach dem ersten Stoß");
        });
    }

    // ------------------------------------------------------------------ Zeugen

    JComponent zeugenReiter() {
        JPanel p = panel();
        FCheckBox ut = new FCheckBox("Untertitel zur Zeit (in der Nacht von 1958)");
        ut.setSelected(r.archiv.untertitel);
        ut.addActionListener(e -> r.archiv.untertitel = ut.isSelected());
        p.add(reihe(ut), BorderLayout.NORTH);
        zeugeTab.getColumnModel().getColumn(5).setPreferredWidth(520);
        p.add(scroll(zeugeTab), BorderLayout.CENTER);
        FButton hin = new FButton("Uhr hierhin und zum Ort");
        hin.addActionListener(e -> {
            Speicher.Zeuge g = zeugen.zeile(zeugeTab.getSelectedRow());
            if (g == null) return;
            if (!Double.isNaN(g.e())) kameraZu(g.e(), g.n());
            if (!Double.isNaN(g.vonS())) uhrAuf(g.vonS() - 3);
            r.archiv.untertitel = true;
            ut.setSelected(true);
        });
        JLabel h = new JLabel("Wörtlich nur kurz, sonst sinngemäß; weitere Aussagen kommen mit Phase 0b");
        h.setForeground(MUTED);
        p.add(reihe(hin, h), BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------------ Läufe

    JComponent laufReiter() {
        JPanel p = panel();
        JSplitPane sp = teiler(new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(laufTab), balken));
        sp.setResizeWeight(0.45);
        sp.setBorder(BorderFactory.createEmptyBorder());
        p.add(sp, BorderLayout.CENTER);
        laufTab.getColumnModel().getColumn(0).setPreferredWidth(380);
        laufTab.getSelectionModel().addListSelectionListener(e -> balken.wahl = laufTab.getSelectedRow());
        FButton ab = new FButton("Abspielen"), neu = new FButton("Neu lesen");
        ab.addActionListener(e -> abspielen(laeufe.zeile(laufTab.getSelectedRow())));
        neu.addActionListener(e -> neu());
        JLabel h = new JLabel("Vorgerechnet: die Wiedergabe ab dem Einschlag · Echtzeit: die Eingaben werden neu gerechnet (Block EREIGNIS)");
        h.setForeground(MUTED);
        p.add(reihe(ab, neu, h), BorderLayout.SOUTH);
        return p;
    }

    void abspielen(Speicher.Lauf l) {
        if (l == null) return;
        if ("Echtzeit".equals(l.art())) {
            Properties p = new Properties();
            try { p.load(new java.io.StringReader(l.eingaben() == null ? "" : l.eingaben())); } catch (java.io.IOException e) { /* leer */ }
            com.dan.lituya.gl.Echtzeit ez = r.echtzeit;
            try {
                if (p.getProperty("echtzeit.volumen") != null) ez.volumen = Double.parseDouble(p.getProperty("echtzeit.volumen"));
                if (p.getProperty("echtzeit.pegel") != null) ez.pegel = Double.parseDouble(p.getProperty("echtzeit.pegel"));
                if (p.getProperty("echtzeit.grund") != null) ez.grund = com.dan.lituya.gl.Echtzeit.Grund.valueOf(p.getProperty("echtzeit.grund"));
                if (p.getProperty("echtzeit.ganglinie") != null) ez.ganglinie = com.dan.lituya.gl.Echtzeit.Ganglinie.valueOf(p.getProperty("echtzeit.ganglinie"));
                String o = p.getProperty("echtzeit.ort", l.ort());
                for (com.dan.lituya.gl.Echtzeit.Ort x : com.dan.lituya.gl.Echtzeit.ORTE) if (x.name().equals(o)) ez.ort = x;
            } catch (RuntimeException e) { status.setText("Eingaben nicht lesbar: " + e.getMessage()); return; }
            ez.neu();
            uhrAuf(com.dan.lituya.scene.WellenNetz.START_S - 5);
            status.setText("Echtzeit wird gebaut: " + l.name());
        } else {
            r.wiedergabe.welle(l.name());
            r.wiedergabe.an(true);
            uhrAuf(com.dan.lituya.scene.WellenNetz.START_S);
            status.setText("Wiedergabe: " + l.name());
        }
    }

    /** Die Kennzahlen als Balken: Auflauf am Sporn gegen den Beleg 524 m, die Ankünfte als Punkte auf einer Zeitachse. */
    static final class LaufBalken extends JComponent {
        List<Speicher.Lauf> l = List.of();
        volatile int wahl = -1;
        LaufBalken() { setPreferredSize(new Dimension(600, 300)); setOpaque(true); }
        void setzen(List<Speicher.Lauf> x) { l = List.copyOf(x); repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(BG);
            g.fillRect(0, 0, w, h);
            if (l.isEmpty()) return;
            int links = 260, mitte = links + (w - links) / 2 - 10, n = l.size(), zh = Math.max(14, Math.min(28, (h - 60) / n));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.setColor(INK);
            g.drawString("Auflauf am Sporn (m), Linie: belegt 524 m", links, 18);
            g.drawString("Ankunft der Front (s nach dem Stoß): ● Cenotaph  ■ Anchorage Cove  ▲ Eingang", mitte + 20, 18);
            double sx = (mitte - links - 10) / 600.0, tmin = 100, tmax = 600, tx = (w - mitte - 40) / (tmax - tmin);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            for (int i = 0; i < n; i++) {
                Speicher.Lauf x = l.get(i);
                int y = 32 + i * zh;
                if (i == wahl) { g.setColor(new Color(255, 255, 255, 25)); g.fillRect(0, y - 2, w, zh); }
                g.setColor(MUTED);
                String name = x.name().length() > 40 ? x.name().substring(0, 39) + "…" : x.name();
                g.drawString(name, 10, y + zh - 8);
                if (!Double.isNaN(x.auflaufSporn())) {
                    g.setColor("Echtzeit".equals(x.art()) ? new Color(90, 160, 235) : new Color(240, 160, 60));
                    g.fillRoundRect(links, y + 2, (int) (x.auflaufSporn() * sx), zh - 8, 4, 4);
                    g.setColor(INK);
                    g.drawString(String.format(Locale.GERMAN, "%.0f", x.auflaufSporn()), links + (int) (x.auflaufSporn() * sx) + 4, y + zh - 8);
                }
                double[] t = {x.ankunftCenotaph(), x.ankunftAnchorage(), x.ankunftEingang()};
                for (int k = 0; k < 3; k++) {
                    if (Double.isNaN(t[k])) continue;
                    int px = mitte + 20 + (int) ((t[k] - tmin) * tx), py = y + zh / 2 - 2;
                    g.setColor(new Color[]{new Color(96, 200, 120), GOLD, new Color(230, 110, 110)}[k]);
                    if (k == 0) g.fillOval(px - 4, py - 4, 8, 8);
                    else if (k == 1) g.fillRect(px - 4, py - 4, 8, 8);
                    else g.fillPolygon(new int[]{px - 5, px + 5, px}, new int[]{py + 4, py + 4, py - 5}, 3);
                }
            }
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{5, 4}, 0));
            int x524 = links + (int) (524 * sx);
            g.drawLine(x524, 26, x524, 32 + n * zh);
            g.setStroke(new BasicStroke(1f));
            g.setColor(MUTED);
            for (int s = 100; s <= 600; s += 100) {
                int px = mitte + 20 + (int) ((s - tmin) * tx);
                g.drawLine(px, 32 + n * zh, px, 36 + n * zh);
                g.drawString(String.valueOf(s), px - 10, 50 + n * zh);
            }
        }
    }

    // ------------------------------------------------------------------ Bilder

    JComponent bilderReiter() {
        JPanel p = panel();
        bilderGitter.setBackground(BG);
        JPanel rechts = panel();
        bildGross.setPreferredSize(new Dimension(640, 400));
        bildText.setForeground(INK);
        bildText.setVerticalAlignment(JLabel.TOP);
        rechts.add(bildGross, BorderLayout.CENTER);
        rechts.add(bildText, BorderLayout.SOUTH);
        JPanel gw = panel();
        gw.add(bilderGitter, BorderLayout.NORTH);
        JScrollPane gs = scroll(gw);
        gs.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        gs.getVerticalScrollBar().setUnitIncrement(24);
        gs.setPreferredSize(new Dimension(430, 400));
        JSplitPane sp = teiler(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gs, rechts));
        sp.setResizeWeight(0.0);
        sp.setBorder(BorderFactory.createEmptyBorder());
        p.add(sp, BorderLayout.CENTER);
        FButton hin = new FButton("Kamera hierhin"), neu = new FButton("Neu lesen");
        hin.addActionListener(e -> kameraWieFoto(bildWahl));
        neu.addActionListener(e -> lesen(Speicher::bilderListe, this::bilderSetzen));
        JLabel h = new JLabel("F12 und die Blätter der Regiebücher kommen hierher · „Kamera hierhin“ stellt Kamera und Uhr wie beim Foto");
        h.setForeground(MUTED);
        p.add(reihe(hin, neu, h), BorderLayout.SOUTH);
        return p;
    }

    void bilderSetzen(List<Speicher.BildKopf> l) {
        bilderListeErster = l.isEmpty() ? null : l.get(0);
        bilderGitter.removeAll();
        for (Speicher.BildKopf b : l) {
            JLabel k = new JLabel();
            k.setForeground(INK);
            k.setHorizontalTextPosition(JLabel.CENTER);
            k.setVerticalTextPosition(JLabel.BOTTOM);
            k.setText("<html><body style='width:150px'>" + b.art() + " · " + (b.aufgenommen() == null ? "" : b.aufgenommen()) + (b.regiebuch() == null ? "" : "<br>" + b.regiebuch()) + "</body></html>");
            try {
                if (b.vorschau() != null) {
                    BufferedImage im = javax.imageio.ImageIO.read(new ByteArrayInputStream(b.vorschau()));
                    if (im != null) k.setIcon(new ImageIcon(im.getScaledInstance(180, -1, Image.SCALE_SMOOTH)));
                }
            } catch (java.io.IOException e) { /* ohne Vorschau */ }
            k.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            k.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { bildZeigen(b); } });
            bilderGitter.add(k);
        }
        bilderGitter.revalidate();
        bilderGitter.repaint();
    }

    void bildZeigen(Speicher.BildKopf b) {
        if (b == null) return;
        bildWahl = b;
        bildText.setText("<html><body style='width:600px'>" + b.art() + " · " + b.datei() + "<br>" + (b.weltzeit() == null ? "" : b.weltzeit() + " · ") + (b.zeitstand() == null ? "" : b.zeitstand())
                + (Double.isNaN(b.e()) ? "" : String.format(Locale.GERMAN, " · Kamera E %.0f N %.0f, %.0f m, Blick %.0f°", b.e(), b.n(), b.h(), b.richtung()))
                + (b.notiz() == null ? "" : "<br>" + b.notiz()) + "</body></html>");
        lesen(sp -> sp.bildDaten(b.datei()), png -> {
            try {
                BufferedImage im = png == null ? null : javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
                if (im == null) { bildGross.setIcon(null); bildGross.setText("(Bild nicht im Speicher)"); return; }
                int w = Math.max(200, bildGross.getWidth() - 10);
                bildGross.setText("");
                bildGross.setIcon(new ImageIcon(im.getScaledInstance(w, -1, Image.SCALE_SMOOTH)));
            } catch (java.io.IOException e) { bildGross.setText(e.getMessage()); }
        });
    }

    void kameraWieFoto(Speicher.BildKopf b) {
        if (b == null || Double.isNaN(b.e())) { status.setText("Das Bild hat keinen Ort der Kamera (Blätter der Regiebücher haben keinen)"); return; }
        r.imBild(() -> {
            double[] p = Site.fromUtm(b.e(), b.n());
            r.camera.stopFahrt();
            r.camera.stopMitfahrt();
            r.camera.place(p[0], b.h(), p[1], b.richtung(), Double.isNaN(b.neigung()) ? 0 : b.neigung());
            if (!Double.isNaN(b.zeitS())) {
                r.clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + Math.round(b.zeitS() * 1000));
                try { r.clock.zeitstand = SceneClock.Zeitstand.valueOf(b.zeitstand()); } catch (RuntimeException e) { /* bleibt */ }
                r.wiedergabe.setzen(r.clock.simSeconds(), r.clock.lapse);
            }
            r.hudMeldung("Kamera und Uhr wie beim Foto " + new java.io.File(b.datei()).getName());
        });
    }

    // ------------------------------------------------------------------ Sitzungen

    JComponent sitzungReiter() {
        JPanel p = panel();
        p.add(scroll(sitzungTab), BorderLayout.CENTER);
        FButton laden = new FButton("Laden"), weg = new FButton("Löschen"), sichern = new FButton("Jetzt sichern");
        laden.addActionListener(e -> {
            Speicher.Sitzung s = sitzungen.zeile(sitzungTab.getSelectedRow());
            if (s == null) return;
            lesen(sp -> sp.sitzung(s.id()), voll -> {
                if (voll == null) return;
                r.imBild(() -> { int n = com.dan.lituya.gl.Sitzungen.herstellen(r, voll); r.hudMeldung("Sitzung „" + voll.name() + "“ geladen (" + n + " Werte)"); });
            });
        });
        weg.addActionListener(e -> {
            Speicher.Sitzung s = sitzungen.zeile(sitzungTab.getSelectedRow());
            if (s == null) return;
            if (com.dan.foptionpane.FOptionPane.showConfirmDialog(frame, "Sitzung „" + s.name() + "“ löschen?", "Archiv", com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != 0) return;
            lesen(sp -> { sp.sitzungLoeschen(s.id()); return sp.sitzungen(); }, sitzungen::setzen);
        });
        sichern.addActionListener(e -> lesen(sp -> { r.hudMeldung(com.dan.lituya.gl.Sitzungen.sichern(r, null)); return sp.sitzungen(); }, sitzungen::setzen));
        p.add(reihe(laden, weg, sichern), BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------------ Regiebücher

    JComponent buchReiter() {
        JPanel p = panel();
        buchListe.setCellRenderer((list, b, i, sel, foc) -> {
            JLabel l = new JLabel((b.mitgeliefert() ? "▸ " : "✎ ") + b.name());
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            l.setBackground(sel ? new Color(40, 70, 78) : BG);
            l.setForeground(b.mitgeliefert() ? INK : GOLD);
            return l;
        });
        buchListe.setBackground(BG);
        buchListe.addListSelectionListener(e -> { Speicher.Buch b = buchListe.getSelectedValue(); if (b != null && !e.getValueIsAdjusting()) { editor.setText(b.text()); editor.setEditable(!b.mitgeliefert()); pruefen(); } });
        editor.setLanguage(com.dan.fcodeeditor.FCodeLanguage.PLAIN_TEXT);
        editor.setCustomKeywords(new HashSet<>(Regie.BEFEHLE), com.dan.fcodeeditor.FTokenType.KEYWORD);
        editor.setShowLineNumbers(true);
        editor.setBackgroundColor(new Color(18, 24, 30));
        editor.setPlainTextColor(INK);
        dunkel(editor);
        JPanel links = panel();
        links.add(scroll(buchListe), BorderLayout.CENTER);
        JLabel leg = new JLabel("<html>▸ mitgeliefert (nur lesen)<br>✎ eigen</html>");
        leg.setForeground(MUTED);
        links.add(leg, BorderLayout.SOUTH);
        links.setPreferredSize(new Dimension(260, 400));
        JSplitPane sp = teiler(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, links, editor));
        sp.setBorder(BorderFactory.createEmptyBorder());
        p.add(sp, BorderLayout.CENTER);
        pruefLbl.setForeground(MUTED);
        FButton kopie = new FButton("Als eigenes kopieren"), pr = new FButton("Prüfen"), sichern = new FButton("Speichern"), weg = new FButton("Löschen"),
                fenster = new FButton("Im Fenster"), kino = new FButton("Im Kino");
        kopie.addActionListener(e -> {
            Speicher.Buch b = buchListe.getSelectedValue();
            String t = b == null ? "Regiebuch: Neu\nBeschreibung: \n0:00 zeit 09.07.1958 22:10 | \n0:20 blatt\n" : b.text();
            String name = (b == null ? "Neu" : b.name()) + " (eigen)";
            t = t.replaceFirst("(?m)^Regiebuch:.*$", java.util.regex.Matcher.quoteReplacement("Regiebuch: " + name));
            editor.setText(t);
            editor.setEditable(true);
            buchListe.clearSelection();
            pruefen();
        });
        pr.addActionListener(e -> pruefen());
        sichern.addActionListener(e -> {
            Regie.Book bk = pruefen();
            if (bk == null) return;
            for (Regie.Book m : Regie.all()) if (m.name().equals(bk.name())) { pruefLbl.setText("„" + bk.name() + "“ ist mitgeliefert: bitte einen eigenen Namen in der ersten Zeile"); return; }
            String t = editor.getText();
            lesen(spx -> { spx.regiebuchSpeichern(new Speicher.Buch(bk.name(), t, false, null)); return spx.wo(); }, wo -> { pruefLbl.setText("gespeichert: „" + bk.name() + "“ (" + wo + ")"); neu(); });
        });
        weg.addActionListener(e -> {
            Speicher.Buch b = buchListe.getSelectedValue();
            if (b == null || b.mitgeliefert()) return;
            lesen(spx -> { spx.regiebuchLoeschen(b.name()); return true; }, x -> neu());
        });
        fenster.addActionListener(e -> { Regie.Book bk = pruefen(); if (bk != null) r.imBild(() -> r.regieStart(bk)); });
        kino.addActionListener(e -> { Regie.Book bk = pruefen(); if (bk != null) r.imBild(() -> r.kinoStart(bk)); });
        JPanel unten = new JPanel();
        unten.setOpaque(false);
        unten.setLayout(new BoxLayout(unten, BoxLayout.Y_AXIS));
        unten.add(reihe(kopie, pr, sichern, weg, fenster, kino));
        unten.add(reihe(pruefLbl));
        p.add(unten, BorderLayout.SOUTH);
        return p;
    }

    /** Die Textfläche des Editors dunkel (sonst malt sie das Look-and-Feel weiß hinter den Text). */
    static void dunkel(java.awt.Container c) {
        for (Component x : c.getComponents()) {
            if (x instanceof javax.swing.text.JTextComponent t) { t.setBackground(new Color(18, 24, 30)); t.setCaretColor(INK); }
            if (x instanceof java.awt.Container k) dunkel(k);
        }
    }

    /** Prüft den Text im Editor; gibt das Buch oder null (Fehler mit Zeile in der Zeile darunter). */
    Regie.Book pruefen() {
        try {
            Regie.Book b = Regie.parse(editor.getText());
            pruefLbl.setForeground(new Color(96, 200, 120));
            pruefLbl.setText(String.format(Locale.GERMAN, "in Ordnung: „%s“, %s, %d Stichworte, %d Fotos%s", b.name(), Regie.zeit(b.length()), b.cues().size(), b.fotos(),
                    b.zeilen().isEmpty() || !b.zeilen().get(b.zeilen().size() - 1).befehl().equals("blatt") ? " · Hinweis: am Ende kein „blatt“" : ""));
            return b;
        } catch (RuntimeException e) {
            pruefLbl.setForeground(new Color(230, 110, 110));
            pruefLbl.setText("Fehler: " + e.getMessage());
            return null;
        }
    }
}
