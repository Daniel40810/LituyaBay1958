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
    if (uStaub == 1) {
        float n = uStaubGeo.w, c = uStaubGeo.z;
        vec3 lo = vec3(uStaubGeo.x - 0.5 * c, uStaubY.x, uStaubGeo.y - (n - 0.5) * c);
        vec3 hi = vec3(uStaubGeo.x + (n - 0.5) * c, uStaubY.y, uStaubGeo.y + 0.5 * c);
        vec3 inv = 1.0 / (d + vec3(1e-9));
        vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
        vec3 tn = min(t0, t1), tf = max(t0, t1);
        float a = max(max(tn.x, tn.y), max(tn.z, 0.0)), b = min(min(tf.x, tf.y), min(tf.z, dist));
        if (b > a) {
            const int N = 48;
            float ds = (b - a) / float(N);
            // W0 (K4): der Versatz wandert von Bild zu Bild (goldener Schnitt); fest je Bildpunkt war er Gesprenkel am Rand der Wolke
            float j = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453 + uBild * 0.61803399);
            float cs = dot(d, uSun), g = 0.6;
            float hg = (1.0 - g * g) / (4.0 * 3.14159265 * pow(1.0 + g * g - 2.0 * g * cs, 1.5));
            vec3 sonne = sunLight() * hg * 4.0 * 3.14159265;
            vec3 himmel = skyColor(vec3(0.0, 1.0, 0.0)) * 0.6 + hazeColor(d) * 0.6 * (0.5 + 0.5 * cs);
            vec3 alb = vec3(0.62, 0.57, 0.50) * 0.8;
            for (int i = 0; i < N; i++) {
                vec3 p = uCam + d * (a + (float(i) + j) * ds);
                vec2 uv = staubUV(p);
                if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) continue;
                vec2 sa = texture(uStaubA, uv).rg, sb = texture(uStaubB, uv).rg;
                vec2 s = mix(sa, sb, uF);
                float base = texture(uBoden, uv).r;
                if (s.y < 1e-3 || p.y < base || p.y > s.x) continue;
                float dicke = max(s.x - base, 5.0);
                float sigma = s.y / dicke;
                // Schatten der Wolke selbst: der Anteil der Säule über dem Punkt, schräg zur Sonne
                float ueber = s.y * (s.x - p.y) / dicke / max(uSun.y + 0.15, 0.1);
                vec3 Li = alb * (sonne * exp(-ueber) + himmel);
                float tr = exp(-sigma * ds);
                L += T * Li * (1.0 - tr);
                T *= tr;
                if (T < 0.01) break;
            }
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
