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
#include "/com/dan/cliff/gl/cliff.glsl"
in vec3 vW;
in vec3 vN;
in float vH;
in float vGletscher;   // W3c: Eis der Gletscherzunge 1958 [G]
out vec4 frag;
// ab Phase 14a: der Boden im Band der Welle 1958 (Fels, Mineralboden, Waldboden des gezeigten Jahrs, linear) aus WaldGl;
// Deckel 1 im Band. Rechteck x0, z0, Breite, Tiefe (Szene).
uniform int uBandAn;
uniform sampler2D uBand;
uniform vec4 uBandRect;
uniform float uBandJahre;   // W6: Jahre nach 1958 des gezeigten Bodens
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
uniform ivec4 uMat2;   // W6 Runde 2: Waldboden, Moos, Firn, Eis für den Boden in der Nähe
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
// W6 Runde 2: ganz gemittelt ohne Rauschen (spart in der Ferne fast alle Abrufe; keine Ableitungen darin)
float knf(vec2 q, float p, float px) { float w = smoothstep(0.25, 0.6, px / p); return w >= 1.0 ? 0.5 : mix(kn(q / p), 0.5, w); }
// gestrecktes Rauschen: quer Periode pa, längs pb (m), gefiltert nach der kurzen Periode
float rinne(vec2 q, float pa, float pb, float px) { float w = smoothstep(0.25, 0.6, px / pa); return w >= 1.0 ? 0.5 : mix(kn(vec2(q.x / pa, q.y / pb)), 0.5, w); }

// Werkbuch Welle W6: Farbe aus Sentinel-2, Fels aus cliff, Schnee mit Form, Gletscher mit Spalten (LandschaftGl)
#include "landschaft.glsl"

// W3c: Zellen (Voronoi) für Blöcke im Grund: Helligkeit, Fuge (Abstand zur nächsten Zelle), Abstand
vec3 gBloecke(vec2 q, float g) {
    vec2 i = floor(q / g), f = q / g - i;
    float d1 = 9.0, d2 = 9.0, id = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 c = vec2(x, y), o = vec2(kh(i + c), kh(i + c + 17.3));
            float d = length(c + o - f);
            if (d < d1) { d2 = d1; d1 = d; id = kh(i + c + 5.1); } else if (d < d2) d2 = d;
        }
    return vec3(id, d2 - d1, d1);
}

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
    float f = clamp(fels + (fl - 0.5) * 0.9, 0.0, 1.0);
    float r1 = rinne(q + 5.0, 6.0, 55.0, px), r2 = rinne(q + 31.0, 22.0, 170.0, px);
    float rin = r1 * 0.45 + r2 * 0.55;                      // 0 Rinne, 1 Rippe
    float r1b = rinne(q + vec2(7.0, 5.0), 6.0, 55.0, px), r2b = rinne(q + vec2(33.0, 31.0), 22.0, 170.0, px);
    float drin = (r1b * 0.45 + r2b * 0.55 - rin) * 0.5;      // Steigung quer (über 2 m)
    float bank = fract((p.y + 7.0 * knf(p.xz + 3.0, 70.0, px)) / 9.0);
    float bk = mix(smoothstep(0.62, 0.92, bank) - smoothstep(0.92, 1.0, bank), 0.3, smoothstep(0.2, 0.5, pyx / 9.0));
    // W3c: wärmer und mit mehr Unterschied (vorher grau in grau: im Dämmerlicht nicht vom Wasser zu unterscheiden) [G]
    vec3 felsF = mix(vec3(0.25, 0.245, 0.225), vec3(0.52, 0.50, 0.46), frisch);
    vec3 schuttF = mix(vec3(0.25, 0.19, 0.13), vec3(0.42, 0.33, 0.22), frisch);
    vec3 kiesF = vec3(0.33, 0.31, 0.27);
    vec3 c = mix(schuttF, felsF, f);
    c *= mix(1.0, 0.82 + 0.36 * bk, f);                      // Bänke im Fels: Stirn hell, Absatz mit Schutt dunkler
    c *= 0.5 + 0.85 * rin;                                   // Rinne dunkel, Rippe hell
    c *= 0.85 + 0.3 * knf(p.xz + 55.0, 12.0, px);
    c = mix(c, kiesF * (0.85 + 0.3 * knf(p.xz, 7.0, px)), kies);
    // W3c: Blöcke und Steine (4 m und 1,2 m) mit dunklen Fugen: rauer Grund statt glatter Flecken, die wie Spiegelungen
    // aussahen; in der Ferne gemittelt [G]
    vec3 b4 = gBloecke(p.xz + 3.7, 4.0), b1 = gBloecke(p.xz + 19.0, 1.2);
    float k4 = 1.0 - smoothstep(0.5, 1.6, px / 4.0 * 3.0), k1 = 1.0 - smoothstep(0.5, 1.6, px / 1.2 * 3.0);
    c *= mix(1.0, 0.72 + 0.56 * b4.x, k4 * 0.8) * mix(1.0, mix(0.55, 1.0, smoothstep(0.02, 0.14, b4.y)), k4);
    c *= mix(1.0, (0.8 + 0.4 * b1.x) * mix(0.7, 1.0, smoothstep(0.02, 0.16, b1.y)), k1);
    c *= mix(1.0, 0.86, smoothstep(1.6, 6.0, px) * (1.0 - smoothstep(6.0, 30.0, px)));   // Mittel der Fugen in der Ferne
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
        // W3c: Hohlräume dunkler (aus der Höhe der Materialien): Relief auch ohne Sonne
        c *= mix(1.0, 0.62 + 0.62 * clamp(mix(b.height, a.height, wa), 0.0, 1.0), near);
        n = normalize(mix(n, normalize(mix(b.normal, a.normal, wa)), near));
    }
    // nass: dunkler, in den Rinnen zuerst, Schlamm bräunlich [G]
    c *= 1.0 - nass * (0.14 + 0.16 * (1.0 - rin));
    c = mix(c, c * vec3(1.06, 0.97, 0.86), nass * 0.6);
    return c;
}

/*
 * Werkbuch Welle W3c: Stämme auf dem abgeschürften Hang [G]. Die Welle brach den Wald und schälte die Stämme (Fotos 1958:
 * helle, entrindete Stämme kreuz und quer). Kurze helle Striche, grob in der Falllinie, mit Schatten daneben; in der Ferne
 * als leichte Aufhellung gemittelt. frisch: 1 im Jahr 1958, mit den Jahren grau und weniger.
 */
vec3 holz(vec3 c, vec3 p, vec3 n, float px, float frisch, float dichte) {
    vec2 fall = length(n.xz) > 1e-3 ? normalize(n.xz) : vec2(0.0, 1.0);
    float i = floor(dot(p.xz, vec2(0.7, 0.3)) / 11.0);
    float wink = (kh(vec2(i, 3.1)) - 0.5) * 1.4;                    // Streuung der Lage um die Falllinie
    vec2 a = vec2(cos(wink) * fall.x - sin(wink) * fall.y, sin(wink) * fall.x + cos(wink) * fall.y);
    vec2 b = vec2(-a.y, a.x);
    vec2 q = vec2(dot(p.xz, a), dot(p.xz, b));
    vec2 zelle = floor(vec2(q.x / 22.0, q.y / 3.0));
    float da = kh(zelle + 7.0), lage = kh(zelle + 13.0);
    vec2 f = vec2(q.x / 22.0, q.y / 3.0) - zelle;
    float schwelle = 1.0 - 0.45 * dichte;   // W5 Runde 4: dichter am Spülsaum
    float stamm = step(schwelle, da) * (1.0 - smoothstep(0.08, 0.16, abs(f.y - 0.5))) * smoothstep(0.05, 0.12, f.x) * (1.0 - smoothstep(0.75 + 0.2 * lage, 0.9 + 0.1 * lage, f.x));
    float schatten = step(schwelle, da) * (1.0 - smoothstep(0.16, 0.30, abs(f.y - 0.62))) * (1.0 - stamm) * smoothstep(0.05, 0.12, f.x) * (1.0 - smoothstep(0.8, 0.95, f.x));
    float nah = 1.0 - smoothstep(0.35, 1.1, px);
    vec3 hell = mix(vec3(0.42, 0.40, 0.37), vec3(0.62, 0.56, 0.47), frisch);
    c = mix(c, c * 0.55, schatten * nah * 0.8);
    c = mix(c, hell, stamm * nah);
    // W5 Runde 4: fern der Teppich aus Holz als Tönung mit Haufen (7 und 18 m, in der Falllinie gestreckt): helle
    // geschälte Stämme, dazwischen Schatten; vorher nur 12 % Aufhellung, aus 1,6 km nicht zu sehen
    vec2 qf = vec2(dot(p.xz, fall), dot(p.xz, vec2(-fall.y, fall.x)));
    float haufen = knf(qf * vec2(0.45, 1.0) + 5.0, 7.0, px) * 0.6 + knf(qf * vec2(0.5, 1.0) + 17.0, 18.0, px) * 0.4;
    float anteil = (0.10 + 0.55 * dichte) * smoothstep(0.30, 0.70, haufen + 0.25 * dichte);
    vec3 teppich = mix(c * 0.5, hell, 0.55 + 0.25 * haufen);
    return mix(c, mix(c, teppich, anteil), 1.0 - nah);
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
    // W6: die Landschaft aus den Daten statt der festen Farben (außerhalb der Szene bleiben sie); der Wald, die Flut, das
    // Band, die Abrissfläche und die Zunge 1958 zeichnen danach darüber wie bisher
    Land land;
    land.alb = alb; land.n = n; land.schatten = 0.0; land.schnee = 0.0; land.eis = 0.0; land.fels = 0.0; land.w = 0.0; land.lage = 0.5;
    if (uLandAn == 1) {
        land = landschaft(vW, n, h, max(length(fwidth(vW.xz)), 1e-3), alb);
        alb = land.alb;
    }
    // W6 Runde 2 (5.1, Nahbereich): unter 1 m je Bildpunkt die Oberflächen aus com.dan.surface auf die Farbe der Aufnahme
    // (als Muster um ihr Mittel, die Farbe bleibt die der Aufnahme): Waldboden und Moos im Grünen, Schiefer am Fels, Kies
    // am Ufer, Firn und Eis [G]. Vorher war der Boden in der Nähe ein Brei aus 10-m-Zellen.
    if (uLandAn == 1 && uSurfAn == 1 && land.w > 0.0) {
        float pxN = max(length(fwidth(vW.xz)), 1e-3);
        // W6 Runde 3 (Kosten): bis 0,7 m je Bildpunkt (vorher 0,9)
        if (pxN < 0.7) {
            float near = (1.0 - smoothstep(0.35, 0.7, pxN)) * land.w;
            float ufer = 1.0 - smoothstep(3.0, 10.0, h);
            int La = land.schnee > 0.5 ? (land.eis > 0.5 && land.schnee < 0.9 ? uMat2.w : uMat2.z)
                   : land.fels > 0.5 ? uMat.y : ufer > 0.5 ? uMat.w : uMat2.x;
            int Lb = land.schnee > 0.5 ? uMat2.z : land.fels > 0.5 ? uMat.x : ufer > 0.5 ? uMat.z : uMat2.y;
            Surf a = mat(La, vW, dpw, dpv, land.n, pxN), b = a;
            if (Lb != La) b = mat(Lb, vW, dpw, dpv, land.n, pxN);
            float wa = smoothstep(0.35, 0.65, knf(vW.xz + 61.0, 3.0, pxN) + (a.height - b.height) * 0.5);
            vec3 ra = a.albedo / max(uSurfMean[La].rgb, vec3(0.02)), rb = b.albedo / max(uSurfMean[Lb].rgb, vec3(0.02));
            vec3 det = clamp(mix(rb, ra, wa), vec3(0.3), vec3(2.2));
            alb *= mix(vec3(1.0), det, near * 0.85);
            land.n = normalize(mix(land.n, normalize(mix(b.normal, a.normal, wa)), near * 0.7));
        }
    }
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
    vec3 nL = land.n;   // Normale für das Licht (Relief der Hänge ohne Wald; seit W6 Fels aus cliff)
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
                // W5 Runde 4: Spülsaum: gleich unter der Spurlinie (bis rund 35 m tiefer) blieb das Holz in Haufen liegen,
                // darunter verstreut; erst wenn das Wasser abgelaufen ist (rund 15–40 s nach der Ankunft) [G]
                float saum = smoothstep(rand - 38.0, rand - 10.0, vH) * (1.0 - smoothstep(rand - 3.0, rand + 1.0, vH));
                float abgelaufen = smoothstep(am.x + 12.0, am.x + 40.0, uEreignis);
                c = holz(c, vW, n, pxh, 1.0, (0.35 + 0.65 * saum) * abgelaufen);
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
            // W6: in den letzten Jahren zeigt die Aufnahme das Band, wie es ist (Erlen, Wiese, Fels); der gerechnete Boden nur
            // noch als Hauch (11 Narbe heute: vorher heller Kies und Holz zwischen den Bäumen)
            // W6 Runde 3 (Kosten): ab 60 Jahren ganz die Aufnahme (vorher blieb ein Hauch von 15 %, für den der ganze Hang
            // mit Blöcken und Texturen gerechnet wurde: in 11 Narbe heute der größte Teil der Zeit im Gelände)
            if (uLandAn == 1) k *= 1.0 - smoothstep(45.0, 60.0, uBandJahre);
            if (k > 0.0) {
                // Fels (0,18 0,17 0,16), Mineralboden (0,20 0,16 0,11), Waldboden (dunkel) aus WaldGl; die ersten beiden als Hang
                float wb = 1.0 - smoothstep(0.08, 0.14, b.r);
                float fe = smoothstep(0.12, 0.155, b.b);
                vec3 nh = n;
                vec3 c = hang(vW, nh, pxh, pyx, dpw, dpv, max(fe, smoothstep(0.3, 0.55, slope)), 1.0 - smoothstep(3.0, 14.0, h), uAbrissFrisch, 0.0);
                c = holz(c, vW, n, pxh, uAbrissFrisch, 0.4 * uAbrissFrisch);
                alb = mix(alb, mix(c, b.rgb, wb), k);
                nL = normalize(mix(nL, nh, k * (1.0 - wb)));
                krone *= 1.0 - k * (1.0 - wb);
            }
        }
    }
    if (uAbrissAn == 1) {
        // W3c: Rand ausgefranst (die Platte der Rechnung ist ein Rechteck mit geraden Kanten) [G]
        vec2 jit = (vec2(knf(vW.xz + 5.0, 90.0, pxh), knf(vW.xz + 77.0, 90.0, pxh)) - 0.5) * 90.0
                 + (vec2(knf(vW.xz + 9.0, 22.0, pxh), knf(vW.xz + 41.0, 22.0, pxh)) - 0.5) * 26.0;
        vec2 aw = vW.xz + jit;
        vec2 au = vec2((aw.x - uAbrissRect.x) / uAbrissRect.z, (uAbrissRect.y - aw.y) / uAbrissRect.w);
        if (au.x > 0.0 && au.y > 0.0 && au.x < 1.0 && au.y < 1.0) {
            float a = smoothstep(0.05, 0.6, texture(uAbriss, au).r);
            // W6: heute zeigt die Aufnahme, wie die Abrissfläche aussieht (Fels, Schutt, Bewuchs); die gerechnete Fläche nur
            // noch als Hauch
            if (uLandAn == 1 && uAbrissFrisch < 0.99) a *= 0.25;
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
    // W3c: die Zunge des Lituya-Gletschers 1958 [G]: oben weißes, zerrissenes Eis mit Spalten quer zum Fluss und dunklen
    // Moränenbändern längs, an der Front blaues Eis mit senkrechten Bruchflächen. Vorher stand hier ein brauner Tafelberg.
    float eis = vGletscher * smoothstep(-0.5, 1.0, h);
    if (eis > 0.0 && uKroneAn == 1) {
        // wo Wald steht, ist kein Eis (die Zunge im Norden ist ein Rechteck; Hänge mit Wald bleiben Wald)
        vec2 ku = (vW.xz - uKroneRect.xy) / uKroneRect.zw;
        if (ku.x > 0.0 && ku.y > 0.0 && ku.x < 1.0 && ku.y < 1.0) {
            vec3 dn = texture(uKrone, ku).rgb;
            eis *= 1.0 - smoothstep(0.06, 0.25, dn.r + dn.g + dn.b);
        }
    }
    if (eis > 0.0) {
        vec2 fl = vec2(0.35, 0.94);                                     // Fluss nach SSO (Szene: x Ost, z Süd)
        vec2 qe = vec2(dot(vW.xz, fl), dot(vW.xz, vec2(-fl.y, fl.x)));  // längs, quer
        float front = smoothstep(0.22, 0.45, slope);
        vec3 oben = vec3(0.70, 0.75, 0.80) * (0.88 + 0.24 * knf(vW.xz + 3.0, 40.0, pxh));
        float band = smoothstep(0.60, 0.80, rinne(vec2(qe.y, qe.x) + 11.0, 38.0, 600.0, pxh));
        oben = mix(oben, vec3(0.22, 0.20, 0.18), band * 0.75);
        // Spalten: quer zum Fluss, 15–25 m, zur Front hin dichter und offener; fern gemittelt
        float sp = fract(qe.x / 19.0 + 0.7 * knf(vW.xz + 51.0, 70.0, pxh));
        float spalte = (1.0 - smoothstep(0.03, 0.09, abs(sp - 0.5))) * smoothstep(0.35, 0.6, knf(vW.xz + 7.0, 45.0, pxh) + 0.2);
        spalte *= 1.0 - smoothstep(2.0, 7.0, pxh);
        oben = mix(oben, vec3(0.10, 0.20, 0.30), spalte * 0.85);
        oben = mix(oben, oben * 0.84, smoothstep(7.0, 20.0, pxh) * (1.0 - smoothstep(20.0, 60.0, pxh)));
        // W6 (E6): wo die Welle das Eis traf (Ankunft der Flut), ist es frisch gebrochen; vorher verwittert [G]
        float frischE = 0.0;
        if (uFlutAn == 1) {
            vec2 fu = vec2((vW.x - uFlutRect.x) / uFlutRect.z, (uFlutRect.y - vW.z) / uFlutRect.w);
            if (fu.x > 0.0 && fu.y > 0.0 && fu.x < 1.0 && fu.y < 1.0) {
                vec2 am = texture(uFlut, fu).rg;
                frischE = smoothstep(am.x, am.x + 4.0, uEreignis) * step(vH - 40.0, am.y);
            }
        } else if (uAbrissAn == 1 && uAbrissFrisch > 0.99) frischE = 1.0;
        // die Oberfläche, über die die Welle ging: in Brocken zerschlagen, weiß mit blauen Bruchflächen und dunklen Fugen
        if (frischE > 0.0) {
            vec3 bz = gBloecke(vW.xz + 7.0, 9.0), bk = gBloecke(vW.xz + 3.0, 2.5);
            float kb = 1.0 - smoothstep(1.5, 5.0, pxh);
            vec3 brocken = mix(vec3(0.80, 0.85, 0.90), vec3(0.28, 0.50, 0.68), step(0.7, bz.x)) * (0.85 + 0.3 * bk.x);
            brocken *= mix(1.0, mix(0.35, 1.0, smoothstep(0.03, 0.12, bz.y)), kb);
            oben = mix(oben, mix(vec3(0.66, 0.72, 0.78), brocken, kb), frischE * (1.0 - front));
        }
        // Front: blaues Eis, senkrecht gebrochen; Bänder in der Höhe [G]
        vec2 seite = length(n.xz) > 1e-3 ? normalize(vec2(-n.z, n.x)) : vec2(1.0, 0.0);
        float bruch = knf(vec2(dot(vW.xz, seite) / 2.5, vW.y / 45.0), 1.0, pxh / 2.5);
        float schicht = knf(vec2(dot(vW.xz, seite) / 60.0, vW.y / 4.0), 1.0, pyx / 4.0);
        vec3 blau = mix(vec3(0.20, 0.38, 0.52), vec3(0.52, 0.68, 0.78), bruch) * (0.8 + 0.35 * schicht);
        // W6 (E6): vor der Welle eine verwitterte, graue Front mit Schmutzstreifen; danach frisch, tiefblau, mit weißen
        // Bruchnarben [G]
        float streif = knf(vec2(dot(vW.xz, seite) / 6.0, vW.y / 60.0), 1.0, pxh / 6.0);
        vec3 alt = mix(vec3(0.46, 0.49, 0.52), blau, 0.35) * (0.75 + 0.4 * streif);
        vec3 neu = mix(vec3(0.05, 0.24, 0.44), vec3(0.78, 0.86, 0.92), smoothstep(0.78, 0.9, bruch)) * (0.85 + 0.3 * schicht);
        blau = mix(alt, neu, frischE);
        vec3 ice = mix(oben, blau, front);
        alb = mix(alb, ice, eis);
        krone *= 1.0 - eis;
    }
    alb = mix(grund, alb, smoothstep(-1.0, 0.5, h));
    float B = skyBrightness(sunElevDeg());
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0));
    // im Kronendach fällt weniger Himmelslicht zwischen die Kronen, die Sonne trifft die Kronen auch am flachen Hang [G]
    float ndl = mix(max(dot(nL, uSun), 0.0), max(dot(normalize(n + vec3(0.0, 0.6, 0.0)), uSun), 0.0), krone);
    // W3c: Himmelslicht nach der Richtung der Fläche (common.umgebung) statt überall der Farbe des Zenits
    ndl *= 1.0 - land.schatten * (1.0 - krone);   // W6: ins Innere der Spalten fällt keine Sonne
    // W6 Runde 5: Himmelslicht nach der Lage im Relief (in Rinnen und Karen weniger Himmel, auf Graten mehr) [G]
    float hoehlung = mix(1.0, 0.45 + 1.1 * land.lage, 0.8 * land.w);
    vec3 col = alb * (sunAt(vW) * ndl + umgebung(nL) * (1.0 - 0.25 * krone) * (1.0 - 0.6 * land.schatten) * hoehlung);
    if (eis > 0.0) {
        vec3 vv = normalize(uCam - vW);
        float fr = 0.03 + 0.25 * pow(1.0 - max(dot(nL, vv), 0.0), 5.0);
        col += skyColor(reflect(-vv, nL) * vec3(1.0, 1.0, 1.0) + vec3(0.0, 0.02, 0.0)) * fr * eis * 0.6;
    }
    vec3 d = vW - uCam;
    float dist = length(d);
    col = applyMarks(col, vW.xz, dist, B);
    col = aerial(col, d / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
