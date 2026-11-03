package com.dan.lituya.data;

import com.dan.store.Db;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Speichern und Laden in Oracle (Phase 19): die Tabellen von {@code db/szene} ({@code LB_SITZUNG}, {@code LB_EREIGNIS_LAUF},
 * {@code LB_SZENARIO} mit {@code LB_SZENARIO_SCHRITT}, {@code LB_SCHNAPPSCHUSS}). Je Aufruf eine eigene Verbindung (kurz),
 * Orte als Punkt in 26908. Schlägt Oracle fehl, meldet die App es und nimmt die Dateien.
 */
public final class OracleSpeicher implements Speicher {
    final Db.Konfig k;

    public OracleSpeicher(Db.Konfig k) { this.k = k; }

    Connection c() throws SQLException {
        Connection c = Db.oeffnen(k);
        c.setAutoCommit(false);
        return c;
    }

    /** Gibt es die Tabellen von db/szene? */
    public boolean tabellenDa() throws SQLException {
        try (Connection c = c()) { return Db.namen(c, "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME IN ('LB_SITZUNG', 'LB_SCHNAPPSCHUSS', 'LB_SZENARIO', 'LB_EREIGNIS_LAUF')").size() == 4; }
    }

    @Override public String wo() { return "Oracle " + k.user; }

    static IOException io(SQLException e) { return new IOException("Oracle: " + Db.erste(e), e); }

    static void num(PreparedStatement p, int i, double v) throws SQLException { if (Double.isNaN(v) || Double.isInfinite(v)) p.setNull(i, Types.NUMERIC); else p.setDouble(i, v); }

    static double num(ResultSet r, String c) throws SQLException { double v = r.getDouble(c); return r.wasNull() ? Double.NaN : v; }

    /** Punkt als SQL (zwei Platzhalter für E, N): NULL, wo E fehlt. */
    static final String PUNKT = "CASE WHEN ? IS NULL THEN CAST(NULL AS MDSYS.SDO_GEOMETRY) ELSE SDO_GEOMETRY(2001, 26908, SDO_POINT_TYPE(?, ?, NULL), NULL, NULL) END";

    static void punkt(PreparedStatement p, int i, double e, double n) throws SQLException { num(p, i, e); num(p, i + 1, e); num(p, i + 2, n); }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungSpeichern(Sitzung s) throws IOException {
        try (Connection c = c()) {
            Long lauf = s.lauf() == null ? null : id(c, "SELECT LAUF_ID FROM LB_EREIGNIS_LAUF WHERE NAME = ?", s.lauf());
            Long buch = s.regiebuch() == null ? null : id(c, "SELECT SZENARIO_ID FROM LB_SZENARIO WHERE NAME = ?", s.regiebuch());
            long nid;
            try (PreparedStatement p = c.prepareStatement("SELECT LB_SITZUNG_SEQ.NEXTVAL FROM DUAL"); ResultSet r = p.executeQuery()) { r.next(); nid = r.getLong(1); }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SITZUNG (SITZUNG_ID, NAME, RECHNER, KAMERA, HOEHE_M, RICHTUNG, NEIGUNG, MODUS, UHR_MS, ZEITSTAND, RAFFER, LAUF_ID, SZENARIO_ID, ZUSTAND) "
                    + "VALUES (?, ?, ?, " + PUNKT + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 1;
                p.setLong(i++, nid);
                p.setString(i++, s.name());
                p.setString(i++, s.rechner());
                punkt(p, i, s.e(), s.n());
                i += 3;
                num(p, i++, s.h()); num(p, i++, s.richtung()); num(p, i++, s.neigung());
                p.setString(i++, s.modus());
                p.setLong(i++, s.uhrMs());
                p.setString(i++, s.zeitstand());
                num(p, i++, s.raffer());
                if (lauf == null) p.setNull(i++, Types.NUMERIC); else p.setLong(i++, lauf);
                if (buch == null) p.setNull(i++, Types.NUMERIC); else p.setLong(i++, buch);
                p.setCharacterStream(i, new StringReader(text(s.zustand())));
                p.executeUpdate();
            }
            c.commit();
            return nid;
        } catch (SQLException e) { throw io(e); }
    }

    static Long id(Connection c, String sql, String name) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, name);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getLong(1) : null; }
        }
    }

    static String text(Properties p) throws IOException {
        StringWriter w = new StringWriter();
        if (p != null) p.store(w, null);
        return w.toString();
    }

    static Properties props(String s) throws IOException {
        Properties p = new Properties();
        if (s != null) p.load(new StringReader(s));
        return p;
    }

    static final String SITZUNG_SQL = "SELECT s.SITZUNG_ID, s.NAME, TO_CHAR(s.GESPEICHERT, 'DD.MM.YYYY HH24:MI:SS') AS GESPEICHERT, s.RECHNER, s.KAMERA.SDO_POINT.X AS E, "
            + "s.KAMERA.SDO_POINT.Y AS N, s.HOEHE_M, s.RICHTUNG, s.NEIGUNG, s.MODUS, s.UHR_MS, s.ZEITSTAND, s.RAFFER, l.NAME AS LAUF, z.NAME AS REGIEBUCH%s "
            + "FROM LB_SITZUNG s LEFT JOIN LB_EREIGNIS_LAUF l ON l.LAUF_ID = s.LAUF_ID LEFT JOIN LB_SZENARIO z ON z.SZENARIO_ID = s.SZENARIO_ID ";

    static Sitzung sitzung(ResultSet r, boolean mitZustand) throws SQLException, IOException {
        return new Sitzung(r.getLong("SITZUNG_ID"), r.getString("NAME"), r.getString("GESPEICHERT"), r.getString("RECHNER"), num(r, "E"), num(r, "N"), num(r, "HOEHE_M"),
                num(r, "RICHTUNG"), num(r, "NEIGUNG"), r.getString("MODUS"), r.getLong("UHR_MS"), r.getString("ZEITSTAND"), num(r, "RAFFER"), r.getString("LAUF"),
                r.getString("REGIEBUCH"), mitZustand ? props(r.getString("ZUSTAND")) : null);
    }

    @Override public List<Sitzung> sitzungen() throws IOException {
        List<Sitzung> l = new ArrayList<>();
        try (Connection c = c(); PreparedStatement p = c.prepareStatement(String.format(SITZUNG_SQL, "") + "ORDER BY s.SITZUNG_ID DESC"); ResultSet r = p.executeQuery()) {
            while (r.next()) l.add(sitzung(r, false));
        } catch (SQLException e) { throw io(e); }
        return l;
    }

    @Override public Sitzung sitzung(long id) throws IOException {
        try (Connection c = c(); PreparedStatement p = c.prepareStatement(String.format(SITZUNG_SQL, ", s.ZUSTAND") + "WHERE s.SITZUNG_ID = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) { return r.next() ? sitzung(r, true) : null; }
        } catch (SQLException e) { throw io(e); }
    }

    // ------------------------------------------------------------------ Läufe

    static final String[] LAUF_SPALTEN = {"ART", "QUELLE", "ORT", "VOLUMEN_M3", "PEGEL_M", "GRUND", "GANGLINIE", "START_S", "GITTER", "RECHNER", "DAUER_S",
            "AUFLAUF_MAX_M", "AUFLAUF_SPORN_M", "ANKUNFT_CENOTAPH_S", "ANKUNFT_ANCHORAGE_S", "ANKUNFT_EINGANG_S", "EINGABEN"};

    @Override public void laufSpeichern(Lauf l) throws IOException {
        try (Connection c = c()) {
            lauf(c, l);
            c.commit();
        } catch (SQLException e) { throw io(e); }
    }

    /** Ein Lauf nach Name einfügen oder ersetzen; gibt seine ID (auch für tools.SzeneDb). */
    public static long lauf(Connection c, Lauf l) throws SQLException {
        Long id = id(c, "SELECT LAUF_ID FROM LB_EREIGNIS_LAUF WHERE NAME = ?", l.name());
        String sql = id == null
                ? "INSERT INTO LB_EREIGNIS_LAUF (" + String.join(", ", LAUF_SPALTEN) + ", ERSTELLT, NAME) VALUES (" + "?, ".repeat(LAUF_SPALTEN.length) + "SYSDATE, ?)"
                : "UPDATE LB_EREIGNIS_LAUF SET " + String.join(" = ?, ", LAUF_SPALTEN) + " = ?, GELADEN = SYSDATE WHERE NAME = ?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            p.setString(i++, l.art());
            p.setString(i++, kurz(l.quelle(), 150));
            p.setString(i++, kurz(l.ort(), 80));
            num(p, i++, l.volumen()); num(p, i++, l.pegel());
            p.setString(i++, l.grund());
            p.setString(i++, l.ganglinie());
            num(p, i++, l.startS());
            p.setString(i++, kurz(l.gitter(), 300));
            p.setString(i++, kurz(l.rechner(), 600));
            num(p, i++, l.dauerS()); num(p, i++, l.auflaufMax()); num(p, i++, l.auflaufSporn());
            num(p, i++, l.ankunftCenotaph()); num(p, i++, l.ankunftAnchorage()); num(p, i++, l.ankunftEingang());
            p.setCharacterStream(i++, new StringReader(l.eingaben() == null ? "" : l.eingaben()));
            p.setString(i, l.name());
            p.executeUpdate();
        }
        return id != null ? id : id(c, "SELECT LAUF_ID FROM LB_EREIGNIS_LAUF WHERE NAME = ?", l.name());
    }

    @Override public List<Lauf> laeufe() throws IOException {
        List<Lauf> l = new ArrayList<>();
        try (Connection c = c(); PreparedStatement p = c.prepareStatement("SELECT NAME, " + String.join(", ", LAUF_SPALTEN) + " FROM LB_EREIGNIS_LAUF ORDER BY ART, NAME");
             ResultSet r = p.executeQuery()) {
            while (r.next())
                l.add(new Lauf(r.getString("NAME"), r.getString("ART"), r.getString("QUELLE"), r.getString("ORT"), num(r, "VOLUMEN_M3"), num(r, "PEGEL_M"), r.getString("GRUND"),
                        r.getString("GANGLINIE"), num(r, "START_S"), r.getString("GITTER"), r.getString("RECHNER"), num(r, "DAUER_S"), num(r, "AUFLAUF_MAX_M"),
                        num(r, "AUFLAUF_SPORN_M"), num(r, "ANKUNFT_CENOTAPH_S"), num(r, "ANKUNFT_ANCHORAGE_S"), num(r, "ANKUNFT_EINGANG_S"), r.getString("EINGABEN")));
        } catch (SQLException e) { throw io(e); }
        return l;
    }

    // ------------------------------------------------------------------ Regiebücher

    @Override public void regiebuchSpeichern(Buch b) throws IOException {
        try (Connection c = c()) {
            buch(c, b);
            c.commit();
        } catch (SQLException e) { throw io(e); }
    }

    /** Ein Regiebuch nach Name einfügen oder ersetzen (Version + 1), dazu seine Stichworte neu; auch für tools.SzeneDb. */
    public static long buch(Connection c, Buch b) throws SQLException {
        com.dan.lituya.scene.Regie.Book bk = com.dan.lituya.scene.Regie.parse(b.text());
        Long id = id(c, "SELECT SZENARIO_ID FROM LB_SZENARIO WHERE NAME = ?", b.name());
        if (id == null) {
            try (PreparedStatement p = c.prepareStatement("SELECT LB_SZENARIO_SEQ.NEXTVAL FROM DUAL"); ResultSet r = p.executeQuery()) { r.next(); id = r.getLong(1); }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SZENARIO (SZENARIO_ID, NAME, DATEI, DAUER_S, STICHWORTE, FOTOS, HERKUNFT, TEXT) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                p.setLong(1, id);
                p.setString(2, b.name());
                p.setString(3, b.mitgeliefert() ? datei(b.name()) : null);
                p.setDouble(4, bk.length());
                p.setInt(5, bk.cues().size());
                p.setInt(6, bk.fotos());
                p.setString(7, b.mitgeliefert() ? "mitgeliefert" : "eigen");
                p.setCharacterStream(8, new StringReader(b.text()));
                p.executeUpdate();
            }
        } else {
            String alt;
            try (PreparedStatement p = c.prepareStatement("SELECT TEXT FROM LB_SZENARIO WHERE SZENARIO_ID = ?")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) { r.next(); alt = r.getString(1); }
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE LB_SZENARIO SET DAUER_S = ?, STICHWORTE = ?, FOTOS = ?, HERKUNFT = ?, TEXT = ?, "
                    + "VERSION = VERSION + ?, GEAENDERT = SYSDATE WHERE SZENARIO_ID = ?")) {
                p.setDouble(1, bk.length());
                p.setInt(2, bk.cues().size());
                p.setInt(3, bk.fotos());
                p.setString(4, b.mitgeliefert() ? "mitgeliefert" : "eigen");
                p.setCharacterStream(5, new StringReader(b.text()));
                p.setInt(6, b.text().equals(alt) ? 0 : 1);
                p.setLong(7, id);
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("DELETE FROM LB_SZENARIO_SCHRITT WHERE SZENARIO_ID = ?")) { p.setLong(1, id); p.executeUpdate(); }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SZENARIO_SCHRITT (SZENARIO_ID, NR, ZEIT_S, BEFEHL, ARGUMENT, UNTERTITEL) VALUES (?, ?, ?, ?, ?, ?)")) {
            int nr = 0;
            for (com.dan.lituya.scene.Regie.Zeile z : bk.zeilen()) {
                p.setLong(1, id);
                p.setInt(2, ++nr);
                p.setDouble(3, z.sekunde());
                p.setString(4, z.befehl());
                p.setString(5, kurz(z.argumente(), 400));
                p.setString(6, kurz(z.text(), 400));
                p.addBatch();
            }
            p.executeBatch();
        }
        return id;
    }

    /** Die Datei eines mitgelieferten Regiebuchs nach seinem Namen (oder null). */
    static String datei(String name) {
        for (String d : com.dan.lituya.scene.Regie.DATEIEN)
            if (com.dan.lituya.scene.Regie.parse(com.dan.lituya.scene.Regie.text(d)).name().equals(name)) return "scene/regie/" + d;
        return null;
    }

    static String kurz(String s, int n) { return s == null || s.isEmpty() ? null : s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    @Override public List<Buch> regiebuecher() throws IOException {
        List<Buch> l = new ArrayList<>();
        try (Connection c = c(); PreparedStatement p = c.prepareStatement("SELECT NAME, TEXT, HERKUNFT, TO_CHAR(GEAENDERT, 'DD.MM.YYYY HH24:MI:SS') AS G FROM LB_SZENARIO ORDER BY HERKUNFT DESC, NAME");
             ResultSet r = p.executeQuery()) {
            while (r.next()) l.add(new Buch(r.getString(1), r.getString(2), "mitgeliefert".equals(r.getString(3)), r.getString(4)));
        } catch (SQLException e) { throw io(e); }
        return l;
    }

    // ------------------------------------------------------------------ Bilder

    @Override public void bildSpeichern(Bild b) throws IOException {
        try (Connection c = c()) {
            bild(c, b);
            c.commit();
        } catch (SQLException e) { throw io(e); }
    }

    /** Ein Bild nach Datei einfügen oder ersetzen; auch für tools.SzeneDb. */
    public static void bild(Connection c, Bild b) throws SQLException {
        Long buch = b.regiebuch() == null ? null : id(c, "SELECT SZENARIO_ID FROM LB_SZENARIO WHERE NAME = ?", b.regiebuch());
        try (PreparedStatement p = c.prepareStatement("DELETE FROM LB_SCHNAPPSCHUSS WHERE DATEI = ?")) { p.setString(1, b.datei()); p.executeUpdate(); }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SCHNAPPSCHUSS (ART, DATEI, WELTZEIT, ZEIT_S, ZEITSTAND, KAMERA, HOEHE_M, RICHTUNG, NEIGUNG, SZENARIO_ID, "
                + "BREITE, HOEHE, BYTES, BILDRATE, NOTIZ, BILD, VORSCHAU) VALUES (?, ?, ?, ?, ?, " + PUNKT + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 1;
            p.setString(i++, b.art());
            p.setString(i++, b.datei());
            p.setString(i++, b.weltzeit());
            num(p, i++, b.zeitS());
            p.setString(i++, b.zeitstand());
            punkt(p, i, b.e(), b.n());
            i += 3;
            num(p, i++, b.h()); num(p, i++, b.richtung()); num(p, i++, b.neigung());
            if (buch == null) p.setNull(i++, Types.NUMERIC); else p.setLong(i++, buch);
            p.setInt(i++, b.breite());
            p.setInt(i++, b.hoehe());
            p.setLong(i++, b.png() == null ? 0 : b.png().length);
            num(p, i++, b.bildrate());
            p.setString(i++, kurz(b.notiz(), 500));
            p.setBytes(i++, b.png());
            p.setBytes(i, b.vorschau());
            p.executeUpdate();
        }
    }

    @Override public int bilder() throws IOException {
        try (Connection c = c()) { return Integer.parseInt(Db.einWert(c, "SELECT COUNT(*) FROM LB_SCHNAPPSCHUSS")); } catch (SQLException e) { throw io(e); }
    }

    /** „12 Sitzungen, 3 Läufe …“ für das Bedienfeld. */
    public String zahlen() throws SQLException {
        try (Connection c = c()) {
            return String.format(Locale.GERMAN, "%s Sitzungen, %s Läufe, %s Regiebücher, %s Bilder, %s Befunde", Db.einWert(c, "SELECT COUNT(*) FROM LB_SITZUNG"),
                    Db.einWert(c, "SELECT COUNT(*) FROM LB_EREIGNIS_LAUF"), Db.einWert(c, "SELECT COUNT(*) FROM LB_SZENARIO"), Db.einWert(c, "SELECT COUNT(*) FROM LB_SCHNAPPSCHUSS"),
                    Db.einWert(c, "SELECT COUNT(*) FROM LB_BEFUND"));
        }
    }
}
