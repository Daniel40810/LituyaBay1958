package com.dan.lituya.gl;

import com.dan.lituya.data.Speicher;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Eine Sitzung sichern und wieder herstellen (Phase 19, Strg+S, F49): Kamera, Uhr, Zeitstand, Raffer und alle Schalter
 * von Renderer, Netzblick, Klang und Echtzeit als Properties. Gesichert werden die öffentlichen, nicht festen Felder
 * einfacher Art (boolean, Zahl, Text, Aufzählung), so kommen neue Schalter von selbst mit; was nur für einen Augenblick
 * gilt (Foto anfordern, angehalten, Tasten), bleibt draußen ({@link #OHNE}).
 */
public final class Sitzungen {
    private Sitzungen() { }

    /** Felder, die nicht in die Sitzung gehören. */
    static final Set<String> OHNE = Set.of("photoRequest", "frozen", "photoDir", "photoName", "lastSheet", "ohneBlatt", "fertig", "kFwd", "kBack", "kLeft", "kRight",
            "kUp", "kDown", "kRun", "diving", "groundLevel", "waterLevel", "targetGround", "offline", "budgetMs", "kruemmungR", "autoOrbit");

    /** Die Sitzung jetzt (Name frei, sonst Weltzeit und Zeitstand). */
    public static Speicher.Sitzung erfassen(Renderer r, String name) {
        Properties p = new Properties();
        felder(p, "renderer.", r);
        felder(p, "netzblick.", r.netzblick);
        felder(p, "klang.", r.klang);
        felder(p, "echtzeit.", r.echtzeit);
        p.setProperty("echtzeit.ort", r.echtzeit.ort.name());
        felder(p, "kamera.", r.camera);
        SceneClock c = r.clock;
        p.setProperty("uhr.ms", String.valueOf((long) c.epochMillis()));
        p.setProperty("uhr.zeitstand", c.zeitstand.name());
        p.setProperty("uhr.raffer", String.valueOf(c.lapse));
        p.setProperty("phase", String.valueOf(Renderer.PHASE));
        double[] utm = Site.toUtm(r.camera.eye[0], r.camera.eye[2]);
        String wann = c.label();
        String n = name != null && !name.isBlank() ? name : wann + " · " + c.zeitstand;
        String rechner;
        try { rechner = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { rechner = System.getProperty("user.name", "?"); }
        return new Speicher.Sitzung(0, n, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), rechner, utm[0], utm[1], r.camera.eye[1],
                r.camera.heading(), r.camera.mode == com.dan.lituya.camera.Camera.Mode.FREI ? r.camera.fpitch : r.camera.pitch, r.camera.mode.name(), (long) c.epochMillis(),
                c.zeitstand.name(), c.lapse, null, r.regie.running() ? r.regie.describe() : null, p);
    }

    /** Stellt die Sitzung wieder her (im Bildfaden aufrufen, etwa über {@code Renderer.imBild}). Gibt die Zahl der gesetzten Werte. */
    public static int herstellen(Renderer r, Speicher.Sitzung s) {
        Properties p = s.zustand();
        if (p == null) return 0;
        int n = 0;
        n += setzen(p, "renderer.", r);
        n += setzen(p, "netzblick.", r.netzblick);
        n += setzen(p, "echtzeit.", r.echtzeit);
        String ort = p.getProperty("echtzeit.ort");
        if (ort != null) for (Echtzeit.Ort o : Echtzeit.ORTE) if (o.name().equals(ort)) r.echtzeit.ort = o;
        boolean ton = Boolean.parseBoolean(p.getProperty("klang.on", "false"));
        p.remove("klang.on");
        n += setzen(p, "klang.", r.klang);
        r.klang.setOn(ton);
        com.dan.lituya.camera.Camera cam = r.camera;
        cam.stopFahrt();
        cam.stopMitfahrt();
        n += setzen(p, "kamera.", cam);
        if (cam.mode == com.dan.lituya.camera.Camera.Mode.FREI) cam.place(cam.fx, cam.fy, cam.fz, cam.fyaw, cam.fpitch);
        cam.snap();
        SceneClock c = r.clock;
        try {
            c.setEpochMillis(Long.parseLong(p.getProperty("uhr.ms")));
            c.zeitstand = SceneClock.Zeitstand.valueOf(p.getProperty("uhr.zeitstand"));
            c.lapse = Double.parseDouble(p.getProperty("uhr.raffer"));
            n += 3;
        } catch (RuntimeException e) { /* alte Sitzung ohne Uhr */ }
        r.wiedergabe.setzen(c.simSeconds(), c.lapse);
        return n;
    }

    static boolean einfach(Field f) {
        int m = f.getModifiers();
        if (!Modifier.isPublic(m) || Modifier.isStatic(m) || Modifier.isFinal(m) || OHNE.contains(f.getName())) return false;
        Class<?> t = f.getType();
        return t.isPrimitive() || t == String.class || t.isEnum();
    }

    static void felder(Properties p, String pre, Object o) {
        for (Field f : o.getClass().getFields()) {
            if (!einfach(f)) continue;
            try {
                Object v = f.get(o);
                if (v != null) p.setProperty(pre + f.getName(), v instanceof Enum<?> e ? e.name() : String.valueOf(v));
            } catch (IllegalAccessException e) { /* nicht lesbar */ }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static int setzen(Properties p, String pre, Object o) {
        int n = 0;
        for (Field f : o.getClass().getFields()) {
            if (!einfach(f)) continue;
            String v = p.getProperty(pre + f.getName());
            if (v == null) continue;
            try {
                Class<?> t = f.getType();
                if (t == boolean.class) f.setBoolean(o, Boolean.parseBoolean(v));
                else if (t == double.class) f.setDouble(o, Double.parseDouble(v));
                else if (t == float.class) f.setFloat(o, Float.parseFloat(v));
                else if (t == int.class) f.setInt(o, Integer.parseInt(v));
                else if (t == long.class) f.setLong(o, Long.parseLong(v));
                else if (t == String.class) f.set(o, v);
                else if (t.isEnum()) f.set(o, Enum.valueOf((Class<Enum>) t, v));
                else continue;
                n++;
            } catch (RuntimeException | IllegalAccessException e) { /* Wert passt nicht mehr (neue Version) */ }
        }
        return n;
    }

    /** „Sitzung „…“ gesichert (Oracle LITUYABAY1958, 84 Werte)“ */
    public static String sichern(Renderer r, String name) {
        Speicher.Sitzung s = erfassen(r, name);
        Speicher sp = Speicher.aktuell();
        try {
            sp.sitzungSpeichern(s);
            return String.format(Locale.GERMAN, "Sitzung „%s“ gesichert (%s, %d Werte)", s.name(), sp.wo(), s.zustand().size());
        } catch (Exception e) {
            // Oracle fällt aus: in die Dateien
            try {
                Speicher.datei().sitzungSpeichern(s);
                return "Sitzung in die Dateien gesichert (" + e.getMessage() + ")";
            } catch (Exception e2) {
                return "Sitzung nicht gesichert: " + e2.getMessage();
            }
        }
    }
}
