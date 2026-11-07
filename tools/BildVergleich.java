import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Vergleicht zwei Ordner der Bildbank Bild für Bild (gleicher Dateiname, nur die Aufnahmen „NN_Name.png“, nicht die
 * Kontaktbögen): wie viele Bildpunkte sich unterscheiden, größte und mittlere Abweichung je Farbkanal (0–255).
 * Werkbuch Welle 5e (W1–W5 in Renderer): das Bild soll gleich bleiben.
 * <pre>java tools/BildVergleich.java docs/bild/PAKETE_VORHER docs/bild/PAKETE</pre>
 */
public final class BildVergleich {
    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        if (a.length < 2) { System.out.println("Aufruf: java tools/BildVergleich.java ordnerVorher ordnerNachher"); return; }
        File va = new File(a[0]), vb = new File(a[1]);
        File[] fs = va.listFiles((d, n) -> n.matches("\\d+_.*\\.png"));
        if (fs == null || fs.length == 0) { System.out.println("keine Aufnahmen in " + va); return; }
        Arrays.sort(fs);
        System.out.printf("%s gegen %s%n", va, vb);
        System.out.printf("%-34s %12s %10s %10s%n", "Aufnahme", "anders", "größte", "Mittel");
        int gleich = 0, n = 0;
        int groesste = 0;
        for (File f : fs) {
            File g = new File(vb, f.getName());
            if (!g.exists()) { System.out.printf("%-34s fehlt in %s%n", f.getName(), vb); continue; }
            BufferedImage x = ImageIO.read(f), y = ImageIO.read(g);
            n++;
            if (x.getWidth() != y.getWidth() || x.getHeight() != y.getHeight()) { System.out.printf("%-34s andere Größe%n", f.getName()); continue; }
            long anders = 0, summe = 0;
            int max = 0;
            for (int j = 0; j < x.getHeight(); j++)
                for (int i = 0; i < x.getWidth(); i++) {
                    int p = x.getRGB(i, j), q = y.getRGB(i, j);
                    if (p == q) continue;
                    anders++;
                    for (int s = 0; s <= 16; s += 8) {
                        int d = Math.abs(((p >> s) & 255) - ((q >> s) & 255));
                        max = Math.max(max, d);
                        summe += d;
                    }
                }
            double pixel = (double) x.getWidth() * x.getHeight();
            System.out.printf("%-34s %9.4f %% %10d %10.4f%n", f.getName(), 100 * anders / pixel, max, summe / (3 * pixel));
            if (anders == 0) gleich++;
            groesste = Math.max(groesste, max);
        }
        System.out.printf("%d von %d Aufnahmen Bit für Bit gleich, größte Abweichung %d von 255%n", gleich, n, groesste);
    }
}
