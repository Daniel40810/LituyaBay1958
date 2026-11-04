// gelaende.frag — das Gelände; ab Werkbuch Welle W3b Hänge ohne Wald (Band, Flut, Abriss) mit Großform und den Texturen
// aus com.dan.surface. Ursprünglich als Platzhalter (Phase 4, bis surface in Phase 5 und Grundbild in 5a): Farbe nach Höhe und
// Hang [G], beleuchtet mit Sonne und Himmel aus common.glsl, mit den Marken und dem Dunst wie das Wasser.
// Farben [G]: Ufer Kies grau, Regenwald (Sitka-Fichte, Hemlock) dunkelgrün bis rund 500 m (die Spurlinie 1958 reicht bis
// 524 m), darüber Gebüsch und Fels, Fels an steilen Hängen (Schiefer), Schnee und Firn ab rund 1 000 m, wo es flach genug ist.
// Unter dem Pegel zeichnet das Wasser darüber (meer.frag schreibt seine Tiefe).
#include "common.glsl"
#include "marken.glsl"
#include "/com/dan/clipmap/gl/clipmap.glsl"
#include "/com/dan/surface/gl/surface.glsl"
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
// Werkbuch Welle W3b: die Materialien aus com.dan.surface (seit Phase 5 gebacken, nie aufs Gelände gelegt) für Hänge ohne
// Wald: Schichten Fels frisch, Fels verwittert, Schutt (Moräne), Kies; uSurfAn 0 ohne Texturen (dann nur die Großform).
uniform int uSurfAn;
uniform ivec4 uMat;
// ... und die Flut: je Ort Ankunft (Ereigniszeit, s) und höchster Stand (m) der Vorrechnung (RG32F); wo das Wasser stand,
// ist der Hang ab der Ankunft abgeschürft und nass. Rechteck x0, z0 (Süden), Breite, Tiefe; uEreignis: Ereigniszeit jetzt.
uniform int uFlutAn;
uniform sampler2D uFlut;
uniform vec4 uFlutRect;
uniform float uEreignis;

float kh(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float kn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(kh(i), kh(i + vec2(1, 0)), f.x), mix(kh(i + vec2(0, 1)), kh(i + vec2(1, 1)), f.x), f.y);
}
// Rauschen mit Periode p (m), zum Mittel 0,5, sobald ein Bildpunkt (px) mehr als ein Drittel der Periode deckt
float knf(vec2 q, float p, float px) { return mix(kn(q / p), 0.5, smoothstep(0.25, 0.6, px / p)); }
// gestrecktes Rauschen: quer Periode pa, längs pb (m), gefiltert nach der kurzen Periode
float rinne(vec2 q, float pa, float pb, float px) { return mix(kn(vec2(q.x / pa, q.y / pb)), 0.5, smoothstep(0.25, 0.6, px / pa)); }

// W3b: ein Material aus com.dan.surface (Ableitungen vorher genommen); Gewicht w ≤ 0 tastet nicht ab
Surf mat(int L, vec3 p, vec3 dpw, vec3 dpv, vec3 n, float px) { return surfSampleD(L, p, dpw, dpv, n, px); }

/*
 * Werkbuch Welle W3b: ein Hang ohne Wald (abgeschürftes Band, Abrissfläche) [G]. fels: Anteil anstehender Fels (sonst
 * Schutt, Mineralboden), kies: Ufer, frisch: 1 frisch gebrochen und hell, 0 verwittert; nass: dunkler und glatter.
 * Großform, die auch in Kilometern trägt: Flecken von Fels und Schutt (150 m, 45 m), Rinnen in der Falllinie (quer 6 und
 * 22 m, längs 55 und 170 m), im Fels Bänke quer zum Hang (rund 9 m); Rinnen dunkler und feucht, Rippen heller, dazu eine
 * leichte Neigung der Normale über die Rinnen (Relief im flachen Licht). In der Nähe das Muster der Texturen, auf die
 * Farbe der Großform gezogen.
 */
vec3 hang(vec3 p, inout vec3 n, float px, float pyx, vec3 dpw, vec3 dpv, float fels, float kies, float frisch, float nass) {
    vec3 gn = n;
    vec2 fall = length(gn.xz) > 1e-3 ? normalize(gn.xz) : vec2(0.0, 1.0);
    vec2 quer = vec2(-fall.y, fall.x);
    vec2 q = vec2(dot(p.xz, quer), dot(p.xz, fall));
    float fl = knf(p.xz + 91.0, 150.0, px) * 0.6 + knf(p.xz + 17.0, 45.0, px) * 0.4;
    float f = clamp(fels + (fl - 0.5) * 1.3, 0.0, 1.0);
    float r1 = rinne(q + 5.0, 6.0, 55.0, px), r2 = rinne(q + 31.0, 22.0, 170.0, px);
    float rin = r1 * 0.45 + r2 * 0.55;                      // 0 Rinne, 1 Rippe
    float r1b = rinne(q + vec2(7.0, 5.0), 6.0, 55.0, px), r2b = rinne(q + vec2(33.0, 31.0), 22.0, 170.0, px);
    float drin = (r1b * 0.45 + r2b * 0.55 - rin) * 0.5;      // Steigung quer (über 2 m)
    float bank = fract((p.y + 7.0 * knf(p.xz + 3.0, 70.0, px)) / 9.0);
    float bk = mix(smoothstep(0.62, 0.92, bank) - smoothstep(0.92, 1.0, bank), 0.3, smoothstep(0.2, 0.5, pyx / 9.0));
    vec3 felsF = mix(vec3(0.24, 0.235, 0.22), vec3(0.50, 0.49, 0.46), frisch);
    vec3 schuttF = mix(vec3(0.22, 0.19, 0.15), vec3(0.36, 0.31, 0.24), frisch);
    vec3 kiesF = vec3(0.30, 0.29, 0.26);
    vec3 c = mix(schuttF, felsF, f);
    c *= mix(1.0, 0.82 + 0.36 * bk, f);                      // Bänke im Fels: Stirn hell, Absatz mit Schutt dunkler
    c *= 0.5 + 0.85 * rin;                                   // Rinne dunkel, Rippe hell
    c *= 0.85 + 0.3 * knf(p.xz + 55.0, 12.0, px);
    c = mix(c, kiesF * (0.85 + 0.3 * knf(p.xz, 7.0, px)), kies);
    n = normalize(gn + vec3(quer.x, 0.0, quer.y) * (-drin) * 1.6 * (1.0 - smoothstep(4.0, 20.0, px)));
    if (uSurfAn == 1 && px < 0.9) {
        // Nähe: Muster der Texturen (Verhältnis zum Mittel des Materials) auf der Farbe der Großform
        float near = 1.0 - smoothstep(0.45, 0.9, px);
        int La = frisch > 0.5 ? uMat.x : uMat.y;
        int Lb = kies > 0.5 ? uMat.w : uMat.z;
        Surf a = mat(La, p, dpw, dpv, n, px), b = mat(Lb, p, dpw, dpv, n, px);
        float wa = clamp(f * (1.0 - kies) + (a.height - b.height) * 0.6, 0.0, 1.0);
        wa = smoothstep(0.35, 0.65, wa);
        vec3 ra = a.albedo / max(uSurfMean[La].rgb, vec3(0.02)), rb = b.albedo / max(uSurfMean[Lb].rgb, vec3(0.02));
        vec3 det = clamp(mix(rb, ra, wa), vec3(0.25), vec3(2.5));
        c *= mix(vec3(1.0), det, near);
        n = normalize(mix(n, normalize(mix(b.normal, a.normal, wa)), near));
    }
    // nass: dunkler, in den Rinnen zuerst [G]
    c *= 1.0 - nass * (0.18 + 0.17 * (1.0 - rin));
    return c;
}

void main() {
    if (clipmapHidden(vW.xz)) discard;
    vec3 dpw = dFdx(vW), dpv = dFdy(vW);          // vor jeder Verzweigung (Texturen der Nähe)
    float pyx = max(fwidth(vW.y), 1e-3);
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
    float pxh = max(length(fwidth(vW.xz)), 1e-3);
    vec3 nL = n;   // Normale für das Licht (Relief der Hänge ohne Wald)
    // W3b: die Flut der Vorrechnung, solange die Welle läuft (danach der Boden im Band aus WaldGl)
    if (uFlutAn == 1) {
        vec2 fu = vec2((vW.x - uFlutRect.x) / uFlutRect.z, (uFlutRect.y - vW.z) / uFlutRect.w);
        if (fu.x > 0.0 && fu.y > 0.0 && fu.x < 1.0 && fu.y < 1.0) {
            vec2 am = texture(uFlut, fu).rg;
            // Spurlinie ausgefranst (± 4 m) [G]; ab der Ankunft
            float rand = am.y + (knf(vW.xz + 23.0, 30.0, pxh) - 0.5) * 8.0;
            float flut = (1.0 - smoothstep(rand - 3.0, rand + 1.0, vH)) * smoothstep(am.x - 0.3, am.x + 1.2, uEreignis) * smoothstep(0.3, 2.0, h);
            if (flut > 0.0) {
                vec3 nh = n;
                vec3 c = hang(vW, nh, pxh, pyx, dpw, dpv, smoothstep(0.22, 0.5, slope), 1.0 - smoothstep(3.0, 14.0, h), 0.55, 1.0);
                alb = mix(alb, c, flut);
                nL = normalize(mix(nL, nh, flut));
                krone *= 1.0 - flut;
            }
        }
    }
    if (uBandAn == 1) {
        vec2 bu = (vW.xz - uBandRect.xy) / uBandRect.zw;
        if (bu.x > 0.0 && bu.y > 0.0 && bu.x < 1.0 && bu.y < 1.0) {
            vec4 b = texture(uBand, bu);
            float k = b.a * smoothstep(0.5, 2.0, h);
            if (k > 0.0) {
                // Fels (0,18 0,17 0,16), Mineralboden (0,20 0,16 0,11), Waldboden (dunkel) aus WaldGl; die ersten beiden als Hang
                float wb = 1.0 - smoothstep(0.08, 0.14, b.r);
                float fe = smoothstep(0.12, 0.155, b.b);
                vec3 nh = n;
                vec3 c = hang(vW, nh, pxh, pyx, dpw, dpv, max(fe, smoothstep(0.3, 0.55, slope)), 1.0 - smoothstep(3.0, 14.0, h), uAbrissFrisch, 0.0);
                alb = mix(alb, mix(c, b.rgb, wb), k);
                nL = normalize(mix(nL, nh, k * (1.0 - wb)));
                krone *= 1.0 - k * (1.0 - wb);
            }
        }
    }
    if (uAbrissAn == 1) {
        vec2 au = vec2((vW.x - uAbrissRect.x) / uAbrissRect.z, (uAbrissRect.y - vW.z) / uAbrissRect.w);
        if (au.x > 0.0 && au.y > 0.0 && au.x < 1.0 && au.y < 1.0) {
            float a = smoothstep(0.05, 0.6, texture(uAbriss, au).r);
            if (a > 0.0) {
                // Abrissfläche: Fels mit Bänken und Rinnen, frisch hell (W3: einheitlich hell mit Flecken) [G]
                vec3 nh = n;
                vec3 c = hang(vW, nh, pxh, pyx, dpw, dpv, 0.9, 0.0, uAbrissFrisch, 0.0) * mix(1.0, 1.12, uAbrissFrisch);
                alb = mix(alb, c, a);
                nL = normalize(mix(nL, nh, a));
                krone *= 1.0 - a;
            }
        }
    }
    alb = mix(grund, alb, smoothstep(-1.0, 0.5, h));
    float B = skyBrightness(sunElevDeg());
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0));
    // im Kronendach fällt weniger Himmelslicht zwischen die Kronen, die Sonne trifft die Kronen auch am flachen Hang [G]
    float ndl = mix(max(dot(nL, uSun), 0.0), max(dot(normalize(n + vec3(0.0, 0.6, 0.0)), uSun), 0.0), krone);
    vec3 col = alb * (sunAt(vW) * ndl + sky * (0.55 + 0.45 * nL.y) * (1.0 - 0.25 * krone));
    vec3 d = vW - uCam;
    float dist = length(d);
    col = applyMarks(col, vW.xz, dist, B);
    col = aerial(col, d / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
