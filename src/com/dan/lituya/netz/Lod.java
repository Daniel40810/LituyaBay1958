package com.dan.lituya.netz;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Die Java-API des Oracle Network Data Model ({@code oracle.spatial.network.lod} aus {@code lib/sdonm.jar}) mit eigenem
 * Klassenlader (F26 A, wie Timmendorf): {@code sdonm.jar} von 2021 bindet in seinen Abfragen Platzhalter mit Namen und
 * mit Nummern zugleich, der JDBC-Treiber in {@code lib/ojdbc11.jar} weist das ab (ORA-17090, Befund Timmendorf Phase 4).
 * Darum rechnet LOD mit einem Treiber 21 aus {@code ORACLE_HOME\jdbc\lib\ojdbc11.jar}, der in {@code lib/lod/} liegt,
 * in einem Klassenlader, der nur ihn und die {@code sdo*.jar} kennt.
 * <p>
 * Phase 1 legt den Weg an: {@link #stand()} sagt, was da ist, {@link #probe()} lädt die Klassen von LOD und den Treiber
 * im eigenen Lader (ohne Datenbank), {@link #main} ({@code ant lod}) holt den Treiber aus {@code ORACLE_HOME}. Die Netze
 * ({@code LB_NET_…}) und die Funktionsprobe 2.8 kommen mit Phase 3. Nur dieses Package und die Werkzeuge kennen Oracle
 * (Paketvertrag 13).
 */
public final class Lod {
    private Lod() { }

    /** Die Jars, die LOD neben dem Treiber braucht (in {@code lib/}). */
    public static final String[] SDO = {"sdonm.jar", "sdoutl.jar", "sdoapi.jar"};
    /** Die Klassen, die {@link #probe()} im eigenen Lader lädt. */
    public static final String[] KLASSEN = {"oracle.spatial.network.lod.LODNetworkManager", "oracle.spatial.network.lod.NetworkAnalyst",
            "oracle.spatial.network.lod.PointOnNet", "oracle.spatial.network.NetworkMetadata", "oracle.jdbc.OracleDriver"};

    /** Der Ordner {@code lib} des Projekts (-Dlituya.lib, sonst ./lib). */
    public static File lib() {
        String d = System.getProperty("lituya.lib");
        return new File(d != null ? d : "lib");
    }

    /** Der Ordner für den Treiber 21. */
    public static File lodDir() { return new File(lib(), "lod"); }

    /** Der Treiber in {@code lib/lod/} oder null. */
    public static File treiber() {
        File[] f = lodDir().listFiles((d, n) -> n.startsWith("ojdbc") && n.endsWith(".jar"));
        if (f == null || f.length == 0) return null;
        java.util.Arrays.sort(f);
        return f[0];
    }

    /** Fehlende Jars für LOD (sdonm.jar, sdoutl.jar, sdoapi.jar in lib/, der Treiber in lib/lod/). */
    public static List<String> fehlend() {
        List<String> l = new ArrayList<>();
        for (String s : SDO) if (!new File(lib(), s).isFile()) l.add("lib/" + s);
        if (treiber() == null) l.add("lib/lod/ojdbc11.jar");
        return l;
    }

    /** Die Version eines Jars aus seinem Manifest (Implementation-Version), oder „?“. */
    public static String version(File jar) {
        try (JarFile j = new JarFile(jar)) {
            var m = j.getManifest();
            if (m == null) return "?";
            String v = m.getMainAttributes().getValue("Implementation-Version");
            return v != null ? v : "?";
        } catch (IOException e) {
            return "?";
        }
    }

    /** Ein eigener Klassenlader mit dem Treiber aus lib/lod und den sdo*.jar, über dem Plattform-Lader (nicht der App). */
    public static URLClassLoader lader() throws IOException {
        File t = treiber();
        if (t == null) throw new IOException("kein Treiber in " + lodDir().getPath() + " (ant lod holt ihn aus ORACLE_HOME\\jdbc\\lib)");
        List<URL> u = new ArrayList<>();
        u.add(t.toURI().toURL());
        for (String s : SDO) {
            File f = new File(lib(), s);
            if (!f.isFile()) throw new IOException(f.getPath() + " fehlt");
            u.add(f.toURI().toURL());
        }
        return new URLClassLoader(u.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    /** Ergebnis der Probe: ob alles geladen wurde, und was. */
    public record Probe(boolean ok, String text) { }

    /** Lädt die Klassen von LOD und den Treiber im eigenen Lader (ohne Datenbank). */
    public static Probe probe() {
        try (URLClassLoader cl = lader()) {
            for (String k : KLASSEN) Class.forName(k, false, cl);
            java.sql.Driver d = (java.sql.Driver) Class.forName("oracle.jdbc.OracleDriver", true, cl).getDeclaredConstructor().newInstance();
            return new Probe(true, String.format("LOD-Klassen aus sdonm.jar und Treiber %d.%d (%s, %s) im eigenen Lader geladen",
                    d.getMajorVersion(), d.getMinorVersion(), treiber().getName(), version(treiber())));
        } catch (Exception | LinkageError e) {
            return new Probe(false, "LOD nicht bereit: " + e.getMessage());
        }
    }

    /** „sdonm.jar da · Treiber 21.x in lib/lod“ oder was fehlt (für Startbild, Bedienfeld, HUD). */
    public static String stand() {
        List<String> f = fehlend();
        if (f.isEmpty()) return "sdonm.jar da · Treiber " + version(treiber()) + " in lib/lod (" + treiber().getName() + ")";
        return "es fehlt " + String.join(", ", f) + (f.contains("lib/lod/ojdbc11.jar") ? " (ant lod holt ihn aus ORACLE_HOME\\jdbc\\lib)" : "");
    }

    /**
     * Wo der Treiber 21 liegen kann (Befund Phase 1: ORACLE_HOME ist auf dem Rechner nicht gesetzt). Gesucht wird in dieser
     * Reihenfolge: %ORACLE_HOME%, die Oracle-Homes aus der Registry ({@code HKLM\SOFTWARE\Oracle}), jedes Verzeichnis im
     * PATH mit {@code sqlplus.exe} oder {@code oci.dll} (dessen Elternordner ist das Home), dann die Ordner unter
     * {@code C:\app\*\product\*\*} und {@code *db_home*} in den Wurzeln von C: bis F:.
     */
    static List<File> kandidaten() {
        java.util.LinkedHashSet<File> homes = new java.util.LinkedHashSet<>();
        String oh = System.getenv("ORACLE_HOME");
        if (oh != null && !oh.isBlank()) homes.add(new File(oh));
        homes.addAll(ausRegistry());
        String path = System.getenv("PATH");
        if (path != null)
            for (String d : path.split(File.pathSeparator)) {
                if (d.isBlank()) continue;
                File f = new File(d.trim().replace("\"", ""));
                if (new File(f, "sqlplus.exe").isFile() || new File(f, "oci.dll").isFile() || new File(f, "sqlplus").isFile())
                    if (f.getParentFile() != null) homes.add(f.getParentFile());
            }
        for (String lw : new String[]{"C:", "D:", "E:", "F:"}) {
            File[] user = new File(lw + "/app").listFiles(File::isDirectory);
            if (user != null)
                for (File u : user) {
                    File[] vers = new File(u, "product").listFiles(File::isDirectory);
                    if (vers != null)
                        for (File v : vers) {
                            File[] h = v.listFiles(File::isDirectory);
                            if (h != null) homes.addAll(java.util.Arrays.asList(h));
                        }
                }
            File[] wurzel = new File(lw + "/").listFiles(f -> f.isDirectory() && f.getName().toLowerCase(java.util.Locale.ROOT).contains("db_home"));
            if (wurzel != null) homes.addAll(java.util.Arrays.asList(wurzel));
        }
        List<File> l = new ArrayList<>();
        for (File h : homes) l.add(new File(h, "jdbc/lib/ojdbc11.jar"));
        return l;
    }

    /** Die ORACLE_HOME-Einträge unter HKLM\SOFTWARE\Oracle (nur unter Windows; sonst leer). */
    static List<File> ausRegistry() {
        List<File> l = new ArrayList<>();
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return l;
        try {
            Process p = new ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Oracle", "/s", "/v", "ORACLE_HOME").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset());
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            for (String z : out.split("\\R")) {
                z = z.strip();
                int k = z.indexOf("REG_SZ");
                if (z.startsWith("ORACLE_HOME") && k > 0) l.add(new File(z.substring(k + 6).strip()));
            }
        } catch (Exception e) { /* ohne Registry */ }
        return l;
    }

    /** {@code ant lod}: den Treiber aus ORACLE_HOME nach lib/lod kopieren (überschreibt nichts) und die Probe zeigen. */
    public static void main(String[] a) throws IOException {
        File t = treiber();
        if (t == null) {
            File quelle = a.length > 0 && !a[0].isBlank() ? new File(a[0]) : null;
            if (quelle == null) for (File k : kandidaten()) if (k.isFile()) { quelle = k; break; }
            if (quelle == null || !quelle.isFile()) {
                System.out.println("Kein ojdbc11.jar gefunden. Gesucht in:");
                for (File k : kandidaten()) System.out.println("  " + k.getPath());
                System.out.println("Bitte den Pfad angeben: ant lod -Dtreiber=<ORACLE_HOME>\\jdbc\\lib\\ojdbc11.jar (den Ordner zeigt in SQL*Plus: host echo %ORACLE_HOME%)");
                System.exit(1);
            }
            lodDir().mkdirs();
            File ziel = new File(lodDir(), "ojdbc11.jar");
            Files.copy(quelle.toPath(), ziel.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            System.out.println("Treiber kopiert: " + quelle + " → " + ziel.getPath() + " (" + version(ziel) + ")");
        } else System.out.println("Treiber schon da: " + t.getPath() + " (" + version(t) + ")");
        System.out.println(stand());
        Probe p = probe();
        System.out.println((p.ok() ? "ok: " : "FEHLER: ") + p.text());
        System.exit(p.ok() ? 0 : 1);
    }
}
