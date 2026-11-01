package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Schritt {@code herkunft} der Aufbereitung (Werkbuch 7.7, Phase 3): das Herkunftsnetz als Dateien in
 * {@code daten/netz/herkunft/}, die {@code ant netzdb} nach {@code LB_NET_HERKUNFT} lädt. Gerichtet von der Quelle zum
 * Szenenobjekt:
 * <pre>
 *   QUELLE ──enthält──▶ ROHDATEI ──liefert──▶ AUSGABE ──zeigt──▶ OBJEKT
 *   SCHRITT ──erzeugt──▶ AUSGABE      AUSGABE ──grob/maske──▶ AUSGABE      ROHDATEI/AUSGABE ──belegt──▶ BEFUND ──gilt──▶ AUSGABE
 * </pre>
 * Je Rohdatei aus {@code download/inventar.csv} ein Knoten (Entscheidung Phase 3), je Datei in {@code daten/} eine
 * Ausgabe (mit SHA-256 aus {@code INHALT.txt}, wo die Größe passt). Welche Rohdatei in welche Kachel geht, rechnet die
 * Herkunft wie die Aufbereitung: dieselben Quellen je Stufe ({@link Gelaende#quellen}), dieselbe Überdeckung
 * ({@link Quelle#trifft}); der Vorrang (was eine bessere Quelle schon gefüllt hat) bleibt außen vor, die Kante sagt also
 * „lag unter der Kachel und war Quelle der Stufe“.
 * <p>
 * Dateien: {@code knoten.tsv} (ID, ART, NAME, PFAD, BYTES, SHA256, QUELLE), {@code kanten.tsv} (ID, VON, NACH, ART),
 * {@code pruefwege.tsv} (je Ausgabe die Zahl der Rohdateien und aller Knoten davor, für die Gegenrechnung mit traceIn in
 * Oracle) und {@code HERKUNFT.txt} (Zahlen und Befunde).
 */
public final class Herkunft {
    public static final String ORDNER = "netz/herkunft";

    /** Ein Knoten. */
    public record Knoten(int id, String art, String name, String pfad, long bytes, String sha, String quelle) { }

    /** Eine Kante. */
    public record Kante(int id, int von, int nach, String art) { }

    final List<Knoten> knoten = new ArrayList<>();
    final List<Kante> kanten = new ArrayList<>();
    final Map<String, Integer> nachName = new HashMap<>();
    final java.util.Set<Long> paare = new java.util.HashSet<>();

    int knoten(String art, String name, String pfad, long bytes, String sha, String quelle) {
        Integer i = nachName.get(art + "|" + name);
        if (i != null) return i;
        int id = knoten.size() + 1;
        knoten.add(new Knoten(id, art, name, pfad, bytes, sha, quelle));
        nachName.put(art + "|" + name, id);
        return id;
    }

    Integer id(String art, String name) { return nachName.get(art + "|" + name); }

    void kante(int von, int nach, String art) {
        if (von == nach || !paare.add(((long) von << 32) | nach)) return;
        kanten.add(new Kante(kanten.size() + 1, von, nach, art));
    }

    /** Der Schritt der Aufbereitung. */
    public static void rechnen(Rohdaten r) throws Exception {
        Herkunft h = new Herkunft();
        List<String> befunde = new ArrayList<>();
        // Quellen und Rohdateien aus dem Inventar
        File inv = new File(r.roh, "inventar.csv");
        if (!inv.isFile()) throw new IOException("Inventar fehlt: " + inv + " (python tools/kartenmaterial/kartenmaterial.py inventar)");
        List<String[]> roh = csv(inv);
        TreeMap<String, Integer> quellen = new TreeMap<>();
        for (String[] z : roh) quellen.merge(z[0] + "/" + z[1], 1, Integer::sum);
        for (var e : quellen.entrySet()) h.knoten("QUELLE", e.getKey(), e.getKey(), 0, null, e.getKey());
        for (String[] z : roh) {
            int q = h.id("QUELLE", z[0] + "/" + z[1]);
            int k = h.knoten("ROHDATEI", z[2], z[2], Long.parseLong(z[3].trim()), null, z[1]);
            h.kante(q, k, "ENTHAELT");
        }
        // die Datei des Repos für die Namen
        int namenRoh = h.knoten("ROHDATEI", "geodaten/namen_szene.geojson", "geodaten/namen_szene.geojson",
                new File("geodaten/namen_szene.geojson").length(), null, "gnis_namen");
        h.kante(quelleMit(h, "gnis_namen"), namenRoh, "ENTHAELT");
        // Schritte
        String[] schritte = {"gelaende fern_30m", "gelaende szene_5m", "gelaende kern_0.5m", "grund", "namen"};
        for (String s : schritte) h.knoten("SCHRITT", s, "com.dan.lituya.tools.Aufbereitung " + s.split(" ")[0], 0, null, null);
        // Ausgaben: alles in daten/ außer Protokoll, Netz und Inhalt
        Map<String, String[]> inhalt = inhalt(new File(r.daten, "INHALT.txt"));
        List<Path> aus = ausgaben(r.daten);
        int neuGerechnet = 0;
        for (Path p : aus) {
            String n = r.daten.toPath().relativize(p).toString().replace('\\', '/');
            long len = Files.size(p);
            String[] i = inhalt.get(n);
            String sha;
            if (i != null && Long.parseLong(i[0]) == len) sha = i[1];
            else { sha = sha(p); neuGerechnet++; }
            h.knoten("AUSGABE", n, "daten/" + n, len, sha, null);
        }
        if (neuGerechnet > 0) befunde.add(neuGerechnet + " Ausgaben ohne passenden Eintrag in INHALT.txt (SHA-256 neu gerechnet; danach Schritt inhalt)");
        // Objekte der Szene
        int oFern = h.knoten("OBJEKT", "Gelände Fern (30 m)", null, 0, null, null);
        int oSzene = h.knoten("OBJEKT", "Gelände Szene (5 m)", null, 0, null, null);
        int oKern = h.knoten("OBJEKT", "Gelände Kern (0,5 m)", null, 0, null, null);
        int oHeute = h.knoten("OBJEKT", "Bucht heute (Grund und Wasser)", null, 0, null, null);
        int oNamen = h.knoten("OBJEKT", "Ortsnamen", null, 0, null, null);
        Map<String, Integer> oJahr = new LinkedHashMap<>();
        for (String[] j : Grund.JAHRE) oJahr.put(j[0], h.knoten("OBJEKT", "Grund " + j[0] + " (Lotungen)", null, 0, null, null));
        // Gelände je Stufe
        Map<Stufe, Integer> oStufe = Map.of(Stufe.FERN, oFern, Stufe.SZENE, oSzene, Stufe.KERN, oKern);
        for (Stufe st : new Stufe[]{Stufe.FERN, Stufe.SZENE, Stufe.KERN}) {
            int schritt = h.id("SCHRITT", "gelaende " + st.ordner);
            List<List<Quelle>> q = Gelaende.quellen(st, r);
            Stufe grob = st == Stufe.KERN ? Stufe.SZENE : st == Stufe.SZENE ? Stufe.FERN : null;
            int nk = 0, ohneRoh = 0;
            for (double[] k : st.kacheln()) {
                String name = "gelaende/" + st.ordner + "/" + Stufe.name(k[0], k[1]);
                Integer a = h.id("AUSGABE", name);
                if (a == null) continue;
                nk++;
                h.kante(schritt, a, "ERZEUGT");
                h.kante(a, oStufe.get(st), "ZEIGT");
                int vorher = h.kanten.size();
                for (List<Quelle> l : q)
                    for (Quelle x : l)
                        for (int f = 0; f < x.anzahl(); f++)
                            if (x.trifft(f, k[0], k[1], k[0] + st.kachel(), k[1] + st.kachel())) {
                                Integer rd = h.id("ROHDATEI", rel(r, x.dateien().get(f)));
                                if (rd == null) rd = h.knoten("ROHDATEI", rel(r, x.dateien().get(f)), rel(r, x.dateien().get(f)), x.dateien().get(f).length(), null, "nicht im Inventar");
                                h.kante(rd, a, "LIEFERT");
                            }
                if (h.kanten.size() == vorher) ohneRoh++;
                if (grob != null)
                    for (double[] g : grob.kacheln())
                        if (g[0] < k[0] + st.kachel() && g[0] + grob.kachel() > k[0] && g[1] < k[1] + st.kachel() && g[1] + grob.kachel() > k[1]) {
                            Integer ga = h.id("AUSGABE", "gelaende/" + grob.ordner + "/" + Stufe.name(g[0], g[1]));
                            if (ga != null) h.kante(ga, a, "GROB");
                        }
            }
            Integer txt = h.id("AUSGABE", "gelaende/" + st.ordner + "/STUFE.txt");
            if (txt != null) h.kante(schritt, txt, "ERZEUGT");
            for (List<Quelle> l : q) for (Quelle x : l) x.schliessen();
            r.log("  herkunft %s: %d Kacheln, davon %d ohne Rohdatei (nur aus der gröberen Stufe)", st, nk, ohneRoh);
            if (ohneRoh > 0) befunde.add(st + ": " + ohneRoh + " Kacheln ohne Rohdatei, nur aus der gröberen Stufe");
        }
        // Grund
        int sGrund = h.id("SCHRITT", "grund");
        Integer heute = h.id("AUSGABE", "grund/heute_4m.r32"), wasser = h.id("AUSGABE", "grund/wasser_4m.u8"), grundTxt = h.id("AUSGABE", "grund/GRUND.txt");
        if (heute != null && wasser != null) {
            List<List<Quelle>> q = Gelaende.quellen(Stufe.KERN, r);
            Site.Fenster kf = Site.KERN;
            for (List<Quelle> l : q)
                for (Quelle x : l)
                    for (int f = 0; f < x.anzahl(); f++)
                        if (x.trifft(f, kf.eMin(), kf.nMin(), kf.eMax(), kf.nMax())) {
                            Integer rd = h.id("ROHDATEI", rel(r, x.dateien().get(f)));
                            if (rd == null) continue;
                            h.kante(rd, heute, "LIEFERT");
                            h.kante(rd, wasser, "LIEFERT");
                        }
            for (List<Quelle> l : q) for (Quelle x : l) x.schliessen();
            for (int a : new int[]{heute, wasser}) { h.kante(sGrund, a, "ERZEUGT"); h.kante(a, oHeute, "ZEIGT"); }
            Integer gez = h.id("ROHDATEI", "klima/gezeiten/bezugshoehen_elfin_cove.json");
            for (String[] j : Grund.JAHRE) {
                Integer a = h.id("AUSGABE", "grund/" + j[0] + "_20m.r32");
                if (a == null) continue;
                h.kante(sGrund, a, "ERZEUGT");
                h.kante(wasser, a, "MASKE");
                h.kante(a, oJahr.get(j[0]), "ZEIGT");
                for (int k = 1; k < j.length; k++) {
                    Integer rd = h.id("ROHDATEI", "bathymetrie/nos_vermessungen/" + j[k] + "/GEODAS/" + j[k] + ".xyz.gz");
                    if (rd != null) h.kante(rd, a, "LIEFERT");
                    else befunde.add("Lotungen " + j[k] + " nicht im Inventar");
                }
            }
            if (grundTxt != null) {
                h.kante(sGrund, grundTxt, "ERZEUGT");
                if (gez != null) h.kante(gez, grundTxt, "LIEFERT");
                for (String n : new String[]{"heute_4m.r32", "1926_20m.r32", "1940_20m.r32", "1959_20m.r32"}) {
                    Integer a = h.id("AUSGABE", "grund/" + n);
                    if (a != null) h.kante(a, grundTxt, "ZAHLEN");
                }
            }
            // Befunde aus Phase 2 (Werkbuch 3.4)
            int b1 = h.knoten("BEFUND", "MLLW liegt 1,157 m unter NAVD88 (BlueTopo/VDatum über den Lotungen 1940; Elfin Cove ohne NAVD88)", null, 0, null, null);
            int b2 = h.knoten("BEFUND", "„heute“ in der tiefen Bucht ist der Grund von 1940 (BlueTopo übernimmt die Vermessung H06581–H06583)", null, 0, null, null);
            Integer h40 = h.id("AUSGABE", "grund/1940_20m.r32");
            for (String s : new String[]{"H06581", "H06582", "H06583"}) {
                Integer rd = h.id("ROHDATEI", "bathymetrie/nos_vermessungen/" + s + "/GEODAS/" + s + ".xyz.gz");
                if (rd != null) { h.kante(rd, b1, "BELEGT"); h.kante(rd, b2, "BELEGT"); }
            }
            if (gez != null) h.kante(gez, b1, "BELEGT");
            for (Knoten k : List.copyOf(h.knoten))
                if (k.art().equals("ROHDATEI") && k.name().startsWith("bathymetrie/noaa_bluetopo/") && h.hatKante(k.id(), heute)) {
                    h.kante(k.id(), b1, "BELEGT");
                    h.kante(k.id(), b2, "BELEGT");
                }
            h.kante(b1, h40 != null ? h40 : heute, "GILT");
            h.kante(b2, heute, "GILT");
        }
        // Namen
        Integer namen = h.id("AUSGABE", "namen.tsv");
        if (namen != null) {
            h.kante(h.id("SCHRITT", "namen"), namen, "ERZEUGT");
            h.kante(namenRoh, namen, "LIEFERT");
            h.kante(namen, oNamen, "ZEIGT");
        }
        // Prüfungen: kreisfrei, jede Ausgabe hat einen Schritt, jede Datenausgabe eine Rohdatei davor
        int n = h.knoten.size();
        List<int[]> rein = h.rein();
        if (!h.kreisfrei()) throw new IllegalStateException("Herkunftsnetz hat einen Kreis");
        List<String> pw = new ArrayList<>();
        pw.add("ID\tNAME\tROHDATEIEN\tVORFAHREN");
        int ohneSchritt = 0, ohneRohAus = 0;
        for (Knoten k : h.knoten) {
            if (!k.art().equals("AUSGABE")) continue;
            BitSet v = h.vorfahren(k.id(), rein);
            int nr = 0;
            boolean schritt = false;
            for (int i = v.nextSetBit(0); i >= 0; i = v.nextSetBit(i + 1)) {
                String art = h.knoten.get(i - 1).art();
                if (art.equals("SCHRITT")) schritt = true;
                if (art.equals("ROHDATEI")) nr++;
            }
            if (!schritt) ohneSchritt++;
            if (nr == 0 && !k.name().endsWith(".txt")) ohneRohAus++;
            pw.add(k.id() + "\t" + k.name() + "\t" + nr + "\t" + v.cardinality());
        }
        if (ohneSchritt > 0) befunde.add(ohneSchritt + " Ausgaben ohne Schritt davor");
        if (ohneRohAus > 0) befunde.add(ohneRohAus + " Datenausgaben ohne Rohdatei davor");
        // ungenutzte Rohdateien je Quelle (Kartenmaterial für spätere Schritte)
        int[] raus = new int[n + 1];
        for (Kante k : h.kanten) raus[k.von()]++;
        TreeMap<String, Integer> ungenutzt = new TreeMap<>();
        int genutzt = 0;
        for (Knoten k : h.knoten)
            if (k.art().equals("ROHDATEI")) {
                if (raus[k.id()] == 0) ungenutzt.merge(k.quelle(), 1, Integer::sum);
                else genutzt++;
            }
        // schreiben
        File dir = new File(r.daten, ORDNER);
        dir.mkdirs();
        List<String> zk = new ArrayList<>();
        zk.add("ID\tART\tNAME\tPFAD\tBYTES\tSHA256\tQUELLE");
        for (Knoten k : h.knoten) zk.add(k.id() + "\t" + k.art() + "\t" + k.name() + "\t" + nz(k.pfad()) + "\t" + k.bytes() + "\t" + nz(k.sha()) + "\t" + nz(k.quelle()));
        List<String> zl = new ArrayList<>();
        zl.add("ID\tVON\tNACH\tART");
        for (Kante k : h.kanten) zl.add(k.id() + "\t" + k.von() + "\t" + k.nach() + "\t" + k.art());
        Files.write(new File(dir, "knoten.tsv").toPath(), zk, StandardCharsets.UTF_8);
        Files.write(new File(dir, "kanten.tsv").toPath(), zl, StandardCharsets.UTF_8);
        Files.write(new File(dir, "pruefwege.tsv").toPath(), pw, StandardCharsets.UTF_8);
        TreeMap<String, Integer> arten = new TreeMap<>(), kantenArten = new TreeMap<>();
        for (Knoten k : h.knoten) arten.merge(k.art(), 1, Integer::sum);
        for (Kante k : h.kanten) kantenArten.merge(k.art(), 1, Integer::sum);
        StringBuilder t = new StringBuilder("# Herkunftsnetz LB_NET_HERKUNFT (Phase 3, " + java.time.LocalDate.now() + ")\n");
        t.append("knoten=").append(n).append("  # ").append(arten).append('\n');
        t.append("kanten=").append(h.kanten.size()).append("  # ").append(kantenArten).append('\n');
        t.append("rohdateien_genutzt=").append(genutzt).append('\n');
        t.append("pruefsumme=").append(pruefsumme(dir)).append("  # SHA-256 über knoten.tsv und kanten.tsv\n");
        t.append("# ungenutzte Rohdateien je Quelle (Kartenmaterial für spätere Schritte): ").append(ungenutzt).append('\n');
        for (String b : befunde) t.append("befund: ").append(b).append('\n');
        Files.writeString(new File(dir, "HERKUNFT.txt").toPath(), t.toString(), StandardCharsets.UTF_8);
        r.log("  herkunft: %d Knoten %s, %d Kanten %s; %d Rohdateien genutzt, %d ungenutzt; %d Befunde", n, arten, h.kanten.size(), kantenArten,
                genutzt, ungenutzt.values().stream().mapToInt(Integer::intValue).sum(), befunde.size());
        for (String b : befunde) r.log("  herkunft Befund: %s", b);
    }

    static int quelleMit(Herkunft h, String q) {
        for (Knoten k : h.knoten) if (k.art().equals("QUELLE") && k.name().endsWith("/" + q)) return k.id();
        return h.knoten("QUELLE", "geodaten/" + q, "geodaten/" + q, 0, null, q);
    }

    boolean hatKante(int von, int nach) { return paare.contains(((long) von << 32) | nach); }

    /** Je Knoten die Vorgänger (Index = ID). */
    List<int[]> rein() {
        int[] c = new int[knoten.size() + 1];
        for (Kante k : kanten) c[k.nach()]++;
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i <= knoten.size(); i++) l.add(new int[c[i]]);
        int[] f = new int[knoten.size() + 1];
        for (Kante k : kanten) l.get(k.nach())[f[k.nach()]++] = k.von();
        return l;
    }

    /** Alle Knoten vor id (Breitensuche rückwärts; das tut traceIn in Oracle). */
    BitSet vorfahren(int id, List<int[]> rein) {
        BitSet b = new BitSet();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(id);
        while (!q.isEmpty()) for (int v : rein.get(q.poll())) if (!b.get(v)) { b.set(v); q.add(v); }
        return b;
    }

    boolean kreisfrei() {
        int n = knoten.size();
        int[] grad = new int[n + 1];
        List<List<Integer>> raus = new ArrayList<>();
        for (int i = 0; i <= n; i++) raus.add(new ArrayList<>());
        for (Kante k : kanten) { grad[k.nach()]++; raus.get(k.von()).add(k.nach()); }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 1; i <= n; i++) if (grad[i] == 0) q.add(i);
        int gesehen = 0;
        while (!q.isEmpty()) {
            int v = q.poll();
            gesehen++;
            for (int w : raus.get(v)) if (--grad[w] == 0) q.add(w);
        }
        return gesehen == n;
    }

    static String nz(String s) { return s == null ? "" : s.replace('\t', ' '); }

    static String rel(Rohdaten r, File f) {
        return r.roh.toPath().toAbsolutePath().normalize().relativize(f.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /** Die Ausgaben in daten/ (ohne Protokoll, Netz, Inhalt, Einstellungen). */
    static List<Path> ausgaben(File daten) throws IOException {
        Path base = daten.toPath();
        List<Path> l = new ArrayList<>();
        try (var st = Files.walk(base)) {
            st.filter(Files::isRegularFile).filter(p -> {
                String n = base.relativize(p).toString().replace('\\', '/');
                return !n.startsWith("netz/") && !n.equals("INHALT.txt") && !n.equals("aufbereitung.log") && !n.equals("rohdaten.properties")
                        && !n.equals("fehler.txt") && !n.endsWith(".tmp");
            }).sorted().forEach(l::add);
        }
        return l;
    }

    /** INHALT.txt: Datei → {Bytes, SHA-256}. */
    static Map<String, String[]> inhalt(File f) throws IOException {
        Map<String, String[]> m = new HashMap<>();
        if (!f.isFile()) return m;
        for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (z.startsWith("#")) continue;
            String[] s = z.split("\t");
            if (s.length >= 3) m.put(s[0], new String[]{s[1], s[2]});
        }
        return m;
    }

    static String sha(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[1 << 20];
        try (InputStream in = Files.newInputStream(p)) {
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    /** SHA-256 über knoten.tsv und kanten.tsv (steht in LB_NETZ_STAND; so passen Oracle und Datei zusammen). */
    public static String pruefsumme(File dir) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String n : new String[]{"knoten.tsv", "kanten.tsv"}) md.update(Files.readAllBytes(new File(dir, n).toPath()));
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    /** Einfaches CSV (Komma, Felder in Anführungszeichen erlaubt); ohne Kopfzeile. */
    static List<String[]> csv(File f) throws IOException {
        List<String[]> l = new ArrayList<>();
        boolean kopf = true;
        for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (kopf) { kopf = false; continue; }
            if (z.isBlank()) continue;
            List<String> s = new ArrayList<>();
            StringBuilder b = new StringBuilder();
            boolean in = false;
            for (int i = 0; i < z.length(); i++) {
                char c = z.charAt(i);
                if (c == '"') { if (in && i + 1 < z.length() && z.charAt(i + 1) == '"') { b.append('"'); i++; } else in = !in; }
                else if (c == ',' && !in) { s.add(b.toString()); b.setLength(0); }
                else b.append(c);
            }
            s.add(b.toString());
            if (s.size() >= 4) l.add(s.toArray(new String[0]));
        }
        return l;
    }

    /** Liest knoten.tsv und kanten.tsv (für NetzDb und die Prüfung). */
    public static Herkunft lesen(File dir) throws IOException {
        Herkunft h = new Herkunft();
        boolean kopf = true;
        for (String z : Files.readAllLines(new File(dir, "knoten.tsv").toPath(), StandardCharsets.UTF_8)) {
            if (kopf) { kopf = false; continue; }
            String[] s = z.split("\t", -1);
            Knoten k = new Knoten(Integer.parseInt(s[0]), s[1], s[2], e(s[3]), Long.parseLong(s[4]), e(s[5]), e(s[6]));
            h.knoten.add(k);
            h.nachName.put(k.art() + "|" + k.name(), k.id());
        }
        kopf = true;
        for (String z : Files.readAllLines(new File(dir, "kanten.tsv").toPath(), StandardCharsets.UTF_8)) {
            if (kopf) { kopf = false; continue; }
            String[] s = z.split("\t", -1);
            Kante k = new Kante(Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]), s[3]);
            h.kanten.add(k);
            h.paare.add(((long) k.von() << 32) | k.nach());
        }
        return h;
    }

    static String e(String s) { return s.isEmpty() ? null : s; }

    public List<Knoten> knotenListe() { return knoten; }

    public List<Kante> kantenListe() { return kanten; }

    /** Die Zahl der Rohdateien vor jeder Ausgabe (wie pruefwege.tsv), neu gerechnet. */
    public Map<Integer, Integer> rohVorfahren() {
        List<int[]> rein = rein();
        Map<Integer, Integer> m = new TreeMap<>();
        for (Knoten k : knoten)
            if (k.art().equals("AUSGABE")) {
                BitSet v = vorfahren(k.id(), rein);
                int n = 0;
                for (int i = v.nextSetBit(0); i >= 0; i = v.nextSetBit(i + 1)) if (knoten.get(i - 1).art().equals("ROHDATEI")) n++;
                m.put(k.id(), n);
            }
        return m;
    }

    /** Prüft das gelesene Netz: IDs fortlaufend, Kanten zwischen vorhandenen Knoten, kreisfrei; null wenn gut. */
    public String fehler() {
        for (int i = 0; i < knoten.size(); i++) if (knoten.get(i).id() != i + 1) return "Knoten-ID " + knoten.get(i).id() + " an Stelle " + (i + 1);
        for (Kante k : kanten) if (k.von() < 1 || k.von() > knoten.size() || k.nach() < 1 || k.nach() > knoten.size()) return "Kante " + k.id() + " zeigt ins Leere";
        return kreisfrei() ? null : "Kreis im Netz";
    }

    /** Liest pruefwege.tsv: Ausgabe-ID → Rohdateien davor. */
    public static Map<Integer, Integer> pruefwege(File dir) throws IOException {
        Map<Integer, Integer> m = new TreeMap<>();
        boolean kopf = true;
        for (String z : Files.readAllLines(new File(dir, "pruefwege.tsv").toPath(), StandardCharsets.UTF_8)) {
            if (kopf) { kopf = false; continue; }
            String[] s = z.split("\t");
            m.put(Integer.parseInt(s[0]), Integer.parseInt(s[2]));
        }
        return m;
    }

    static String fmt(String f, Object... a) { return String.format(Locale.ROOT, f, a); }
}
