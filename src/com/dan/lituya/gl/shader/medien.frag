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
    float n = vn3(p / 70.0 + vec3(0.0, -uTime * 0.012, uTime * 0.008));
    float oben = s.x + (n - 0.45) * 0.45 * dicke;
    float f = smoothstep(base - 2.0, base + 0.12 * dicke, p.y) * (1.0 - smoothstep(oben - 0.35 * dicke, oben, p.y));
    // Turbulenz mit Lücken (erste Probe auf der Radeon: 0,35–1,65 ließ die dichte Wolke eine glatte Fläche bleiben)
    float t = fbm3(p / 28.0 + vec3(uTime * 0.02, -uTime * 0.03, 0.0));
    t = pow(t, 2.2) * 3.2 * (0.6 + 0.8 * vn3(p / 90.0 + 3.7));
    return s.y / dicke * f * t;
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
    // Wand: in rund 1,5 s auf ihre Höhe über dem höchsten Stand, dann fällt sie zurück; danach ein Schleier
    float H = 12.0 + 110.0 * k;
    float auf = 1.0 - exp(-max(alter, 0.0) / 1.5);
    float top = am.y + H * auf - 4.9 * max(alter - 3.0, 0.0) * max(alter - 3.0, 0.0) * 0.35;
    float schleierTop = am.y + H * 0.8;
    float y = p.y;
    if (y < fuss - 3.0) return vec2(0.0);
    float wand = smoothstep(-0.3, 0.4, alter) * exp(-max(alter, 0.0) / 4.0)
               * smoothstep(fuss - 3.0, fuss + 4.0, y) * (1.0 - smoothstep(top - 0.45 * H, top, y));
    float schleier = smoothstep(2.0, 8.0, alter) * exp(-alter / 18.0) * 0.07
               * smoothstep(fuss - 3.0, fuss + 8.0, y) * (1.0 - smoothstep(fuss + 0.2 * (schleierTop - fuss), schleierTop + 30.0, y));
    float d = wand + schleier;
    if (d < 1e-3) return vec2(0.0);
    // zerrissen: Fetzen, die steigen (Wand) und verwehen; senkrechte Strähnen [G]
    vec3 q = p / vec3(26.0, 48.0, 26.0) + vec3(0.0, -uGischt.y * 0.35, 0.0);
    float nv = fbm2(q + vec3(am.x * 0.05));
    float f = smoothstep(0.38, 0.72, nv) * (0.4 + 1.1 * vn3(p / 9.0 + vec3(0.0, -uGischt.y * 0.8, 0.0)));
    float dichte = d * f * k * 0.05;
    float schlamm = wand * f * k * 0.02 * (1.0 - smoothstep(fuss + 10.0, fuss + 60.0, y)) * smoothstep(5.0, 40.0, hw);
    return vec2(dichte, schlamm);
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
                    float dicke = max(s.x - base, 5.0);
                    ueber = s.y * clamp((s.x - p.y) / dicke, 0.0, 1.0) / max(uSun.y + 0.15, 0.1);
                }
            }
            vec2 v = uGischt.x > 0.5 ? gischtDichte(p) : vec2(0.0);
            float sig = sigS + v.x + v.y;
            if (sig < 1e-6) continue;
            vec3 sonne = sunAt(p);
            // Himmelslicht dringt nur ein Stück ein: innen und unten dunkler (gibt der dichten Wolke Form) [G]
            vec3 Ls = albStaub * (sonne * phStaub * exp(-ueber) + himmel * (0.18 + 0.82 * exp(-ueber * 0.35)));
            vec3 Lg = albGischt * (sonne * phGischt * exp(-v.x * 120.0) + himmel * 1.1);
            vec3 Lm = albSchlamm * (sonne * phStaub * exp(-v.y * 150.0) + himmel);
            vec3 Li = (Ls * sigS + Lg * v.x + Lm * v.y) / sig;
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
