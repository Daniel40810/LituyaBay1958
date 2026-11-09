package com.dan.lituya.tools;

import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Werkbuch Bild, Abschnitt 3.1 und Phase B0: die Bildbank. Liest die zwölf festen Ansichten aus
 * {@code scene/bildbank.txt}, rechnet jede mit {@link Tempo} in einer eigenen JVM (ohne HUD und Marken) und schreibt
 * <ul>
 * <li>{@code docs/bild/<phase>/NN_Name.png} (1920 × 1080; mit {@code gross=an} zusätzlich {@code 4k/NN_Name.png} in 3840 × 2160),</li>
 * <li>{@code Bildbank_<phase>.png}: Kontaktbogen aller zwölf Bilder mit Brennweite, Zeit und Bildzeit,</li>
 * <li>{@code Tafel_NN.png}: Ist gegen Foto, wenn unter {@code docs/bild/referenz/} ein Foto {@code NN_….jpg|png} liegt,</li>
 * <li>{@code Vergleich_<phase>.png}: vorher gegen nachher, wenn {@code vorher=<Phase>} gesetzt ist,</li>
 * <li>{@code bildbank_<phase>.txt}: die Ausgaben der Läufe.</li>
 * </ul>
 * Schalter (System-Properties, aus Ant): {@code bild.phase} (B0), {@code stufe} (HOCH), {@code nur} (z. B. 1,5,9),
 * {@code gross} (an), {@code vorher} (Phase), {@code bildbank} (andere Liste als Datei), {@code bilder} (Bilder je Lauf, 30),
 * seit W0 {@code breite}, {@code hoehe} (1920 × 1080).
 */
public final class Bildbank {
    private Bildbank() { }

    /** Ein Eintrag der Liste in Dateireihenfolge. */
    record Eintrag(int nr, String name, Map<String, String> w) {
        String get(String k) { String v = w.get(k); return v == null || v.isBlank() ? null : v.trim(); }
        String datei() { return String.format(Locale.ROOT, "%02d_%s.png", nr, name); }
        String titel() { return nr + " · " + name.replace('_', ' '); }
    }

    static final Pattern MITTEL = Pattern.compile("Mittel ([\\d,.]+) ms");
    /** W6 Runde 4: die Zeit wie im Programm (Bilder ohne Warten auf die Grafikkarte); „Mittel“ wartet je Bild (glFinish) und liegt 5–12 ms höher. */
    static final Pattern FLUSS = Pattern.compile("Im Fluss ([0-9.,]+) ms");

    public static void main(String[] a) throws Exception {
        String phase = prop("bild.phase", "B0"), stufe = prop("stufe", "HOCH"), vorher = prop("vorher", "");
        boolean gross = "an".equalsIgnoreCase(prop("gross", "aus"));
        int bilder = Integer.parseInt(prop("bilder", "30"));
        // W0: Bildgröße (die Aufnahmen aus der App haben die Größe des Fensters, etwa 1495 × 940)
        int bw = Integer.parseInt(prop("breite", "1920")), bh = Integer.parseInt(prop("hoehe", "1080"));
        Set<Integer> nur = new TreeSet<>();
        for (String s : prop("nur", "").split("[,; ]+")) if (!s.isBlank()) nur.add(Integer.parseInt(s.trim()));

        List<Eintrag> liste = lesen(prop("bildbank", ""));
        File dir = new File("docs/bild/" + phase);
        dir.mkdirs();
        File log = new File(dir, "bildbank_" + phase + ".txt");
        Map<Integer, String> zeiten = new LinkedHashMap<>();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(log.toPath(), StandardCharsets.UTF_8))) {
            out.printf("Bildbank %s, Stufe %s, %d Ansichten%n", phase, stufe, liste.size());
            for (Eintrag e : liste) {
                if (!nur.isEmpty() && !nur.contains(e.nr())) continue;
                if (e.get("offen") != null) {
                    System.out.println(e.titel() + ": offen, " + e.get("offen"));
                    out.println("### " + e.titel() + ": offen, " + e.get("offen"));
                    continue;
                }
                String ms = lauf(e, new File(dir, e.datei()), bw, bh, stufe, bilder, out);
                zeiten.put(e.nr(), ms);
                if (gross) lauf(e, new File(dir, "4k/" + e.datei()), 3840, 2160, stufe, 4, out);
            }
        }
        BufferedImage bogen = kontaktbogen(liste, dir, phase, stufe, zeiten);
        ImageIO.write(bogen, "png", new File(dir, "Bildbank_" + phase + ".png"));
        System.out.println("Kontaktbogen: " + new File(dir, "Bildbank_" + phase + ".png").getPath());
        tafeln(liste, dir);
        if (!vorher.isBlank()) {
            File alt = new File("docs/bild/" + vorher);
            ImageIO.write(vergleich(liste, alt, dir, vorher, phase), "png", new File(dir, "Vergleich_" + phase + ".png"));
            System.out.println("Vergleich: " + new File(dir, "Vergleich_" + phase + ".png").getPath());
        }
    }

    static String prop(String k, String d) {
        String v = System.getProperty(k);
        return v == null || v.isBlank() || v.startsWith("${") ? d : v;
    }

    /** Die Liste: als Datei (Schalter {@code bildbank}), sonst aus dem Klassenpfad, sonst aus {@code src/}. */
    static List<Eintrag> lesen(String datei) throws IOException {
        List<String> zeilen;
        if (!datei.isBlank()) zeilen = Files.readAllLines(new File(datei).toPath(), StandardCharsets.UTF_8);
        else {
            InputStream in = Bildbank.class.getResourceAsStream("/com/dan/lituya/scene/bildbank.txt");
            if (in != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { zeilen = r.lines().toList(); }
            else zeilen = Files.readAllLines(new File("src/com/dan/lituya/scene/bildbank.txt").toPath(), StandardCharsets.UTF_8);
        }
        List<Eintrag> l = new ArrayList<>();
        for (String z : zeilen) {
            z = z.strip();
            if (z.isEmpty() || z.startsWith("#")) continue;
            Map<String, String> w = new LinkedHashMap<>();
            for (String t : z.split(";")) {
                int i = t.indexOf('=');
                if (i > 0) w.put(t.substring(0, i).trim().toLowerCase(Locale.ROOT), t.substring(i + 1).trim());
            }
            l.add(new Eintrag(Integer.parseInt(w.get("nr")), w.getOrDefault("name", "Bild"), w));
        }
        return l;
    }

    static Site.Ort ort(String name) {
        try { return (Site.Ort) Site.class.getField(name.trim().toUpperCase(Locale.ROOT)).get(null); }
        catch (ReflectiveOperationException | ClassCastException ex) { throw new IllegalArgumentException("kein Ort „" + name + "“ in Site"); }
    }

    static double zahl(String s) { return Double.parseDouble(s.trim().replace(',', '.')); }

    /** Kamera für {@link Tempo} als „x,z,Richtung,Neigung,Auge“ aus ort/versatz/xz, blick/blickauf, neigung, auge. */
    static String kamera(Eintrag e) {
        double x, z;
        if (e.get("xz") != null) {
            String[] p = e.get("xz").split(",");
            x = zahl(p[0]);
            z = zahl(p[1]);
        } else {
            Site.Ort o = ort(e.get("ort"));
            x = o.x();
            z = o.z();
        }
        if (e.get("versatz") != null) {
            String[] p = e.get("versatz").split(",");
            double r = Math.toRadians(zahl(p[0])), d = zahl(p[1]);
            x += Math.sin(r) * d;
            z -= Math.cos(r) * d;
        }
        double blick = e.get("blick") != null ? zahl(e.get("blick")) : 0;
        if (e.get("blickauf") != null) {
            Site.Ort t = ort(e.get("blickauf"));
            blick = (Math.toDegrees(Math.atan2(t.x() - x, -(t.z() - z))) + 360) % 360;
        }
        double neigung = e.get("neigung") != null ? zahl(e.get("neigung")) : 0, auge = e.get("auge") != null ? zahl(e.get("auge")) : 2;
        // W0: hoehe = Auge in m über NAVD88 (absolut, wie es die Fotos in LB_SCHNAPPSCHUSS speichern) statt auge über Grund
        if (e.get("hoehe") != null) return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f,%.1f,%.2f", x, z, blick, neigung, auge, zahl(e.get("hoehe")));
        return String.format(Locale.ROOT, "%.1f,%.1f,%.2f,%.2f,%.1f", x, z, blick, neigung, auge);
    }

    /** Ein Bild in eigener JVM; gibt die mittlere Bildzeit („12,3 ms“) oder „Fehler“ zurück. */
    static String lauf(Eintrag e, File png, int w, int h, String stufe, int bilder, PrintWriter out) throws Exception {
        png.getAbsoluteFile().getParentFile().mkdirs();
        SceneClock.Zeitstand zs = SceneClock.Zeitstand.VOR;
        if (e.get("zeitstand") != null)
            for (SceneClock.Zeitstand z : SceneClock.Zeitstand.values())
                if (z.name().equalsIgnoreCase(e.get("zeitstand")) || z.label.equalsIgnoreCase(e.get("zeitstand"))) zs = z;
        String uhr = e.get("uhr") != null ? e.get("uhr") : String.format(Locale.ROOT, "%.4f", zs.hour);
        String datum = e.get("datum") != null ? e.get("datum") : zs.date.toString();

        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("--enable-native-access=ALL-UNNAMED");
        cmd.add("-Xmx8g");
        cmd.add("-Dhud=aus");
        cmd.add("-Dmarken=aus");
        cmd.add("-Dnetzblick=aus");   // W0 (K6): in B0 stand die Karte trotz „ohne HUD und Marken“ in allen Bildern
        if (e.get("schaustueck") != null) cmd.add("-Dschaustueck=" + e.get("schaustueck"));
        else if (e.get("drohne") == null && e.get("anbord") == null) cmd.add("-Dkamera=" + kamera(e));   // W11: die Kamera hängt am Boot
        if (e.get("zeitstand") != null) cmd.add("-Dzeitstand=" + e.get("zeitstand"));
        for (String k : new String[]{"brennweite", "ereignis", "lauf", "regen", "schalter", "seitwaerts", "last", "jfr", "wetter", "wetterzeit", "verschluss", "zeitlupe",
                "objektiv", "blende", "fokus", "lamellen", "rundung", "film", "kopierlicht", "kopie", "bildrate", "umlaufblende", "teilbilder", "schwenk", "bewegung", "kinolichter", "tropfen",
                "malweise", "vorlage", "umkreis", "unterwuchs", "rinde", "tour", "drohne", "anbord",
                "aurora", "kp", "energie", "fluss", "faltung", "strahlen", "bewegt", "zumbogen"})
            if (e.get(k) != null) cmd.add("-D" + k + "=" + e.get(k));
        // W6 Runde 5: weitere -D-Schalter für jede Aufnahme aus der Umgebung (etwa ein Vergleich „alles aus“ im selben Lauf)
        String zusatz = System.getenv("LITUYA_BILDBANK_ZUSATZ");
        if (zusatz != null && !zusatz.isBlank()) for (String z : zusatz.trim().split("\\s+")) if (z.startsWith("-D")) cmd.add(z);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(Tempo.class.getName());
        cmd.addAll(List.of(String.valueOf(bilder), String.valueOf(w), String.valueOf(h), png.getPath(), stufe, uhr, datum));

        System.out.printf("%s (%d × %d) …%n", e.titel(), w, h);
        out.println("### " + e.titel() + " · " + w + "×" + h);
        out.println(String.join(" ", cmd.subList(4, cmd.size())).replace(System.getProperty("java.class.path"), "<cp>"));
        out.flush();
        long t0 = System.nanoTime();
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String ms = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                out.println(l);
                Matcher m = MITTEL.matcher(l);
                if (m.find() && (ms == null || !ms.contains("Fluss"))) ms = m.group(1) + " ms";
                Matcher fl = FLUSS.matcher(l);
                if (fl.find()) ms = fl.group(1) + " ms im Fluss";
                if (l.startsWith("FEHLER")) System.out.println("  " + l);
            }
        }
        boolean fertig = p.waitFor(20, TimeUnit.MINUTES);
        if (!fertig) p.destroyForcibly();
        int code = fertig ? p.exitValue() : -1;
        out.printf(Locale.GERMAN, "Ende %d nach %.0f s%n%n", code, (System.nanoTime() - t0) / 1e9);
        out.flush();
        System.out.printf(Locale.GERMAN, "  %s, %.0f s%s%n", ms != null ? ms : "ohne Bildzeit", (System.nanoTime() - t0) / 1e9, code == 0 ? "" : ", Ende " + code);
        return code == 0 && png.isFile() ? (ms != null ? ms : "–") : "Fehler";
    }

    static final Color GRUND = new Color(0x0c, 0x14, 0x19), TEXT = new Color(0xe8, 0xee, 0xf0), LEISE = new Color(0x8a, 0x9a, 0xa2),
            RAHMEN = new Color(0x2a, 0x3a, 0x42);

    static Graphics2D malen(BufferedImage b) {
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(GRUND);
        g.fillRect(0, 0, b.getWidth(), b.getHeight());
        return g;
    }

    static BufferedImage lies(File f) {
        try { return f.isFile() ? ImageIO.read(f) : null; } catch (IOException ex) { return null; }
    }

    /** Bild eingepasst in die Kachel (Seitenverhältnis bleibt), sonst ein Hinweis. */
    static void kachel(Graphics2D g, BufferedImage img, int x, int y, int w, int h, String leer) {
        g.setColor(RAHMEN);
        g.fillRect(x, y, w, h);
        if (img != null) {
            double s = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
            int iw = (int) Math.round(img.getWidth() * s), ih = (int) Math.round(img.getHeight() * s);
            g.drawImage(img, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);
        } else {
            g.setColor(LEISE);
            g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 15));
            g.drawString(leer, x + 16, y + h / 2);
        }
    }

    static String unterzeile(Eintrag e) {
        StringBuilder s = new StringBuilder();
        if (e.get("brennweite") != null) s.append(e.get("brennweite")).append(" mm");
        if (e.get("ereignis") != null) s.append(" · t = ").append(e.get("ereignis")).append(" s");
        else if (e.get("uhr") != null) {
            double u = zahl(e.get("uhr"));
            s.append(String.format(Locale.ROOT, " · %02d:%02d", (int) u, (int) Math.round((u - (int) u) * 60) % 60));
        }
        if (e.get("zeitstand") != null) s.append(" · ").append(e.get("zeitstand"));
        if (e.get("wetter") != null) s.append(" · Wetter ").append(e.get("wetter"));   // W7
        if (e.get("objektiv") != null || e.get("blende") != null) s.append(" · Objektiv").append(e.get("blende") != null ? " f/" + e.get("blende") : "")
                .append(e.get("fokus") != null ? ", Fokus " + e.get("fokus") : "");   // W9a
        if (e.get("film") != null || e.get("kopierlicht") != null || e.get("kopie") != null)   // W9b
            s.append(" · Film").append(e.get("film") != null && !e.get("film").equals("an") ? " " + e.get("film") : "")
                    .append(e.get("kopierlicht") != null ? ", Kopierlicht " + e.get("kopierlicht").replace('_', '-') : "")
                    .append(e.get("kopie") != null ? ", Kopie " + e.get("kopie") + " Jahre" : "");
        if (e.get("teilbilder") != null) s.append(" · Kino-Stufe ").append(e.get("teilbilder")).append(" Teilbilder");   // W9c
        if (e.get("kinolichter") != null) s.append(" · Lichter ").append(e.get("kinolichter").equalsIgnoreCase("aus") ? "als Punkte" : "als Kreise");   // W9c Runde 2
        if (e.get("tropfen") != null) s.append(" · Tropfen auf der Linse ").append(e.get("tropfen"));   // W9c Runde 2 (W7e)
        if (e.get("bildrate") != null) s.append(" · ").append(e.get("bildrate")).append(" B/s");
        if (e.get("schwenk") != null) s.append(" · Schwenk ").append(e.get("schwenk")).append("°/s");
        if (e.get("schaustueck") != null) s.append(" · Schaustück ").append(e.get("schaustueck"));
        return s.toString();
    }

    /** Kontaktbogen 3 × 4 mit Titel, Brennweite, Zeit, Bildzeit und „warum“. */
    static BufferedImage kontaktbogen(List<Eintrag> l, File dir, String phase, String stufe, Map<Integer, String> zeiten) {
        int tw = 640, th = 360, cap = 52, pad = 16, cols = 3, rows = (l.size() + cols - 1) / cols, head = 64;
        BufferedImage b = new BufferedImage(cols * tw + (cols + 1) * pad, head + rows * (th + cap) + (rows + 1) * pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = malen(b);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString("LituyaBay1958 · Bildbank " + phase, pad, 40);
        g.setColor(LEISE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString("Werkbuch Bild 3.1 · 1920 × 1080 · Stufe " + stufe + " · ohne HUD und Marken · " + java.time.LocalDate.now(), pad + 420, 40);
        for (int i = 0; i < l.size(); i++) {
            Eintrag e = l.get(i);
            int x = pad + (i % cols) * (tw + pad), y = head + pad + (i / cols) * (th + cap + pad);
            String leer = e.get("offen") != null ? "offen: " + e.get("offen") : "kein Bild";
            kachel(g, lies(new File(dir, e.datei())), x, y, tw, th, leer.length() > 70 ? leer.substring(0, 70) + " …" : leer);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(e.titel(), x, y + th + 20);
            String ms = zeiten.get(e.nr());
            g.setColor(LEISE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString(unterzeile(e) + (ms != null ? " · " + ms : ""), x, y + th + 37);
            if (e.get("warum") != null) g.drawString(e.get("warum"), x + 330, y + th + 20);
        }
        g.dispose();
        return b;
    }

    /** Ist gegen Foto: je Bild mit einem Foto {@code docs/bild/referenz/NN_….jpg|png} eine Tafel. */
    static void tafeln(List<Eintrag> l, File dir) throws IOException {
        File ref = new File("docs/bild/referenz");
        File[] fotos = ref.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).matches("\\d+[_.].*\\.(jpe?g|png)"));
        if (fotos == null || fotos.length == 0) {
            System.out.println("Tafeln: keine Fotos unter " + ref.getPath() + " (NN_Name.jpg, FB9)");
            return;
        }
        for (Eintrag e : l) {
            for (File f : fotos) {
                if (Integer.parseInt(f.getName().replaceAll("^(\\d+).*", "$1")) != e.nr()) continue;
                int h = 720, w = 1280, pad = 16, cap = 40;
                BufferedImage b = new BufferedImage(2 * w + 3 * pad, h + cap + 2 * pad + 30, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = malen(b);
                kachel(g, lies(new File(dir, e.datei())), pad, pad + 30, w, h, "kein Bild");
                kachel(g, lies(f), 2 * pad + w, pad + 30, w, h, "Foto nicht lesbar");
                g.setColor(TEXT);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
                g.drawString(e.titel() + " · Ist", pad, pad + 20);
                g.drawString("Foto: " + f.getName(), 2 * pad + w, pad + 20);
                g.setColor(LEISE);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                g.drawString(unterzeile(e) + " · " + (e.get("warum") != null ? e.get("warum") : ""), pad, b.getHeight() - pad - 8);
                g.dispose();
                File t = new File(dir, String.format(Locale.ROOT, "Tafel_%02d_%s.png", e.nr(), f.getName().replaceAll("\\.[^.]+$", "")));
                ImageIO.write(b, "png", t);
                System.out.println("Tafel: " + t.getPath());
            }
        }
    }

    /** Vorher gegen nachher: je Zeile ein Bild der Bildbank links aus {@code alt}, rechts aus {@code neu}. */
    static BufferedImage vergleich(List<Eintrag> l, File alt, File neu, String a, String b) {
        int tw = 960, th = 540, pad = 16, cap = 28, head = 56;
        BufferedImage img = new BufferedImage(2 * tw + 3 * pad, head + l.size() * (th + cap + pad) + pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = malen(img);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString("Phase " + a, pad, 38);
        g.drawString("Phase " + b, 2 * pad + tw, 38);
        g.setStroke(new BasicStroke(1));
        for (int i = 0; i < l.size(); i++) {
            Eintrag e = l.get(i);
            int y = head + i * (th + cap + pad);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.drawString(e.titel() + "  ·  " + unterzeile(e), pad, y + 18);
            kachel(g, lies(new File(alt, e.datei())), pad, y + cap, tw, th, "kein Bild in " + a);
            kachel(g, lies(new File(neu, e.datei())), 2 * pad + tw, y + cap, tw, th, "kein Bild in " + b);
        }
        g.dispose();
        return img;
    }
}
