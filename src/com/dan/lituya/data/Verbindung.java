package com.dan.lituya.data;

import com.dan.store.Db;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Die Datenbank (Werkbuch Abschnitt 13, Phase 19): Ort von {@code db/}, Zugang aus {@code db/db.properties}. Benutzer und
 * Schema legt {@code db/info/USER.sql} an ({@code manage_user} auf PDBORCL, Rolle DB_CONNECT). Phase 1 verbindet nur und
 * meldet, wer man ist; die Funktionsprobe des Network Data Model (2.8) und die Netze ({@code LB_NET_…}) kommen mit
 * Phase 3, die Tabellen ({@code LB_…}, Sequenz und Trigger, nie IDENTITY, SRID 26908) mit Phase 19. Kein Package kennt
 * Oracle (Paketvertrag 13): JDBC gibt es nur hier, in {@code com.dan.lituya.netz} und in den Werkzeugen.
 */
public final class Verbindung {
    private Verbindung() { }

    public static final String PRAEFIX = "LB_";
    /** Umgebungsvariable für ein leeres Passwort (wie in db.properties.example beschrieben). */
    public static final String ENV = "LituyaBay1958_DB_PASSWORD";

    /** Der Stand der Verbindung (für Startbild, Bedienfeld und Statuszeile). */
    private static volatile String stand = "ohne Datenbank";

    /** Der Ordner db: -Dlituya.db.dir, sonst ./db, sonst db neben dem Jar. */
    public static File ordner() { return Db.ordner("lituya.db.dir", Verbindung.class); }

    /**
     * Der Zugang aus db/db.properties. Fehlt die Datei, wird sie aus der Vorlage db.properties.example angelegt (ohne
     * Passwort); das Startbild fragt dann danach und trägt es nach dem Verbinden ein.
     */
    public static Db.Konfig konfig() throws IOException {
        File f = new File(ordner(), "db.properties"), vorlage = new File(ordner(), "db.properties.example");
        if (!f.isFile() && vorlage.isFile()) Files.copy(vorlage.toPath(), f.toPath());
        Db.Konfig k = Db.konfig(f, "LituyaBay1958", ENV);
        if (!k.hatPasswort()) {
            // auch in Großbuchstaben (Windows kennt beide, andere Systeme nicht)
            String e = System.getenv(ENV.toUpperCase(java.util.Locale.ROOT));
            if (e != null && !e.isEmpty()) k = k.mitPasswort(e.toCharArray());
        }
        return k;
    }

    /** Der Zugang, mit dem das Startbild verbunden hat (null ohne Datenbank); ab Phase 3 lesen die Netze damit aus Oracle. */
    private static volatile Db.Konfig verbunden;

    public static Db.Konfig verbunden() { return verbunden; }

    public static void verbunden(Db.Konfig k) { verbunden = k; }

    /** „LITUYABAY1958 auf PDBORCL · Oracle 21“ oder „ohne Datenbank“. */
    public static String stand() { return stand; }

    public static void stand(String s) { stand = s; }
}
