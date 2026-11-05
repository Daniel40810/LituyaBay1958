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
uniform vec4 uStaubGeo;    // x der Spalte 0, z der Zeile 0 (Süden), Zelle, Zellen je Seite
uniform vec2 uStaubY;      // Hülle in der Höhe: unten, oben
// Werkbuch Welle W3b (E4, E5): Gischt aus den Daten der Welle statt einer Kuppel um einen Punkt (W3: „ein Ball, der ins
// Schwimmbecken fällt“). Je Ort Ankunft der Front (Ereigniszeit) und höchster Stand aus der Vorrechnung (uFlut, RG32F,
// Rechteck x0, z0 Süden, Breite, Tiefe); Grund aus Licht (uGrund, Rechteck x0, z0, Kante). Wo die Front ankommt, steht
// über dem Wasser eine Wand aus Gischt, umso höher, je höher die Welle dort über dem Pegel aufläuft (am Hang gegenüber bis
// über 500 m); sie fällt in Sekunden zurück und verweht als Schleier [G]. So läuft der Vorhang mit der Front durch die
// Bucht und den Hang hinauf. uGischt: an, Ereigniszeit jetzt, Nachlauf (s).
uniform vec3 uGischt;
uniform sampler2D uFlut, uGrund;
uniform vec4 uFlutRect;
uniform vec3 uGrundRect;
uniform vec4 uGischtBox;   // Rechteck der wirksamen Gischt jetzt: x0, z0, x1, z1 (Szene)
uniform vec2 uGischtY;     // unten, oben

// Werkbuch Welle W3c, Frage WF15 B: die Staubfahne über dem Abriss bis rund 1 200 m [G]. uFahne: Achse x, z, Oberkante
// jetzt (m), Alter seit dem Abbruch (s); uFahneR: Halbmesser der Platte (m), Wind (Richtung x, z, nach der er weht).
uniform int uFahneAn;
uniform vec4 uFahne;
uniform vec3 uFahneR;

// 3D-Rauschen aus einer kachelbaren Textur 64³ (W3: mit Sinus-Hashes kostete der Vorhang über 100 ms je Bild)
uniform sampler3D uRausch;
float vn3(vec3 x) { return texture(uRausch, x / 64.0).r; }
float fbm3(vec3 p) { return vn3(p) * 0.6 + vn3(p * 2.03 + 7.1) * 0.3 + vn3(p * 4.1 + 3.3) * 0.1; }
float fbm2(vec3 p) { return vn3(p) * 0.67 + vn3(p * 2.03 + 7.1) * 0.33; }

// Staub (E3): Dichte (1/m) an p aus Oberkante s.x und optischer Dicke s.y über dem Grund base. Weicher Fuß, aufquellende
// Oberkante, Turbulenz; die mittlere optische Dicke der Säule bleibt die der Vorrechnung [G].
float staubDichte(vec3 p, vec2 s, float base) {
    if (s.y < 1e-3) return 0.0;
    float dicke = max(s.x - base, 5.0);
    // W3c (fünfte Probe): die dichte Wolke stand mit glatter Oberkante und dunklem Inneren wie ein Tafelberg im Inlet.
    // Jetzt quillt die Oberkante in Ballen (3D), sie läuft weicher aus, und das Innere bekommt mehr Himmelslicht (unten)
    float n = fbm3(p / 85.0 + vec3(0.0, -uTime * 0.012, uTime * 0.008));
    float n2 = vn3(p / 30.0 + vec3(uTime * 0.01, -uTime * 0.02, 0.0));
    float oben = s.x + (n - 0.5) * 1.6 * dicke + (n2 - 0.5) * 0.35 * dicke;
    float f = smoothstep(base - 2.0, base + 0.12 * dicke, p.y) * (1.0 - smoothstep(oben - 0.6 * dicke, oben, p.y));
    // Turbulenz mit Lücken (erste Probe auf der Radeon: 0,35–1,65 ließ die dichte Wolke eine glatte Fläche bleiben)
    float t = fbm3(p / 28.0 + vec3(uTime * 0.02, -uTime * 0.03, 0.0));
    t = pow(t, 2.2) * 3.2 * (0.6 + 0.8 * vn3(p / 90.0 + 3.7));
    return s.y / dicke * f * t * 0.7;
}

// W4b Runde 3: wo die Lippe als Netz die Röhre bildet (Bucht, Welle 8–80 m, tiefes Wasser), vor und unter der Krone, in
// den ersten Sekunden: 1, sonst 0. Dort lichten sich Gischt, Kammgischt und der Staub der Vorrechnung; sie lagen als
// lilagrauer Schleier vor der Röhre, deren Rückwand im Wasser selbst schon dunkel war (Probe mit Farben) [G]
float roehreMaske(vec3 p) {
    if (p.y > uLevel + 85.0) return 0.0;   // über jeder Krone: ohne Abfrage weiter (Kosten im Strahl)
    vec2 fu = vec2((p.x - uFlutRect.x) / uFlutRect.z, (uFlutRect.y - p.z) / uFlutRect.w);
    if (any(lessThan(fu, vec2(0.0))) || any(greaterThan(fu, vec2(1.0)))) return 0.0;
    vec2 am = textureLod(uFlut, fu, 0.0).rg;
    if (am.x > 1e5) return 0.0;
    float alter = uGischt.y - am.x, hw = am.y - uLevel;
    // (ohne Bedingung an den Grund: die Textur des Grunds trägt über Wasser nicht die Tiefe, die Maske war dort null)
    return smoothstep(8.0, 14.0, hw) * (1.0 - smoothstep(60.0, 80.0, hw))
         * smoothstep(-6.0, -2.0, alter) * (1.0 - smoothstep(0.8, 2.5, alter)) * (1.0 - smoothstep(am.y - 0.15 * hw, am.y + 0.05 * hw, p.y));
}

// Gischt an p (Dichte 1/m), Schlamm im Fuß der Wand (y) [G]
vec2 gischtDichte(vec3 p) {
    // Ort der Abfrage um bis zu 25 m verschoben, damit die Wand keine senkrechten Kanten aus dem Gitter bekommt
    vec2 jit = (vec2(vn3(p / 40.0 + 11.0), vn3(p / 40.0 + 29.0)) - 0.5) * 50.0;
    vec2 fu = vec2((p.x + jit.x - uFlutRect.x) / uFlutRect.z, (uFlutRect.y - p.z - jit.y) / uFlutRect.w);
    if (any(lessThan(fu, vec2(0.0))) || any(greaterThan(fu, vec2(1.0)))) return vec2(0.0);
    vec2 am = textureLod(uFlut, fu, 0.0).rg;
    float alter = uGischt.y - am.x;
    if (alter < -0.3 || alter > uGischt.z) return vec2(0.0);
    float hw = am.y - uLevel;                       // wie hoch die Welle hier über dem Pegel stand
    if (hw < 2.0) return vec2(0.0);
    vec2 gu = (p.xz - uGrundRect.xy) / uGrundRect.z;
    float grund = textureLod(uGrund, gu, 0.0).r;
    float fuss = max(grund, uLevel);
    float k = pow(clamp((hw - 2.0) / 60.0, 0.0, 1.0), 0.7);
    // Werkbuch Welle W5 (E11): am Hang (Auflauf) stand über der ganzen Zunge dieselbe Wand wie über der Bucht, bis 120 m
    // über dem höchsten Stand, dicht und lange: in H6 lag der Sporn hinter einem rosa Schleier, die Zunge war nicht zu sehen.
    // An Land jetzt nach der Tiefe der Zunge (8 m + 1,3 × Tiefe, höchstens rund 60 m), kürzer und lichter; die Schleier,
    // die oben zurückfallen, zeichnet gl.SchleierGl als Fäden [G]
    // (Probe W5D: auch mit halber Dichte lag die Zunge im Nebel; die Wand stand vor allem über dem Wasser am Fuß des Hangs,
    // wo die Welle 250–300 m hoch aufläuft. Über dem Inlet nahe dem Sporn deshalb dieselbe Form wie an Land.)
    float land = max(smoothstep(uLevel + 4.0, uLevel + 40.0, grund), smoothstep(150.0, 260.0, hw) * 0.85);
    float tiefeMax = max(am.y - grund, 0.0);
    // Wand: in rund 1,5 s auf ihre Höhe über dem höchsten Stand, dann fällt sie zurück; danach ein Schleier
    float H = mix(12.0 + 110.0 * k, 8.0 + min(1.3 * tiefeMax, 52.0), land);
    float auf = 1.0 - exp(-max(alter, 0.0) / 1.5);
    float top = am.y + H * auf - 4.9 * max(alter - 3.0, 0.0) * max(alter - 3.0, 0.0) * 0.35;
    float schleierTop = am.y + H * 0.8;
    float y = p.y;
    if (y < fuss - 3.0) return vec2(0.0);
    float wand = smoothstep(-0.3, 0.4, alter) * exp(-max(alter, 0.0) / mix(4.0, 2.0, land))
               * smoothstep(fuss - 3.0, fuss + 4.0, y) * (1.0 - smoothstep(top - 0.45 * H, top, y));
    // W4b Runde 3: vor und unter der Krone, wo die Lippe als Netz die Röhre bildet (Bucht, 8–80 m), lichtet sich die Gischt:
    // der Schleier lag vor der Röhre, ihr Inneres blieb lilagrau [G]
    float roehre = roehreMaske(p);
    wand *= 1.0 - 0.85 * roehre;
    float schleier = smoothstep(2.0, 8.0, alter) * exp(-alter / 18.0) * 0.07
               * smoothstep(fuss - 3.0, fuss + 8.0, y) * (1.0 - smoothstep(fuss + 0.2 * (schleierTop - fuss), schleierTop + 30.0, y));
    schleier *= 1.0 - 0.75 * land;
    float d = (wand + schleier) * (1.0 - 0.72 * land);
    // W4b (E9) Kammgischt [G]: von der Krone reißt Gischt ab und weht gegen die Laufrichtung zurück. Dicht über dem
    // höchsten Stand, steigt in rund 1 s bis gut die halbe Wellenhöhe, lebt einige Sekunden hinter der Front; Fäden
    // längs der Laufrichtung (aus dem Gefälle der Ankunft), nicht senkrecht wie der Vorhang aus W3b.
    float kammG = 0.0;
    if (alter > -0.2 && alter < 7.0 && hw > 8.0) {
        float e = 16.0;
        vec2 ex = vec2(e / uFlutRect.z, 0.0), ez = vec2(0.0, e / uFlutRect.w);
        float ax = textureLod(uFlut, fu + ex, 0.0).r, bx = textureLod(uFlut, fu - ex, 0.0).r;
        float zp = textureLod(uFlut, fu - ez, 0.0).r, zm = textureLod(uFlut, fu + ez, 0.0).r;   // +z ist −v
        if (ax < 1e5 && bx < 1e5 && zp < 1e5 && zm < 1e5) {
            vec2 g = vec2(ax - bx, zp - zm);
            vec2 T = length(g) > 1e-5 ? normalize(g) : vec2(1.0, 0.0), Tq = vec2(-T.y, T.x);
            float lo = am.y - 0.25 * hw, hi = am.y + hw * (0.12 + 0.55 * (1.0 - exp(-max(alter, 0.0) / 1.0)));
            float env = smoothstep(-0.2, 0.3, alter) * exp(-max(alter, 0.0) / 2.2)
                      * smoothstep(lo, lo + 0.2 * hw, p.y) * (1.0 - smoothstep(mix(lo, hi, 0.55), hi, p.y));
            env *= 1.0 - 0.9 * roehre;   // Runde 3: nicht vor der Röhre
            if (env > 1e-3) {
                vec3 qk = vec3(dot(p.xz, T) / 40.0 + uGischt.y * 0.9, p.y / 9.0, dot(p.xz, Tq) / 13.0);
                float fil = smoothstep(0.40, 0.72, fbm2(qk)) * (0.5 + 0.9 * smoothstep(0.35, 0.8, vn3(vec3(dot(p.xz, T) / 14.0 + uGischt.y * 2.0, p.y / 3.0, dot(p.xz, Tq) / 4.0))));
                kammG = env * fil * smoothstep(8.0, 30.0, hw) * 0.25;   // W4b Probe 1: mit 0,09 unsichtbar
            }
        }
    }
    if (d < 1e-3 && kammG < 1e-4) return vec2(0.0);
    // zerrissen: Fetzen, die steigen (Wand) und verwehen; senkrechte Strähnen [G]
    // W3c: stärker senkrecht gestreift (Strähnen, die steigen und fallen), Fetzen schärfer begrenzt [G]
    vec3 q = p / vec3(20.0, 64.0, 20.0) + vec3(0.0, -uGischt.y * 0.35, 0.0);
    float nv = fbm2(q + vec3(am.x * 0.05));
    float f = smoothstep(0.42, 0.66, nv) * (0.25 + 1.4 * smoothstep(0.3, 0.75, vn3(p / vec3(6.0, 26.0, 6.0) + vec3(0.0, -uGischt.y * 0.8, 0.0))));
    float dichte = d * f * k * 0.05 + kammG;
    float schlamm = wand * f * k * 0.02 * (1.0 - smoothstep(fuss + 10.0, fuss + 60.0, y)) * smoothstep(5.0, 40.0, hw);
    return vec2(dichte, schlamm);
}

// Fahne (WF15 B): Dichte (1/m) und Tiefe zur Sonne (für das Glühen oben und das Blau unten) [G]
vec2 fahneDichte(vec3 p) {
    float alter = uFahne.w, top = uFahne.z;
    float y = p.y - uLevel;
    if (y < -5.0 || p.y > top + 220.0) return vec2(0.0);
    float hrel = clamp(y / max(top - uLevel, 30.0), 0.0, 1.2);
    // die Achse neigt sich mit der Höhe in den Wind, zum Kopf hin stärker
    vec2 ax = uFahne.xy + uFahneR.yz * y * 0.10 * smoothstep(0.0, 90.0, alter) * (0.6 + 0.8 * hrel);
    if (length(p.xz - ax) > uFahneR.x * 2.6 + 60.0) return vec2(0.0);   // grob außerhalb: ohne Rauschen weiter
    // Säule über der Platte, nach oben breiter, Kopf quillt pilzförmig auf [G]
    float R = uFahneR.x * (0.55 + 0.6 * hrel) * (0.6 + 0.4 * smoothstep(0.0, 40.0, alter));
    R *= 1.0 + 0.7 * smoothstep(0.72, 1.0, hrel);
    float bq = fbm3(p / 150.0 + vec3(0.0, -alter * 0.02, alter * 0.006));
    float bq2 = vn3(p / 55.0 + vec3(0.0, -alter * 0.06, 0.0));
    float r = length(p.xz - ax) + (bq - 0.5) * 2.6 * R + (bq2 - 0.5) * 0.5 * R;   // ausgefranster Rand
    float form = 1.0 - smoothstep(0.45 * R, R, r);
    float kopf = 1.0 - smoothstep(top - 0.18 * (top - uLevel), top + 60.0, p.y + (bq - 0.5) * 380.0 + (bq2 - 0.5) * 90.0);
    float fuss = smoothstep(-5.0, 60.0, y);
    if (form * kopf * fuss < 1e-3) return vec2(0.0);   // außerhalb der Hülle: die Ballen sparen
    // Ballen (Blumenkohl): zwei Lagen, die mitsteigen; dazwischen Lücken
    float ballen = fbm3(p / 95.0 + vec3(alter * 0.01, -alter * 0.045, 0.0));
    ballen = smoothstep(0.40, 0.60, ballen) * (0.45 + 1.1 * smoothstep(0.3, 0.7, vn3(p / 26.0 + vec3(0.0, -alter * 0.11, 0.0))));
    float lebt = smoothstep(0.0, 6.0, alter) * (1.0 - smoothstep(200.0, 900.0, alter));
    float dichte = form * kopf * fuss * ballen * lebt * 0.0045 * mix(1.3, 0.55, hrel);
    // Tiefe zur Sonne: bis zur Oberkante und zum Rand (grob) [G]
    float zurSonne = 0.0045 * lebt * (max(top - p.y, 0.0) * 0.35 + max(R - r, 0.0) * 0.6);
    return vec2(dichte, zurSonne);
}

float hg(float cs, float g) { return (1.0 - g * g) / (4.0 * 3.14159265 * pow(1.0 + g * g - 2.0 * g * cs, 1.5)); }


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
    vec3 inv = 1.0 / (d + vec3(1e-9));
    if (uStaub == 1) {
        float n = uStaubGeo.w, c = uStaubGeo.z;
        vec3 lo = vec3(uStaubGeo.x - 0.5 * c, uStaubY.x, uStaubGeo.y - (n - 0.5) * c);
        vec3 hi = vec3(uStaubGeo.x + (n - 0.5) * c, uStaubY.y + 0.3 * (uStaubY.y - uStaubY.x), uStaubGeo.y + 0.5 * c);
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float aa = max(max(tn.x, tn.y), max(tn.z, 0.0)), bb = min(min(tf.x, tf.y), min(tf.z, dist));
        if (bb > aa) { a = min(a, aa); b = max(b, bb); }
    }
    if (uGischt.x > 0.5) {
        vec3 lo = vec3(min(uGischtBox.x, uGischtBox.z), uGischtY.x, min(uGischtBox.y, uGischtBox.w)),
             hi = vec3(max(uGischtBox.x, uGischtBox.z), uGischtY.y, max(uGischtBox.y, uGischtBox.w));
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float aa = max(max(tn.x, tn.y), max(tn.z, 0.0)), bb = min(min(tf.x, tf.y), min(tf.z, dist));
        if (bb > aa) { a = min(a, aa); b = max(b, bb); }
    }
    if (uFahneAn == 1) {
        float Rm = uFahneR.x * 2.2 + 0.25 * uFahne.z + 120.0;
        vec3 lo = vec3(uFahne.x - Rm, uLevel - 5.0, uFahne.y - Rm), hi = vec3(uFahne.x + Rm, uFahne.z + 240.0, uFahne.y + Rm);
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float aa = max(max(tn.x, tn.y), max(tn.z, 0.0)), bb = min(min(tf.x, tf.y), min(tf.z, dist));
        if (bb > aa) { a = min(a, aa); b = max(b, bb); }
    }
    if (b > a) {
        const int N = 64;
        float ds = (b - a) / float(N);
        // W0 (K4): der Versatz wandert von Bild zu Bild; die Kantenglättung mittelt ihn
        float j = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453 + uBild * 0.61803399);
        float cs = dot(d, uSun);
        float phStaub = hg(cs, 0.6) * 4.0 * 3.14159265;
        float phGischt = (0.8 * hg(cs, 0.75) + 0.2 * hg(cs, -0.25)) * 4.0 * 3.14159265;   // Gischt im Gegenlicht
        vec3 himmel = skyColor(vec3(0.0, 1.0, 0.0)) * 0.6 + hazeColor(d) * 0.6 * (0.5 + 0.5 * cs);
        vec3 albStaub = vec3(0.62, 0.57, 0.50) * 0.8, albGischt = vec3(0.92, 0.95, 0.97), albSchlamm = vec3(0.30, 0.27, 0.22);
        for (int i = 0; i < N; i++) {
            vec3 p = uCam + d * (a + (float(i) + j) * ds);
            float sigS = 0.0, ueber = 0.0;
            if (uStaub == 1) {
                vec2 uv = staubUV(p);
                if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
                    vec2 sa = texture(uStaubA, uv).rg, sb = texture(uStaubB, uv).rg;
                    vec2 s = mix(sa, sb, uF);
                    float base = texture(uBoden, uv).r;
                    sigS = staubDichte(p, s, base);
                    if (uGischt.x > 0.5) sigS *= 1.0 - 0.85 * roehreMaske(p);   // W4b Runde 3: nicht vor der Röhre
                    float dicke = max(s.x - base, 5.0);
                    ueber = s.y * clamp((s.x - p.y) / dicke, 0.0, 1.0) / max(uSun.y + 0.15, 0.1);
                }
            }
            vec2 v = uGischt.x > 0.5 ? gischtDichte(p) : vec2(0.0);
            vec2 fh = uFahneAn == 1 ? fahneDichte(p) : vec2(0.0);
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
            vec3 glut = hazeColor(normalize(vec3(uSun.x, 0.04, uSun.z))) * (0.35 + 0.65 * hg(cs, 0.45) * 4.0 * 3.14159265 * 0.35)
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
