import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prüfung von {@code lib/Renderer.jar} (Ant-Target {@code check-packages}, aus Sylt und Timmendorf). Die
 * Element-Packages liegen nicht im Projekt LituyaBay1958: ihre Quelle ist das Projekt Renderer (Werkbuch 4.1,
 * Paketvertrag Punkt 11), dort prüft {@code tools/PaketPruefung.java} jedes Package einzeln. Hier wird nur geprüft, ob
 * das Jar alles hat, was LituyaBay1958 braucht: jedes gebrauchte Package mit Klassen und README, Klassen für Java 21
 * (Version 65) oder älter, und von den 14 neuen Packages (Werkbuch 5) die, deren Phase erreicht ist, mit Kern,
 * Darstellung und Prüfklasse. Aufruf ohne Ant: {@code java tools/JarPruefung.java lib/Renderer.jar [phase]}.
 */
public final class JarPruefung {
    /** Was LituyaBay1958 aus Renderer.jar braucht (wie {@code app.Bibliothek.GEBRAUCHT}). */
    static final String[] GEBRAUCHT = {"wave", "fracture", "ice", "forest", "tree", "ocean", "sky", "cloud", "fog", "soundscape", "kino", "route",
            "surf", "spray", "buoyancy", "cliff", "tide", "wind", "weather", "rain", "flock", "seal", "figure", "smoke", "store", "ground"};
    /** Die neuen Packages mit ihrer Phase und der Prüfklasse (wie {@code app.Bibliothek.NEU}). */
    static final String[] NEU = {"clipmap", "surface", "understory", "scree", "netview", "quake", "landslide", "impact", "dustcloud", "debris",
            "succession", "vessel", "aurora", "bear"};
    static final int[] NEU_PHASE = {4, 5, 6, 6, 7, 8, 9, 10, 12, 12, 14, 15, 18, 18};

    public static void main(String[] a) throws Exception {
        File jar = new File(a.length > 0 ? a[0] : "lib/Renderer.jar");
        int phase = a.length > 1 ? Integer.parseInt(a[1]) : 1;
        if (!jar.isFile()) { System.err.println("check-packages: " + jar + " fehlt"); System.exit(1); }
        Map<String, int[]> p = new TreeMap<>();   // Klassen, README, höchste Version
        try (ZipFile z = new ZipFile(jar)) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                String n = e.getName();
                if (!n.startsWith("com/dan/") || e.isDirectory()) continue;
                String[] t = n.split("/");
                if (t.length < 4) continue;
                int[] v = p.computeIfAbsent(t[2], k -> new int[3]);
                if (n.endsWith(".class")) {
                    v[0]++;
                    try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
                        d.readInt();
                        d.readUnsignedShort();
                        v[2] = Math.max(v[2], d.readUnsignedShort());
                    }
                } else if (t.length == 4 && t[3].equals("README.md")) v[1] = 1;
            }
            int fehler = 0;
            System.out.println("check-packages: " + jar.getName() + " mit " + p.size() + " Packages (Quelle: Projekt Renderer)");
            System.out.println("  Package        Klassen  README  Java");
            for (String g : GEBRAUCHT) {
                int[] v = p.get(g);
                boolean ok = v != null && v[0] > 0 && v[1] == 1 && v[2] <= 65;
                if (!ok) fehler++;
                System.out.printf("  %-14s %-8s %-7s %-5s %s%n", g, v == null ? "–" : v[0], v == null || v[1] == 0 ? "fehlt" : "ok", v == null ? "–" : v[2] - 44, ok ? "" : "FEHLER");
            }
            int neu = 0;
            for (int i = 0; i < NEU.length; i++) {
                if (NEU_PHASE[i] > phase) continue;
                neu++;
                String n = NEU[i], k = Character.toUpperCase(n.charAt(0)) + n.substring(1);
                for (String e : new String[]{"README.md", "demo/" + k + "Check.class"})
                    if (z.getEntry("com/dan/" + n + "/" + e) == null) { fehler++; System.out.println("  FEHLER: com/dan/" + n + "/" + e + " fehlt (fällig seit Phase " + NEU_PHASE[i] + ")"); }
            }
            if (fehler > 0) { System.out.println("check-packages: " + fehler + " Fehler – Renderer neu bauen (Clean and Build) und dist/Renderer.jar nach lib/ kopieren"); System.exit(1); }
            System.out.println("check-packages: alle " + GEBRAUCHT.length + " gebrauchten Packages in " + jar.getName() + "; von den 14 neuen sind bis Phase "
                    + phase + " " + neu + " fällig" + (neu == 0 ? " (das erste, clipmap, mit Phase 4)" : ", alle mit README und Prüfklasse") + ".");
        }
    }
}
