package com.dan.lituya.ui;

import com.dan.fframe.FFrame;
import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Schaustuecke;
import com.dan.lituya.data.Speicher;
import com.dan.lituya.data.Verbindung;
import com.dan.lituya.scene.Regie;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das Hilfefenster (Phase 19a, F1): links die Kapitel, rechts HTML. Tasten, Ansichten und Schaustücke kommen aus dem
 * Code, die ant-Ziele aus {@code build.xml}, die Kapitel des Werkbuchs aus {@code docs/claude/LITUYABAY1958_WERKBUCH.md}
 * (wenn die App im Projektordner läuft), der Datenstand aus Speicher und Verbindung.
 */
public final class Hilfe {
    private static Hilfe offen;
    final FFrame frame;
    final JEditorPane seite = new JEditorPane();
    final DefaultListModel<String> kapitel = new DefaultListModel<>();
    final JList<String> liste = new JList<>(kapitel);
    final Map<String, java.util.function.Supplier<String>> inhalt = new LinkedHashMap<>();

    public static synchronized void zeigen() {
        if (offen == null) offen = new Hilfe();
        offen.frame.setVisible(true);
        offen.frame.toFront();
        if (offen.liste.getSelectedIndex() < 0) offen.liste.setSelectedIndex(0);
        else offen.zeige(offen.liste.getSelectedValue());
    }

    Hilfe() {
        frame = new FFrame("LituyaBay1958 · Hilfe");
        inhalt.put("Tasten", Hilfe::tasten);
        inhalt.put("Ansichten und Schaustücke", Hilfe::ansichten);
        inhalt.put("Bedienfeld", Hilfe::bedienfeld);
        inhalt.put("Archiv", Hilfe::archiv);
        inhalt.put("Regiebücher schreiben", Hilfe::regie);
        inhalt.put("Datenstand", Hilfe::datenstand);
        inhalt.put("ant-Ziele", Hilfe::ant);
        for (Map.Entry<String, String> e : werkbuch().entrySet()) { String t = e.getValue(); inhalt.put("Werkbuch · " + e.getKey(), () -> t); }
        for (String k : inhalt.keySet()) kapitel.addElement(k);

        JPanel root = frame.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(Archiv.BG);
        liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liste.setBackground(Archiv.BG);
        liste.setForeground(Archiv.INK);
        liste.setSelectionBackground(new Color(40, 70, 78));
        liste.setSelectionForeground(new Color(232, 178, 92));
        liste.setFixedCellHeight(26);
        javax.swing.DefaultListCellRenderer dr = new javax.swing.DefaultListCellRenderer();
        liste.setCellRenderer((li, v, i, sel, foc) -> {
            java.awt.Component c = dr.getListCellRendererComponent(li, v, i, sel, false);
            ((javax.swing.JComponent) c).setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));
            return c;
        });
        liste.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && liste.getSelectedValue() != null) zeige(liste.getSelectedValue()); });
        seite.setEditable(false);
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = new StyleSheet();
        css.addRule("body { font-family: SansSerif; font-size: 12pt; color: #dee6ea; background: #10161b; margin: 12px; }");
        css.addRule("h1 { font-size: 17pt; color: #e8b25c; } h2 { font-size: 14pt; color: #e8b25c; } h3 { font-size: 12pt; color: #9fd0d8; }");
        css.addRule("td { padding: 3px 8px; vertical-align: top; } th { text-align: left; color: #9fd0d8; padding: 3px 8px; }");
        css.addRule("code { font-family: Monospaced; color: #f0d090; } .m { color: #96a5ac; }");
        kit.setStyleSheet(css);
        seite.setEditorKit(kit);
        seite.setBackground(Archiv.BG);
        seite.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED && e.getDescription() != null && e.getDescription().startsWith("#"))
                liste.setSelectedValue(e.getDescription().substring(1), true);
        });
        JScrollPane l = Archiv.scroll(liste), s = Archiv.scroll(seite);
        l.setPreferredSize(new Dimension(300, 600));
        JSplitPane sp = Archiv.teiler(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, l, s));
        root.add(sp, BorderLayout.CENTER);
        frame.setPreferredFrameSize(new Dimension(1100, 760));
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);
    }

    void zeige(String k) {
        java.util.function.Supplier<String> f = inhalt.get(k);
        if (f == null) return;
        new Thread(() -> {
            String h;
            try { h = f.get(); } catch (RuntimeException e) { h = "<p>Nicht lesbar: " + esc(e.getMessage()) + "</p>"; }
            String html = "<html><body>" + h + "</body></html>";
            SwingUtilities.invokeLater(() -> { seite.setText(html); seite.setCaretPosition(0); });
        }, "LituyaBay1958-Hilfe").start();
    }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static String tabelle(String[][] z) {
        StringBuilder b = new StringBuilder("<table>");
        for (String[] r : z) b.append("<tr><td><code>").append(r[0]).append("</code></td><td>").append(r[1]).append("</td></tr>");
        return b.append("</table>").toString();
    }

    // ------------------------------------------------------------------ Kapitel

    static String tasten() {
        return "<h1>Tasten</h1><h2>Szene</h2>" + tabelle(new String[][]{
                {"Links ziehen", "drehen"}, {"Rechts ziehen", "verschieben"}, {"Rad, Bild↑/↓, + / −", "zoomen"},
                {"Pfeile", "drehen"}, {"W A S D", "gleiten"}, {"Q / E", "ab und auf (frei)"}, {"Umschalt", "schneller"},
                {"F", "frei fliegen"}, {"R", "von selbst kreisen"}, {"Pos1", "Grundansicht"}, {"H", "HUD kurz, voll, aus"},
                {"0 … 9", "die Ansichten (Kapitel <a href='#Ansichten und Schaustücke'>Ansichten</a>)"},
                {"Klick auf eine Marke", "wählt den Befund im Archiv (wenn „Marken in der Szene“ an ist)"},
                {"Strg + Klick", "Ort des Sturzes für „Was wäre wenn“ (Echtzeit im Block EREIGNIS)"}})
                + "<h2>Zeit</h2>" + tabelle(new String[][]{
                {"Leertaste", "Uhr anhalten und weiter (mit dem vorigen Tempo, auch rückwärts)"},
                {"J · K · L", "rückwärts · anhalten · vorwärts"}, {"Z", "Zeitlupe (1:1 bis 1:20)"}, {"Y", "Jahres-Zeitraffer an und aus"}})
                + "<h2>Bild, Netze, Klang</h2>" + tabelle(new String[][]{
                {"N", "Netzblick an und aus"}, {"M", "Klang an und aus"}, {"F12", "Foto (nach docs/status und ins Archiv)"}, {"F5", "Shader neu laden"}})
                + "<h2>Fenster und Daten</h2>" + tabelle(new String[][]{
                {"F1", "diese Hilfe"}, {"F2", "das Archiv"}, {"F9", "das Bedienfeld gleitet zur Seite weg und wieder herein (W9)"}, {"F11", "nur die Szene (Bedienfeld und Statuszeile weg und wieder da)"},
                {"Strg + S", "Sitzung sichern (Oracle, sonst daten/speicher)"}})
                + "<h2>Im Kino</h2>" + tabelle(new String[][]{{"Leertaste", "anhalten und weiter"}, {"Esc", "Kino verlassen"}, {"F12 · F5", "Foto · Shader"}});
    }

    static String ansichten() {
        StringBuilder b = new StringBuilder("<h1>Ansichten</h1><table><tr><th>Taste</th><th>Ansicht</th><th>seit Phase</th></tr>");
        for (Ansichten.Ansicht a : Ansichten.ALLE) b.append("<tr><td><code>").append(a.key()).append("</code></td><td>").append(esc(a.name())).append("</td><td>").append(a.phase()).append("</td></tr>");
        b.append("</table><h1>Schaustücke</h1><p class='m'>Im Block EREIGNIS; jedes stellt Kamera, Uhr und Tempo.</p><table>");
        for (Schaustuecke.Stueck s : Schaustuecke.ALLE) b.append("<tr><td>").append(esc(Schaustuecke.describe(s))).append("</td></tr>");
        return b.append("</table>").toString();
    }

    static String bedienfeld() {
        StringBuilder b = new StringBuilder("<h1>Bedienfeld</h1><p>Die Blöcke rechts, in dieser Reihenfolge (zu- und aufklappbar):</p><table>");
        String[][] z = {
                {"ANSICHT", "Ansichten, frei fliegen, kreisen, Fahrten über das Netz der Sicht"},
                {"KAMERA", "Objektiv (Brennweite, Blende, Fokus, Reflexe), Verschluss, Film (Material, Kopierlicht, Korn, Halation)"},
                {"MALWEISE", "die Szene nach Franz Gertsch: der Wald um die Kamera gemalt (dieselben Bäume auf dem Gelände, Unterwuchs aus understory, Flechten auf der Rinde, Wasser unter dem Pegel), wahlweise als Holzschnitt, oder die ganze Szene als Holzschnitt; Vorlage, Umkreis, „In den Wald“"},
                {"ZEIT", "Datum, Stunde, Zeitstand (vor, Welle, danach, heute), Tempo, Jahres-Zeitraffer"},
                {"EREIGNIS", "die Nacht vom 9. Juli 1958: Beben, Sturz, Welle; Wiedergabe der vorgerechneten Läufe, Echtzeit „Was wäre wenn“, Schaustücke, Boote"},
                {"NETZE", "Netzblick: Welle, Wasser, Weg, Sicht; Bild im Bild"},
                {"WETTER", "Wetterlage für heute (klar, Quellwolken, Küstennebel, grauer Morgen, Regen am Berg, stiller Regen, Sturm aus Südost) mit Übergang in Minuten, „Sofort“, Zeitraffer; 1958 und am Morgen danach klar nach den Zeugen; Wolken, Seenebel, Regen einzeln abschaltbar; Regen von Hand auf die Bäche"},
                {"KLANG", "Grollen, Brandung, Wind, Lautstärke (Taste M)"},
                {"REGIEBÜCHER", "die mitgelieferten Abläufe im Fenster oder im Kino (eigene im Archiv)"},
                {"DATEN", "Verbindung, LOD, Datenstand; Sitzung sichern und laden, Lauf sichern; Archiv (F2)"},
                {"TECHNIK", "Qualität, Auflösung, Belichtung, HUD, Marken, Foto, Grafikkarte, Packages"}};
        for (String[] r : z) b.append("<tr><td><b>").append(r[0]).append("</b></td><td>").append(r[1]).append("</td></tr>");
        return b.append("</table>").toString();
    }

    static String archiv() {
        return "<h1>Archiv (F2)</h1><p>Ein eigenes Fenster über dem Speicher (Oracle, sonst <code>daten/speicher</code>):</p><table>"
                + "<tr><td><b>Befunde</b></td><td>Was belegt, gemessen, gerechnet oder gesetzt ist, mit Quellen. „Marken in der Szene“ zeigt sie als Fähnchen in der Farbe der Art "
                + "(<span style='color:#60c878'>belegt</span>, <span style='color:#5aa0eb'>gemessen</span>, <span style='color:#f0a03c'>gerechnet</span>, <span style='color:#aaaaaa'>gesetzt</span>); "
                + "ein Klick auf eine Marke wählt die Zeile. „Kamera zum Ort“, „Uhr auf die Zeit“.</td></tr>"
                + "<tr><td><b>Zeugen</b></td><td>Die Aussagen nach der Zeit; „Untertitel zur Zeit“ zeigt sie unten, solange die Uhr in der Nacht von 1958 in ihrer Spanne steht.</td></tr>"
                + "<tr><td><b>Läufe</b></td><td>Vorgerechnete und gesicherte Echtzeitläufe nebeneinander, als Balken gegen den Beleg 524 m und die Ankünfte; „Abspielen“.</td></tr>"
                + "<tr><td><b>Bilder</b></td><td>Fotos (F12) und Blätter der Regiebücher; „Kamera hierhin“ stellt Kamera und Uhr wie beim Foto.</td></tr>"
                + "<tr><td><b>Sitzungen</b></td><td>Laden, löschen, sichern.</td></tr>"
                + "<tr><td><b>Regiebücher</b></td><td>Lesen, als eigenes kopieren, ändern, prüfen, speichern, im Fenster oder im Kino abspielen (Kapitel <a href='#Regiebücher schreiben'>Regiebücher schreiben</a>).</td></tr></table>";
    }

    static String regie() {
        String[][] z = {
                {"zeit TT.MM.JJJJ hh:mm", "Datum und Uhrzeit setzen (vor dem ersten warte uhr nötig)"},
                {"ereignis s", "Sekunden nach dem ersten Stoß (−… bis …)"},
                {"raffer v", "Tempo der Uhr (0 hält an, 1 Echtzeit)"},
                {"zeitstand vor|welle|danach|heute", "der Stand der Bucht"},
                {"jahre v|aus", "Jahres-Zeitraffer mit v Jahren je Sekunde"},
                {"ansicht Name", "eine der Ansichten"},
                {"schaustueck Nummer|Name", "ein Schaustück"},
                {"fahrt Rundflug|Kamerafahrt|A > B [Tempo]", "eine Fahrt über das Netz der Sicht"},
                {"mitfahrt edrie|badger [nebenher]|aus", "auf einem Boot mitfahren"},
                {"netzblick welle|wasser|weg|sicht [szene]|aus", "Netzblick"},
                {"echtzeit " + String.join("|", Regie.ECHTZEIT_ORTE) + " Mio.m³|aus", "„Was wäre wenn“ rechnen"},
                {"regen v", "Regen 0 … 1"},
                {"verschluss 1/250|s", "Belichtungszeit des Regens (W7b; 1/250 wie das Auge, 1/30 wie Film, 1/2000 friert die Tropfen ein)"},
                {"zeitlupe v", "Zeitmaß des Regens (1 Echtzeit, 0,05 Zeitlupe)"},
                {"warte " + String.join("|", Regie.WARTEN), "warten (uhr [TT.MM.] hh:mm, ereignis s, bis die Echtzeit, die Jahre, die Boote fertig sind)"},
                {"foto Unterschrift", "ein Foto fürs Blatt"},
                {"blatt [Name]", "die Fotos als Blatt (docs/status und Archiv)"},
                {"sage Text", "ein Text im HUD"}};
        StringBuilder b = new StringBuilder("<h1>Regiebücher schreiben</h1><p>Kopf:</p><p><code>Regiebuch: Name</code><br><code>Beschreibung: ein Satz</code><br><code>Länge: m:ss</code> (wahlweise)</p>"
                + "<p>Dann je Zeile ein Stichwort: <code>m:ss befehl argumente | Text fürs HUD</code>. Zeilen zur selben Zeit ohne Text gehören zum Stichwort davor "
                + "(außer <code>warte</code>); <code>#</code> beginnt einen Kommentar. Fehler nennen die Zeile.</p><h2>Befehle</h2>");
        b.append(tabelle(z)).append("<p class='m'>Bekannt im Code: ").append(esc(String.join(", ", Regie.BEFEHLE))).append("</p><h2>Mitgeliefert</h2><table>");
        for (Regie.Book bk : Regie.all()) b.append("<tr><td><b>").append(esc(bk.name())).append("</b></td><td nowrap>").append(Regie.zeit(bk.length())).append("</td><td>").append(esc(bk.about())).append("</td></tr>");
        return b.append("</table>").toString();
    }

    static String datenstand() {
        StringBuilder b = new StringBuilder("<h1>Datenstand</h1><table>");
        Speicher g = Speicher.gewaehlt();
        b.append("<tr><td>Datenbank</td><td>").append(esc(Verbindung.stand() == null ? "–" : Verbindung.stand())).append("</td></tr>");
        b.append("<tr><td>Speicher</td><td>").append(esc(g == null ? "noch nicht gewählt" : g.wo())).append(Speicher.hinweis().isEmpty() ? "" : " (" + esc(Speicher.hinweis()) + ")").append("</td></tr>");
        File d = new File(System.getProperty("lituya.daten", "daten"));
        b.append("<tr><td>Daten</td><td>").append(esc(d.getAbsolutePath())).append("</td></tr>");
        String[] ordner = {"gelaende", "texturen", "netz", "ereignis", "welle", "speicher"};
        for (String o : ordner) {
            File f = new File(d, o);
            b.append("<tr><td><code>").append(o).append("</code></td><td>").append(f.isDirectory() ? groesse(f) : "<span class='m'>fehlt</span>").append("</td></tr>");
        }
        File inh = new File(d, "INHALT.txt");
        b.append("</table>");
        if (inh.isFile()) {
            try { b.append("<h2>INHALT.txt</h2><pre>").append(esc(Files.readString(inh.toPath(), StandardCharsets.UTF_8))).append("</pre>"); } catch (java.io.IOException e) { /* ohne */ }
        }
        return b.toString();
    }

    static String groesse(File d) {
        long[] s = {0, 0};
        try (var w = Files.walk(d.toPath())) { w.filter(Files::isRegularFile).forEach(p -> { s[0]++; s[1] += p.toFile().length(); }); } catch (java.io.IOException e) { return "?"; }
        return String.format(java.util.Locale.GERMAN, "%d Dateien, %.1f MB", s[0], s[1] / 1e6);
    }

    static String ant() {
        File f = new File("build.xml");
        if (!f.isFile()) return "<h1>ant-Ziele</h1><p>build.xml nicht im Arbeitsordner (" + esc(new File("").getAbsolutePath()) + ").</p>";
        StringBuilder b = new StringBuilder("<h1>ant-Ziele</h1><p class='m'>Aus build.xml; im Projektordner mit <code>ant ziel</code>.</p><table>");
        try {
            Matcher m = Pattern.compile("<target\\s+name=\"([^\"]+)\"[^>]*?description=\"([^\"]*)\"").matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            while (m.find()) b.append("<tr><td><code>").append(esc(m.group(1))).append("</code></td><td>").append(m.group(2)).append("</td></tr>");
        } catch (java.io.IOException e) { b.append("<tr><td>").append(esc(e.getMessage())).append("</td></tr>"); }
        return b.append("</table>").toString();
    }

    // ------------------------------------------------------------------ Werkbuch

    /** Die Kapitel (## …) des Werkbuchs als HTML; leer, wenn es nicht da ist. */
    static Map<String, String> werkbuch() {
        Map<String, String> m = new LinkedHashMap<>();
        File f = new File(System.getProperty("lituya.werkbuch", "docs/claude/LITUYABAY1958_WERKBUCH.md"));
        if (!f.isFile()) return m;
        List<String> zeilen;
        try { zeilen = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8); } catch (java.io.IOException e) { return m; }
        String titel = null;
        List<String> teil = new ArrayList<>();
        for (String z : zeilen) {
            if (z.startsWith("## ")) {
                if (titel != null) m.put(titel, markdown(teil));
                titel = z.substring(3).strip();
                teil = new ArrayList<>();
            }
            teil.add(z);
        }
        if (titel != null) m.put(titel, markdown(teil));
        return m;
    }

    /** Ein kleines Markdown: Überschriften, Listen, Tabellen, Code, fett, kursiv, `code`. */
    public static String markdown(List<String> zs) {
        StringBuilder b = new StringBuilder();
        boolean liste = false, tab = false, pre = false;
        for (String z : zs) {
            if (z.startsWith("```")) { if (pre) b.append("</pre>"); else { if (liste) { b.append("</ul>"); liste = false; } b.append("<pre>"); } pre = !pre; continue; }
            if (pre) { b.append(esc(z)).append('\n'); continue; }
            String s = z.strip();
            boolean istTab = s.startsWith("|"), istListe = s.startsWith("- ") || s.startsWith("* ");
            if (tab && !istTab) { b.append("</table>"); tab = false; }
            if (liste && !istListe) { b.append("</ul>"); liste = false; }
            if (istTab) {
                if (s.matches("\\|[\\s:|-]+\\|?")) continue;
                if (!tab) { b.append("<table>"); tab = true; }
                b.append("<tr>");
                String[] c = s.substring(1, s.endsWith("|") ? s.length() - 1 : s.length()).split("\\|", -1);
                for (String x : c) b.append("<td>").append(inline(x.strip())).append("</td>");
                b.append("</tr>");
            } else if (istListe) {
                if (!liste) { b.append("<ul>"); liste = true; }
                b.append("<li>").append(inline(s.substring(2))).append("</li>");
            } else if (s.startsWith("#")) {
                int n = 0;
                while (n < s.length() && s.charAt(n) == '#') n++;
                int h = Math.min(3, Math.max(1, n - 1));
                b.append("<h").append(h).append('>').append(inline(s.substring(n).strip())).append("</h").append(h).append('>');
            } else if (!s.isEmpty()) b.append("<p>").append(inline(s)).append("</p>");
        }
        if (pre) b.append("</pre>");
        if (liste) b.append("</ul>");
        if (tab) b.append("</table>");
        return b.toString();
    }

    static String inline(String s) {
        String e = esc(s);
        e = e.replaceAll("`([^`]+)`", "<code>$1</code>");
        e = e.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        e = e.replaceAll("(?<![\\w*])\\*([^*]+)\\*(?![\\w*])", "<i>$1</i>");
        return e;
    }
}
