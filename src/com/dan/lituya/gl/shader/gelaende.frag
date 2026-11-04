// gelaende.frag — das Gelände als Platzhalter (Phase 4, bis surface in Phase 5 und Grundbild in 5a): Farbe nach Höhe und
// Hang [G], beleuchtet mit Sonne und Himmel aus common.glsl, mit den Marken und dem Dunst wie das Wasser.
// Farben [G]: Ufer Kies grau, Regenwald (Sitka-Fichte, Hemlock) dunkelgrün bis rund 500 m (die Spurlinie 1958 reicht bis
// 524 m), darüber Gebüsch und Fels, Fels an steilen Hängen (Schiefer), Schnee und Firn ab rund 1 000 m, wo es flach genug ist.
// Unter dem Pegel zeichnet das Wasser darüber (meer.frag schreibt seine Tiefe).
#include "common.glsl"
#include "marken.glsl"
#include "/com/dan/clipmap/gl/clipmap.glsl"
in vec3 vW;
in vec3 vN;
in float vH;
out vec4 frag;
// ab Phase 14a: der Boden im Band der Welle 1958 (Fels, Mineralboden, Waldboden des gezeigten Jahrs, linear) aus WaldGl;
// Deckel 1 im Band. Rechteck x0, z0, Breite, Tiefe (Szene).
uniform int uBandAn;
uniform sampler2D uBand;
uniform vec4 uBandRect;
// Werkbuch Welle W0 (K3): Kronendach hinter den Einzelbäumen aus dem Dichtegitter des Walds (r Sitka-Fichte,
// g Hemlocktanne, b Roterle, 0..1); Rechteck x0, z0, Breite, Tiefe; uKroneAb: ab wo die Bäume ausdünnen und wo sie enden (m).
uniform int uKroneAn;
uniform sampler2D uKrone;
uniform vec4 uKroneRect;
uniform vec2 uKroneAb;
// Werkbuch Welle W3 (E2): die Abrissfläche des Felssturzes (R8, 1 = Platte vor dem Abgang) ab dem Abbruch: frischer,
// heller Fels (Schiefer und Gneis, frisch gebrochen [G]) mit Blöcken und Rinnen; heute verwittert (uAbrissFrisch < 1).
uniform int uAbrissAn;
uniform sampler2D uAbriss;
uniform vec4 uAbrissRect;     // x0, z0 (Süden), Breite, Tiefe
uniform float uAbrissFrisch;

float kh(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float kn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(kh(i), kh(i + vec2(1, 0)), f.x), mix(kh(i + vec2(0, 1)), kh(i + vec2(1, 1)), f.x), f.y);
}
// Rauschen mit Periode p (m), zum Mittel 0,5, sobald ein Bildpunkt (px) mehr als ein Drittel der Periode deckt
float knf(vec2 q, float p, float px) { return mix(kn(q / p), 0.5, smoothstep(0.25, 0.6, px / p)); }

void main() {
    if (clipmapHidden(vW.xz)) discard;
    vec3 n = normalize(vN);
    float slope = 1.0 - n.y;                          // 0 flach, 0,29 bei 45°
    float h = vH - uLevel;
    vec3 kies = vec3(0.30, 0.29, 0.27), wald = vec3(0.045, 0.075, 0.045), busch = vec3(0.10, 0.12, 0.07),
         fels = vec3(0.21, 0.20, 0.19), schnee = vec3(0.80, 0.83, 0.88), grund = vec3(0.10, 0.11, 0.10);
    vec3 alb = mix(wald, busch, smoothstep(350.0, 650.0, h));
    alb = mix(alb, fels, smoothstep(0.30, 0.45, slope));
    alb = mix(kies, alb, smoothstep(2.0, 8.0, h));
    alb = mix(alb, schnee, smoothstep(900.0, 1300.0, h) * (1.0 - smoothstep(0.40, 0.60, slope)));
    vec3 dc = vW - uCam;
    float distC = length(dc);
    float krone = 0.0;   // Anteil Kronendach (dunkler Schatten zwischen den Kronen, für das Licht)
    if (uKroneAn == 1) {
        vec2 ku = (vW.xz - uKroneRect.xy) / uKroneRect.zw;
        if (ku.x > 0.0 && ku.y > 0.0 && ku.x < 1.0 && ku.y < 1.0) {
            vec3 dn = texture(uKrone, ku).rgb;
            float dichte = clamp(dn.r + dn.g + dn.b, 0.0, 1.0);
            // erst wo die Einzelbäume ausdünnen, voll dort, wo sie enden
            krone = dichte * smoothstep(uKroneAb.x, mix(uKroneAb.x, uKroneAb.y, 0.85), distC) * smoothstep(1.0, 4.0, h);
            if (krone > 0.0) {
                float px = max(length(fwidth(vW.xz)), 1e-3);
                // Kronen: Nadelbäume dunkel blaugrün, Erlen heller gelbgrün [G]; Gruppen (25 m) und Einzelkronen (7 m)
                float erle = dn.b / max(dichte, 1e-3);
                vec3 nadel = vec3(0.028, 0.048, 0.034), laub = vec3(0.060, 0.085, 0.038);
                // Bestände (90 m), Gruppen (25 m), Kronen (9 m); was unter einem Bildpunkt liegt, geht ins Mittel
                float bst = knf(vW.xz + 41.0, 90.0, px), grp = knf(vW.xz, 25.0, px), kr = knf(vW.xz + 13.0, 9.0, px);
                vec3 kc = mix(nadel, laub, erle) * (0.55 + 0.9 * bst) * (0.55 + 0.9 * grp) * (0.6 + 0.8 * kr);
                alb = mix(alb, kc, krone);
            }
        }
    }
    if (uBandAn == 1) {
        vec2 bu = (vW.xz - uBandRect.xy) / uBandRect.zw;
        if (bu.x > 0.0 && bu.y > 0.0 && bu.x < 1.0 && bu.y < 1.0) {
            vec4 b = texture(uBand, bu);
            alb = mix(alb, b.rgb, b.a * smoothstep(0.5, 2.0, h));
        }
    }
    if (uAbrissAn == 1) {
        vec2 au = vec2((vW.x - uAbrissRect.x) / uAbrissRect.z, (uAbrissRect.y - vW.z) / uAbrissRect.w);
        if (au.x > 0.0 && au.y > 0.0 && au.x < 1.0 && au.y < 1.0) {
            float a = texture(uAbriss, au).r;
            if (a > 0.01) {
                float px = max(length(fwidth(vW.xz)), 1e-3);
                float b1 = knf(vW.xz + 7.0, 14.0, px), b2 = knf(vW.xz * vec2(1.0, 0.35) + 3.0, 4.0, px);
                vec3 frisch = mix(vec3(0.46, 0.44, 0.41), vec3(0.58, 0.55, 0.50), b1) * (0.75 + 0.5 * b2);
                vec3 alt = vec3(0.24, 0.23, 0.21) * (0.8 + 0.4 * b1);
                alb = mix(alb, mix(alt, frisch, uAbrissFrisch), smoothstep(0.05, 0.6, a));
            }
        }
    }
    alb = mix(grund, alb, smoothstep(-1.0, 0.5, h));
    float B = skyBrightness(sunElevDeg());
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0));
    // im Kronendach fällt weniger Himmelslicht zwischen die Kronen, die Sonne trifft die Kronen auch am flachen Hang [G]
    float ndl = mix(max(dot(n, uSun), 0.0), max(dot(normalize(n + vec3(0.0, 0.6, 0.0)), uSun), 0.0), krone);
    vec3 col = alb * (sunAt(vW) * ndl + sky * (0.55 + 0.45 * n.y) * (1.0 - 0.25 * krone));
    vec3 d = vW - uCam;
    float dist = length(d);
    col = applyMarks(col, vW.xz, dist, B);
    col = aerial(col, d / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
