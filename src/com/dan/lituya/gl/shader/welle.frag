// welle.frag — die Welle der Vorrechnung (Phase 13b): Fläche aus spur.vert. Seit Werkbuch Welle W3c als Wasser lesbar:
//  · Spiegel der Berge (das Spiegelbild aus W2, uRefl) überall dort, wo die Fläche nahe am Pegel und flach liegt; vorher
//    spiegelte die Welle nur den Himmel, die ganze Bucht war während der Welle eine violette Fläche wie der Hang daneben;
//  · Kräusel fein und nach dem Wind gerichtet (vier Lagen, in der Ferne gemittelt), stärker, wo das Wasser aufgewühlt ist;
//    vorher zwei grobe Lagen zu 8 und 11 m, die den Himmel in Flecken („Leopard“) zurückwarfen;
//  · Schaum: Saum am Ufer (dünnes Wasser), Weißwasser an Front und Flanken, Schaumstraßen und Treibgut hinter der Front
//    (ab der Ankunft der Vorrechnung, klingt in Minuten ab), Aufruhr im Nahfeld der Quelle [G];
//  · Körper fjordgrün mit Gletschermilch, nach der Front schlammig [G].
// Von unten (Auge unter der Fläche): dunkler Körper, Totalreflexion außerhalb des Snellschen Fensters.
// Wo das feinere Gitter liegt, zeichnet dieses (uFeiner), und was zu weit vom Nassen liegt, fällt weg.
#include "common.glsl"
in vec3 vW;
in vec3 vN;
in float vNass;
in float vH;
in float vSteig;
in float vTiefe;
in float vQuelle;
out vec4 frag;
uniform vec4 uFeiner;     // Rechteck des feineren Gitters (x0, z0, x1, z1), x0 > x1: keins
// W3c: Spiegelbild der Berge aus W2 (halbe Größe, a = 1 wo Gelände oder Wald im Spiegel steht) und Größe des Bilds
uniform sampler2D uRefl;
uniform int uSpiegelAn;
uniform vec2 uBildPx;
// W3c: Flutkarte (Ankunft s, höchster Stand m) wie im Gelände, Ereigniszeit
uniform int uFlutAn;
uniform sampler2D uFlut;
uniform vec4 uFlutRect;
uniform float uEreignis;
uniform vec2 uWindXZ;     // W3c: Richtung, in die der Wind weht (Szene x, z)

float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
// W0 (K4): Rauschen mit Periode p (m), das ausblendet (zum Mittel 0,5 geht), sobald ein Bildpunkt mehr als rund ein
// Drittel der Periode deckt
float vnf(vec2 q, float p, vec2 o, float px) { return mix(vn(q / p + o), 0.5, smoothstep(0.25, 0.6, px / p)); }
// Steigung einer Lage (zentrale Differenz über ein Viertel der Periode), gefiltert wie vnf
vec2 lage(vec2 q, float p, vec2 o, float px) {
    float e = 0.25;
    vec2 g = vec2(vn(q / p + o + vec2(e, 0.0)) - vn(q / p + o - vec2(e, 0.0)), vn(q / p + o + vec2(0.0, e)) - vn(q / p + o - vec2(0.0, e)));
    return g * (1.0 - smoothstep(0.2, 0.55, px / p));
}

void main() {
    if (vNass < 0.35) discard;
    // W3b (K11): was nach dem Ablaufen als dünner Film am Hang liegt (unter rund 1 m), ist kein Wasser mehr im Bild,
    // sondern nasser Grund. Solange die Front aufläuft (der Stand steigt), bleibt der Film als weißes Wasser.
    if (vTiefe < 1.2 && vSteig < 0.15 && vQuelle <= 0.0) discard;
    if (vW.x > min(uFeiner.x, uFeiner.z) && vW.x < max(uFeiner.x, uFeiner.z) && vW.z > min(uFeiner.y, uFeiner.w) && vW.z < max(uFeiner.y, uFeiner.w)) discard;
    vec3 d = vW - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    vec2 q = vW.xz;
    float px = max(length(fwidth(q)), 1e-4);   // Bildpunkt auf der Fläche (m)
    vec3 N = normalize(vN);
    float steil = 1.0 - N.y;
    float hoch = vH - uLevel;
    // ---- wie lange die Front schon da ist (Flutkarte), Aufruhr
    float seit = -1.0, stand = uLevel;
    if (uFlutAn == 1) {
        vec2 fu = vec2((vW.x - uFlutRect.x) / uFlutRect.z, (uFlutRect.y - vW.z) / uFlutRect.w);
        if (fu.x > 0.0 && fu.y > 0.0 && fu.x < 1.0 && fu.y < 1.0) {
            vec2 am = texture(uFlut, fu).rg;
            if (am.x < 1e5) { seit = uEreignis - am.x; stand = am.y; }
        }
    }
    float nach = seit >= 0.0 ? 1.0 : 0.0;
    float frisch = nach * (1.0 - smoothstep(15.0, 300.0, seit));          // die ersten Minuten nach der Front
    float aufruhr = clamp(smoothstep(0.5, 4.0, vSteig) + smoothstep(0.05, 0.28, steil) + vQuelle + frisch * 0.7, 0.0, 1.0);
    // ---- Kräusel: vier Lagen in Windrichtung gestreckt [G]; ruhig 0,2, aufgewühlt bis 1
    vec2 wd = length(uWindXZ) > 1e-3 ? normalize(uWindXZ) : vec2(0.8, 0.6);
    vec2 wq = vec2(-wd.y, wd.x);
    vec2 qr = vec2(dot(q, wd), dot(q, wq));   // längs und quer zum Wind
    vec2 qs = vec2(qr.x * 0.55, qr.y);   // quer zum Wind länger
    float t = uTime;
    vec2 g = lage(qs, 23.0, vec2(-t * 0.035, 0.0), px) * 0.55 + lage(qs + 7.0, 9.0, vec2(-t * 0.07, t * 0.01), px) * 0.45
           + lage(qs + 3.0, 3.7, vec2(-t * 0.16, 0.0), px) * 0.35 + lage(qs + 11.0, 1.4, vec2(-t * 0.33, t * 0.05), px) * 0.25;
    g = (wd * (g.x * 0.55) + wq * g.y) * 0.35;
    float kr = mix(0.32, 1.0, aufruhr) * (1.0 - 0.5 * smoothstep(1500.0, 9000.0, dist));
    vec3 n = normalize(N + vec3(g.x, 0.0, g.y) * kr);
    vec3 v = -d;
    float B = skyBrightness(sunElevDeg());
    // ---- Körper: fjordgrün mit Gletschermilch, nach der Front Schlamm und Gesteinsmehl [G]
    float schlamm = clamp(nach * (0.35 + 0.65 * (1.0 - smoothstep(120.0, 1800.0, seit))) + smoothstep(8.0, 40.0, hoch) * 0.6 + vQuelle * 0.8, 0.0, 1.0);
    vec3 eDown = (uSkyMode == 1 ? uSkyAmb * 3.14159 : skyColor(vec3(0.0, 1.0, 0.0)) * 0.6) + sunAt(vW) * max(uSun.y, 0.0);
    vec3 klar = vec3(0.020, 0.050, 0.046), milch = vec3(0.060, 0.105, 0.092), lehm = vec3(0.085, 0.070, 0.050);
    vec3 body = mix(mix(klar, milch, 0.55), lehm, schlamm) * eDown * 0.32;
    vec3 col;
    float fres = 0.02;
    if (dot(v, N) >= 0.0) {
        fres = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        vec3 r = reflect(d, n);
        vec3 refl = skyColor(vec3(r.x, max(r.y, 0.01), r.z));
        // Spiegel der Berge (W2-Bild): nur nahe am Pegel und wo die Fläche flach liegt; Verzerrung mit den Kräuseln
        if (uSpiegelAn == 1) {
            float nahe = (1.0 - smoothstep(6.0, 22.0, abs(hoch))) * (1.0 - smoothstep(0.05, 0.16, steil));
            if (nahe > 0.0) {
                vec2 suv = gl_FragCoord.xy / uBildPx;
                vec2 off = n.xz * 0.6 / max(dist, 30.0) * vec2(1.0, 1.6) * (1.0 + 2.0 * aufruhr);
                vec4 R = texture(uRefl, suv + off);
                refl = mix(refl, R.rgb, clamp(R.a, 0.0, 1.0) * nahe);
            }
        }
        col = mix(body, refl, fres);
        vec3 hv = normalize(uSun + v);
        float shin = mix(600.0, 80.0, smoothstep(100.0, 8000.0, dist));
        col += sunAt(vW) * pow(max(dot(n, hv), 0.0), shin) * (shin / 600.0) * 2.0;
    } else {
        // von unten: im Snellschen Fenster (48,6°) der Himmel gebrochen, sonst der Körper gespiegelt
        float c = dot(-v, n);
        float fenster = smoothstep(0.62, 0.70, c);
        col = mix(body * 1.4, skyColor(normalize(vec3(-v.x, 1.0, -v.z))) * 0.5, fenster);   // W3c: Körper heller (Unterwasserblick)
    }
    // ---- Schaum [G]
    vec3 weissL = sunAt(vW) * max(dot(n, uSun), 0.0) * 0.9 + umgebung(n) * 1.05;
    // 1) Weißwasser an der Front und an steilen Flanken, ausgefranst
    float roh = vnf(q, 6.0, vec2(t * 0.35, 0.0), px) * 0.55 + vnf(q, 1.8, vec2(0.0, t * 0.6), px) * 0.45;
    float front = clamp(smoothstep(0.08, 0.35, steil) + smoothstep(0.8, 4.0, vSteig), 0.0, 1.0) * smoothstep(0.30, 0.70, roh + 0.25);
    // 2) Schaumstraßen und Flecken hinter der Front: in Windrichtung gestreckt, klingen in rund 10 min ab
    vec2 qz = vec2(qr.x * 0.18, qr.y);
    float str = vnf(qz, 14.0, vec2(-t * 0.02, 0.0), px) * 0.6 + vnf(qz + 5.0, 4.5, vec2(-t * 0.05, 0.0), px) * 0.4;
    float strasse = nach * (1.0 - smoothstep(60.0, 900.0, seit)) * smoothstep(0.66, 0.80, str) * 0.6;
    // 3) Saum am Ufer: dünnes Wasser über dem Grund
    float saum = (1.0 - smoothstep(0.6, 3.5, vTiefe)) * smoothstep(0.35, 0.7, vnf(q + 13.0, 2.5, vec2(t * 0.2, 0.0), px) + 0.25);
    // 4) Aufruhr im Nahfeld der Quelle: Weißwasser und Schlamm in Fetzen
    float quelle = vQuelle * smoothstep(0.52, 0.80, vnf(q + 31.0, 24.0, vec2(t * 0.05, -t * 0.04), px) * 0.45 + vnf(q + 5.0, 8.0, vec2(t * 0.25, -t * 0.18), px) * 0.35 + vnf(q, 2.6, vec2(-t * 0.4, 0.0), px) * 0.2);
    float schaum = clamp(max(max(front * 0.9, strasse), max(saum * 0.8, quelle * 0.9)), 0.0, 1.0);
    vec3 schaumF = mix(vec3(0.82, 0.83, 0.82), vec3(0.55, 0.49, 0.40), clamp(schlamm * 0.55 + quelle * 0.3, 0.0, 1.0));
    col = mix(col, schaumF * weissL, schaum);
    // ---- Treibgut hinter der Front [G]: Stämme (geschält hell, mit Rinde dunkel), in Feldern gehäuft, die mit der Zeit
    // dichter werden (Treibholzteppich); Striche quer zum Wind; in der Ferne als Mittel (Fleckung) statt Gesprenkel
    if (nach > 0.0) {
        float feld = smoothstep(0.45, 0.75, vnf(q + 41.0, 120.0, vec2(0.0), px)) * smoothstep(20.0, 240.0, seit);
        float i0 = floor(qr.x / 9.0);
        vec2 qd = vec2(qr.x + 9.0 * (h2(vec2(i0, 7.0)) - 0.5), qr.y + 40.0 * h2(vec2(i0, 3.0)));
        vec2 zl = floor(vec2(qd.x / 9.0, qd.y / 1.6)), fz = vec2(qd.x / 9.0, qd.y / 1.6) - zl;
        float da = h2(zl + 3.3);
        float dicht = mix(0.92, 0.55, feld);
        float stamm = step(dicht, da) * (1.0 - smoothstep(0.18, 0.30, abs(fz.y - 0.5))) * smoothstep(0.08, 0.16, fz.x) * (1.0 - smoothstep(0.7, 0.85, fz.x));
        float nahS = 1.0 - smoothstep(0.25, 0.9, px);
        vec3 holzF = mix(vec3(0.05, 0.04, 0.03), vec3(0.50, 0.45, 0.38), step(0.5, h2(zl + 9.1)));
        float anteil = stamm * nahS + (1.0 - nahS) * (1.0 - dicht) * 0.55;
        col = mix(col, holzF * weissL * 0.85, clamp(anteil, 0.0, 0.9) * (1.0 - front) * (1.0 - quelle));
    }
    col = aerial(col, d, dist);
    frag = vec4(col, 1.0);
}
