package com.dan.lituya.tools;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Werkbuch Welle W9c Runde 2b („Speicher“): wie viel Speicher die Aufbereitung braucht und wie viel das System noch gibt.
 * <p>
 * Anlass: Am 7. November 2026 um 15:55 brach die Aufbereitung in der Stufe Kern ab (hs_err_pid16552.log): Der Heap war
 * auf 23 GB gewachsen (Grenze 24 GB), lebendig waren nach jeder Sammlung nur 8,6 bis 9,8 GB, und Windows konnte keine
 * 16 MB mehr zusagen (Zusagegrenze 72,1 GB, frei 10 MB; die anderen Programme hielten 48,5 GB). Ein Absturz dieser Art
 * kommt nicht vom Heap allein, sondern von der Summe aller Programme gegen die Zusagegrenze (Arbeitsspeicher und
 * Auslagerungsdatei). Darum protokolliert die Aufbereitung jetzt je Schritt:
 * <ul>
 * <li>den größten Heap nach einer Sammlung (das, was wirklich gebraucht wurde, nach oben gerundet: nach einer kleinen
 * Sammlung zählt Altes mit, das erst die nächste große findet),</li>
 * <li>den zugesagten Heap (was die JVM von Windows hält),</li>
 * <li>was Windows noch zusagen kann ({@code com.sun.management.OperatingSystemMXBean#getFreeSwapSpaceSize}: unter
 * Windows ullAvailPageFile aus GlobalMemoryStatusEx, die freie Zusage, nicht nur die Auslagerungsdatei [zu prüfen] gegen
 * {@code FreeVirtualMemory} im Skript).</li>
 * </ul>
 * {@link #tragbar()} sagt {@link Gelaende}, wie viel Heap es höchstens einplanen darf.
 */
public final class Speicher {
    private Speicher() { }

    private static final AtomicLong maxNachGc = new AtomicLong(), maxZugesagt = new AtomicLong(), sammlungen = new AtomicLong();
    private static volatile boolean an;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final double GB = 1024.0 * 1024 * 1024;

    /** Hört ab jetzt auf die Sammlungen der JVM (einmal). */
    public static synchronized void an() {
        if (an) return;
        an = true;
        Set<String> heap = new HashSet<>();
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) if (p.getType() == MemoryType.HEAP) heap.add(p.getName());
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gc instanceof NotificationEmitter ne)) continue;
            ne.addNotificationListener((n, h) -> {
                if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) return;
                GarbageCollectionNotificationInfo i = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                long benutzt = 0, zugesagt = 0;
                for (var e : i.getGcInfo().getMemoryUsageAfterGc().entrySet()) {
                    if (!heap.contains(e.getKey())) continue;
                    MemoryUsage u = e.getValue();
                    benutzt += u.getUsed();
                    zugesagt += u.getCommitted();
                }
                maxNachGc.accumulateAndGet(benutzt, Math::max);
                maxZugesagt.accumulateAndGet(zugesagt, Math::max);
                sammlungen.incrementAndGet();
            }, null, null);
        }
    }

    /** Fängt einen neuen Schritt an (die Höchstwerte von vorn). */
    public static void neu() {
        maxNachGc.set(0);
        maxZugesagt.set(Runtime.getRuntime().totalMemory());
        sammlungen.set(0);
    }

    private static com.sun.management.OperatingSystemMXBean os() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean o ? o : null;
    }

    /** Was das System noch zusagen kann (Bytes; unter Windows die freie Zusage); −1, wenn unbekannt. */
    public static long zusageFrei() {
        var o = os();
        return o == null ? -1 : o.getFreeSwapSpaceSize();
    }

    /** Die Zusagegrenze des Systems (Bytes; unter Windows Arbeitsspeicher und Auslagerungsdatei); −1, wenn unbekannt. */
    public static long zusageGrenze() {
        var o = os();
        return o == null ? -1 : o.getTotalSwapSpaceSize();
    }

    /**
     * So viel Heap darf eine Rechnung höchstens einplanen (Bytes): die Grenze der JVM, unter Windows höchstens, was sie
     * schon hält, plus was Windows noch zusagen kann, weniger 1 GB.
     */
    public static long tragbar() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        if (!WINDOWS) return max;
        long frei = zusageFrei();
        return frei < 0 ? max : Math.max(rt.totalMemory() / 2, Math.min(max, rt.totalMemory() + frei - (1L << 30)));
    }

    /** Eine Zeile für das Protokoll: Heap nach den Sammlungen dieses Schritts, zugesagt, Rohdaten, System. */
    public static String stand() {
        Runtime rt = Runtime.getRuntime();
        long jetzt = rt.totalMemory() - rt.freeMemory();
        StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "Heap nach GC höchstens %.1f GB (%d Sammlungen), zugesagt bis %.1f GB von %.1f, Rohdaten %.1f GB",
                maxNachGc.get() / GB, sammlungen.get(), Math.max(maxZugesagt.get(), rt.totalMemory()) / GB, rt.maxMemory() / GB, GeoTiff.cacheBelegt() / GB));
        if (sammlungen.get() == 0) b.append(String.format(Locale.GERMAN, ", jetzt %.1f GB", jetzt / GB));
        b.append(", ").append(system());
        return b.toString();
    }

    /** Was das System noch hat. */
    public static String system() {
        var o = os();
        if (o == null) return "System unbekannt";
        if (WINDOWS)
            return String.format(Locale.GERMAN, "Windows sagt noch %.1f GB zu (Grenze %.1f GB), frei im Arbeitsspeicher %.1f GB von %.1f",
                    o.getFreeSwapSpaceSize() / GB, o.getTotalSwapSpaceSize() / GB, o.getFreeMemorySize() / GB, o.getTotalMemorySize() / GB);
        return String.format(Locale.GERMAN, "frei im Arbeitsspeicher %.1f GB von %.1f, Auslagerung frei %.1f GB",
                o.getFreeMemorySize() / GB, o.getTotalMemorySize() / GB, o.getFreeSwapSpaceSize() / GB);
    }

    /** Eine Warnung, wenn der Heap bis zu seiner Grenze nicht mehr zugesagt werden könnte; sonst null. */
    public static String warnung() {
        if (!WINDOWS) return null;
        long frei = zusageFrei();
        Runtime rt = Runtime.getRuntime();
        long fehlt = rt.maxMemory() - rt.totalMemory() + (1L << 30) - frei;
        if (frei < 0 || fehlt <= 0) return null;
        return String.format(Locale.GERMAN, "ACHTUNG: Windows kann nur noch %.1f GB zusagen, der Heap darf aber bis %.1f GB wachsen (es fehlen %.1f GB). "
                + "Andere Programme schließen oder kleiner rechnen (ant aufbereitung -Dspeicher=8g); die Gelände-Stufen planen nur %.1f GB ein.",
                frei / GB, rt.maxMemory() / GB, fehlt / GB, tragbar() / GB);
    }
}
