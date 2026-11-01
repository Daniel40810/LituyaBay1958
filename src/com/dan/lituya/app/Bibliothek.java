package com.dan.lituya.app;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Die Packages aus {@code lib/Renderer.jar} (Bibliothek statt Kopie, aus Timmendorf). Findet das Jar über eine Klasse,
 * die darin liegen muss ({@code com.dan.sky.Sky}), und zählt die Packages unter {@code com/dan/}. Für HUD, Bedienfeld und
 * Prüfung.
 */
public final class Bibliothek {
    private Bibliothek() { }

    /**
     * Packages, die LituyaBay1958 nach dem Werkbuch (Abschnitte 6 und 10.2) aus Renderer.jar braucht: genutzt oder
     * erweitert ({@code wave}, {@code fracture}, {@code ice}, {@code forest}, {@code tree}, {@code ocean}, {@code sky},
     * {@code cloud}, {@code fog}, {@code soundscape}, {@code kino}, {@code route}).
     */
    public static final String[] GEBRAUCHT = {"wave", "fracture", "ice", "forest", "tree", "ocean", "sky", "cloud", "fog", "soundscape", "kino", "route",
            "surf", "spray", "buoyancy", "cliff", "tide", "wind", "weather", "rain", "flock", "seal", "figure", "smoke", "store", "ground"};

    /**
     * Packages, die für LituyaBay1958 im Projekt Renderer neu entstehen (Werkbuch 5), mit der Phase, in der sie kommen
     * (Abschnitt 14); Zugaben ({@code aurora}, {@code bear}) zuletzt (F8 A). Seit Phase 6 als Text, weil {@code scree} in
     * Phase 6a kommt (Renderer, Tabelle 14), nach {@code understory} in Phase 6.
     */
    public static final String[] NEU = {"clipmap", "surface", "understory", "scree", "netview", "quake", "landslide", "impact", "dustcloud", "debris",
            "succession", "vessel", "aurora", "bear"};
    public static final String[] NEU_PHASE = {"4", "5", "6", "6a", "7", "8", "9", "10", "12", "12", "14", "15", "18", "18"};

    /** Reihenfolge einer Phase: „6“ = 6, „6a“ = 6,5. */
    public static double rang(String phase) {
        String p = phase.trim().toLowerCase(Locale.ROOT);
        return p.endsWith("a") ? Double.parseDouble(p.substring(0, p.length() - 1)) + 0.5 : Double.parseDouble(p);
    }

    private static volatile List<String> cache;
    private static volatile String jar = "";

    /** Die Packages in Renderer.jar (Namen unter com.dan), sortiert; leer, wenn das Jar fehlt. */
    public static List<String> pakete() {
        List<String> c = cache;
        if (c != null) return c;
        TreeSet<String> s = new TreeSet<>();
        try {
            URL u = Class.forName("com.dan.sky.Sky").getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            jar = f.getName();
            if (f.isFile()) {
                try (ZipFile z = new ZipFile(f)) {
                    for (ZipEntry e : java.util.Collections.list(z.entries())) {
                        String n = e.getName();
                        if (!n.startsWith("com/dan/") || !n.endsWith(".class")) continue;
                        String[] p = n.split("/");
                        if (p.length >= 4) s.add(p[2]);
                    }
                }
            } else if (f.isDirectory()) {
                File[] d = new File(f, "com/dan").listFiles(File::isDirectory);
                if (d != null) for (File x : d) s.add(x.getName());
            }
        } catch (Exception | LinkageError e) {
            jar = "nicht gefunden (" + e.getClass().getSimpleName() + ")";
        }
        cache = List.copyOf(s);
        return cache;
    }

    /** Was das Werkbuch aus Renderer.jar schon jetzt braucht, dort aber fehlt (nur die gebrauchten). */
    public static List<String> fehlend() {
        List<String> p = pakete(), f = new ArrayList<>();
        for (String g : GEBRAUCHT) if (!p.contains(g)) f.add(g);
        return f;
    }

    /**
     * Die neuen Packages, deren Phase schon erreicht ist, die aber im Jar fehlen. Seit Phase 7 ohne die Packages einer
     * a-Phase ({@code scree} in 6a): die a-Phasen gehören der App und werden nach den Renderer-Phasen nachgeholt
     * ({@link #nachzuholen}).
     */
    public static List<String> neuFaellig(String phase) {
        List<String> p = pakete(), f = new ArrayList<>();
        for (int i = 0; i < NEU.length; i++) if (!aPhase(NEU_PHASE[i]) && rang(NEU_PHASE[i]) <= rang(phase) && !p.contains(NEU[i])) f.add(NEU[i]);
        return f;
    }

    /** Packages einer a-Phase vor {@code phase}, die noch fehlen (Hinweis, kein Fehler). */
    public static List<String> nachzuholen(String phase) {
        List<String> p = pakete(), f = new ArrayList<>();
        for (int i = 0; i < NEU.length; i++) if (aPhase(NEU_PHASE[i]) && rang(NEU_PHASE[i]) <= rang(phase) && !p.contains(NEU[i])) f.add(NEU[i] + " (Phase " + NEU_PHASE[i] + ")");
        return f;
    }

    static boolean aPhase(String phase) { return phase.trim().toLowerCase(Locale.ROOT).endsWith("a"); }

    /** „50 Packages in Renderer.jar, alle 26 gebrauchten da; neu 0 von 14“ */
    public static String kurz() {
        List<String> p = pakete(), f = fehlend();
        if (p.isEmpty()) return "!nicht gefunden " + jar;
        int n = 0;
        for (String x : NEU) if (p.contains(x)) n++;
        return String.format(Locale.GERMAN, "%d Packages in %s, %s; neu %d von %d", p.size(), jar,
                f.isEmpty() ? "alle " + GEBRAUCHT.length + " gebrauchten da" : "es fehlen " + String.join(", ", f), n, NEU.length);
    }

    /** „clipmap Phase 4 · surface Phase 5 · …“ oder „✓“ für die, die im Jar liegen. */
    public static String neuStand() {
        List<String> p = pakete();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NEU.length; i++)
            sb.append(sb.length() > 0 ? " · " : "").append(NEU[i]).append(p.contains(NEU[i]) ? " ✓" : " (Phase " + NEU_PHASE[i] + ")");
        return sb.toString();
    }
}
