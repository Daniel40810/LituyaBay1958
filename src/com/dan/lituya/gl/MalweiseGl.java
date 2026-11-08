package com.dan.lituya.gl;

import com.dan.forest.ForestCulture;
import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeModel;
import com.dan.forest.TreeStates;
import com.dan.forest.gl.ForestGpu;
import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Druck;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Quelle;
import com.dan.gertsch.Stich;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;
import com.dan.gertsch.gl.GertschGl;
import com.dan.gertsch.gl.StichGl;
import com.dan.understory.Kind;
import com.dan.understory.PlantMesh;
import com.dan.understory.Understory;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Werkbuch Welle W10 „Malweise“: die Szene nach Franz Gertsch ({@code com.dan.gertsch}), im Bedienfeld MALWEISE zu- und
 * abschaltbar. Drei Arten:
 * <ul>
 * <li><b>Gertsch: der Wald ringsum.</b> Die Bäume im Umkreis der Kamera kommen aus dem Wald der App ({@code forest.gl.ForestGpu}:
 * dieselben Plätze, Arten, Varianten, Drehungen und Größen, dieselben Modelle, Farben nach dem Tag) über die
 * Schnittstelle {@link Quelle} in ein {@link Dickicht}; {@link GertschGl} malt es auf der Grafikkarte mit Licht, Wind und
 * den Tonstufen einer Vorlage. Das ersetzt das Bild der Szene: kein Himmel, kein Wasser, der Grund ist die dunkelste Stufe.</li>
 * <li><b>Gertsch als Holzschnitt.</b> Dasselbe Bild, dann gedruckt wie Gertschs Holzschnitte ({@link StichGl}).</li>
 * <li><b>Holzschnitt der ganzen Szene.</b> Das fertige Bild der App (nach Filmkurve oder Film) als Holzschnitt, für jede
 * Ansicht, auch die Welle.</li>
 * </ul>
 * <b>Die Brücke.</b> Das Paket {@code gertsch} kennt {@code forest} nicht (Paketvertrag). Hier, in der App, wird jeder Baum
 * wie in {@code forest.vert} in die Welt gesetzt (Fuß, Drehung um die Senkrechte, Größe) und dann in den Bezug von
 * {@code gertsch} gebracht: Boden y = 0, die Mitte des Ausschnitts im Ursprung, x gespiegelt. Gertschs Kamera hat
 * „rechts = oben × vorn“, die Szene der App ist rechtshändig (x Ost, y oben, z Süd); ohne die Spiegelung stünde das Bild
 * seitenverkehrt. Seit W10a geht das Höhengitter der Kultur als {@code gertsch.Gelaende} mit (tiefste Stelle auf y = 0): Bäume,
 * Unterwuchs und Auge stehen auf ihrer echten Höhe. Eine Blattstelle wird ein Büschel (Fächer von {@code forest}: Radius
 * {@code cluster · 0,5 · (0,75 + 0,5 · tone)}), ein Stück Ast ein Glied; Äste dünner als {@link #MIN_AST} fallen weg.
 * <p>
 * Der Ausschnitt entsteht im Hintergrund und wird neu gebaut, wenn die Kamera aus seiner Mitte wandert, der Tag oder
 * der Umkreis sich ändert oder die Zustände des Walds (Welle) sich ändern; bis dahin malt der alte weiter.
 */
public final class MalweiseGl {
    /** Was gemalt wird. */
    public enum Art {
        AUS("aus"), GERTSCH("Gertsch: der Wald ringsum"), GERTSCH_STICH("Gertsch als Holzschnitt"), STICH("Holzschnitt der ganzen Szene");

        public final String label;

        Art(String label) { this.label = label; }

        /** Ersetzt das Bild der Szene durch den gemalten Wald. */
        public boolean gertsch() { return this == GERTSCH || this == GERTSCH_STICH; }

        @Override public String toString() { return label; }
    }

    /** Welche Vorlage die Tonstufen, den Grund und die Akzente gibt (gemessen in {@code gertsch}, Phase 1). */
    public enum Vorbild {
        SOMMER("Sommer (grün-schwarz)", Vorlagen::sommer), HERBST("Herbst (ocker)", Vorlagen::herbst), WINTER("Winter", Vorlagen::winter),
        BLAETTER("Blätter (Huflattich)", Vorlagen::blaetter), GRAESER("Gräser", Vorlagen::graeser);

        public final String label;
        final Supplier<Vorlage> vorlage;

        Vorbild(String label, Supplier<Vorlage> v) { this.label = label; this.vorlage = v; }

        @Override public String toString() { return label; }

        /** Nach Name oder Anfang („sommer“, „herbst“ …); null, wenn keins passt. */
        public static Vorbild byName(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT).replace("ä", "ae");
            for (Vorbild v : values()) if (v.name().toLowerCase(Locale.ROOT).startsWith(k) || v.label.toLowerCase(Locale.ROOT).startsWith(k)) return v;
            return null;
        }
    }

    /** Äste dünner als so viele Meter (nach der Größe des Baums) fallen weg: sie liegen im Laub und kosten nur. */
    public static final double MIN_AST = 0.015;
    /** Bis so hoch über Grund (m) malt die Malweise; darüber zeichnet die Szene wie sonst (Gertsch malte vom Boden aus). */
    public static final double BIS_HOEHE = 120;
    /**
     * Ein Platz im alten Wald am Boden, nordwestlich von Fish Lake (wie die Probe W9c 939): Auge 1,8 m, Blick nach Osten,
     * 6° nach unten. Die Ansichten 1 bis 9 stehen über dem Wasser oder hoch in der Luft, dort ist kein Wald im Umkreis.
     */
    public static final com.dan.lituya.camera.Ansichten.Ansicht IM_WALD = new com.dan.lituya.camera.Ansichten.Ansicht(0, "Im Wald bei Fish Lake", -6500, -1500, 90, 6, 1.8, 1);
    /** Albedo des Waldbodens (Moos und Nadelstreu unter Sitka und Hemlock) [G]. */
    static final float[] BODEN = {0.040f, 0.045f, 0.025f};
    /** Rand des Bilds und Papier, wenn das Bild schmaler ist als das Fenster (0xRRGGBB). */
    static final int RAND = 0x0B1118;

    public volatile Art art = Art.AUS;
    public volatile Vorbild vorbild = Vorbild.SOMMER;
    /** Umkreis des Ausschnitts (m) und höchstens so viele Büschel (die nächsten Bäume zuerst). */
    public volatile double umkreis = 50;
    public volatile int hoechstensBueschel = 1_500_000;
    /**
     * Auslöschung des Himmelslichts je Blattfläche im Dickicht (W10a; gertsch: 0,5). Die Bäume aus {@code forest} tragen
     * für {@code gertsch} rund dreimal zu viel Blattfläche (jede Blattstelle mindestens drei Triebe von 40 cm): über dem
     * Stammraum des alten Walds rund 29 m²/m² statt 6 bis 10 (understory, [G]). Mit 0,5 kam dort kein Himmelslicht an,
     * die Hälfte des Bilds lag unter 2⁻²⁴ und fiel in die dunkelste Stufe. Mit k · LAI ≈ 0,45 · 8 wie im Regenwald.
     */
    public volatile double ausloeschung = 0.13;
    /** Den Unterwuchs aus {@code understory} mitmalen (Farne, Moos, Streu, Teufelskeule, Beeren, Totholz). */
    public volatile boolean unterwuchs = true;
    /** Ton des Holzschnitts der ganzen Szene: wie die Szene (LINEAR, Vorgabe) oder nach der Vorlage (sehr dunkel). */
    public volatile Stich.Tonwerte tonSzene = Stich.Tonwerte.LINEAR;

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Malweise"); t.setDaemon(true); return t; });
    private Future<Ausschnitt> bau;
    private Ausschnitt jetzt;
    private GertschGl gg;
    private StichGl sg;
    private Vorbild geladenVorbild;
    private Art letzteArt = Art.AUS;
    private int quelleTex, quelleW, quelleH;
    private double zeit;
    private long letzterBau;
    private final double[] sonneGeladen = {Double.NaN, 0, 0};
    private volatile String status = "aus";
    private volatile boolean fehler;

    // ------------------------------------------------------------------ der Ausschnitt

    /** Ein gemalter Ausschnitt: Dickicht und Licht (Himmel), Mitte und Grund, wofür er gebaut ist, Zahlen. */
    static final class Ausschnitt {
        final Dickicht d;
        final com.dan.gertsch.Licht licht = new com.dan.gertsch.Licht();
        final double cx, cz, umkreis;
        double ferne = 1;
        final int tag, version, baeume, baeumeNah, bueschel, glieder, blaetter, pflanzen;
        final boolean unterwuchs;
        /** Höhe der tiefsten Stelle (m über NAVD88) und Höhenunterschied im Ausschnitt (m). */
        final float gRef, relief;
        double ms;

        Ausschnitt(Dickicht d, double cx, double cz, double umkreis, int tag, int version, WaldQuelle q, boolean unterwuchs) {
            this.d = d; this.cx = cx; this.cz = cz; this.umkreis = umkreis; this.tag = tag; this.version = version;
            this.baeume = q.baeume; this.baeumeNah = q.baeumeNah; this.bueschel = q.bueschelZahl(); this.glieder = q.glieder(); this.blaetter = q.blaetter(); this.pflanzen = q.pflanzen;
            this.unterwuchs = unterwuchs;
            this.gRef = q.gRef;
            this.relief = q.gel == null ? 0 : q.gel.max();
        }
    }

    /**
     * Die Brücke: die Bäume des Walds im Umkreis r um (cx, cz) als {@link Quelle}, im Bezug von {@code gertsch} (Boden
     * y = 0 unter jedem Baum, Mitte im Ursprung, x gespiegelt). Die Bäume nahe dem Auge (ex, ez) zuerst, bis höchstens
     * {@code max} Büschel. Farben nach dem Tag im Jahr wie im Wald der App ({@link Season}); die Tönung je Baum wie in
     * {@code forest.vert}. Ohne Grafikkarte (für Hintergrund und Prüfung).
     */
    public static WaldQuelle quelle(ForestGpu f, double cx, double cz, double r, double ferne, double ex, double ez, int max, int tag, boolean mitUnterwuchs) {
        WaldQuelle q = new WaldQuelle();
        q.gelaende(f.culture(), cx, cz, ferne * r + 12);
        baeume(q, f, cx, cz, r, ferne * r, ex, ez, max, tag);
        if (mitUnterwuchs) unterwuchs(q, f.culture(), cx, cz, r);
        return q;
    }

    /**
     * Bis zu so vielen Umkreisen stehen hinter dem Umkreis noch Bäume, zusammengefasst (W10a). Ohne sie endete der Blick
     * zwischen den Stämmen nach 50 m im leeren Grund: Die Tonstufe 0 deckte 37 bis 47 % des Bilds statt 29 % der Vorlage,
     * weil die Rangregel jedem gezeichneten Fleck schon seinen Anteil gibt und der leere Grund obendrauf kommt.
     */
    public static final double FERNE = 2.5;
    /** Ferne in Umkreisen (Vorgabe {@link #FERNE}; 1 = nur der Umkreis). */
    public volatile double ferne = 1;
    /** Hinter dem Umkreis: so viele Blattstellen eines Asts in einem Büschel, und Äste erst ab dieser Dicke (m). */
    static final int ZUSAMMEN = 4;
    static final double FERN_AST = 0.05;

    static void baeume(WaldQuelle q, ForestGpu f, double cx, double cz, double r, double rFern, double ex, double ez, int max, int tag) {
        ForestCulture cu = f.culture();
        cu.minGround = (float) f.minGround;
        double sp = cu.spacing;
        int i0 = (int) Math.floor((cx - rFern) / sp), i1 = (int) Math.floor((cx + rFern) / sp);
        int j0 = (int) Math.floor((cz - rFern) / sp), j1 = (int) Math.floor((cz + rFern) / sp);
        List<ForestCulture.Tree> baeume = new ArrayList<>();
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                ForestCulture.Tree t = new ForestCulture.Tree();
                if (cu.at(i, j, t) && Math.hypot(t.x - cx, t.z - cz) <= rFern) baeume.add(t);
            }
        baeume.sort((a, b) -> Double.compare(Math.hypot(a.x - ex, a.z - ez), Math.hypot(b.x - ex, b.z - ez)));
        q.bodenAlbedo = BODEN.clone();
        float gy = q.gRef;   // seit dem Gelände: alles auf seiner Höhe über der tiefsten Stelle des Ausschnitts
        Map<Integer, Season> jahreszeit = new HashMap<>();
        float[] c3 = new float[3];
        for (ForestCulture.Tree t : baeume) {
            if (q.bueschelZahl() >= max) break;
            TreeModel m = f.model(t.type);
            Species art = m.species;
            Season se = jahreszeit.computeIfAbsent(t.type, k -> Season.of(art, tag, 0));
            double c = Math.cos(t.yaw), s = Math.sin(t.yaw), k = t.scale;
            // die Tönung je Baum wie forest.vert: mix(vec3(0.84, 0.88, 0.86), vec3(1.14, 1.08, 0.96), tint)
            float tr = 0.84f + 0.30f * t.tint, tg = 0.88f + 0.20f * t.tint, tb = 0.86f + 0.10f * t.tint;
            boolean fein = Math.hypot(t.x - cx, t.z - cz) <= r;
            double minAst = fein ? MIN_AST : FERN_AST;
            for (TreeModel.Branch b : m.branches)
                for (int n = 0; n + 1 < b.nodes(); n++) {
                    if (b.level > 0 && (k * Math.max(b.r[n], b.r[n + 1]) < minAst || (!fein && b.level > 1))) continue;
                    double ax = b.x[n], az = b.z[n], bx = b.x[n + 1], bz = b.z[n + 1];
                    q.glied((float) -(t.x + k * (c * ax - s * az) - cx), (float) (t.y + k * b.y[n] - gy), (float) (t.z + k * (s * ax + c * az) - cz), (float) (k * b.r[n]),
                            (float) -(t.x + k * (c * bx - s * bz) - cx), (float) (t.y + k * b.y[n + 1] - gy), (float) (t.z + k * (s * bx + c * bz) - cz), (float) (k * b.r[n + 1]),
                            art.bark[0] * tr, art.bark[1] * tg, art.bark[2] * tb, b.level == 0);
                }
            if (se.foliage > 0.02f) {
                // nah: jede Blattstelle ein Büschel; dahinter je ZUSAMMEN Stellen desselben Asts in einem (Mitte, Farbe gemittelt,
                // Radius so, dass die Fläche bleibt, Blätter √ZUSAMMEN-mal so lang, also gleiche Blattfläche mit weniger Blättern)
                int gruppe = fein ? 1 : ZUSAMMEN, zahl = 0, ast = -1, blaetter = 0;
                double sx = 0, sy = 0, sz = 0, sr2 = 0, sl = 0, fr = 0, fg = 0, fb = 0;
                List<TreeModel.LeafSpot> stellen = m.leaves;
                for (int li = 0; li <= stellen.size(); li++) {
                    TreeModel.LeafSpot l = li < stellen.size() ? stellen.get(li) : null;
                    if (l != null && !se.evergreen && l.drop < se.drop) continue;   // schon gefallen
                    if (zahl > 0 && (l == null || zahl == gruppe || l.branch != ast)) {
                        double rr = Math.sqrt(sr2), lang = sl / zahl * Math.sqrt(gruppe);
                        int n = (int) Math.max(3, Math.min(255, Math.round(blaetter / (double) gruppe)));
                        q.bueschel((float) -(t.x + k * (c * sx / zahl - s * sz / zahl) - cx), (float) (t.y + k * sy / zahl - gy), (float) (t.z + k * (s * sx / zahl + c * sz / zahl) - cz),
                                (float) (k * rr), n, (float) (k * lang), art.leafAspect, (float) (fr / zahl) * tr, (float) (fg / zahl) * tg, (float) (fb / zahl) * tb);
                        zahl = 0; blaetter = 0; sx = sy = sz = sr2 = sl = fr = fg = fb = 0;
                    }
                    if (l == null) break;
                    double rr = art.cluster * 0.5 * (0.75 + 0.5 * l.tone);
                    sx += l.x + l.dx * rr * 0.5; sy += l.y + l.dy * rr * 0.5; sz += l.z + l.dz * rr * 0.5;
                    sr2 += rr * rr;
                    sl += l.size;
                    se.leafColor(art, l.tone, c3);
                    fr += c3[0]; fg += c3[1]; fb += c3[2];
                    blaetter += (int) Math.max(3, Math.min(255, Math.round(0.55 * Math.PI * rr * rr / (0.7 * l.size * l.size * art.leafAspect))));
                    ast = l.branch;
                    zahl++;
                }
            }
            q.baeume++;
            if (fein) q.baeumeNah++;
        }
    }

    /** Arten des Waldbodens aus {@code understory} (ohne Bartflechten: die hängen an den Ästen). */
    static final List<Kind> BODEN_ARTEN = List.of(Kind.MOSS, Kind.FERN, Kind.DEVILS_CLUB, Kind.BERRY, Kind.DEADWOOD, Kind.LITTER);

    /**
     * Der Unterwuchs im Umkreis r um (cx, cz) aus {@code understory} (dasselbe Package, dieselbe Verteilung nach Licht und
     * Feuchte, Startwert 1958), so wie Gertsch ihn malt: Farne aus einzelnen Fiedern (Blätter), Moospolster und
     * Nadelstreu als liegende Büschel, Teufelskeule als Stiele mit einem Schirm großer Blätter, Beerensträucher als
     * Büschel, Totholz als liegende Glieder mit Moos darauf. Das Kronendach (Blattflächenindex) kommt aus der Dichte des
     * Walds, die Größen je Art und Variante aus {@link PlantMesh}, die Farben wie dort. Jede Pflanze steht auf y = 0.
     */
    static void unterwuchs(WaldQuelle q, ForestCulture cu, double cx, double cz, double r) {
        Understory.Site ort = new Understory.Site() {
            @Override public float ground(double x, double z) { return cu.height != null ? cu.height.sample(0, (float) x, (float) z) : 0; }

            @Override public double lai(double x, double z) {
                if (cu.density == null) return 7;
                double s = 0;
                for (int k = 0; k < cu.density.layers; k++) s += cu.density.sample(k, (float) x, (float) z);
                return 7 * Math.min(1, s);   // alter Küstenregenwald 6 bis 10 [G]
            }

            @Override public double moisture(double x, double z) { return 0.75; }
        };
        Understory u = new Understory(BODEN_ARTEN, ort, 1958);
        u.rangeFactor = r / 50.0 * 1.05;   // jede Art reicht bis zum Umkreis (die Streu reicht am kürzesten, 50 m)
        double[] mitte = {cx, ort.ground(cx, cz), cz};
        u.completeNow(mitte);   // bepflanzt die fehlenden Kacheln (erst nach der Auswahl) …
        u.completeNow(mitte);   // … und wählt sie dann aus
        float[][] groesse = new float[BODEN_ARTEN.size()][Understory.VARIANTS];
        for (int k = 0; k < BODEN_ARTEN.size(); k++)
            for (int v = 0; v < Understory.VARIANTS; v++) groesse[k][v] = PlantMesh.build(BODEN_ARTEN.get(k), v, 1).size;
        int gruppe = 1 << 20;   // Pflanzen schwingen je für sich
        for (int b = 0; b < u.buckets(); b++) {
            Kind kind = u.kindOf(b);
            int v = Understory.variantOf(b), ki = BODEN_ARTEN.indexOf(kind);
            float[] o = u.bucket[b];
            for (int p = 0; p < u.count[b]; p++) {
                int i = p * Understory.STRIDE;
                double wx = o[i], wz = o[i + 2];
                if (Math.hypot(wx - cx, wz - cz) > r) continue;
                float x = (float) -(wx - cx), z = (float) (wz - cz), rot = o[i + 3], ton = 0.85f + 0.3f * o[i + 5];
                float s = groesse[ki][v] * o[i + 4], gy = q.grund(x, z);
                java.util.SplittableRandom zr = new java.util.SplittableRandom(Float.floatToIntBits(o[i]) * 31L + Float.floatToIntBits(o[i + 2]));
                // Richtung rot in der Szene (cos, sin) um die Senkrechte, gespiegelt wie alles andere
                float dx = (float) -Math.cos(rot), dz = (float) Math.sin(rot);
                switch (kind) {
                    case MOSS -> q.bueschel(x, gy + 0.12f * s, z, s / 2, anzahl(s / 2, 0.05f, 0.6f), 0.05f, 0.6f, 0.075f * ton, 0.13f * ton, 0.018f * ton, 8, true);
                    case LITTER -> q.bueschel(x, gy + 0.02f, z, s / 2, anzahl(s / 2, 0.05f, 0.35f), 0.05f, 0.35f, 0.10f * ton, 0.055f * ton, 0.025f * ton, 8, true);
                    case FERN -> farn(q, x, gy, z, s, rot, v % 2 == 0, ton, gruppe++, zr);
                    case BERRY -> {
                        q.bueschel(x, gy + 0.40f * s, z, 0.40f * s, anzahl(0.40f * s, 0.045f, 0.6f), 0.045f, 0.6f, 0.07f * ton, 0.165f * ton, 0.032f * ton, 35, false);
                        q.bueschel(x, gy + 0.78f * s, z, 0.28f * s, anzahl(0.28f * s, 0.045f, 0.6f), 0.045f, 0.6f, 0.07f * ton, 0.165f * ton, 0.032f * ton, 35, false);
                    }
                    case DEVILS_CLUB -> {
                        int stiele = 2 + zr.nextInt(3);
                        for (int c = 0; c < stiele; c++) {
                            double az = zr.nextDouble() * 2 * Math.PI, lean = Math.toRadians(8 + 28 * zr.nextDouble());
                            float h = s * (0.6f + 0.4f * (float) zr.nextDouble());
                            float tx = x + (float) (Math.cos(az) * Math.sin(lean) * h), ty = gy + (float) (Math.cos(lean) * h), tz = z + (float) (Math.sin(az) * Math.sin(lean) * h);
                            q.glied(x, gy, z, 0.025f, tx, ty, tz, 0.015f, 0.16f * ton, 0.12f * ton, 0.07f * ton, false);
                            q.bueschel(tx, ty, tz, 0.45f, 7, 0.32f, 0.95f, 0.055f * ton, 0.16f * ton, 0.025f * ton, 15, false);
                        }
                    }
                    case DEADWOOD -> {
                        float rad = 0.18f + 0.025f * s * 0.9f;
                        q.glied(x, gy + rad * 0.8f, z, rad, x + dx * s, q.grund(x + dx * s, z + dz * s) + rad * 0.8f * 0.75f, z + dz * s, rad * 0.75f, 0.085f * ton, 0.07f * ton, 0.055f * ton, false);
                        for (float a = 0.5f; a < s; a += 1.2f)
                            q.bueschel(x + dx * a, q.grund(x + dx * a, z + dz * a) + rad * 1.6f, z + dz * a, rad * 1.1f, anzahl(rad * 1.1f, 0.05f, 0.6f), 0.05f, 0.6f, 0.05f * ton, 0.11f * ton, 0.02f * ton, 8, true);
                    }
                    default -> { }
                }
                q.pflanzen++;
            }
        }
        teppich(q, cx, cz, r);
    }

    /** Zelle des Geländes im Ausschnitt (m); das Höhengitter der Kultur hat rund 5,9 m. */
    static final double GELAENDE_ZELLE = 1.0;

    /** Abstand des Teppichs aus Moos und Streu (m). */
    static final double TEPPICH = 0.9;

    /**
     * Der Boden zwischen den Pflanzen: ein Teppich liegender Büschel auf einem gewürfelten Gitter (fest in der Szene, damit
     * er beim Neubau nicht springt), Moos in zwei Tönen und orange-braune Nadelstreu als Gegenfarbe [G]. So hat der Boden
     * bis in die Tiefe dasselbe Korn wie bei Gertsch, statt glatter Flächen zwischen den Pflanzen.
     */
    static void teppich(WaldQuelle q, double cx, double cz, double r) {
        int i0 = (int) Math.floor((cx - r) / TEPPICH), i1 = (int) Math.floor((cx + r) / TEPPICH);
        int j0 = (int) Math.floor((cz - r) / TEPPICH), j1 = (int) Math.floor((cz + r) / TEPPICH);
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int h = com.dan.gertsch.Zufall.pcg(i * 73856093 ^ j * 19349663 ^ 0x1958);
                double u = (h >>> 8) * 0x1.0p-24, v = (com.dan.gertsch.Zufall.pcg(h) >>> 8) * 0x1.0p-24, w = (com.dan.gertsch.Zufall.pcg(h + 1) >>> 8) * 0x1.0p-24;
                double wx = (i + 0.1 + 0.8 * u) * TEPPICH, wz = (j + 0.1 + 0.8 * v) * TEPPICH;
                if (Math.hypot(wx - cx, wz - cz) > r) continue;
                float rad = (float) (TEPPICH * (0.45 + 0.2 * v)), ton = (float) (0.8 + 0.4 * u);
                float cr, cg, cb;
                if (w < 0.22) { cr = 0.17f; cg = 0.065f; cb = 0.02f; }          // Nadelstreu, kräftig: bleibt auch im Schatten ein Akzent (W10a)
                else if (w < 0.50) { cr = 0.035f; cg = 0.07f; cb = 0.014f; }    // Moos im Schatten
                else { cr = 0.065f; cg = 0.115f; cb = 0.02f; }                  // Moos
                q.bueschel((float) -(wx - cx), q.grund((float) -(wx - cx), (float) (wz - cz)) + 0.03f, (float) (wz - cz), rad, anzahl(rad, 0.08f, 0.5f), 0.08f, 0.5f, cr * ton, cg * ton, cb * ton, 8, true);
            }
    }

    /** Blätter der Länge l (Breite l·schmal) für einen flachen Büschel mit Radius rad: etwa die halbe Fläche bedeckt. */
    static int anzahl(float rad, float l, float schmal) {
        return (int) Math.max(6, Math.min(255, Math.round(0.55 * Math.PI * rad * rad / (0.7 * l * l * schmal))));
    }

    /**
     * Ein Farn: ein Trichter aus 8 bis 12 bogigen Wedeln der Länge s, jeder Wedel aus Fiederpaaren (Schwertfarn dunkel und
     * schmal, Frauenfarn hell), die zur Spitze kleiner werden. Die Spindel ist eine Bézierkurve vom Fuß über (0,35 s, 0,7 s)
     * zur Spitze (0,85 s, 0,25 s) in (nach außen, nach oben).
     */
    static void farn(WaldQuelle q, float x, float gy, float z, float s, float rot, boolean schwert, float ton, int gruppe, java.util.SplittableRandom zr) {
        float cr = (schwert ? 0.028f : 0.06f) * ton, cg = (schwert ? 0.075f : 0.15f) * ton, cb = (schwert ? 0.018f : 0.028f) * ton;
        int wedel = 8 + zr.nextInt(5), paare = 9;
        for (int f = 0; f < wedel; f++) {
            double az = rot + 2 * Math.PI * f / wedel + zr.nextGaussian() * 0.2;
            float ox = (float) Math.cos(az), oz = (float) Math.sin(az);   // nach außen
            float sx = -oz, sz = ox;                                       // zur Seite
            float L = s * (0.8f + 0.25f * (float) zr.nextDouble());
            for (int k = 0; k < paare; k++) {
                float t = 0.18f + 0.78f * k / (paare - 1), a = (1 - t) * (1 - t), b = 2 * t * (1 - t), c = t * t;
                float rad = b * 0.35f * L + c * 0.85f * L, hoch = gy + 0.04f + b * 0.7f * L + c * 0.25f * L;
                // Tangente der Spindel (nach außen, nach oben)
                float tr = 2 * (1 - t) * 0.35f * L + 2 * t * (0.85f - 0.35f) * L, th = 2 * (1 - t) * 0.7f * L + 2 * t * (0.25f - 0.7f) * L;
                float lang = 0.17f * L * (1 - 0.55f * t), breit = lang * (schwert ? 0.28f : 0.40f);
                for (int seite = -1; seite <= 1; seite += 2) {
                    // Fieder: von der Spindel zur Seite, leicht nach vorn; Fläche etwa waagerecht, mit der Spindel geneigt
                    float ax = sx * seite + ox * 0.35f, az2 = sz * seite + oz * 0.35f;
                    float mx = x + ox * rad + ax * lang * 0.5f, mz = z + oz * rad + az2 * lang * 0.5f;
                    // Normale = Seite × Tangente der Spindel (die Fiedern liegen in der Ebene aus beiden), nach oben gedreht.
                    // Alles im Bezug von gertsch: x, z sind schon gespiegelt, die Richtung az ist ohnehin zufällig.
                    double tx = ox * tr, ty = th, tz = oz * tr;
                    double nx = -sz * ty, ny = sz * tx - sx * tz, nz = sx * ty;
                    double ln = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (ln < 1e-6) { nx = 0; ny = 1; nz = 0; } else if (ny < 0) { nx = -nx; ny = -ny; nz = -nz; }
                    q.blatt(mx, hoch, mz, (float) nx, (float) ny, (float) nz, ax, 0, az2, lang, breit, cr, cg, cb, gruppe);
                }
            }
        }
    }

    /** Eine Quelle in flachen Feldern (eine Million Büschel ohne eine Million Objekte). */
    public static final class WaldQuelle implements Quelle {
        float[] bu = new float[12 * 4096], gli = new float[11 * 4096], bl = new float[15 * 4096];
        int[] blGruppe = new int[4096];
        boolean[] stamm = new boolean[4096];
        int nb, ng, nl;
        public int baeume, baeumeNah, pflanzen;
        float[] bodenAlbedo;
        /** Das Gelände des Ausschnitts (im Bezug von gertsch) und die Höhe seiner tiefsten Stelle in der Szene (m). */
        com.dan.gertsch.Gelaende gel;
        float gRef;

        /**
         * Das Höhengitter der Kultur um (cx, cz) bis r als Gelände: Zellen zu {@link #GELAENDE_ZELLE}, x gespiegelt, die
         * tiefste Stelle auf y = 0 (so liegt das Blattflächenfeld von gertsch ganz über dem Boden). Ohne Höhengitter: die Ebene.
         */
        void gelaende(ForestCulture cu, double cx, double cz, double r) {
            if (cu.height == null) return;
            int n = (int) Math.ceil(2 * r / GELAENDE_ZELLE) + 1;
            float[] h = new float[n * n];
            float min = Float.MAX_VALUE;
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double xl = -r + i * GELAENDE_ZELLE, zl = -r + j * GELAENDE_ZELLE;
                    float v = cu.height.sample(0, (float) (cx - xl), (float) (cz + zl));
                    h[j * n + i] = v;
                    min = Math.min(min, v);
                }
            for (int k = 0; k < h.length; k++) h[k] -= min;
            gRef = min;
            gel = new com.dan.gertsch.Gelaende(-r, -r, GELAENDE_ZELLE, n, n, h);
        }

        /** Höhe des Bodens im Bezug von gertsch bei (x, z) (0 ohne Gelände). */
        float grund(float x, float z) { return gel == null ? 0 : gel.hoehe(x, z); }

        @Override public com.dan.gertsch.Gelaende gelaende() { return gel; }

        void bueschel(float x, float y, float z, float radius, int blaetter, float laenge, float schmal, float r, float g, float b) {
            bueschel(x, y, z, radius, blaetter, laenge, schmal, r, g, b, 20, false);
        }

        void bueschel(float x, float y, float z, float radius, int blaetter, float laenge, float schmal, float r, float g, float b, float neigung, boolean liegt) {
            if (12 * (nb + 1) > bu.length) bu = Arrays.copyOf(bu, bu.length * 2);
            int o = 12 * nb++;
            bu[o] = x; bu[o + 1] = y; bu[o + 2] = z; bu[o + 3] = radius; bu[o + 4] = blaetter; bu[o + 5] = laenge; bu[o + 6] = schmal;
            bu[o + 7] = r; bu[o + 8] = g; bu[o + 9] = b; bu[o + 10] = neigung; bu[o + 11] = liegt ? 1 : 0;
        }

        /** Ein Blatt; die Normale wird nicht normiert (das tut {@link Blatt#richtung}). */
        void blatt(float x, float y, float z, float nx, float ny, float nz, float ax, float ay, float az, float laenge, float breite, float r, float g, float b, int gruppe) {
            if (15 * (nl + 1) > bl.length) { bl = Arrays.copyOf(bl, bl.length * 2); blGruppe = Arrays.copyOf(blGruppe, blGruppe.length * 2); }
            int o = 15 * nl;
            bl[o] = x; bl[o + 1] = y; bl[o + 2] = z; bl[o + 3] = nx; bl[o + 4] = ny; bl[o + 5] = nz; bl[o + 6] = ax; bl[o + 7] = ay; bl[o + 8] = az;
            bl[o + 9] = laenge; bl[o + 10] = breite; bl[o + 11] = r; bl[o + 12] = g; bl[o + 13] = b;
            blGruppe[nl++] = gruppe;
        }

        void glied(float x0, float y0, float z0, float r0, float x1, float y1, float z1, float r1, float r, float g, float b, boolean st) {
            if (11 * (ng + 1) > gli.length) { gli = Arrays.copyOf(gli, gli.length * 2); stamm = Arrays.copyOf(stamm, stamm.length * 2); }
            int o = 11 * ng;
            gli[o] = x0; gli[o + 1] = y0; gli[o + 2] = z0; gli[o + 3] = r0; gli[o + 4] = x1; gli[o + 5] = y1; gli[o + 6] = z1; gli[o + 7] = r1;
            gli[o + 8] = r; gli[o + 9] = g; gli[o + 10] = b;
            stamm[ng++] = st;
        }

        @Override public int blaetter() { return nl; }

        @Override public void blatt(int i, Blatt o) {
            int k = 15 * i;
            o.x = bl[k]; o.y = bl[k + 1]; o.z = bl[k + 2];
            o.richtung(bl[k + 3], bl[k + 4], bl[k + 5], bl[k + 6], bl[k + 7], bl[k + 8]);
            o.laenge = bl[k + 9]; o.breite = bl[k + 10]; o.r = bl[k + 11]; o.g = bl[k + 12]; o.b = bl[k + 13];
            o.gruppe = blGruppe[i];
            o.liegt = false;
        }

        @Override public int bueschelZahl() { return nb; }

        @Override public void bueschel(int i, Bueschel o) {
            int k = 12 * i;
            o.x = bu[k]; o.y = bu[k + 1]; o.z = bu[k + 2]; o.radius = bu[k + 3]; o.blaetter = (int) bu[k + 4]; o.laenge = bu[k + 5]; o.schmal = bu[k + 6];
            o.r = bu[k + 7]; o.g = bu[k + 8]; o.b = bu[k + 9]; o.neigung = bu[k + 10]; o.liegt = bu[k + 11] != 0;
        }

        @Override public int glieder() { return ng; }

        @Override public void glied(int i, Glied o) {
            int k = 11 * i;
            o.x0 = gli[k]; o.y0 = gli[k + 1]; o.z0 = gli[k + 2]; o.r0 = gli[k + 3]; o.x1 = gli[k + 4]; o.y1 = gli[k + 5]; o.z1 = gli[k + 6]; o.r1 = gli[k + 7];
            o.r = gli[k + 8]; o.g = gli[k + 9]; o.b = gli[k + 10]; o.stamm = stamm[i];
        }

        @Override public float[] boden() { return bodenAlbedo; }
    }

    // ------------------------------------------------------------------ je Bild (GL-Faden)

    /** Für Bedienfeld und HUD: „Gertsch: 143 Bäume, 0,79 Mio. Büschel, 4,1 s …“ oder warum nicht gemalt wird. */
    public String status() { return status; }

    /** Ein Ausschnitt ist geladen (für Standbilder: erst dann stimmt das Bild). */
    public boolean fertig() { return jetzt != null && bau == null; }

    /**
     * Vor dem Bild: baut den Ausschnitt um die Kamera, wenn nötig (im Hintergrund), lädt einen fertigen auf die Grafikkarte
     * und richtet die Sonne. Liefert true, wenn der Wald gemalt werden kann; false, solange nichts geladen ist, die Kamera zu
     * hoch steht oder der Wald fehlt (dann zeichnet die Szene wie sonst).
     *
     * @param grundAuge Grund unter dem Auge (m über NAVD88)
     * @param zurSonne  Richtung zur Sonne in der Szene (x Ost, y oben, z Süd)
     */
    public boolean vorbereiten(GL4 gl, ForestGpu f, double[] auge, double[] vorn, double grundAuge, int tag, double[] zurSonne, Quality q) {
        if (!art.gertsch() || fehler) return false;
        if (f == null || !f.ready()) { status = "Gertsch: wartet auf den Wald"; return false; }
        double ueber = auge[1] - grundAuge;
        if (ueber > BIS_HOEHE) {
            status = String.format(Locale.GERMAN, "Gertsch: Kamera %.0f m über Grund, gemalt wird bis %.0f m (Taste F, tiefer fliegen)", ueber, BIS_HOEHE);
            return false;
        }
        try {
            if (gg == null) { gg = new GertschGl(); gg.init(gl); }
            gg.quality = GertschGl.Quality.values()[q.ordinal()];
            if (vorbild != geladenVorbild) { gg.vorlage = vorbild.vorlage.get(); geladenVorbild = vorbild; }
            // die Mitte des Ausschnitts liegt etwas vor dem Auge (hinter der Kamera braucht es nur die Werfer der Schatten)
            double hx = vorn[0], hz = vorn[2], hl = Math.hypot(hx, hz);
            double r = umkreis, cx = auge[0] + (hl > 1e-6 ? hx / hl : 0) * r * 0.35, cz = auge[2] + (hl > 1e-6 ? hz / hl : 0) * r * 0.35;
            TreeStates st = f.culture().states;
            int ver = st == null ? 0 : st.version();
            long jetztMs = System.currentTimeMillis();
            boolean mitUw = unterwuchs;
            boolean neu = jetzt == null || Math.hypot(cx - jetzt.cx, cz - jetzt.cz) > r * 0.3 || tag != jetzt.tag || Math.abs(r - jetzt.umkreis) > 0.5
                    || mitUw != jetzt.unterwuchs || ferne != jetzt.ferne || (ver != jetzt.version && jetztMs - letzterBau > 3000);
            if (neu && bau == null) {
                final double fx = cx, fz = cz, ex = auge[0], ez = auge[2];
                final int max = hoechstensBueschel;
                final double fe = ferne;
                letzterBau = jetztMs;
                status = "Gertsch: der Ausschnitt wird gerechnet …";
                bau = exec.submit(() -> {
                    long t0 = System.nanoTime();
                    WaldQuelle wq = quelle(f, fx, fz, r, fe, ex, ez, max, tag, mitUw);
                    Ausschnitt a = new Ausschnitt(Dickicht.aus(wq), fx, fz, r, tag, ver, wq, mitUw);
                    a.ferne = fe;
                    a.licht.vorbereitenGl(a.d);
                    a.ms = (System.nanoTime() - t0) / 1e6;
                    return a;
                });
            }
            if (bau != null && bau.isDone()) {
                Ausschnitt a = bau.get();
                bau = null;
                sonneRichten(a.licht, zurSonne);
                gg.laden(gl, a.d, a.licht);
                jetzt = a;
                sonneGeladen[0] = Double.NaN;
            }
            if (jetzt == null) return false;
            jetzt.licht.ausloeschung = ausloeschung;
            if (jetzt.baeumeNah == 0) {
                status = String.format(Locale.GERMAN, "Gertsch: kein Wald im Umkreis von %.0f m (Knopf „In den Wald“ im Bedienfeld MALWEISE)%s", jetzt.umkreis,
                        bau != null ? " · der nächste wird gerechnet" : "");
                return false;
            }
            // die Sonne wandert (Uhr, Zeitraffer): Achsen und Schattenkarten neu ab 0,25°
            if (Double.isNaN(sonneGeladen[0]) || zurSonne[0] * sonneGeladen[0] + zurSonne[1] * sonneGeladen[1] + zurSonne[2] * sonneGeladen[2] < Math.cos(Math.toRadians(0.25))) {
                sonneRichten(jetzt.licht, zurSonne);
                gg.sonneGeaendert();
                System.arraycopy(zurSonne, 0, sonneGeladen, 0, 3);
            }
            status = String.format(Locale.GERMAN, "Gertsch (%s): %d Bäume in %.0f m, %d dahinter bis %.0f m, %.2f Mio. Büschel, %d Glieder%s, gebaut in %.1f s%s",
                    gg.vorlage.name, jetzt.baeumeNah, jetzt.umkreis, jetzt.baeume - jetzt.baeumeNah, jetzt.ferne * jetzt.umkreis, jetzt.bueschel / 1e6, jetzt.glieder,
                    (jetzt.unterwuchs ? String.format(Locale.GERMAN, ", Unterwuchs %d Pflanzen (%d Fiedern)", jetzt.pflanzen, jetzt.blaetter) : "") + String.format(Locale.GERMAN, ", Gelände %.0f m Höhenunterschied", jetzt.relief),
                    jetzt.ms / 1000, bau != null ? " · der nächste wird gerechnet" : "");
            return true;
        } catch (Exception | Error e) {
            fehler = true;
            status = "Gertsch: Fehler " + e;
            System.err.println("LituyaBay1958: Malweise: " + e);
            return false;
        }
    }

    /** Sonne der Szene in den Bezug von gertsch (x gespiegelt); unter dem Horizont bleibt nur der Himmel. */
    static void sonneRichten(com.dan.gertsch.Licht l, double[] s) {
        double h = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, s[1]))));
        l.sonneHoehe = Math.max(1, h);
        l.sonneRichtung = Math.toDegrees(Math.atan2(-s[0], s[2]));
        double u = Math.max(0, Math.min(1, (h + 6) / 7.0));
        l.sonneStaerke = u * u * (3 - 2 * u);
        l.richten();
    }

    /**
     * Malt den Wald (nach {@link #vorbereiten} mit true) in den Rahmen ziel, w × h. Kamera der App in den Bezug von gertsch:
     * Auge über dem Grund, Gier und Neigung aus der Blickrichtung (x gespiegelt), waagerechter Bildwinkel aus dem senkrechten.
     */
    public void zeichnen(GL4 gl, double[] auge, double[] vorn, double grundAuge, double fovyDeg, double windMs, double windVonGrad, int ziel, int w, int h, double dt) {
        Ausschnitt a = jetzt;
        if (a == null || gg == null) return;
        zeit += Math.max(0, dt);
        double asp = Math.round(w / (double) Math.max(1, h) * 100) / 100.0;
        gg.seitenverhaeltnis = asp;
        Kamera k = new Kamera();
        double xl = -(auge[0] - a.cx), zl = auge[2] - a.cz;
        k.auge = new double[]{xl, Math.max(a.d.bodenHoehe(xl, zl) + 0.3, auge[1] - a.gRef), zl};
        k.gier = Math.toDegrees(Math.atan2(-vorn[0], vorn[2]));
        k.neigung = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, vorn[1]))));
        k.bildwinkel = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(fovyDeg) / 2) * asp));
        // Wind: die App sagt, woher (Kompass); gertsch auch, aber 0 = von +z und x gespiegelt
        gg.wind.staerke = windMs;
        gg.wind.richtung = windVonGrad + 180;
        try {
            if (art == Art.GERTSCH_STICH) {
                if (sg == null) { sg = new StichGl(); sg.init(gl); }
                if (letzteArt != art) sg.neu();
                sg.stich.tonwerte = Stich.Tonwerte.VORLAGE;
                gg.zeichnen(gl, k, zeit, -1, 0, 0, 0, 0);
                sg.drucken(gl, gg.helligkeitTextur(), gg.bildBreite(), gg.bildHoehe(), StichGl.Eingang.HELLIGKEIT, Druck.BREITE, zeit);
                sg.zeigen(gl, ziel, 0, 0, w, h, 0, 0.5, 0.5, RAND);
            } else gg.zeichnen(gl, k, zeit, ziel, 0, 0, w, h);
            letzteArt = art;
        } catch (RuntimeException e) {
            fehler = true;
            status = "Gertsch: Fehler " + e;
            System.err.println("LituyaBay1958: Malweise: " + e);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, ziel);
    }

    /**
     * Holzschnitt der ganzen Szene: das fertige Bild im Rahmen ziel (w × h) wird in eine Textur kopiert, gedruckt und an
     * seine Stelle gezeigt. Vor Netzblick, Archiv und HUD.
     */
    public void szeneDrucken(GL4 gl, int ziel, int w, int h, double dt) {
        if (art != Art.STICH || fehler) return;
        zeit += Math.max(0, dt);
        try {
            if (sg == null) { sg = new StichGl(); sg.init(gl); }
            if (letzteArt != art) sg.neu();
            letzteArt = art;
            sg.stich.tonwerte = tonSzene;
            if (quelleTex == 0 || w != quelleW || h != quelleH) {
                if (quelleTex == 0) { int[] t = new int[1]; gl.glGenTextures(1, t, 0); quelleTex = t[0]; }
                gl.glBindTexture(GL.GL_TEXTURE_2D, quelleTex);
                gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
                quelleW = w;
                quelleH = h;
            }
            gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, ziel);
            if (ziel == 0) gl.glReadBuffer(GL.GL_BACK);
            gl.glBindTexture(GL.GL_TEXTURE_2D, quelleTex);
            gl.glCopyTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            sg.drucken(gl, quelleTex, w, h, StichGl.Eingang.FARBE, Druck.BREITE, zeit);
            sg.zeigen(gl, ziel, 0, 0, w, h, 0, 0.5, 0.5, RAND);
            status = String.format(Locale.GERMAN, "Holzschnitt der Szene: %d × %d gedruckt, %.1f ms, Ton %s", sg.druckBreite(), sg.druckHoehe(), sg.zeit(),
                    tonSzene == Stich.Tonwerte.LINEAR ? "wie die Szene" : "nach der Vorlage");
        } catch (RuntimeException e) {
            fehler = true;
            status = "Holzschnitt: Fehler " + e;
            System.err.println("LituyaBay1958: Holzschnitt: " + e);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, ziel);
    }

    /** Nach einem Fehler wieder versuchen (Bedienfeld: neue Wahl). */
    public void zuruecksetzen() { fehler = false; if (art == Art.AUS) status = "aus"; }

    /** Grenzen der Tonbänder in log2 Y (für Prüfung und volles HUD); leer ohne Bild. */
    public double[] grenzen() { return gg == null ? new double[0] : gg.grenzen(); }

    /** Zeit der Grafikkarte je Durchgang (ms) des Gertsch-Bilds, für das HUD; null ohne Bild. */
    public double[] zeiten() { return gg == null ? null : gg.zeiten(); }

    public void dispose(GL4 gl) {
        exec.shutdownNow();
        if (gg != null) gg.dispose(gl);
        if (sg != null) sg.dispose(gl);
        if (quelleTex != 0) gl.glDeleteTextures(1, new int[]{quelleTex}, 0);
        gg = null;
        sg = null;
        quelleTex = 0;
    }
}
