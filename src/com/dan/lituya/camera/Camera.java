package com.dan.lituya.camera;

import com.dan.lituya.gl.Mat4;

/**
 * Die Kamera der Szene (aus Geiranger über Brocken und Timmendorf; zwei Arten. Die Ansichten nach Namen stehen in
 * Phase 1 über dem Wasser, ab Phase 4 auf dem Gelände; Fahrten und Mitfahrt sind da, gebraucht werden sie mit den
 * Booten in Phase 15 („Mit der Edrie“) und den Regiebüchern in Phase 17):
 * <ul>
 * <li><b>Kreisen (Orbit):</b> um einen Drehpunkt, links ziehen dreht, rechts ziehen verschiebt, das Rad zoomt in
 * Schritten von 12 %. R lässt sie von selbst kreisen.</li>
 * <li><b>Frei:</b> fliegen mit W A S D, Q und E für ab und auf, Umschalt für schneller; ziehen schaut umher.</li>
 * </ul>
 * Richtungen wie auf der Karte: {@code yaw} ist die Blickrichtung in Grad von Nord im Uhrzeigersinn, {@code pitch} die
 * Neigung nach unten. Alle Werte laufen weich ihrem Ziel nach. Das Auge bleibt mindestens {@link #MIN_EYE} m über dem
 * Grund an dieser Stelle. Der Maßstab ist größer als in Timmendorf: vom Kutter vor Anker (Auge 3 m) bis zum Blick über
 * den ganzen Fern (250 km Abstand), Unterwasser ab Phase 11 (F34 A).
 */
public final class Camera {
    public enum Mode { KREISEN, FREI }

    public static final double MIN_DIST = 3, MAX_DIST = 250_000, MIN_EYE = 1.5, MAX_EYE = 30_000;
    public static final double FOVY = 55;
    /** Wo der Drehpunkt liegen darf (m in der Szene): der Fern (Site.FERN, 260 × 240 km). */
    public static final double X_MIN = -132_000, X_MAX = 128_000, Z_MIN = -122_000, Z_MAX = 118_000;

    public volatile Mode mode = Mode.KREISEN;
    /** Ziel: Drehpunkt, Blickrichtung (Grad), Neigung (Grad, positiv nach unten), Abstand. */
    public double tx, ty, tz, yaw, pitch, dist;
    /** Laufender Stand (weich nachgeführt). */
    private double cx, cy, cz, cyaw, cpitch, cdist;
    /** Freie Kamera: Auge und Blick. */
    public double fx, fy, fz, fyaw, fpitch;
    private double fxs, fys, fzs, fyaws, fpitchs;
    public volatile boolean autoOrbit;
    /** Grad je Sekunde beim Kreisen. */
    public double orbitSpeed = 3;
    public volatile boolean kFwd, kBack, kLeft, kRight, kUp, kDown, kRun;
    /** Höhe von Boden und Wasser unter dem Auge (vom Renderer gesetzt; Phase 1: kein Gelände, nur der Pegel). */
    public volatile double groundLevel, waterLevel;
    /** Unter Wasser erlaubt (aus Geiranger; Schaustück 7 „Unter Wasser“, F34 A, ab Phase 11): das Auge darf unter den Spiegel, nicht in den Grund. */
    public volatile boolean diving;
    /** Phase 2: Höhe des Bodens unter dem Drehpunkt; der Drehpunkt bleibt mindestens 0,5 m darüber. */
    public volatile double targetGround = Double.NEGATIVE_INFINITY;

    /** Auge und Blickrichtung des laufenden Bilds. */
    public final double[] eye = new double[3], fwd = new double[3];

    /** Die laufende Kamerafahrt (null: keine); sie stellt das Auge in jedem Schritt. */
    public volatile Fahrt fahrt;

    /**
     * Mitfahrt (null: keine; ab Phase 15 ein Kutter, Schaustück 8 „Mit der Edrie“). Liefert je Schritt {x, y, z, Kurs, Stampfen, Rollen}; Ziehen schaut umher
     * (gegen den Kurs versetzt), die Kamera fährt mit.
     */
    public volatile java.util.function.Supplier<double[]> mitfahrt;
    private double mitYaw, mitPitch = 8, mitYawS, mitBobS;

    /** Werkbuch Welle W11c: eine Drohne liefert je Schritt {x, y, z, Blick, Neigung} (Welt, Grad), null, wenn sie nichts mehr begleitet. */
    public interface Begleiter { double[] pose(double dt); }
    /** Die Drohne (null: keine); sie stellt Auge und Blick in jedem Schritt, Ziehen dreht den Blick um einen Versatz. */
    public volatile Begleiter drohne;
    private double drYaw, drPitch;

    /** W11c: Drohne starten (ersetzt Fahrt und Mitfahrt). */
    public void startDrohne(Begleiter b) {
        stopFahrt();
        mitfahrt = null;
        drYaw = drPitch = 0;
        drohne = b;
        double[] q = b.pose(0);
        if (q != null) place(q[0], q[1], q[2], q[3], q[4]);
    }

    /** W11c: Drohne beenden; die Kamera bleibt frei, wo sie ist. */
    public void stopDrohne() { drohne = null; }

    public Camera() { home(); snap(); }

    /** Mitfahrt starten: Blick voraus, leicht nach unten. */
    public void startMitfahrt(java.util.function.Supplier<double[]> pose) { startMitfahrt(pose, 0); }

    /** Mitfahrt mit dem Blick um lookDeg zur Seite (negativ nach Backbord). */
    public void startMitfahrt(java.util.function.Supplier<double[]> pose, double lookDeg) {
        stopFahrt();
        drohne = null;
        mitYaw = mitYawS = lookDeg;
        mitPitch = 8;
        mitBobS = 0;
        mitfahrt = pose;
        double[] q = pose.get();
        place(q[0], q[1], q[2], q[3], mitPitch);
    }

    /** Phase 16: bei der Mitfahrt den Blick weich zur Seite drehen (Grad, negativ nach Backbord). */
    public void mitBlick(double lookDeg) { mitYaw = Math.max(-180, Math.min(180, lookDeg)); }

    /** Mitfahrt (und Drohne) beenden; die Kamera bleibt frei, wo sie ist. */
    public void stopMitfahrt() { mitfahrt = null; drohne = null; }

    /** Fahrt starten (vom Anfang). */
    public void startFahrt(Fahrt f) {
        mitfahrt = null;
        drohne = null;
        f.start();
        fahrt = f;
        double[] q = f.pose(0);
        place(q[0], q[1], q[2], q[3], q[4]);
    }

    /** Laufende Fahrt anhalten; die Kamera bleibt frei, wo sie ist. */
    public void stopFahrt() {
        Fahrt f = fahrt;
        if (f != null) f.running = false;
        fahrt = null;
    }

    /** Untergrenze für das Auge. */
    private double floor() { return (diving ? groundLevel : Math.max(groundLevel, waterLevel)) + MIN_EYE; }

    /**
     * Grundansicht (Pos1): kreisen um Cenotaph Island, 9 km weit von See aus Südwest (Auge rund 1,9 km), Blick nach
     * Nordost die Bucht hinauf zum T-Kopf mit Gilbert und Crillon Inlet und dahinter zur Fairweather-Kette [G].
     */
    public void home() {
        mitfahrt = null;
        drohne = null;
        mode = Mode.KREISEN;
        autoOrbit = false;
        tx = com.dan.lituya.scene.Site.CENOTAPH.x(); ty = 0; tz = com.dan.lituya.scene.Site.CENOTAPH.z();
        yaw = 52; pitch = 12; dist = 9000;
        clamp();
    }

    /** Setzt alles sofort auf das Ziel (nach Sprüngen, beim Start, für Standbilder). */
    public void snap() {
        cx = tx; cy = ty; cz = tz; cyaw = yaw; cpitch = pitch; cdist = dist;
        fxs = fx; fys = fy; fzs = fz; fyaws = fyaw; fpitchs = fpitch;
        update();
    }

    public void lookAt(double x, double y, double z, double yawDeg, double pitchDeg, double distance) {
        mitfahrt = null;
        drohne = null;
        tx = x; ty = y; tz = z; yaw = yawDeg; pitch = pitchDeg; dist = distance;
        clamp();
    }

    /** Wechsel zwischen Kreisen und Frei; die Ansicht bleibt dabei stehen. */
    public void setMode(Mode m) {
        if (m == mode) return;
        if (m == Mode.FREI) {
            fx = fxs = eye[0]; fy = fys = eye[1]; fz = fzs = eye[2];
            fyaw = fyaws = cyaw; fpitch = fpitchs = cpitch;
            autoOrbit = false;
        } else {
            // Drehpunkt dort, wo der Blick 60 m voraus hinzeigt (nicht unter dem Boden)
            double d = 60;
            tx = eye[0] + fwd[0] * d; tz = eye[2] + fwd[2] * d;
            ty = Math.max(groundLevel + 1, eye[1] + fwd[1] * d);
            yaw = fyaw; dist = Math.hypot(Math.hypot(tx - eye[0], tz - eye[2]), ty - eye[1]);
            pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, (eye[1] - ty) / dist))));
            cx = tx; cy = ty; cz = tz; cyaw = yaw; cpitch = pitch; cdist = dist;
        }
        mode = m;
    }

    /** Drehen um den Drehpunkt bzw. Umschauen (Grad). */
    public void orbit(double dYawDeg, double dPitchDeg) {
        if (drohne != null) {   // W11c: der Gimbal der Drohne schwenkt um den Versatz
            drYaw = Math.max(-170, Math.min(170, drYaw + dYawDeg));
            drPitch = Math.max(-60, Math.min(60, drPitch + dPitchDeg));
            return;
        }
        if (mitfahrt != null) {
            mitYaw = Math.max(-180, Math.min(180, mitYaw + dYawDeg));
            mitPitch = Math.max(-40, Math.min(60, mitPitch + dPitchDeg));
            return;
        }
        if (mode == Mode.FREI) {
            fyaw += dYawDeg;
            fpitch = Math.max(-85, Math.min(85, fpitch + dPitchDeg));
        } else {
            yaw += dYawDeg;
            pitch += dPitchDeg;
            clamp();
        }
    }

    /** Verschieben in Bildschirmanteilen (rechts ziehen). */
    public void pan(double dxFrac, double dyFrac) {
        if (mode != Mode.KREISEN) return;
        double s = dist * 1.1;
        double a = Math.toRadians(yaw);
        double rx = Math.cos(a), rz = Math.sin(a);
        double fxh = Math.sin(a), fzh = -Math.cos(a);
        tx += -dxFrac * s * rx + dyFrac * s * fxh;
        tz += -dxFrac * s * rz + dyFrac * s * fzh;
        clamp();
    }

    /** Rad: Schritte von 12 % (positiv = weg). */
    public void zoom(double steps) {
        if (mode == Mode.FREI) {
            double d = steps * -4 * (1 + eye[1] / 20);
            fx += fwd[0] * d; fy += fwd[1] * d; fz += fwd[2] * d;
            return;
        }
        dist *= Math.pow(1.12, steps);
        clamp();
    }

    private void clamp() {
        dist = Math.max(MIN_DIST, Math.min(MAX_DIST, dist));
        pitch = Math.max(-60, Math.min(89, pitch));
        // Auge über Boden und Wasser: ty + dist·sin(pitch) ≥ Untergrenze
        double need = (floor() - ty) / dist;
        if (need > -1 && need < 1) pitch = Math.max(pitch, Math.toDegrees(Math.asin(need)));
        ty = Math.max(Math.max(groundLevel - 5, targetGround + 0.5), Math.min(MAX_EYE, ty));
        // im Fern
        tx = Math.max(X_MIN, Math.min(X_MAX, tx));
        tz = Math.max(Z_MIN, Math.min(Z_MAX, tz));
    }

    /** Frei an einen Ort stellen und in eine Richtung schauen; springt sofort. */
    public void place(double x, double y, double z, double yawDeg, double pitchDeg) {
        mode = Mode.FREI;
        autoOrbit = false;
        fx = x; fy = y; fz = z; fyaw = yawDeg; fpitch = pitchDeg;
        snap();
    }

    /** Läuft um dt Sekunden weiter: Kreisen, Tasten, weiches Nachführen. */
    public void step(double dt) {
        Begleiter dr = drohne;
        if (dr != null) {   // W11c: die Drohne stellt Auge und Blick, der Gimbal bleibt waagerecht
            double[] q = dr.pose(dt);
            if (q == null) { drohne = null; return; }   // Boot fort (keine Bahn mehr)
            mode = Mode.FREI;
            autoOrbit = false;
            fx = fxs = q[0]; fy = fys = Math.max(floor(), q[1]); fz = fzs = q[2];
            fyaw = fyaws = q[3] + drYaw;
            fpitch = fpitchs = Math.max(-85, Math.min(85, q[4] + drPitch));
            update();
            return;
        }
        java.util.function.Supplier<double[]> mf = mitfahrt;
        if (mf != null) {
            double[] q = mf.get();
            if (q == null) { mitfahrt = null; return; }   // Fahrzeug fort (keine Bahn mehr)
            mode = Mode.FREI;
            autoOrbit = false;
            mitYawS += (mitYaw - mitYawS) * (1 - Math.exp(-dt * 8));
            fx = fxs = q[0]; fy = fys = q[1]; fz = fzs = q[2];
            fyaw = fyaws = q[3] + mitYawS;
            // das Fahrzeug nickt und wankt, das Auge mit (vorn hoch: der Blick voraus hebt sich, zur Seite gilt das Rollen);
            // W11c: mit einem Pendel (Zeitkonstante 0,25 s), das die Stöße des Decks glättet wie ein Kopf auf dem Hals [G]
            double lk = Math.toRadians(mitYawS), bob = q.length > 5 ? q[4] * Math.cos(lk) - q[5] * Math.sin(lk) : q.length > 4 ? q[4] : 0;
            mitBobS += (bob - mitBobS) * (1 - Math.exp(-dt * 4));
            fpitch = fpitchs = mitPitch - mitBobS;
            update();
            return;
        }
        Fahrt ride = fahrt;
        if (ride != null) {
            boolean more = ride.advance(dt);
            double[] q = ride.pose(ride.u);
            mode = Mode.FREI;
            autoOrbit = false;
            fx = fxs = q[0]; fy = fys = q[1]; fz = fzs = q[2];
            // Blick weich nachführen (der Weg knickt an den Toren)
            double k = 1 - Math.exp(-dt * 3);
            double dy = ((q[3] - fyaws + 540) % 360) - 180;
            fyaw = fyaws = fyaws + dy * k;
            fpitch = fpitchs = q[4];
            if (!more) fahrt = null;
            update();
            return;
        }
        if (mode == Mode.KREISEN) {
            if (autoOrbit) yaw += orbitSpeed * dt;
            double f = (kFwd ? 1 : 0) - (kBack ? 1 : 0), s = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
            if (f != 0 || s != 0) {
                double v = (kRun ? 4 : 1) * Math.max(8, dist * 0.6) * dt, a = Math.toRadians(yaw);
                tx += (Math.sin(a) * f + Math.cos(a) * s) * v;
                tz += (-Math.cos(a) * f + Math.sin(a) * s) * v;
            }
            clamp();
            double k = 1 - Math.exp(-dt * 7);
            cx += (tx - cx) * k; cy += (ty - cy) * k; cz += (tz - cz) * k;
            cyaw += (yaw - cyaw) * k; cpitch += (pitch - cpitch) * k;
            cdist = Math.exp(Math.log(cdist) + (Math.log(dist) - Math.log(cdist)) * k);
        } else {
            double f = (kFwd ? 1 : 0) - (kBack ? 1 : 0), s = (kRight ? 1 : 0) - (kLeft ? 1 : 0), u = (kUp ? 1 : 0) - (kDown ? 1 : 0);
            double v = (kRun ? 4 : 1) * 6 * (1 + Math.max(0, fy - groundLevel) / 20) * dt;
            double a = Math.toRadians(fyaw), p = Math.toRadians(fpitch);
            double dx = Math.sin(a) * Math.cos(p), dy = -Math.sin(p), dz = -Math.cos(a) * Math.cos(p);
            fx += (dx * f + Math.cos(a) * s) * v;
            fy += (dy * f + u) * v;
            fz += (dz * f + Math.sin(a) * s) * v;
            fy = Math.max(floor(), Math.min(MAX_EYE, fy));
            double k = 1 - Math.exp(-dt * 10);
            fxs += (fx - fxs) * k; fys += (fy - fys) * k; fzs += (fz - fzs) * k;
            fyaws += (fyaw - fyaws) * k; fpitchs += (fpitch - fpitchs) * k;
        }
        update();
    }

    private void update() {
        if (mode == Mode.KREISEN) {
            setFwd(cyaw, cpitch);
            eye[0] = cx - fwd[0] * cdist;
            eye[1] = cy - fwd[1] * cdist;
            eye[2] = cz - fwd[2] * cdist;
            if (eye[1] < floor()) eye[1] = floor();
        } else {
            setFwd(fyaws, fpitchs);
            eye[0] = fxs; eye[1] = Math.max(floor(), fys); eye[2] = fzs;
        }
    }

    private void setFwd(double yawDeg, double pitchDeg) {
        double a = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        fwd[0] = Math.sin(a) * Math.cos(p);
        fwd[1] = -Math.sin(p);
        fwd[2] = -Math.cos(a) * Math.cos(p);
    }

    public float[] view() {
        return Mat4.lookAt(eye[0], eye[1], eye[2], eye[0] + fwd[0], eye[1] + fwd[1], eye[2] + fwd[2]);
    }

    /** Blickrichtung des laufenden Bilds als Kompasswert 0..360. */
    public double heading() {
        double h = Math.toDegrees(Math.atan2(fwd[0], -fwd[2]));
        return h < 0 ? h + 360 : h;
    }

    /** Abstand vom Auge zum Ursprung (Mitte des Kerns) in der Ebene (m). */
    public double originDistance() { return Math.hypot(eye[0], eye[2]); }

    /** „Kreisen“, „Kreisen (läuft)“, „Frei“ (HUD). */
    public String modeName() { return drohne != null ? "Drohne" : mitfahrt != null ? "Mitfahrt" : mode == Mode.FREI ? "Frei" : autoOrbit ? "Kreisen (läuft)" : "Kreisen"; }
}
