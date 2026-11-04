// strom.frag — der Felssturz der Vorrechnung als Fläche über dem Grund von 1958 (Phase 13b, spur.vert mit STROM):
// Felsfarbe [G] (Schiefer und Gneis der Fairweather-Kette, grau mit braunen Flecken), Blöcke mit Fugen, an der Front
// heller vom Staub. Wo die Mächtigkeit unter 0,3 m fällt, liegt nichts.
// Werkbuch Welle W3c: dreifach projiziert (vorher nur von oben: an den steilen Flanken senkrechte Streifen), Himmelslicht
// nach der Richtung (common.umgebung), unter und dicht über dem Pegel nass und schlammig.
#include "common.glsl"
in vec3 vW;
in vec3 vN;
in float vNass;
in float vH;
in float vSteig;
out vec4 frag;

float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
// W0 (K4): Rauschen mit Periode p (m), das zum Mittel 0,5 ausblendet, sobald ein Bildpunkt mehr als ein Drittel davon deckt
float vnf(vec2 q, float p, float px) { return mix(vn(q / p), 0.5, smoothstep(0.25, 0.6, px / p)); }

// W3 (E2): Blöcke statt Sprenkel. Zellen (Voronoi) mit eigener Helligkeit und dunklen Fugen
vec3 bloecke(vec2 q, float g) {
    vec2 i = floor(q / g), f = q / g - i;
    float d1 = 9.0, d2 = 9.0, id = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 c = vec2(x, y), o = vec2(h2(i + c), h2(i + c + 17.3));
            float d = length(c + o - f);
            if (d < d1) { d2 = d1; d1 = d; id = h2(i + c + 5.1); } else if (d < d2) d2 = d;
        }
    return vec3(id, d2 - d1, d1);
}

// Albedo des Felssturzes in einer Projektion q (m), Bildpunkt px (m)
vec3 fels(vec2 q, float px) {
    float blk = vnf(q, 6.0, px) * 0.5 + vnf(q, 2.1, px) * 0.3 + vnf(q, 0.7, px) * 0.2;
    vec3 grau = vec3(0.21, 0.205, 0.195), braun = vec3(0.25, 0.19, 0.13), hell = vec3(0.44, 0.42, 0.38);
    vec3 alb = mix(grau, braun, smoothstep(0.45, 0.7, vnf(q, 15.0, px)));
    vec3 b5 = bloecke(q, 5.0), b1 = bloecke(q + 11.0, 1.5);
    float k5 = 1.0 - smoothstep(0.6, 1.8, px / 5.0 * 3.0), k1 = 1.0 - smoothstep(0.6, 1.8, px / 1.5 * 3.0);
    float fuge = mix(1.0, smoothstep(0.02, 0.12, b5.y), k5) * mix(1.0, smoothstep(0.02, 0.15, b1.y) * 0.4 + 0.6, k1);
    alb *= mix(1.0, 0.65 + 0.7 * b5.x, k5) * mix(1.0, 0.8 + 0.4 * b1.x, k1) * mix(0.72, 1.0, fuge);
    alb *= 0.75 + 0.5 * mix(0.5, blk, 0.6);
    return mix(alb, hell, smoothstep(0.75, 0.95, blk) * 0.5);
}

void main() {
    if (vNass < 0.5) discard;
    vec3 n = normalize(vN);
    float px = max(length(fwidth(vW.xz)), 1e-4);
    float pyx = max(length(fwidth(vW.xy)), 1e-4), pyz = max(length(fwidth(vW.zy)), 1e-4);
    // dreifach projiziert: von oben und an steilen Flanken von der Seite
    vec3 bw = pow(abs(n), vec3(4.0));
    bw /= bw.x + bw.y + bw.z;
    vec3 alb = fels(vW.xz, px) * bw.y;
    if (bw.x > 0.02) alb += fels(vW.zy * vec2(1.0, 1.3) + 101.0, pyz) * bw.x;
    if (bw.z > 0.02) alb += fels(vW.xy * vec2(1.0, 1.3) + 57.0, pyx) * bw.z;
    // Staub an der Front: wo der Strom schnell wächst, heller [G]; nass und schlammig am Pegel
    float nass = 1.0 - smoothstep(uLevel + 1.0, uLevel + 14.0, vH);
    alb = mix(alb, alb * vec3(0.62, 0.58, 0.52), nass);
    vec3 col = alb * (sunAt(vW) * max(dot(n, uSun), 0.0) + umgebung(n));
    vec3 d = vW - uCam;
    float dist = length(d);
    col = aerial(col, d / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
