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
in float vLippe;      // W4: Anteil der Lippe (aus spur.vert)
in vec2 vLauf;        // W4: Laufrichtung der Front (Szene x, z)
in float vSeit;       // W4: s seit der Ankunft der Front
in float vStand;      // W4: höchster Stand hier (m)
out vec4 frag;
uniform vec4 uFeiner;     // Rechteck des feineren Gitters (x0, z0, x1, z1), x0 > x1: keins
// W3c: Spiegelbild der Berge aus W2 (halbe Größe, a = 1 wo Gelände oder Wald im Spiegel steht) und Größe des Bilds
uniform sampler2D uRefl;
uniform mat4 uSpiegelVP, uJetztVP;   // Runde 7: Blick beim Spiegelbild und jetzt (Spiegel jedes zweite Bild)
uniform int uSpiegelAn;
uniform vec2 uBildPx;
// W3c: Flutkarte (Ankunft s, höchster Stand m) wie im Gelände, Ereigniszeit
uniform int uFlutAn;
uniform sampler2D uFlut;
uniform vec4 uFlutRect;
uniform float uEreignis;
uniform vec2 uWindXZ;     // W3c: Richtung, in die der Wind weht (Szene x, z)
uniform int uLipNetz;     // W4b: die Lippe als eigenes Netz (LippeGl): die steile Wand darunter ist das Innere der Röhre

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

// Runde 4: Muster der Zunge in einer festen Richtung D. Acht Richtungen zu 45°, weich gemischt. Vorher lief das Muster
// längs der Laufrichtung je Fragment; mit Lagen von einigen Kilometern verschob schon ein Grad Drehung es um zig Meter,
// an jeder Kante des Netzes anders (dieselbe Falle wie in W4b): daraus die hellen und braunen Vielecke in der Zunge
struct Zm { float faden; float ball; vec2 gb; float st; float stHell; float stFern; };
Zm zungeMuster(vec2 D, vec2 xz, float seitL, float t, float px) {
    Zm m;
    vec2 Dq = vec2(-D.y, D.x);
    float la = dot(xz, Dq), qu = dot(xz, D);
    vec2 qL = vec2(la, qu - t * (seitL < 6.0 ? 22.0 : -9.0));
    // (Runde 2: die Lage zu 17 m gab aus der Ferne große Flecken wie Schnee auf dem Hang; jetzt schwach)
    m.faden = vnf(qL * vec2(1.0, 0.18), 6.0, vec2(0.0), px) * 0.6 + vnf(qL * vec2(1.0, 0.25) + 5.3, 2.4, vec2(0.0), px) * 0.3
            + vnf(qL * vec2(1.0, 0.12) + 9.1, 17.0, vec2(0.0), px) * 0.1;
    // Runde 3: Schaum als Ballen [G], zwei Lagen gewölbter Ballen (4 und 10 m), die mit dem Wasser wandern
    vec2 qb = vec2(la, qu - t * (seitL < 6.0 ? 13.0 : -6.0));
    #define BALL(q) ((1.0 - abs(2.0 * vnf(q, 4.0, vec2(0.0), px) - 1.0)) * 0.55 + (1.0 - abs(2.0 * vnf((q) + 11.0, 10.0, vec2(0.0), px) - 1.0)) * 0.45)
    m.ball = BALL(qb);
    vec2 gb = vec2(0.0);   // Gefälle nur, solange ein Ballen mehr als rund drei Bildpunkte deckt (Tempo)
    if (px < 1.3) gb = vec2(BALL(qb + vec2(0.8, 0.0)) - m.ball, BALL(qb + vec2(0.0, 0.8)) - m.ball) / 0.8 * 3.0 * (1.0 - smoothstep(0.4, 1.3, px));
    #undef BALL
    m.gb = Dq * gb.x + D * gb.y;
    // Stämme in der Zunge, längs der Strömung; nur nah als Striche, fern als dunklere Schlieren
    vec2 zs = floor(vec2(qL.x / 7.0, qL.y / 13.0)), fs = vec2(qL.x / 7.0, qL.y / 13.0) - zs;
    float wink = (h2(zs + 2.9) - 0.5) * 1.2;
    vec2 rs = vec2(cos(wink) * (fs.x - 0.5) - sin(wink) * (fs.y - 0.5), sin(wink) * (fs.x - 0.5) + cos(wink) * (fs.y - 0.5));
    m.st = step(0.80, h2(zs + 5.1)) * (1.0 - smoothstep(0.03, 0.06, abs(rs.x))) * (1.0 - smoothstep(0.30, 0.38, abs(rs.y)));
    m.stHell = step(0.5, h2(zs + 8.8));
    m.stFern = smoothstep(0.55, 0.8, vnf(qL * vec2(1.0, 0.12) + 3.3, 9.0, vec2(0.0), px));
    return m;
}
float fingerMuster(vec2 D, vec2 xz, float tE) {
    float la = dot(xz, vec2(-D.y, D.x)), qu = dot(xz, D);
    return vn(vec2(la / 31.0, qu / 90.0 + tE * 0.05)) * 0.55 + vn(vec2(la / 11.0 + 3.1, qu / 40.0)) * 0.30
         + vn(vec2(la / 4.3 + 7.7, qu / 15.0)) * 0.15;
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
    // W4b: auf steilen Wänden die Normale des Dreiecks selbst: die Wand ist eine Reihe langer schmaler Dreiecke, die
    // gemittelte Normale gab dort Zickzack-Bänder (Probe Röhre)
    // (zurückgenommen: die Normale je Dreieck machte jedes Dreieck zur eigenen Facette, Sägezahn über die Wand)
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
    // ---- Werkbuch Welle W5 (E11) „524 m“: die Zunge am Hang [G]. Wo die Welle über Land aufläuft (Grund über dem Pegel),
    // läuft ihre Vorderkante nicht als glatte Linie, sondern in Fingern und Loben den Hang hinauf (Swash): die Ankunft
    // wird längs der Front um bis zu rund eine Sekunde verschoben, und wo das Wasser „noch nicht da“ ist, bleibt der Hang
    // (mit seinen Bäumen) sichtbar.
    float grundW = vTiefe < 98.0 ? vH - vTiefe : uLevel - 50.0;
    float landW = smoothstep(uLevel + 3.0, uLevel + 25.0, grundW);
    bool hatL0 = dot(vLauf, vLauf) > 0.25 && vSeit > -1e8;
    vec2 TL = hatL0 ? normalize(vLauf) : vec2(1.0, 0.0), TLq = vec2(-TL.y, TL.x);
    // Runde 4: zwei der acht festen Richtungen, nach dem Winkel der Laufrichtung gemischt (siehe zungeMuster)
    float aL = atan(TL.y, TL.x) / 0.7853982, kL = floor(aL), wL = smoothstep(0.32, 0.68, aL - kL);
    vec2 DL0 = vec2(cos(kL * 0.7853982), sin(kL * 0.7853982)), DL1 = vec2(cos((kL + 1.0) * 0.7853982), sin((kL + 1.0) * 0.7853982));
    float fingerN = wL <= 0.0 ? fingerMuster(DL0, vW.xz, uEreignis) : wL >= 1.0 ? fingerMuster(DL1, vW.xz, uEreignis)
                  : 0.5 + (mix(fingerMuster(DL0, vW.xz, uEreignis), fingerMuster(DL1, vW.xz, uEreignis), wL) - 0.5) * inversesqrt(wL * wL + (1.0 - wL) * (1.0 - wL));
    float vorkante = (fingerN - 0.45) * 1.6;   // s, wie weit die Kante hier vor- oder zurückliegt
    if (landW > 0.05 && stand > uLevel + 0.5 && seit < vorkante * landW) discard;
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
    // W4b: Muster aus dem Grundriss (Kräusel, Straßen, Treibgut) nicht auf steilen Wänden, dort zogen sie sich zu
    // waagerechten Zickzack-Bändern zusammen (Probe Röhre)
    float flach = 1.0 - smoothstep(0.30, 0.60, 1.0 - abs(N.y));
    float kr = mix(0.32, 1.0, aufruhr) * (1.0 - 0.5 * smoothstep(1500.0, 9000.0, dist)) * flach;
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
        // W4: eine steile Wand sieht im Spiegel flach hinaus, und dort stehen in der Bucht die Hänge (dunkel, im Dunst),
        // nicht der helle Himmel am Horizont; der Spiegel der Berge (uRefl) gilt nur nahe am Pegel [G]
        float wandS = smoothstep(0.10, 0.35, 1.0 - N.y);
        if (wandS > 0.0) {
            vec3 hang = hazeColor(normalize(vec3(r.x, 0.0, r.z) + vec3(1e-4))) * 0.20 * (0.6 + 0.4 * smoothstep(-0.2, 0.3, r.y));
            refl = mix(refl, hang, wandS * (1.0 - smoothstep(0.10, 0.40, r.y)));
        }
        // Spiegel der Berge (W2-Bild): nur nahe am Pegel und wo die Fläche flach liegt; Verzerrung mit den Kräuseln
        if (uSpiegelAn == 1) {
            float nahe = (1.0 - smoothstep(6.0, 22.0, abs(hoch))) * (1.0 - smoothstep(0.05, 0.16, steil));
            if (nahe > 0.0) {
                vec2 suv = gl_FragCoord.xy / uBildPx;
                {   // Runde 7: Spiegelbild aus dem vorigen Bild an die jetzige Stelle umrechnen
                    vec3 sd = normalize(vW - uCam);   // die Richtung (siehe spiegel.frag)
                    vec4 sa = uSpiegelVP * vec4(sd, 0.0), sj = uJetztVP * vec4(sd, 0.0);
                    if (sa.w > 1e-3 && sj.w > 1e-3) suv += (sa.xy / sa.w - sj.xy / sj.w) * 0.5;
                }
                vec2 off = n.xz * 0.6 / max(dist, 30.0) * vec2(1.0, 1.6) * (1.0 + 2.0 * aufruhr);
                vec4 R = texture(uRefl, suv + off);
                refl = mix(refl, R.rgb, clamp(R.a, 0.0, 1.0) * nahe);
            }
        }
        col = mix(body, refl, fres);
        vec3 hv = normalize(uSun + v);
        float shin = mix(600.0, 80.0, smoothstep(100.0, 8000.0, dist));
        col += sunAt(vW) * pow(max(dot(n, hv), 0.0), shin) * (shin / 600.0) * 2.0;
    } else if (uCam.y > uLevel + 0.5 && hoch > 1.0) {
        // W4b: die Unterseite der Lippe, von vorn in die Röhre gesehen: Licht kommt durch das dünne Wasser der Lippe
        // (türkis, wo klar, braun im Schlamm), innen dunkel, zur Spitze hin heller [G]
        vec3 durch = skyColor(vec3(0.0, 1.0, 0.0)) * mix(vec3(0.16, 0.48, 0.42), vec3(0.36, 0.28, 0.16), clamp(schlamm * 0.8, 0.0, 1.0));
        float spitze = clamp(vLippe * 1.6, 0.0, 1.0);
        float hr = clamp((vH - uLevel) / max(vStand - uLevel, 1.0), 0.0, 1.0);
        // oben unter der Lippe Licht durch das dünne Wasser, nach unten in die Röhre dunkel, Schlieren in der Fallrichtung
        float schl = vn((vec2(dot(vW.xz, vec2(-vLauf.y, vLauf.x)) * 0.25, vW.y * 0.08 - uTime * 0.6)));
        col = mix(body * 0.55, durch * (0.5 + 0.35 * schl), smoothstep(0.35, 0.95, hr) * (0.5 + 0.5 * spitze));
        fres = 0.0;
    } else {
        // von unten: im Snellschen Fenster (48,6°) der Himmel gebrochen, sonst der Körper gespiegelt
        float c = dot(-v, n);
        float fenster = smoothstep(0.62, 0.70, c);
        col = mix(body * 1.4, skyColor(normalize(vec3(-v.x, 1.0, -v.z))) * 0.5, fenster);   // W3c: Körper heller (Unterwasserblick)
    }
    // ---- Werkbuch Welle W4 „Die Wand“ (E7–E9, E12) [G]: wo die Front gerade durchläuft, auf der Wand eigene
    // Koordinaten (längs der Front und in der Höhe), damit das Rauschen keine senkrechten Streifen aus dem Grundriss zieht
    bool hatLauf = dot(vLauf, vLauf) > 0.25 && vSeit > -1e8;
    vec2 T = hatLauf ? normalize(vLauf) : wd, Tq = vec2(-T.y, T.x);
    float hs = max((hatLauf ? vStand : stand) - uLevel, 1.0);
    float hrel = clamp(hoch / hs, 0.0, 1.2);
    float wandZeit = hatLauf ? smoothstep(-1.5, 0.3, vSeit) * (1.0 - smoothstep(4.0, 16.0, vSeit)) : 0.0;
    float wandSteil = smoothstep(0.06, 0.30, steil);
    float wand = wandZeit * smoothstep(4.0, 22.0, hoch) * max(wandSteil, vLippe);
    vec2 qw = vec2(dot(q, Tq), vW.y);
    float pxw = max(length(fwidth(qw)), 1e-4);
    float vorn = smoothstep(-0.1, 0.5, dot(normalize(v.xz + vec2(1e-5)), T));   // das Auge steht vor der Front
    // E8 Durchlicht: der dünne Kamm leuchtet von hinten, türkis wo klar, braun wo Schlamm
    float duenn = clamp(vLippe * 1.4, 0.0, 1.0) * smoothstep(0.80, 0.98, hrel) * vorn * wandSteil;
    if (duenn > 0.0) {
        vec3 hinten = skyColor(normalize(vec3(d.x, max(d.y, 0.03) + 0.05, d.z)));
        vec3 farbe = mix(vec3(0.20, 0.72, 0.62), vec3(0.55, 0.42, 0.22), clamp(schlamm * 0.8, 0.0, 1.0));
        float adern = vnf(qw + vec2(0.0, t * 3.0), 7.0, vec2(0.0), pxw) * 0.6 + vnf(qw * vec2(1.0, 0.4), 2.0, vec2(t * 0.6, 0.0), pxw) * 0.4;
        col = mix(col, hinten * farbe * (0.45 + 0.35 * adern) + body * 0.5, duenn * 0.6);
    }
    // unter der Lippe: Schatten in der Röhre
    col *= 1.0 - 0.5 * clamp(vLippe * 1.5, 0.0, 1.0) * smoothstep(0.25, 0.5, hrel) * (1.0 - smoothstep(0.7, 0.9, hrel)) * vorn;
    // ---- Schaum [G]
    vec3 weissL = sunAt(vW) * max(dot(n, uSun), 0.0) * 0.9 + umgebung(n) * 1.05;
    // 1) Weißwasser an der Front und an steilen Flanken, ausgefranst (auf der Wand in Wandkoordinaten)
    vec2 qf = mix(q, qw * vec2(1.0, 1.6), wandSteil);
    float pf = mix(px, pxw, wandSteil);
    float roh = vnf(qf, 6.0, vec2(t * 0.35, -t * 0.5 * wandSteil), pf) * 0.55 + vnf(qf, 1.8, vec2(0.0, t * 0.6), pf) * 0.45;
    float front = clamp(smoothstep(0.08, 0.35, steil) + smoothstep(0.8, 4.0, vSteig), 0.0, 1.0) * smoothstep(0.30, 0.70, roh + 0.25);
    // E9 Walze am Fuß der Wand: dichtes Weißwasser, das an der Wand aufsteigt; Kammgischt oben, die nach hinten weht
    float walzeN = vnf(qw * vec2(1.0, 0.7) + vec2(0.0, -t * 4.0), 9.0, vec2(0.0), pxw) * 0.55 + vnf(qw + vec2(t * 1.5, -t * 7.0), 3.0, vec2(0.0), pxw) * 0.45;
    // große Formen (Fahnen aus Schaum, die über die Wand herabfallen; Lücken, durch die das dunkle Wasser sieht): längs
    // der Front 25–70 m, in der Höhe gestreckt; sie bleiben, wenn das feine Rauschen in der Ferne zum Mittel geht
    float gross = vnf(vec2(qw.x, qw.y * 0.22 + t * 1.2), 38.0, vec2(0.0), pxw) * 0.65 + vnf(vec2(qw.x, qw.y * 0.4 + t * 2.0) + 17.0, 13.0, vec2(0.0), pxw) * 0.35;
    float fahnen = smoothstep(0.42, 0.66, gross);
    float walze = wand * (1.0 - smoothstep(0.35, 0.62, hrel + (walzeN - 0.5) * 0.3)) * smoothstep(0.25, 0.55, walzeN + 0.15);
    walze = max(walze, wand * fahnen * (1.0 - smoothstep(0.55, 0.9, hrel)) * 0.85);
    float kammN = vnf(vec2(dot(q, Tq), dot(q, T)) * vec2(1.0, 0.35) + vec2(0.0, t * 4.0), 5.0, vec2(0.0), px);
    float kammG = vnf(vec2(dot(q, Tq), t * 0.8), 24.0, vec2(0.0), px);   // der Kamm bricht nicht überall zugleich
    float kammB = smoothstep(mix(0.80, 0.62, kammG), 0.93, hrel);
    float kamm = wandZeit * kammB * smoothstep(8.0, 25.0, hoch) * smoothstep(0.30, 0.62, kammN * 0.5 + kammG * 0.5 + 0.35 * clamp(vLippe * 2.0, 0.0, 1.0));
    // zwischen Kamm und Walze: die Wand selbst, glasig und dunkel, mit wenig Schaum
    front = max(front * (1.0 - 0.75 * wand), max(walze, kamm));
    // Sturz: steiles Wasser hoch über dem Pegel (die Wände im Gilbert Inlet, 50–150 m) ist ganz durchlüftet, Weißwasser
    // in Kaskaden mit Lücken; glasig bleibt nur die niedrigere Wand weiter draußen (Probe 4: dunkle Wände wie Fels)
    float sturz = smoothstep(0.22, 0.45, steil) * smoothstep(18.0, 55.0, hoch);
    front = max(front, sturz * smoothstep(0.34, 0.58, gross + 0.2 * walzeN));
    // Licht im Schaum: Kaskaden heller, wo sie vorstehen, Rinnen dazwischen im Schatten (in der Höhe gestreckt), damit
    // die Wand aus der Ferne nicht als glatte Schneewand steht
    float rinne = vnf(vec2(qw.x * 1.0, qw.y * 0.12 + t * 1.6) + 5.0, 9.0, vec2(0.0), pxw);
    float hell = mix(0.74, 1.12, smoothstep(0.25, 0.75, gross * 0.6 + rinne * 0.4));
    // 2) Schaumstraßen und Flecken hinter der Front: in Laufrichtung gestreckt (die Strömung zieht sie mit, W4 E10),
    //    sonst nach dem Wind; dicht gleich hinter der Front, klingen in rund 10 min ab
    vec2 qz = hatLauf ? vec2(dot(q, T) * 0.18, dot(q, Tq)) : vec2(qr.x * 0.18, qr.y);
    float str = vnf(qz, 14.0, vec2(-t * 0.02, 0.0), px) * 0.6 + vnf(qz + 5.0, 4.5, vec2(-t * 0.05, 0.0), px) * 0.4;
    float strasse = nach * (1.0 - smoothstep(60.0, 900.0, seit)) * smoothstep(mix(0.66, 0.50, 1.0 - smoothstep(4.0, 60.0, seit)), 0.80, str) * 0.6;
    // W4 E10: gleich hinter der Front ein Teppich aus Schaum mit Wirbeln und Lücken
    float wirbel = vnf(q + vec2(31.0, 7.0) + T * t * 1.5, 26.0, vec2(0.0), px) * 0.6 + vnf(q * 1.7 + 3.0, 9.0, vec2(t * 0.1, 0.0), px) * 0.4;
    float teppich = (hatLauf ? smoothstep(2.0, 6.0, vSeit) * (1.0 - smoothstep(25.0, 140.0, vSeit)) : 0.0) * smoothstep(0.38, 0.62, wirbel);
    strasse = max(strasse, teppich * 0.85) * flach;
    // 3) Saum am Ufer: dünnes Wasser über dem Grund
    float saum = (1.0 - smoothstep(0.6, 3.5, vTiefe)) * smoothstep(0.35, 0.7, vnf(q + 13.0, 2.5, vec2(t * 0.2, 0.0), px) + 0.25);
    // 4) Aufruhr im Nahfeld der Quelle: Weißwasser und Schlamm in Fetzen
    float quelle = vQuelle * smoothstep(0.52, 0.80, vnf(q + 31.0, 24.0, vec2(t * 0.05, -t * 0.04), px) * 0.45 + vnf(q + 5.0, 8.0, vec2(t * 0.25, -t * 0.18), px) * 0.35 + vnf(q, 2.6, vec2(-t * 0.4, 0.0), px) * 0.2);
    float schaum = clamp(max(max(front * 0.9, strasse), max(saum * 0.8, quelle * 0.9)), 0.0, 1.0);
    vec3 schaumF = mix(vec3(0.82, 0.83, 0.82), vec3(0.55, 0.49, 0.40), clamp(schlamm * 0.55 + quelle * 0.3, 0.0, 1.0));
    // Weißwasser ist durchlüftet und streut das Licht des ganzen Himmels darüber, nicht wie eine matte senkrechte Fläche
    // (Probe 9: die Wand war voll Schaum und doch so dunkel wie die Hänge im Schatten)
    float wandW = clamp(max(wand, sturz), 0.0, 1.0);
    vec3 weissW = mix(weissL, sunAt(vW) * max(dot(n, uSun), 0.0) * 0.9 + umgebung(normalize(n + vec3(0.0, 1.2, 0.0))) * 1.15, wandW);
    col = mix(col, schaumF * weissW * mix(1.0, hell, wandW), schaum);
    // E12 Stämme in der Walze: wild gedreht, quer zur Front, mitgerissen und an der Wand nach oben geworfen [G]
    if (walze > 0.05) {
        vec2 qs2 = qw + vec2(0.0, -t * 5.0);
        // Zellen 11 × 6 m, je Zelle ein Stamm an zufälliger Stelle (Probe 7: im festen Raster standen sie in Reih und Glied)
        vec2 zl = floor(vec2(qs2.x / 11.0, qs2.y / 6.0)), fz = vec2(qs2.x / 11.0, qs2.y / 6.0) - zl;
        fz -= (vec2(h2(zl + 4.1), h2(zl + 6.7)) - 0.5) * 0.5;
        float wink = (h2(zl + 1.7) - 0.5) * 2.4;
        vec2 rr = vec2(cos(wink) * (fz.x - 0.5) - sin(wink) * (fz.y - 0.5), sin(wink) * (fz.x - 0.5) + cos(wink) * (fz.y - 0.5));
        float st = step(0.72, h2(zl + 8.3)) * (1.0 - smoothstep(0.04, 0.07, abs(rr.y))) * (1.0 - smoothstep(0.22, 0.30, abs(rr.x)));
        st *= 1.0 - smoothstep(0.25, 0.7, pxw);
        vec3 hz = mix(vec3(0.06, 0.045, 0.03), vec3(0.52, 0.46, 0.38), step(0.45, h2(zl + 2.2)));
        col = mix(col, hz * weissL, st * walze);
    }
    // ---- Treibgut hinter der Front [G]: Stämme (geschält hell, mit Rinde dunkel), in Feldern gehäuft, die mit der Zeit
    // dichter werden (Treibholzteppich); Striche quer zum Wind; in der Ferne als Mittel (Fleckung) statt Gesprenkel
    if (nach > 0.0 && landW < 0.99) {   // Runde 4: am Hang zeichnet die Zunge ihre Stämme selbst
        float feld = smoothstep(0.45, 0.75, vnf(q + 41.0, 120.0, vec2(0.0), px)) * smoothstep(20.0, 240.0, seit);
        float i0 = floor(qr.x / 9.0);
        vec2 qd = vec2(qr.x + 9.0 * (h2(vec2(i0, 7.0)) - 0.5), qr.y + 40.0 * h2(vec2(i0, 3.0)));
        vec2 zl = floor(vec2(qd.x / 9.0, qd.y / 1.6)), fz = vec2(qd.x / 9.0, qd.y / 1.6) - zl;
        float da = h2(zl + 3.3);
        float dicht = mix(0.92, 0.55, feld);
        float stamm = step(dicht, da) * (1.0 - smoothstep(0.18, 0.30, abs(fz.y - 0.5))) * smoothstep(0.08, 0.16, fz.x) * (1.0 - smoothstep(0.7, 0.85, fz.x));
        float nahS = 1.0 - smoothstep(0.25, 0.9, px);
        vec3 holzF = mix(vec3(0.05, 0.04, 0.03), vec3(0.50, 0.45, 0.38), step(0.5, h2(zl + 9.1)));
        float anteil = (stamm * nahS + (1.0 - nahS) * (1.0 - dicht) * 0.55) * flach;
        col = mix(col, holzF * weissL * 0.85, clamp(anteil, 0.0, 0.9) * (1.0 - front) * (1.0 - quelle) * (1.0 - landW));
    }
    // ---- W5 (E11) die Zunge am Hang [G]: durchlüftetes Weißwasser, das hangauf schießt, längs der Laufrichtung in Fäden
    // gerissen; zwischen den Fäden braunes Wasser voll Erde; an der Kante ein Saum aus Schaum und Spritzern; Stämme
    // treiben darin. Fällt der Stand wieder (Rückfall), laufen Schleier und Rinnen hangab, das Wasser wird dünn und glänzt.
    if (landW > 0.0) {
        float seitL = max(seit - vorkante * landW, 0.0);
        Zm m = zungeMuster(wL < 0.5 ? DL0 : DL1, vW.xz, seitL, t, px);
        if (wL > 0.0 && wL < 1.0) {
            Zm o = zungeMuster(wL < 0.5 ? DL1 : DL0, vW.xz, seitL, t, px);
            float a = wL < 0.5 ? wL : 1.0 - wL;
            // Runde 5: zwei unabhängige Muster gemischt sind flauer (Streuung × √(a² + (1−a)²)); auf die alte Streuung zurück
            float vk = inversesqrt(a * a + (1.0 - a) * (1.0 - a));
            m.faden = 0.5 + (mix(m.faden, o.faden, a) - 0.5) * vk; m.ball = 0.5 + (mix(m.ball, o.ball, a) - 0.5) * vk;
            m.gb = mix(m.gb, o.gb, a) * vk;
            // Runde 6: Stämme nicht gemischt (in der Mitte standen sie halb so kräftig): die der Hauptrichtung voll, die der
            // zweiten kommen kurz vor der Mitte voll dazu (dort kurz doppelt so dicht); die Farbe vom kräftigeren
            float s1 = o.st * smoothstep(0.3, 0.5, a);
            m.stHell = s1 > m.st ? o.stHell : m.stHell; m.st = max(m.st, s1); m.stFern = mix(m.stFern, o.stFern, a);
        }
        float faden = m.faden, ball = m.ball, pxL = px;
        float sinkt = 1.0 - smoothstep(-0.9, -0.05, vSteig + 0.5 * (faden - 0.5));
        float jung = 1.0 - smoothstep(4.0, 14.0, seitL);
        float duenn = 1.0 - smoothstep(1.5, 8.0, vTiefe);
        // Weißwasser: hangauf fast überall, beim Rückfall in Rinnen
        // Runde 3: Schaum als Ballen [G]. Aus 1,5 km las sich die Zunge als flache helle Streifen (wie Schnee): jetzt zwei
        // Lagen gewölbter Ballen (4 und 10 m, „Billow“), die mit dem Wasser wandern, aus ihrem Gefälle eine Normale; Licht
        // von oben auf die Kuppen, die Furchen dazwischen im Eigenschatten
        // (Probe W5Q: mit 0,30–0,62 war die Zunge braun mit hellen Flecken; sie ist durchlüftet, also fast ganz weiß)
        // Runde 3: die Ränder der Fäden in Ballen aufgebrochen (vorher glatte Bänder von 30 m Länge: wie Schnee)
        float bruch = (ball - 0.62) * 0.7;
        float wsL = mix(smoothstep(0.12, 0.42, faden + 0.30 * jung + bruch), smoothstep(0.50, 0.78, faden + bruch) * 0.75, sinkt);
        wsL = max(wsL, (1.0 - smoothstep(0.0, 1.4, seitL)) * smoothstep(0.25, 0.55, faden + 0.3));   // Saum an der Kante
        vec3 erde = mix(vec3(0.30, 0.24, 0.17), vec3(0.20, 0.15, 0.10), duenn) * eDown * 0.45;   // Wasser voll Erde und Nadeln
        vec3 colL = mix(erde, col, 0.25 * (1.0 - duenn));
        // dünnes Wasser beim Rückfall glänzt (Himmel im Wasserfilm), dickes ist trüb
        colL = mix(colL, mix(erde, skyColor(reflect(d, normalize(N + vec3(g.x, 0.0, g.y) * 0.6))) * 0.5, 0.6 * fres + 0.25 * sinkt * duenn), sinkt * 0.7);
        vec3 nB = normalize(N + vec3(m.gb.x, 0.0, m.gb.y));
        vec3 lichtB = sunAt(vW) * max(dot(nB, uSun), 0.0) * 0.9 + umgebung(normalize(nB + vec3(0.0, 0.6, 0.0))) * 1.15;
        float furche = mix(0.38, 1.15, smoothstep(0.15, 0.85, ball));
        vec3 schaumL = mix(vec3(0.86, 0.85, 0.82), vec3(0.62, 0.55, 0.45), 0.35 * duenn + 0.25 * sinkt) * mix(weissW, lichtB * furche, 0.8);
        colL = mix(colL, schaumL, clamp(wsL * (0.95 - 0.35 * duenn * sinkt), 0.0, 1.0));
        // Stämme in der Zunge, längs der Strömung, dunkel mit hellen geschälten (Runde 4: nur nah als Striche, in der
        // Ferne standen sie als kleine helle Rechtecke; fern als dunklere Schlieren)
        float leben = smoothstep(0.5, 3.0, seitL) * (1.0 - smoothstep(30.0, 90.0, seitL));
        float stNah = 1.0 - smoothstep(0.08, 0.3, pxL);
        float stL = m.st * leben * stNah;
        colL *= 1.0 - 0.18 * (1.0 - stNah) * leben * m.stFern;
        colL = mix(colL, mix(vec3(0.05, 0.04, 0.03), vec3(0.45, 0.40, 0.33), m.stHell) * weissW * 0.9, stL * 0.85);
        col = mix(col, colL, landW);
    }
    // W4b: unter dem Netz der Lippe ist die steile Wand die Rückwand der Röhre: dunkel, oben etwas Licht durch die Lippe;
    // ihre Treppe aus dem Höhenfeld (Zickzack) geht im Dunkel unter [G]
    if (uLipNetz == 1 && hatLauf) {
        // Runde 2: auch die flacheren Teile unter der Lippe (in den offenen Abschnitten sah man dort hellen Dunst)
        // Runde 4: die Maske hängt nur noch an Zeit, Höhe und Blick (vorher an der Normale des Höhenfelds, die auf der
        // Treppe der Zellen zickzackt: daraus die geschwungenen Linien auf der Rückwand); ganz dunkel statt 95 %, damit
        // die Streifen des Schaums darunter nicht mehr durchscheinen
        // (zweite Probe: der Fuß der Wand kommt später an; mit −0,6 s lag sein unterer Teil halb außerhalb, dort standen
        // die Linien weiter; jetzt ab −2,5 s, vor der Front hält die Höhe über dem Pegel die Maske zu)
        float roehre = smoothstep(-2.5, -1.2, vSeit) * (1.0 - smoothstep(1.8, 3.5, vSeit)) * smoothstep(0.0, 0.05, hrel) * (1.0 - smoothstep(0.92, 1.04, hrel))
                     * smoothstep(8.0, 16.0, hs) * (1.0 - smoothstep(45.0, 80.0, hs)) * smoothstep(0.0, 0.35, vorn);
        float rs = vn(vec2(dot(vW.xz, Tq) * 0.12, vW.y * 0.05 - uTime * 0.45)) * 0.6 + vn(vec2(dot(vW.xz, Tq) * 0.03 + 4.0, vW.y * 0.02 - uTime * 0.2)) * 0.4;
        // (dritte Probe: ganz ohne die Linien war die Röhre ein schwarzes Loch; etwas mehr Licht von oben durch die Lippe)
        vec3 innenL = skyColor(vec3(0.0, 1.0, 0.0)) * vec3(0.16, 0.50, 0.44) * (0.05 + 0.30 * smoothstep(0.15, 1.0, hrel)) * (0.7 + 0.6 * rs);
        // ohne die Sonne: die Rückwand liegt im Schatten der Lippe, und der Schatten der Treppe aus dem Höhenfeld auf sich
        // selbst (Schattenkarte) zog die dunklen Linien (Probe Runde 4)
        vec3 eAmb = uSkyMode == 1 ? uSkyAmb * 3.14159 : skyColor(vec3(0.0, 1.0, 0.0)) * 0.6;
        vec3 bodyR = mix(mix(klar, milch, 0.55), lehm, schlamm) * eAmb * 0.32;
        col = mix(col, bodyR * 0.5 + innenL, roehre);
    }
    col = aerial(col, d, dist);
    frag = vec4(col, 1.0);
}
