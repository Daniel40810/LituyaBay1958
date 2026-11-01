import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Phase 6 (Werkbuch 6.4, Paketvertrag 14): Sind die Wälder der anderen Szenen (Haithabu, Timmendorf, Brocken, Glacier,
 * Geyser …) nach dem Umbau von {@code com.dan.forest} und {@code com.dan.tree} unverändert? Vergleicht zwei Renderer.jar
 * (alt, neu): erstens Byte für Byte alle Klassen und Shader der beiden Packages; zweitens für jede Art, die es im alten
 * Jar schon gab, die Netze aller vier Stufen (Lage, Normale, Farbe, Dreiecke) aus dem gleichen Startwert. Aufruf ohne Ant:
 * {@code java tools/WaldVergleich.java lib/Renderer_phase5.jar lib/Renderer.jar}.
 */
public final class WaldVergleich {
    public static void main(String[] a) throws Exception {
        File alt = new File(a.length > 0 ? a[0] : "lib/Renderer_phase5.jar"), neu = new File(a.length > 1 ? a[1] : "lib/Renderer.jar");
        if (!alt.isFile() || !neu.isFile()) {
            System.out.println("waldvergleich: " + (!alt.isFile() ? alt : neu) + " fehlt (vor dem Kopieren des neuen Jars das alte als lib/Renderer_phase5.jar sichern)");
            System.exit(1);
        }
        System.out.println("Vergleich " + alt.getName() + " (alt) gegen " + neu.getName() + " (neu)");
        TreeMap<String, String> diff = new TreeMap<>();
        try (ZipFile za = new ZipFile(alt); ZipFile zn = new ZipFile(neu)) {
            List<String> names = new ArrayList<>();
            for (ZipEntry e : java.util.Collections.list(za.entries())) names.add(e.getName());
            for (ZipEntry e : java.util.Collections.list(zn.entries())) if (za.getEntry(e.getName()) == null) names.add(e.getName());
            for (String n : names) {
                if (!(n.startsWith("com/dan/forest/") || n.startsWith("com/dan/tree/")) || n.endsWith("/")) continue;
                ZipEntry ea = za.getEntry(n), en = zn.getEntry(n);
                if (ea == null) diff.put(n, "neu");
                else if (en == null) diff.put(n, "fehlt im neuen Jar");
                else if (!Arrays.equals(za.getInputStream(ea).readAllBytes(), zn.getInputStream(en).readAllBytes())) diff.put(n, "geändert");
            }
        }
        int fehler = 0;
        System.out.println();
        System.out.println("Dateien in com/dan/forest und com/dan/tree, die sich unterscheiden:");
        for (var e : diff.entrySet()) {
            boolean erwartet = e.getValue().equals("neu") || e.getKey().matches("com/dan/forest/(Species|TreeGenerator|demo/ForestCheck|README\\.md)(\\$.*)?(\\.class|\\.md)?");
            if (!erwartet) fehler++;
            System.out.printf("  %-60s %-20s %s%n", e.getKey(), e.getValue(), erwartet ? "" : "UNERWARTET");
        }
        if (diff.isEmpty()) System.out.println("  keine");
        System.out.println("  (erwartet: neue Dateien sowie Species, TreeGenerator, ForestCheck und README; ForestGl, ForestField, TreeMesh, alle Shader und com.dan.tree unverändert)");
        // Netze der alten Arten aus beiden Jars
        System.out.println();
        try (URLClassLoader la = new URLClassLoader(new URL[]{alt.toURI().toURL()}, ClassLoader.getPlatformClassLoader());
             URLClassLoader ln = new URLClassLoader(new URL[]{neu.toURI().toURL()}, ClassLoader.getPlatformClassLoader())) {
            List<?> altArten = (List<?>) la.loadClass("com.dan.forest.Species").getField("ALL").get(null);
            List<?> neuArten = (List<?>) ln.loadClass("com.dan.forest.Species").getField("ALL").get(null);
            int gleich = 0;
            for (int i = 0; i < altArten.size(); i++) {
                long[] ha = hash(la, (Supplier<?>) altArten.get(i)), hn = hash(ln, (Supplier<?>) neuArten.get(i));
                String name = ((Supplier<?>) altArten.get(i)).get().toString();
                boolean ok = Arrays.equals(ha, hn);
                if (ok) gleich++; else fehler++;
                System.out.printf("  %-48s %s%n", name, ok ? "gleich (" + ha[0] + " Dreiecke in Stufe 0)" : "VERSCHIEDEN");
            }
            System.out.printf("%nNetze: %d von %d alten Arten gleich (Stufe 0 bis 3, Lage, Normale, Farbe, Dreiecke, Startwert 42); neu dazu %d Arten%n",
                    gleich, altArten.size(), neuArten.size() - altArten.size());
        }
        System.out.println(fehler == 0 ? "Alles in Ordnung: die Wälder der anderen Szenen sind unverändert." : fehler + " Abweichungen.");
        System.exit(fehler == 0 ? 0 : 1);
    }

    /** Je Stufe: Dreiecke und Prüfsumme über Lage, Normale, Farbe, Dreiecke. */
    static long[] hash(ClassLoader l, Supplier<?> art) throws Exception {
        Object sp = art.get();
        Class<?> gen = l.loadClass("com.dan.forest.TreeGenerator"), mesh = l.loadClass("com.dan.forest.TreeMesh"), model = l.loadClass("com.dan.forest.TreeModel");
        Object m = gen.getMethod("grow", l.loadClass("com.dan.forest.Species"), long.class, float.class).invoke(null, sp, 42L, 1f);
        Method build = mesh.getMethod("build", model, int.class);
        long[] out = new long[8];
        for (int lod = 0; lod < 4; lod++) {
            Object t = build.invoke(null, m, lod);
            int nv = mesh.getField("nv").getInt(t), nt = mesh.getField("nt").getInt(t);
            float[] pos = (float[]) mesh.getField("pos").get(t), nrm = (float[]) mesh.getField("nrm").get(t), col = (float[]) mesh.getField("col").get(t);
            int[] tri = (int[]) mesh.getField("tri").get(t);
            long h = 1125899906842597L;
            for (int i = 0; i < 3 * nv; i++) h = 31 * h + Float.floatToIntBits(pos[i]) * 7L + Float.floatToIntBits(nrm[i]) * 13L + Float.floatToIntBits(col[i]);
            for (int i = 0; i < 3 * nt; i++) h = 31 * h + tri[i];
            out[2 * lod] = nt;
            out[2 * lod + 1] = h;
        }
        return out;
    }
}
