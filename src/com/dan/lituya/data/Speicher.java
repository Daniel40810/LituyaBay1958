package com.dan.lituya.data;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Speichern und Laden der App (Phase 19, Werkbuch 13.1, F49/F50): Sitzungen, Echtzeitläufe, Regiebücher, Fotos und
 * Blätter. Zwei Wege mit derselben Schnittstelle: {@link OracleSpeicher} (die Tabellen von {@code db/szene}) und
 * {@link DateiSpeicher} ({@code daten/speicher}); {@link #aktuell()} nimmt Oracle, wenn das Startbild verbunden hat und
 * die Tabellen da sind, sonst die Dateien. Die App startet immer.
 */
public interface Speicher {

    /** Eine gesicherte Sitzung: Kopf und der ganze Zustand als Properties ({@code gl.Sitzungen}). */
    record Sitzung(long id, String name, String gespeichert, String rechner, double e, double n, double h, double richtung, double neigung,
                   String modus, long uhrMs, String zeitstand, double raffer, String lauf, String regiebuch, Properties zustand) {
        /** „Name · 03.11.2026 10:42 · 22:17 Uhr · WELLE“ */
        public String zeile() { return name + " · " + gespeichert + " · " + zeitstand; }
    }

    /** Ein Echtzeitlauf: die Eingaben (zum Wiederabspielen) und was davon bekannt ist. */
    record Lauf(String name, String art, String quelle, String ort, double volumen, double pegel, String grund, String ganglinie,
                double startS, String gitter, String rechner, double dauerS, double auflaufMax, double auflaufSporn, double ankunftCenotaph,
                double ankunftAnchorage, double ankunftEingang, String eingaben) { }

    /** Ein Bild: Foto (F12), Blatt eines Regiebuchs oder Status, als PNG. */
    record Bild(String art, String datei, byte[] png, byte[] vorschau, int breite, int hoehe, String weltzeit, double zeitS, String zeitstand,
                double e, double n, double h, double richtung, double neigung, String regiebuch, double bildrate, String notiz) { }

    /** Ein Befund (LB_BEFUND, Phase 19a im Archiv): Ort in UTM 8 N (NaN ohne), Quellen als Text. */
    record Befund(String schluessel, String thema, String aussage, double wert, String einheit, String art, String abschnitt, String ortName, double e, double n,
                  double zeitS, String gilt, String quellen, String herkunft) { }

    /** Eine Aussage eines Zeugen (LB_ZEUGE): Zeit von/bis in s nach dem Stoß, Ort in UTM. */
    record Zeuge(String name, String rolle, String boot, double vonS, double bisS, String ortName, double e, double n, String aussage, String art, String quelle) { }

    /** Kopf eines Bilds mit Vorschau (ohne das große PNG). */
    record BildKopf(String art, String datei, String aufgenommen, String weltzeit, double zeitS, String zeitstand, double e, double n, double h, double richtung,
                    double neigung, String regiebuch, int breite, int hoehe, String notiz, byte[] vorschau) { }

    /** Ein Regiebuch: Name, Text, mitgeliefert oder eigen. */
    record Buch(String name, String text, boolean mitgeliefert, String geaendert) { }

    /** „Oracle LITUYABAY1958“ oder „Dateien in daten/speicher“. */
    String wo();

    long sitzungSpeichern(Sitzung s) throws IOException;

    /** Die gesicherten Sitzungen, neueste zuerst (ohne Zustand, der kommt mit {@link #sitzung}). */
    List<Sitzung> sitzungen() throws IOException;

    Sitzung sitzung(long id) throws IOException;

    void laufSpeichern(Lauf l) throws IOException;

    List<Lauf> laeufe() throws IOException;

    void regiebuchSpeichern(Buch b) throws IOException;

    /** Die eigenen (und in Oracle auch die mitgelieferten) Regiebücher. */
    List<Buch> regiebuecher() throws IOException;

    void bildSpeichern(Bild b) throws IOException;

    /** Zahl der Bilder (für Bedienfeld und Prüfung). */
    int bilder() throws IOException;

    // ------------------------------------------------------------------ Phase 19a: das Archiv

    /** Alle Befunde (mitgeliefert und gerechnet; ohne Datenbank nur die mitgelieferten). */
    List<Befund> befunde() throws IOException;

    List<Zeuge> zeugen() throws IOException;

    /** Die Bilder, neueste zuerst, mit Vorschau. */
    List<BildKopf> bilderListe() throws IOException;

    /** Das ganze PNG eines Bilds (null, wenn es fehlt). */
    byte[] bildDaten(String datei) throws IOException;

    void sitzungLoeschen(long id) throws IOException;

    /** Ein eigenes Regiebuch löschen (mitgelieferte bleiben). */
    void regiebuchLoeschen(String name) throws IOException;

    // ------------------------------------------------------------------ Wahl

    /** Oracle, wenn verbunden und {@code LB_SITZUNG} da ist; sonst die Dateien. Einmal gewählt, bis {@link #neuWaehlen()}. */
    static Speicher aktuell() { return Wahl.aktuell(); }

    static void neuWaehlen() { Wahl.neu(); }

    /** Der gewählte Speicher oder null, solange keiner gewählt ist (ohne zu wählen; für das Bedienfeld). */
    static Speicher gewaehlt() { return Wahl.gewaehlt; }

    /** Die Dateien, immer (Rückfall). */
    static Speicher datei() { return Wahl.datei(); }

    /** Warum es Dateien sind (oder leer). */
    static String hinweis() { return Wahl.hinweis; }

    final class Wahl {
        private Wahl() { }
        static volatile Speicher gewaehlt;
        static volatile String hinweis = "";
        private static DateiSpeicher datei;

        static synchronized Speicher datei() {
            if (datei == null) datei = new DateiSpeicher(new java.io.File(new java.io.File(System.getProperty("lituya.daten", "daten")), "speicher"));
            return datei;
        }

        static synchronized void neu() { gewaehlt = null; }

        static synchronized Speicher aktuell() {
            if (gewaehlt != null) return gewaehlt;
            com.dan.store.Db.Konfig k = Verbindung.verbunden();
            if (k != null) {
                try {
                    OracleSpeicher o = new OracleSpeicher(k);
                    if (o.tabellenDa()) { hinweis = ""; return gewaehlt = o; }
                    hinweis = "Tabellen von db/szene fehlen (ant szenedb)";
                } catch (Exception e) {
                    hinweis = "Oracle nicht erreichbar: " + e.getMessage();
                }
            } else hinweis = "ohne Datenbank";
            return gewaehlt = datei();
        }
    }
}
