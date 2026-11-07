// medien.frag — was zwischen Auge und Szene in der Luft und im Wasser liegt (Phase 13b), als Bild über dem HDR-Puffer
// (Mischung: Ziel · a + Quelle):
//  * Staub des Felssturzes: die Wolke der Vorrechnung (Oberkante und optische Dicke je Zelle zu 25 m, zwei Bilder
//    gemischt) als Schicht zwischen dem Grund von 1958 und der Oberkante; der Strahl läuft in 48 Schritten durch die
//    Hülle, die Dichte ist die optische Dicke durch die Mächtigkeit. Licht: Sonne mit Vorwärtsstreuung (Henyey-Greenstein,
//    g = 0,6 [G], „Staub im Gegenlicht“) und Himmel; beim Stoß steht die Sonne 1° unter der Kimm, dann trägt das Glühen
//    über der Kimm. Albedo 0,8, Farbe grau-braun [G].
//  * Unter Wasser (Auge unter der Fläche der Welle): Trübe mit 25 m Sichtweite aus Gletschermilch und Schlamm [G].
#include "common.glsl"
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDepth;
uniform sampler2D uStaubA, uStaubB, uBoden;   // RG32F (Oberkante, optische Dicke), R32F Grund
uniform float uF, uNear, uFar;
uniform vec3 uFwd;
uniform int uStaub, uUnter;
uniform int uSchritte;      // W5: Schritte des Strahls (vorher fest 64)
uniform vec4 uStaubGeo;    // x der Spalte 0, z der Zeile 0 (Süden), Zelle, Zellen je Seite
uniform vec2 uStaubY;      // Hülle in der Höhe: unten, oben
// Werkbuch Welle W3b (E4, E5), W4b, W5: die Gischt an der Front aus com.dan.surge (surge_spray.glsl: Gischtwand,
// Kammgischt, Schlamm, Maske vor der Röhre), aus der Flutkarte (uSgFlood) und dem Grund (uSgBed); uSgSpray: an,
// Ereigniszeit jetzt, Nachlauf (s). Die Hülle der Gischt jetzt (FloodMap.sprayBox):
uniform vec4 uGischtBox;   // Rechteck der wirksamen Gischt jetzt: x0, z0, x1, z1 (Szene)
uniform vec2 uGischtY;     // unten, oben
#include "/com/dan/surge/gl/surge_spray.glsl"

// Werkbuch Welle W3 (E3), W3c (WF15 B): Staub der Vorrechnung und Staubfahne über dem Abriss bis rund 1 200 m aus
// com.dan.dustcloud (dust.glsl: dustLayerDensity, dustPlumeDensity mit uDcPlume, uDcPlumeR, dustPhaseHG) [G].
uniform int uFahneAn;
#include "/com/dan/dustcloud/gl/dust.glsl"

float sceneDist(vec3 d) {
    float z = texture(uDepth, vUV).r;
    float vz;
    if (uReversed == 1) vz = z <= 0.0 ? 1e9 : uNear / z;
    else { float zn = z * 2.0 - 1.0; vz = z >= 1.0 ? 1e9 : 2.0 * uFar * uNear / ((uFar + uNear) - zn * (uFar - uNear)); }
    return vz / max(dot(d, uFwd), 1e-4);
}

vec2 staubUV(vec3 p) {
    return vec2((p.x - uStaubGeo.x) / uStaubGeo.z + 0.5, (uStaubGeo.y - p.z) / uStaubGeo.z + 0.5) / uStaubGeo.w;
}

void main() {
    vec3 d = viewRay(vUV);
    float dist = sceneDist(d);
    vec3 L = vec3(0.0);
    float T = 1.0;
    // Abschnitt des Strahls: Hülle des Staubs und Zylinder des Vorhangs, vereinigt
    float a = 1e30, b = -1e30;
    // Runde 5: der Abschnitt je Hülle, damit ein Schritt nur die Dichten abfragt, in deren Hülle er liegt (vorher alle drei
    // an jedem Schritt der Vereinigung: in H6 lief der Strahl kilometerweit durch den Staub, die Gischt kostete dort mit)
    vec2 abS = vec2(1e30, -1e30), abG = abS, abF = abS;
    vec3 inv = 1.0 / (d + vec3(1e-9));
    if (uStaub == 1) {
        float n = uStaubGeo.w, c = uStaubGeo.z;
        vec3 lo = vec3(uStaubGeo.x - 0.5 * c, uStaubY.x, uStaubGeo.y - (n - 0.5) * c);
        vec3 hi = vec3(uStaubGeo.x + (n - 0.5) * c, uStaubY.y + 0.3 * (uStaubY.y - uStaubY.x), uStaubGeo.y + 0.5 * c);
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float aa = max(max(tn.x, tn.y), max(tn.z, 0.0)), bb = min(min(tf.x, tf.y), min(tf.z, dist));
        if (bb > aa) { a = min(a, aa); b = max(b, bb); abS = vec2(aa, bb); }
    }
    if (uSgSpray.x > 0.5) {
        vec3 lo = vec3(min(uGischtBox.x, uGischtBox.z), uGischtY.x, min(uGischtBox.y, uGischtBox.w)),
             hi = vec3(max(uGischtBox.x, uGischtBox.z), uGischtY.y, max(uGischtBox.y, uGischtBox.w));
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float aa = max(max(tn.x, tn.y), max(tn.z, 0.0)), bb = min(min(tf.x, tf.y), min(tf.z, dist));
        if (bb > aa) { a = min(a, aa); b = max(b, bb); abG = vec2(aa, bb); }
    }
    if (uFahneAn == 1) {
        float Rm = uDcPlumeR.x * 2.2 + 0.25 * uDcPlume.z + 120.0;
        vec3 lo = vec3(uDcPlume.x - Rm, uLevel - 5.0, uDcPlume.y - Rm), hi = vec3(uDcPlume.x + Rm, uDcPlume.z + 240.0, uDcPlume.y + Rm);
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float aa = max(max(tn.x, tn.y), max(tn.z, 0.0)), bb = min(min(tf.x, tf.y), min(tf.z, dist));
        if (bb > aa) { a = min(a, aa); b = max(b, bb); abF = vec2(aa, bb); }
    }
    if (b > a) {
        int N = clamp(uSchritte, 16, 96);
        float ds = (b - a) / float(N);
        // W0 (K4): der Versatz wandert von Bild zu Bild; die Kantenglättung mittelt ihn
        float j = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453 + uBild * 0.61803399);
        float cs = dot(d, uSun);
        float phStaub = dustPhaseHG(cs, 0.6) * 4.0 * 3.14159265;
        float phGischt = (0.8 * dustPhaseHG(cs, 0.75) + 0.2 * dustPhaseHG(cs, -0.25)) * 4.0 * 3.14159265;   // Gischt im Gegenlicht
        vec3 himmel = skyColor(vec3(0.0, 1.0, 0.0)) * 0.6 + hazeColor(d) * 0.6 * (0.5 + 0.5 * cs);
        vec3 albStaub = vec3(0.62, 0.57, 0.50) * 0.8, albGischt = vec3(0.92, 0.95, 0.97), albSchlamm = vec3(0.30, 0.27, 0.22);
        for (int i = 0; i < N; i++) {
            float tq = a + (float(i) + j) * ds;
            vec3 p = uCam + d * tq;
            float sigS = 0.0, ueber = 0.0;
            if (uStaub == 1 && tq >= abS.x && tq <= abS.y) {
                vec2 uv = staubUV(p);
                if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
                    vec2 sa = texture(uStaubA, uv).rg, sb = texture(uStaubB, uv).rg;
                    vec2 s = mix(sa, sb, uF);
                    float base = texture(uBoden, uv).r;
                    sigS = dustLayerDensity(p, s, base, uTime);
                    if (uSgSpray.x > 0.5) sigS *= 1.0 - 0.85 * surgeTubeMask(p, uLevel);   // W4b Runde 3: nicht vor der Röhre
                    float dicke = max(s.x - base, 5.0);
                    ueber = s.y * clamp((s.x - p.y) / dicke, 0.0, 1.0) / max(uSun.y + 0.15, 0.1);
                }
            }
            vec2 v = uSgSpray.x > 0.5 && tq >= abG.x && tq <= abG.y ? surgeSprayDensity(p, uLevel) : vec2(0.0);
            vec2 fh = uFahneAn == 1 && tq >= abF.x && tq <= abF.y ? dustPlumeDensity(p, uLevel) : vec2(0.0);
            float sig = sigS + v.x + v.y + fh.x;
            if (sig < 1e-6) continue;
            vec3 sonne = sunAt(p);
            // Himmelslicht dringt nur ein Stück ein: innen und unten dunkler (gibt der dichten Wolke Form) [G]
            vec3 Ls = albStaub * 1.25 * (sonne * phStaub * exp(-ueber) + himmel * (0.42 + 0.58 * exp(-ueber * 0.25)));
            // W3c: innen dunkler; W4b: die dichte Kammgischt (bis 0,25/m) dunkelte sich damit selbst zu Gesprenkel vor dem
            // Himmel ab, daher zählt für das Dunkeln nur die Dichte, die der Vorhang aus W3b hat (bis rund 0,02/m)
            float vg = min(v.x, 0.02);
            vec3 Lg = albGischt * (sonne * phGischt * exp(-vg * 120.0) + himmel * (0.55 + 0.6 * exp(-vg * 40.0)));
            vec3 Lm = albSchlamm * (sonne * phStaub * exp(-v.y * 150.0) + himmel);
            // Fahne: oben in der letzten Sonne warm (sunAt gibt dort Rot), innen und unten blaugrau vom Himmel
            // Fahne: feiner Staub, hell (Albedo 0,85); oben sieht sie mehr vom hellen Abendhimmel im Nordwesten und glüht
            // warm, unten bleibt sie blaugrau; wo die Sonne hinkommt, glüht sie rot [G]
            vec3 glut = hazeColor(normalize(vec3(uSun.x, 0.04, uSun.z))) * (0.35 + 0.65 * dustPhaseHG(cs, 0.45) * 4.0 * 3.14159265 * 0.35)
                      * smoothstep(250.0, 1150.0, p.y - uLevel);
            vec3 Lf = vec3(0.86, 0.82, 0.76) * (sonne * phStaub * exp(-fh.y) * 1.3 + himmel * (0.5 + 0.5 * exp(-fh.y * 0.25)) + glut * 1.6 * exp(-fh.y * 0.12));
            vec3 Li = (Ls * sigS + Lg * v.x + Lm * v.y + Lf * fh.x) / sig;
            float tr = exp(-sig * ds);
            // Dunst zwischen Auge und Punkt wie die übrige Szene
            float tp = a + (float(i) + j) * ds;
            Li = aerial(Li, d, tp);
            L += T * Li * (1.0 - tr);
            T *= tr;
            if (T < 0.01) break;
        }
    }
    if (uUnter == 1) {
        float B = skyBrightness(sunElevDeg());
        vec3 w = vec3(0.03, 0.075, 0.07) * (B / 0.32) * (0.35 + 0.65 * max(uSun.y, 0.0));
        float tw = exp(-min(dist, 1e5) / 25.0);
        L = L * tw + w * (1.0 - tw);
        T *= tw;
    }
    frag = vec4(L, T);
}
