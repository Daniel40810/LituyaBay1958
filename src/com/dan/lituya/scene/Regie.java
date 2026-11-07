package com.dan.lituya.scene;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Phase 17a: die Regiebücher (Werkbuch 11.3) als Text, aus Rungholt und Glacier übernommen. Die fünf mitgelieferten
 * stehen in {@code regie/*.txt} neben dieser Klasse. Ein Regiebuch ist eine Folge von Stichworten in echten Sekunden seit
 * dem Start: Uhr und Ereigniszeit, Zeitraffer und Zeitlupe, Zeitstand, Jahres-Zeitraffer, Ansichten, Schaustücke,
 * Fahrten aus {@code LB_NET_SICHT}, Mitfahrt auf den Booten, Netzblick, Wellen in Echtzeit, Regen, Fotos und am Ende ein
 * Blatt mit allen Fotos in {@code docs/status}. Was ein Stichwort tut, führt die App über {@link Ctx} aus; so läuft
 * dasselbe Regiebuch auch ohne Bild (Prüfung).
 * <p>{@code warte} hält die Uhr des Regiebuchs an, bis die Szene so weit ist (eine Uhrzeit, eine Ereigniszeit, die Welle
 * in Echtzeit, das Ende des Jahres-Zeitraffers, die Bahnen der Boote). Ein {@code warte} ist immer ein eigenes
 * Stichwort; was danach in derselben Sekunde steht, kommt, sobald die Bedingung erfüllt ist. Nach
 * {@link #WARTE_HOECHSTENS} Sekunden geht es mit einer Meldung weiter.
 * <ul>
 * <li><b>Zehn Minuten</b>: die Ereigniskette vom Stoß bis zur Front am Eingang in Zeitlupe, mit dem Netzblick der Welle.</li>
 * <li><b>Die drei Boote</b>: Edrie, Badger und Sunmore, je aus ihrer Sicht; am Ende „Wer hätte entkommen können?“.</li>
 * <li><b>Die Nacht der Welle</b>: der 9. Juli 1958 vom Abend vor Anker bis zum Morgen, die Welle in Echtzeit.</li>
 * <li><b>68 Jahre</b>: vom Morgen danach bis heute, der Wald wächst nach.</li>
 * <li><b>Die Bucht der Wellen</b>: von La Pérouse 1786 bis heute; die früheren Wellen in Echtzeit nachgerechnet [G].</li>
 * </ul>
 * Gestaltet [G]: Abläufe, Zeiten, Blickpunkte, Volumen der früheren Wellen.
 */
public final class Regie {

    /** Was ein Regiebuch braucht; die App führt es aus (ohne Bild: die Prüfung). */
    public interface Ctx {
        /** Datum und Uhrzeit (Ortszeit); der Zeitstand folgt daraus. */
        void time(LocalDate date, double hour);
        /** Ereigniszeit (s nach dem ersten Stoß), Zeitstand Welle. */
        void ereignis(double s);
        void lapse(double x);
        /** vor, welle, danach, heute. */
        void zeitstand(SceneClock.Zeitstand z);
        /** Jahres-Zeitraffer (Jahre je Minute) oder NaN = aus. */
        void jahre(double jeMinute);
        void view(String name);
        /** Schaustück nach Nummer oder Name (stellt Kamera und Uhr). */
        void stueck(String name);
        /** Fahrt: Rundflug, Kamerafahrt oder von A nach B die Sichtlinien entlang (ziel null), mit Tempo. */
        void fahrt(String name, String ziel, double tempo);
        /**
         * Werkbuch Welle W6: ein Flug durch Stützpunkte {x, y (m über NAVD88), z} in dauer Sekunden, Blick längs des Wegs,
         * Neigung nach unten (Grad), am Ende zur Blickrichtung endBlick (Grad von Nord, NaN = wie der Weg).
         */
        default void flug(double[][] punkte, double dauer, double neigung, double endBlick) { }
        /** Mitfahrt auf Edrie (im Steuerhaus) oder Badger (nebenher); null = aus. */
        void mitfahrt(String boot, boolean nebenher);
        /** Netzblick: welle, wasser, weg, sicht oder null (aus); szene = auch in der Szene. */
        void netzblick(String netz, boolean szene);
        /** Eine Welle in Echtzeit am Ort (Schlüssel) mit Volumen (Mio. m³); ort null = aus. */
        void echtzeit(String ort, double mio);
        void regen(double mmH);
        /**
         * Werkbuch Welle W7: die Wetterlage für heute (Name wie {@link Wetterlage#byName}) mit einem Übergang in Minuten
         * Szenenzeit (NaN = wie eingestellt; 0 = sofort).
         */
        default void wetter(Wetterlage lage, double uebergangMin) { }
        /** Werkbuch Welle W7b: Belichtungszeit des Regens in s (1/250 wie das Auge) und Zeitmaß des Regens (1 Echtzeit, unter 1 Zeitlupe). */
        default void verschluss(double sekunden) { }
        default void zeitlupe(double mass) { }
        /** Werkbuch Welle W9a: das Objektiv „an“, „aus“ oder „Brennweite [Blende]“; die Blende (f/N); der Fokus „auto“, Meter oder ein Ort. */
        default void objektiv(String a) { }
        default void blende(double fNumber) { }
        default void fokus(String a) { }
        /** Ob die Szene so weit ist (für {@code warte}). */
        boolean ready(Warte w);
        void capture(String caption);
        void sheet(String name);
        void say(String text);
    }

    /** Worauf {@code warte} wartet: eine Uhrzeit (Stunden seit 1970), eine Ereigniszeit (s) oder ein Zustand. */
    public record Warte(String was, double wert) {
        @Override public String toString() {
            return switch (was) {
                case "uhr" -> "Uhr " + java.time.Instant.ofEpochMilli((long) (wert * 3_600_000)).atZone(SceneClock.ZONE).toLocalDateTime().toString().replace('T', ' ');
                case "ereignis" -> String.format(Locale.GERMAN, "Ereignis t = %.0f s", wert);
                default -> was;
            };
        }
    }

    /** Ein Stichwort: Sekunde (echte Zeit seit dem Start), Text, Handlung; bei {@code warte} die Bedingung. */
    public record Cue(double t, String text, Consumer<Ctx> action, Warte warte) { }

    /** Ein Regiebuch: Name, Beschreibung, Länge (s, ohne die Wartezeit), Stichworte, Text, Stichworte als Zeilen. */
    public record Book(String name, String about, double length, List<Cue> cues, String text, List<Zeile> zeilen) {
        @Override public String toString() { return name; }
        public int fotos() { return (int) zeilen.stream().filter(z -> z.befehl().equals("foto")).count(); }
    }

    /** Länger wartet ein {@code warte} nicht (echte Sekunden). */
    public static final double WARTE_HOECHSTENS = 240;

    // ------------------------------------------------------------------ Ablauf

    /** Bekommt den Text jedes Stichworts (der Kinomodus macht Untertitel daraus); null = niemand. */
    public volatile Consumer<String> beiText;

    private volatile Book book;
    private double t, waited, waitedTotal;
    private int next;
    private volatile String status = "";
    private int fired;

    /** Regiebuch beginnen (das erste Stichwort sofort). */
    public void start(Book b, Ctx c) {
        book = b;
        t = 0;
        next = 0;
        fired = 0;
        waited = waitedTotal = 0;
        status = b.name() + " beginnt";
        step(0, c);
    }

    public void stop() {
        Book b = book;
        book = null;
        if (b != null) status = b.name() + " angehalten";
    }

    public boolean running() { return book != null; }

    public Book book() { return book; }

    /** Uhr des Regiebuchs (s, steht beim Warten). */
    public double time() { return t; }

    public int fired() { return fired; }

    public double waitedTotal() { return waitedTotal; }

    /** Das {@code warte}, an dem das Regiebuch gerade steht, oder null. */
    public Warte waiting() {
        Book b = book;
        if (b == null || next >= b.cues().size()) return null;
        Cue q = b.cues().get(next);
        return q.warte() != null && q.t() <= t ? q.warte() : null;
    }

    /** Um dt echte Sekunden weiter, fällige Stichworte ausführen; an einem {@code warte} steht die Uhr. */
    public void step(double dt, Ctx c) {
        Book b = book;
        if (b == null) return;
        double d = Math.max(0, dt);
        if (next < b.cues().size() && b.cues().get(next).warte() != null && b.cues().get(next).t() <= t) {
            waited += d;
            waitedTotal += d;
        } else t += d;
        while (next < b.cues().size() && b.cues().get(next).t() <= t) {
            Cue q = b.cues().get(next);
            if (q.warte() != null) {
                if (!c.ready(q.warte()) && waited < WARTE_HOECHSTENS) {
                    t = q.t();
                    status = String.format(Locale.GERMAN, "%s · wartet auf %s", b.name(), q.warte());
                    return;
                }
                status = waited >= WARTE_HOECHSTENS ? b.name() + " · " + q.warte() + " kam nicht, weiter" : b.name() + " · " + q.warte() + ": weiter";
                waited = 0;
                next++;
                fired++;
                continue;
            }
            next++;
            if (!q.text().isEmpty()) {
                status = String.format(Locale.GERMAN, "%s · %s", b.name(), q.text());
                Consumer<String> h = beiText;
                if (h != null) h.accept(q.text());
            }
            try { q.action().accept(c); } catch (RuntimeException e) { status = b.name() + " · Fehler bei „" + q.text() + "“: " + e.getMessage(); }
            fired++;
        }
        if (t >= b.length() && next >= b.cues().size()) {
            book = null;
            status = b.name() + " ist zu Ende (" + fired + " Stichworte)";
        }
    }

    /** „Zehn Minuten · 1:48: die Wand aus Wasser · 1:10 von 5:00“ */
    public String describe() {
        Book b = book;
        if (b == null) return status.isEmpty() ? "kein Regiebuch" : status;
        return String.format(Locale.GERMAN, "%s · %d:%02d von %d:%02d", status, (int) t / 60, (int) t % 60, (int) b.length() / 60, (int) b.length() % 60);
    }

    // ------------------------------------------------------------------ die Regiebücher (als Text)

    /** Die fünf mitgelieferten Regiebücher aus den Textdateien neben dieser Klasse (regie/*.txt). */
    public static final String[] DATEIEN = {"zehn_minuten.txt", "drei_boote.txt", "nacht.txt", "68_jahre.txt", "bucht_der_wellen.txt", "der_sturz.txt", "die_wand.txt", "524_m.txt", "flug_zur_kette.txt", "wetterwechsel.txt"};

    public static List<Book> all() {
        List<Book> l = new ArrayList<>();
        for (String d : DATEIEN) l.add(parse(text(d)));
        return l;
    }

    /** Nach Name, auch ohne Umlaute und in Teilen („Zehn“, „Boote“, „Nacht“, „68“, „Bucht“). */
    public static Book byName(String n) {
        String k = plain(n);
        for (Book b : all()) if (plain(b.name()).equals(k)) return b;
        for (Book b : all()) if (plain(b.name()).contains(k)) return b;
        return null;
    }

    static String plain(String s) {
        return s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replace("é", "e").replace(" ", "").replace("„", "").replace("“", "");
    }

    /** Der Text eines mitgelieferten Regiebuchs. */
    public static String text(String datei) {
        try (java.io.InputStream in = Regie.class.getResourceAsStream("regie/" + datei)) {
            if (in == null) throw new IllegalStateException("Regiebuch regie/" + datei + " fehlt im Build");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Regiebuch regie/" + datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    /** Die Befehle der Stichworte (klein geschrieben im Text). */
    public static final List<String> BEFEHLE = List.of("zeit", "ereignis", "raffer", "zeitstand", "jahre", "ansicht", "schaustueck", "fahrt", "mitfahrt",
            "netzblick", "echtzeit", "regen", "warte", "foto", "blatt", "sage", "flug", "wetter", "verschluss", "zeitlupe",
            "objektiv", "blende", "fokus");

    /** W7b: Belichtungszeit „1/250“ oder „0,004“ in s (zwischen 1/20000 und 1 s). */
    public static double belichtung(String a) {
        String t = a.trim().replace(',', '.');
        int sl = t.indexOf('/');
        double v = sl > 0 ? Double.parseDouble(t.substring(0, sl).trim()) / Double.parseDouble(t.substring(sl + 1).trim()) : Double.parseDouble(t);
        return Math.max(5e-5, Math.min(1, v));
    }

    /** Die Bedingungen von {@code warte}. */
    public static final List<String> WARTEN = List.of("uhr", "ereignis", "echtzeit", "jahre", "boote");

    /** Die Orte der Echtzeit (Schlüssel → Index in {@code gl.Echtzeit.ORTE}). */
    public static final List<String> ECHTZEIT_ORTE = List.of("wand", "sporn", "crillon", "mudslide", "cenotaph");

    /** Ein Stichwort, wie es im Text steht: Sekunde, Befehl, Argumente, Text fürs HUD. */
    public record Zeile(double sekunde, String befehl, String argumente, String text) { }

    /**
     * Liest ein Regiebuch aus Text. Kopfzeilen: {@code Regiebuch:}, {@code Beschreibung:}, {@code Länge:} (m:ss). Dann je
     * Zeile ein Stichwort: {@code m:ss befehl argumente | Text}. Zeilen zur selben Zeit ohne Text gehören zum Stichwort
     * davor, außer {@code warte}. Fehler nennen die Zeile.
     */
    public static Book parse(String text) {
        String name = null, about = "";
        double length = Double.NaN;
        List<Zeile> zeilen = new ArrayList<>();
        List<Warte> warten = new ArrayList<>();
        LocalDate[] day = {null};
        int no = 0;
        for (String raw : text.split("\\R")) {
            no++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String low = line.toLowerCase(Locale.ROOT);
            try {
                if (low.startsWith("regiebuch:")) name = line.substring(10).strip();
                else if (low.startsWith("beschreibung:")) about = line.substring(13).strip();
                else if (low.startsWith("länge:")) length = sekunden(line.substring(6).strip());
                else {
                    Zeile z = zeile(line);
                    if (z.befehl().equals("zeit")) day[0] = datum(z.argumente().split("\\s+")[0]);
                    if (z.befehl().equals("wetter") && Wetterlage.byName(z.argumente().replaceAll("\\s+[0-9]+([.,][0-9]+)?$", "").trim()) == null)
                        throw new IllegalArgumentException("wetter: keine Lage „" + z.argumente() + "“");   // W7
                    if (z.befehl().equals("blende")) {   // W9a
                        double bn = Double.parseDouble(z.argumente().trim().toLowerCase(Locale.ROOT).replaceFirst("^f/", "").replace(',', '.'));
                        if (bn < 1 || bn > 64) throw new IllegalArgumentException("blende 1 … 64 erwartet (f/N)");
                    }
                    if (z.befehl().equals("warte")) warten.add(warte(z.argumente(), day[0]));
                    else warten.add(null);
                    zeilen.add(z);
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Zeile " + no + ": " + e.getMessage() + " („" + line + "“)");
            }
        }
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Es fehlt die Zeile „Regiebuch: Name“");
        if (zeilen.isEmpty()) throw new IllegalArgumentException("Das Regiebuch hat keine Stichworte");
        Integer[] idx = new Integer[zeilen.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, java.util.Comparator.comparingDouble(i -> zeilen.get(i).sekunde()));
        List<Zeile> sorted = new ArrayList<>();
        List<Warte> sortedW = new ArrayList<>();
        for (int i : idx) { sorted.add(zeilen.get(i)); sortedW.add(warten.get(i)); }
        List<Cue> cues = new ArrayList<>();
        for (int i = 0; i < sorted.size(); ) {
            Zeile z = sorted.get(i);
            if (sortedW.get(i) != null) { cues.add(new Cue(z.sekunde(), z.text(), x -> { }, sortedW.get(i))); i++; continue; }
            List<Zeile> grp = new ArrayList<>(List.of(z));
            int k = i + 1;
            while (k < sorted.size() && sorted.get(k).sekunde() == z.sekunde() && sorted.get(k).text().isEmpty() && sortedW.get(k) == null) grp.add(sorted.get(k++));
            List<Consumer<Ctx>> acts = new ArrayList<>();
            for (Zeile g : grp) acts.add(handlung(g, name));
            cues.add(new Cue(z.sekunde(), z.text(), x -> { for (Consumer<Ctx> a : acts) a.accept(x); }, null));
            i = k;
        }
        if (Double.isNaN(length)) length = cues.get(cues.size() - 1).t() + 1;
        return new Book(name, about, length, cues, text, sorted);
    }

    /** „1:48“, „0:04“, „96“ oder „1:36.5“ → Sekunden. */
    static double sekunden(String s) {
        int c = s.indexOf(':');
        if (c < 0) return Double.parseDouble(s);
        return Integer.parseInt(s.substring(0, c)) * 60 + Double.parseDouble(s.substring(c + 1));
    }

    /** Sekunden → „m:ss“. */
    public static String zeit(double s) {
        int m = (int) (s / 60);
        double r = s - m * 60;
        return Math.abs(r - Math.rint(r)) < 1e-6 ? String.format(Locale.ROOT, "%d:%02d", m, (int) Math.rint(r)) : String.format(Locale.ROOT, "%d:%04.1f", m, r);
    }

    static LocalDate datum(String s) {
        String[] d = s.split("\\.");
        if (d.length != 3) throw new IllegalArgumentException("Datum TT.MM.JJJJ erwartet");
        return LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0]));
    }

    /** „uhr 22:10“, „uhr 10.07. 06:00“, „ereignis 447“, „echtzeit“, „jahre“, „boote“ */
    static Warte warte(String a, LocalDate day) {
        String[] p = a.strip().split("\\s+");
        String w = p[0].toLowerCase(Locale.ROOT);
        if (!WARTEN.contains(w)) throw new IllegalArgumentException("warte " + String.join("|", WARTEN) + " erwartet");
        if (w.equals("ereignis")) {
            if (p.length != 2) throw new IllegalArgumentException("warte ereignis Sekunden erwartet");
            return new Warte(w, Double.parseDouble(p[1]));
        }
        if (!w.equals("uhr")) return new Warte(w, Double.NaN);
        if (day == null) throw new IllegalArgumentException("warte uhr vor dem ersten zeit");
        LocalDate d = day;
        String hm;
        if (p.length == 3) {
            String[] dm = p[1].split("\\.");
            d = LocalDate.of(day.getYear(), Integer.parseInt(dm[1]), Integer.parseInt(dm[0]));
            if (d.isBefore(day)) d = d.plusYears(1);
            hm = p[2];
        } else if (p.length == 2) hm = p[1];
        else throw new IllegalArgumentException("warte uhr [TT.MM.] hh:mm erwartet");
        double h = sekunden(hm) / 60;
        int sec = (int) Math.round(Math.max(0, Math.min(86_399, h * 3600)));
        long ms = java.time.ZonedDateTime.of(d, java.time.LocalTime.ofSecondOfDay(sec), SceneClock.ZONE).toInstant().toEpochMilli();
        return new Warte("uhr", ms / 3_600_000.0);
    }

    static Zeile zeile(String line) {
        String cmd = line, txt = "";
        int bar = line.indexOf(" | ");
        if (bar >= 0) { cmd = line.substring(0, bar).strip(); txt = line.substring(bar + 3).strip(); }
        String[] p = cmd.split("\\s+", 3);
        if (p.length < 2) throw new IllegalArgumentException("Zeit und Befehl erwartet");
        double t = sekunden(p[0]);
        String b = p[1].toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ü", "ue").replace("ö", "oe");
        if (!BEFEHLE.contains(b)) throw new IllegalArgumentException("unbekannter Befehl „" + p[1] + "“, bekannt sind " + String.join(", ", BEFEHLE));
        return new Zeile(t, b, p.length > 2 ? p[2].strip() : "", txt);
    }

    /** Was ein Befehl tut; prüft die Argumente schon beim Lesen. */
    static Consumer<Ctx> handlung(Zeile z, String buch) {
        String a = z.argumente();
        String low = a.toLowerCase(Locale.ROOT);
        switch (z.befehl()) {
            case "zeit": {
                String[] p = a.split("\\s+");
                if (p.length != 2) throw new IllegalArgumentException("zeit TT.MM.JJJJ hh:mm erwartet");
                LocalDate date = datum(p[0]);
                double h = sekunden(p[1]) / 60;
                return x -> x.time(date, h);
            }
            case "ereignis": { double s = Double.parseDouble(a); return x -> x.ereignis(s); }
            case "raffer": { double v = Double.parseDouble(a); if (v < 0) throw new IllegalArgumentException("raffer ≥ 0 erwartet"); return x -> x.lapse(v); }
            case "zeitstand": {
                SceneClock.Zeitstand zs = switch (low) {
                    case "vor" -> SceneClock.Zeitstand.VOR;
                    case "welle" -> SceneClock.Zeitstand.WELLE;
                    case "danach" -> SceneClock.Zeitstand.DANACH;
                    case "heute" -> SceneClock.Zeitstand.HEUTE;
                    default -> throw new IllegalArgumentException("zeitstand vor|welle|danach|heute erwartet");
                };
                return x -> x.zeitstand(zs);
            }
            case "jahre": {
                double v = low.equals("aus") ? Double.NaN : Double.parseDouble(a);
                return x -> x.jahre(v);
            }
            case "ansicht": { need(a, "ansicht Name"); return x -> x.view(a); }
            case "schaustueck": { need(a, "schaustueck Nummer oder Name"); return x -> x.stueck(a); }
            case "fahrt": {
                // fahrt Rundflug [Tempo] · fahrt Kamerafahrt [Tempo] · fahrt A > B [Tempo]
                need(a, "fahrt Rundflug|Kamerafahrt|A > B [Tempo]");
                String s = a;
                double tempo = 15;
                int sp = s.lastIndexOf(' ');
                if (sp > 0) {
                    String last = s.substring(sp + 1).replace("×", "").replace("x", "");
                    try { tempo = Double.parseDouble(last); s = s.substring(0, sp).strip(); } catch (NumberFormatException e) { /* kein Tempo */ }
                }
                String von = s, nach = null;
                int gt = s.indexOf('>');
                if (gt >= 0) { von = s.substring(0, gt).strip(); nach = s.substring(gt + 1).strip(); if (von.isEmpty() || nach.isEmpty()) throw new IllegalArgumentException("fahrt A > B erwartet"); }
                String fv = von, fn = nach;
                double ft = tempo;
                return x -> x.fahrt(fv, fn, ft);
            }
            case "flug": {
                // W6: flug Dauer, Neigung, Endblick: x y z; x y z; … (Szene-m, y über NAVD88; Endblick „-“ = wie der Weg)
                need(a, "flug Dauer, Neigung, Endblick: x y z; x y z; …");
                int dp = a.indexOf(':');
                if (dp < 0) throw new IllegalArgumentException("flug Dauer, Neigung, Endblick: x y z; … erwartet");
                String[] kopf = a.substring(0, dp).split(",");
                if (kopf.length != 3) throw new IllegalArgumentException("flug: Dauer, Neigung, Endblick erwartet");
                double dauer = Double.parseDouble(kopf[0].strip()), neig = Double.parseDouble(kopf[1].strip());
                double ende = kopf[2].strip().equals("-") ? Double.NaN : Double.parseDouble(kopf[2].strip());
                String[] pk = a.substring(dp + 1).split(";");
                if (pk.length < 2) throw new IllegalArgumentException("flug: mindestens zwei Punkte");
                double[][] pts = new double[pk.length][];
                for (int i = 0; i < pk.length; i++) {
                    String[] q = pk[i].strip().split("\\s+");
                    if (q.length != 3) throw new IllegalArgumentException("flug: Punkt „" + pk[i].strip() + "“, x y z erwartet");
                    pts[i] = new double[]{Double.parseDouble(q[0]), Double.parseDouble(q[1]), Double.parseDouble(q[2])};
                }
                return x -> x.flug(pts, dauer, neig, ende);
            }
            case "mitfahrt": {
                if (low.equals("aus")) return x -> x.mitfahrt(null, false);
                String[] p = low.split("\\s+");
                if (!p[0].equals("edrie") && !p[0].equals("badger")) throw new IllegalArgumentException("mitfahrt edrie|badger [nebenher]|aus erwartet");
                String bt = p[0].equals("edrie") ? "Edrie" : "Badger";
                boolean nb = p.length > 1 && p[1].startsWith("neben");
                return x -> x.mitfahrt(bt, nb);
            }
            case "netzblick": {
                String[] p = low.split("\\s+");
                if (p[0].equals("aus")) return x -> x.netzblick(null, false);
                if (!List.of("welle", "wasser", "weg", "sicht").contains(p[0])) throw new IllegalArgumentException("netzblick welle|wasser|weg|sicht [szene]|aus erwartet");
                boolean sz = p.length > 1 && p[1].equals("szene");
                String nz = p[0];
                return x -> x.netzblick(nz, sz);
            }
            case "echtzeit": {
                if (low.equals("aus")) return x -> x.echtzeit(null, Double.NaN);
                String[] p = low.split("\\s+");
                if (!ECHTZEIT_ORTE.contains(p[0]) || p.length != 2) throw new IllegalArgumentException("echtzeit " + String.join("|", ECHTZEIT_ORTE) + " Mio.m³ oder aus erwartet");
                double v = Double.parseDouble(p[1].replace(',', '.'));
                String o = p[0];
                return x -> x.echtzeit(o, v);
            }
            case "regen": { double v = Double.parseDouble(a); return x -> x.regen(v); }
            case "verschluss": { double v = belichtung(a); return x -> x.verschluss(v); }   // W7b: „verschluss 1/250“ oder in s
            case "objektiv": { need(a, "objektiv an|aus|Brennweite [Blende]"); return x -> x.objektiv(a); }   // W9a
            case "blende": {
                double v = Double.parseDouble(a.trim().toLowerCase(java.util.Locale.ROOT).replaceFirst("^f/", "").replace(',', '.'));
                if (v < 1 || v > 64) throw new IllegalArgumentException("blende 1 … 64 erwartet (f/N)");
                return x -> x.blende(v);
            }
            case "fokus": { need(a, "fokus auto|Meter|Ort"); return x -> x.fokus(a); }
            case "zeitlupe": { double v = Double.parseDouble(a.trim().replace(',', '.')); return x -> x.zeitlupe(Math.max(1e-3, Math.min(1, v))); }
            case "wetter": {
                // W7: „wetter kuestennebel 30“ = Lage, Übergang in Minuten (ohne: wie eingestellt)
                need(a, "wetter Lage [Übergang in min]");
                String[] p = a.trim().split("\\s+");
                double ueb = Double.NaN;
                String name = a.trim();
                if (p.length > 1 && p[p.length - 1].matches("[0-9]+([.,][0-9]+)?")) {
                    ueb = Double.parseDouble(p[p.length - 1].replace(',', '.'));
                    name = a.trim().substring(0, a.trim().lastIndexOf(p[p.length - 1])).trim();
                }
                Wetterlage l = Wetterlage.byName(name);
                if (l == null) throw new IllegalArgumentException("wetter: keine Lage „" + name + "“ (" + java.util.Arrays.toString(Wetterlage.values()) + ")");
                double u = ueb;
                return x -> x.wetter(l, u);
            }
            case "foto": { need(a, "foto Unterschrift"); return x -> x.capture(a); }
            case "blatt": { String n = a.isEmpty() ? buch : a; return x -> x.sheet(n); }
            case "sage": { need(a, "sage Text"); return x -> x.say(a); }
            default: throw new IllegalArgumentException(z.befehl());
        }
    }

    private static void need(String a, String form) { if (a.isEmpty()) throw new IllegalArgumentException(form + " erwartet"); }
}
