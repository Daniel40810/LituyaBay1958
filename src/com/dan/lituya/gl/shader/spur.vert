// spur.vert — eine Kachel einer Spur der Vorrechnung als Höhenfeld (Phase 13b): die Welle je Gitter (16, 4, 2 m) und,
// mit STROM, die Mächtigkeit des Felssturzes über dem Grund von 1958. Die Scheitel kommen aus gl_VertexID (kein Puffer):
// eine Kachel hat uQuads × uQuads Vierecke im Abstand uSchritt Zellen. Die Werte liegen roh als short in zwei Texturen
// (Bild a und b, Stufen zu uStufe m, −32768 trocken) und werden mit uF gemischt.
// Ränder ohne Risse: grenzt eine gröbere Kachel an (uNachbar W, O, S, N), liegen die Scheitel am Rand auf der Geraden
// zwischen den Scheiteln der gröberen; beide Seiten zeichnen dieselbe Kante.
// Trockene Zellen: der Scheitel sinkt 1,5 m unter das Mittel der nassen Nachbarn (das Gelände deckt ihn), vNass = 0, und
// der Bildpunkt verwirft, was zu weit vom Nassen liegt. Erdkrümmung wie die Clipmap.
#include "common.glsl"
uniform isampler2D uA, uB;
uniform float uF, uStufe, uKruemmung;
uniform ivec2 uN;
uniform vec3 uGeo;          // Szene x der Spalte 0, Szene z der Zeile 0 (Süden), Zelle (m); Zeilen laufen nach Norden (−z)
uniform ivec2 uKachel;      // erste Zelle der Kachel
uniform int uSchritt, uQuads;
uniform ivec4 uNachbar;     // Schritt der Nachbarkacheln W, O, S, N (0: keine)
uniform sampler2D uBoden;   // Grund 1958 je Zelle (R32F); bei der Welle seit W3b, wenn uBodenAn
uniform int uBodenAn;
out vec3 vW;
out vec3 vN;
out float vNass;
out float vH;
out float vSteig;           // Steigen in m/s (Brandung an der Front)
out float vTiefe;           // W3b: Wasser über dem Grund (m); 99 ohne Grund
out float vQuelle;          // W3c: Wasser des Nahfelds [G] (0 … 1, Stärke des Aufruhrs), sonst 0
// Werkbuch Welle W3c („Schwimmbecken“): das Nahfeld hinter der Linie der Quelle rechnet die Vorrechnung nicht (Fels, damit
// die Quelle frei einspeist). Im Bild war es ein leeres Becken mit Wänden. Jetzt liegt dort Wasser [G]: je Zelle der Stand
// jenseits des nächsten Rands des Nahfelds (Linie, Seiten, hinten), zum Inneren hin zum Pegel abklingend, dazu Aufruhr ab
// dem Einschlag (Wogen bis rund 8 m, klingt in Minuten ab). Nur wo der Grund unter dem Pegel liegt.
uniform int uNahAn;
uniform vec4 uNah;          // Mitte der Linie (Szene x, z), Richtung in die Bucht (Szene x, z)
uniform vec2 uNahMass;      // halbe Länge, Tiefe hinter der Linie (m)
uniform float uEreignisT;   // Ereigniszeit (s)
// W3c: der Felssturz liegt auf demselben Grund wie das Gelände (Rückbau im Bild); vorher auf dem harten Rückbau der
// Rechnung, der an der 60-m-Linie senkrecht abbricht: die Masse stand dort als Tafelberg über dem Wasser
#include "rueckbau.glsl"

const int TROCKEN = -32768;

#ifndef STROM
// Werkbuch Welle W4 (E7): die Lippe. Aus der Flutkarte (Ankunft der Front, höchster Stand) die Laufrichtung (Gefälle der
// Ankunftszeit) und die Zeit seit der Ankunft; Scheitel kurz hinter der Front wandern nach vorn, umso weiter, je höher sie
// im Kamm liegen. So wird die Front steiler, und ganz oben ragt der Kamm über die Wand hinaus [G]. Die Höhen bleiben die
// der Vorrechnung, nur der Ort im Grundriss verschiebt sich (wie die „Choppy Waves“ der Meeresdarstellung).
uniform int uFlutAn;
uniform sampler2D uFlut;
uniform vec4 uFlutRect;
uniform float uEreignis;
uniform float uLippe;       // Stärke der Lippe (0 aus, 1 voll) [G]
out float vLippe;           // Anteil der Lippe (0 … 1)
out vec2 vLauf;             // Laufrichtung der Front (Szene x, z)
out float vSeit;            // s seit der Ankunft der Front (−1e9 ohne)
out float vStand;           // höchster Stand hier (m)
out float vAuf;             // W4: wie viel das Aufsteilen hier angehoben hat (Teile der Höhe)
const float LAUF_C = 60.0;  // Schnelligkeit der Front für die Nachbarn (m/s) [G]

vec2 flutUV(vec2 xz) { return vec2((xz.x - uFlutRect.x) / uFlutRect.z, (uFlutRect.y - xz.y) / uFlutRect.w); }

vec4 lauf(vec2 xz) {
    if (uFlutAn == 0) return vec4(0.0, 0.0, -1e9, 0.0);
    vec2 u = flutUV(xz);
    if (any(lessThan(u, vec2(0.0))) || any(greaterThan(u, vec2(1.0)))) return vec4(0.0, 0.0, -1e9, 0.0);
    vec2 am = textureLod(uFlut, u, 0.0).rg;
    if (am.x > 1e5) return vec4(0.0, 0.0, -1e9, am.y);
    float e = 24.0;
    float ax = textureLod(uFlut, flutUV(xz + vec2(e, 0.0)), 0.0).r, az = textureLod(uFlut, flutUV(xz + vec2(0.0, e)), 0.0).r;
    float bx = textureLod(uFlut, flutUV(xz - vec2(e, 0.0)), 0.0).r, bz = textureLod(uFlut, flutUV(xz - vec2(0.0, e)), 0.0).r;
    float gxa = ax < 1e5 && bx < 1e5 ? ax - bx : ax < 1e5 ? 2.0 * (ax - am.x) : bx < 1e5 ? 2.0 * (am.x - bx) : 0.0;
    float gza = az < 1e5 && bz < 1e5 ? az - bz : az < 1e5 ? 2.0 * (az - am.x) : bz < 1e5 ? 2.0 * (am.x - bz) : 0.0;
    vec2 g = vec2(gxa, gza);
    float lg = length(g);
    // Probe 8: die Ankunft liegt in Stufen der Bilder der Vorrechnung auf 8 m; ihre Linie ist längs der Front gezackt,
    // und das Aufsteilen zeichnete die Zacken als Bögen am Fuß der Wand. Gemittelt über die Nachbarn (±24 m) und vier
    // Diagonalen (±34 m) ist sie glatt.
    float sum = am.x * 2.0, n = 2.0;
    if (ax < 1e5) { sum += ax; n += 1.0; }
    if (bx < 1e5) { sum += bx; n += 1.0; }
    if (az < 1e5) { sum += az; n += 1.0; }
    if (bz < 1e5) { sum += bz; n += 1.0; }
    for (int k = 0; k < 4; k++) {
        vec2 o = vec2(k == 0 || k == 3 ? e : -e, k < 2 ? e : -e);
        float a = textureLod(uFlut, flutUV(xz + o), 0.0).r;
        if (a < 1e5) { sum += a; n += 1.0; }
    }
    return vec4(lg > 1e-4 ? g / lg : vec2(0.0), uEreignis - sum / n, am.y);
}

// Anteil der Lippe: kurz nach der Ankunft, oben im Kamm, nur bei hohen Wellen
float lippe(float seit, float H, float stand) {
    float hs = max(stand - uLevel, 1.0);
    float hrel = clamp(H / hs, 0.0, 1.2);
    float glocke = smoothstep(-0.8, 1.0, seit) * (1.0 - smoothstep(2.5, 9.0, seit));
    return glocke * smoothstep(6.0, 30.0, H) * smoothstep(0.45, 0.95, hrel);
}
#endif

float vh2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vvn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(vh2(i), vh2(i + vec2(1, 0)), f.x), mix(vh2(i + vec2(0, 1)), vh2(i + vec2(1, 1)), f.x), f.y);
}

// roher Wert einer Zelle aus der Vorrechnung, gemischt; nass = 0 trocken, 1 nass
float wertRoh(ivec2 c, out float nass);
float nahStaerke;   // Aufruhr der zuletzt gefüllten Zelle

// W3c: Szenenort der Zelle
vec2 zellOrt(ivec2 c) { return vec2(uGeo.x + float(c.x) * uGeo.z, uGeo.y - float(c.y) * uGeo.z); }
ivec2 ortZelle(vec2 xz) { return ivec2(int(floor((xz.x - uGeo.x) / uGeo.z + 0.5)), int(floor((uGeo.y - xz.y) / uGeo.z + 0.5))); }

// W3c: Wasser im Nahfeld; false außerhalb oder über Land
bool nahfeld(ivec2 c, out float v) {
    v = 0.0;
    nahStaerke = 0.0;
    if (uNahAn == 0) return false;
    vec2 xz = zellOrt(c);
    vec2 dir = uNah.zw, quer = vec2(-dir.y, dir.x);
    vec2 q = xz - uNah.xy;
    float along = dot(q, dir), across = dot(q, quer);
    if (!(along < 2.0 * uGeo.z && along > -uNahMass.y && abs(across) < uNahMass.x)) return false;
#ifndef STROM
    if (uBodenAn == 1 && texelFetch(uBoden, clamp(c, ivec2(0), uN - 1), 0).r > uLevel - 0.5) return false;
#endif
    // nächster Rand: Linie (vorn), hinten, die beiden Seiten; dort knapp außerhalb den Stand der Vorrechnung nehmen
    float dl = max(-along, 0.0), dh = along + uNahMass.y, ds1 = uNahMass.x - across, ds2 = uNahMass.x + across;
    float d = min(min(dl, dh), min(ds1, ds2));
    vec2 aus = d == dl ? dir : d == dh ? -dir : d == ds1 ? quer : -quer;
    float rand = uLevel, nr;
    float x = wertRoh(ortZelle(xz + aus * (d + 3.0 * uGeo.z)), nr);
    if (nr > 0.5) rand = x;
    // zum Inneren hin schnell weg vom Stand am Rand (vierte Probe: mit 260 m stand hinter der Linie ein Wasserberg wie ein
    // Tafelberg); dahinter die Kaverne, in die der Fels fährt, und Wogen ab dem Einschlag [G]
    float k = exp(-d / 70.0);
    float t = uEreignisT;
    float auf = smoothstep(104.0, 108.0, t) * (1.0 - smoothstep(150.0, 420.0, t));
    float tief = smoothstep(0.0, 260.0, d);   // zum Rand hin null: dort schließt die Fläche an die Vorrechnung an
    vec2 w = xz / 70.0;
    float wog = (vvn(w + vec2(t * 0.11, -t * 0.07)) - 0.5) * 2.0 + (vvn(w * 2.3 + vec2(-t * 0.23, t * 0.19) + 7.0) - 0.5) * 0.9;
    float kaverne = smoothstep(104.0, 107.0, t) * (1.0 - smoothstep(118.0, 175.0, t));
    v = uLevel + (rand - uLevel) * k - 26.0 * kaverne * tief + wog * 7.0 * auf * tief;
    nahStaerke = (auf * 0.9 + 0.2 * (1.0 - k) * smoothstep(104.0, 108.0, t) * (1.0 - smoothstep(600.0, 1500.0, t))) * tief;
    return true;
}

float wert(ivec2 c, out float nass) {
    float v = wertRoh(c, nass);
#ifndef STROM
    if (nass < 0.5) {
        float f;
        if (nahfeld(clamp(c, ivec2(0), uN - 1), f)) { nass = 1.0; return f; }
    }
#endif
    return v;
}

float wertRoh(ivec2 c, out float nass) {
    c = clamp(c, ivec2(0), uN - 1);
    int a = texelFetch(uA, c, 0).r, b = texelFetch(uB, c, 0).r;
    bool na = a != TROCKEN, nb = b != TROCKEN;
#ifdef STROM
    // Strom: trocken heißt Mächtigkeit 0
    float x = na ? float(a) : 0.0, y = nb ? float(b) : 0.0;
    float v = mix(x, y, uF) * uStufe;
    nass = v > 0.3 ? 1.0 : 0.0;
    return v;
#else
    if (na && nb) { nass = 1.0; return mix(float(a), float(b), uF) * uStufe; }
    if (na && uF < 0.5) { nass = 1.0; return float(a) * uStufe; }
    if (nb && uF >= 0.5) { nass = 1.0; return float(b) * uStufe; }
    nass = 0.0;
    return 0.0;
#endif
}

// Höhe der Fläche an der Zelle (über NAVD88); trocken: unter dem Mittel der nassen Nachbarn
float hoehe(ivec2 c, out float nass) {
    float v = wert(c, nass);
#ifdef STROM
    // W3c: Mächtigkeit leicht geglättet (Kreuz über eine Zelle), damit der Rand ausläuft statt als senkrechte Wand zu
    // stehen; trocken: 1 m unter den Grund
    float w1, w2, w3, w4;
    float vs = v * 0.4 + 0.15 * (wert(c + ivec2(1, 0), w1) + wert(c - ivec2(1, 0), w2) + wert(c + ivec2(0, 1), w3) + wert(c - ivec2(0, 1), w4));
    nass = vs > 0.3 ? 1.0 : 0.0;
    float g = texelFetch(uBoden, clamp(c, ivec2(0), uN - 1), 0).r;
    if (uRueckbauAn == 1) g = rbHoehe(zellOrt(clamp(c, ivec2(0), uN - 1)), g);
    // W3c: im Wasser bleibt der Strom unter dem Pegel (die Rechnung des Felssturzes kennt kein Wasser und türmte die
    // Masse im Inlet zu einem trockenen Tafelberg auf; die Ablagerung von 1958 liegt unter Wasser). Weich über 30 m Grund.
    float top = g + (nass > 0.5 ? vs : -1.0);
    float unter = 1.0 - smoothstep(uLevel - 30.0, uLevel + 2.0, g);
    return mix(top, min(top, uLevel - 14.0), unter);
#else
    if (nass > 0.5) return v;
    float s = 0.0, n = 0.0, w;
    ivec2 d[4] = ivec2[4](ivec2(uSchritt, 0), ivec2(-uSchritt, 0), ivec2(0, uSchritt), ivec2(0, -uSchritt));
    for (int i = 0; i < 4; i++) { float x = wert(c + d[i], w); s += x * w; n += w; }
    // W3c: ohne nasse Nachbarn unter den Grund (vorher 30 m unter den Pegel: an Stufengrenzen senkrechte Schürzen)
    if (n > 0.0) return s / n - 1.5;
    return uBodenAn == 1 ? min(texelFetch(uBoden, clamp(c, ivec2(0), uN - 1), 0).r, uLevel) - 2.0 : uLevel - 30.0;
#endif
}

float hoehe(ivec2 c) { float w; return hoehe(c, w); }

#ifndef STROM
// W4 (E7) Aufsteilen [G]: die Vorrechnung (Gitter 2–16 m, numerisch geglättet) läuft vor Cenotaph als sanfter Buckel von
// einigen hundert Metern Länge; Zeugen sahen eine Wand. Gleich hinter der Ankunftslinie nimmt jede Ecke die höchste
// Höhe bis D m hinter sich (gegen die Laufrichtung T): eine Dehnung zur Front hin. Sie kann nicht falten, die Linie der
// Ankunft bleibt, wo die Rechnung sie hat, und hinter der Front läuft sie in S s auf die Rechnung zurück.
// Probe 6: hart an der Ankunftslinie gab das Gitter Treppen (Kerben in der Krone, wo die Linie schräg über die Zeilen
// läuft); jetzt gleitet die Ecke über rund anderthalb Abstände der Ecken (uSchritt Zellen) nach der stetigen Ankunftszeit.
float aufsteilen(ivec2 c, float h0, float n0, float seit, vec2 T, float D, float S) {
    float w = 1.5 * float(uSchritt) * uGeo.z / LAUF_C;
    if (n0 < 0.5 || seit < -w || seit > S) return h0;
    float dl = D * (1.0 - smoothstep(0.0, S, max(seit, 0.0)));
    if (dl < uGeo.z) return h0;
    vec2 bk = vec2(-T.x, T.y) / uGeo.z;   // gegen T in Zellen (Zeilen laufen nach Norden, −z)
    float m = h0;
    for (int k = 1; k <= 4; k++) {
        ivec2 cb = c + ivec2(floor(bk * dl * float(k) * 0.25 + 0.5));
        float nb;
        float hb = hoehe(cb, nb);
        if (nb > 0.5) m = max(m, hb);
    }
    return mix(h0, m, smoothstep(-w, 0.3 * w, seit));
}
#endif

void main() {
    int q = gl_VertexID / 6, e = gl_VertexID % 6;
    int qi = q % uQuads, qj = q / uQuads;
    // zwei Dreiecke je Viereck, gegen den Uhrzeigersinn von oben (z nach Süden, Zeilen nach Norden)
    const ivec2 ECK[6] = ivec2[6](ivec2(0, 0), ivec2(0, 1), ivec2(1, 0), ivec2(1, 0), ivec2(0, 1), ivec2(1, 1));
    ivec2 l = ivec2(qi, qj) + ECK[e];
    ivec2 c = uKachel + l * uSchritt;
    float nass;
    float h = hoehe(c, nass);
#ifndef STROM
    // W4: Laufrichtung und Zeit der Front hier, und die Länge des Aufsteilens (0: keins)
    vec2 xz0 = vec2(uGeo.x + float(c.x) * uGeo.z, uGeo.y - float(c.y) * uGeo.z);
    vec4 lf = lauf(xz0);
    float aufD = 0.0;
    if (uLippe > 0.0 && dot(lf.xy, lf.xy) > 0.25 && lf.z > -1.5 && lf.z < 14.0) {
        float Hs = max(lf.w - uLevel, 0.0);
        aufD = clamp(5.0 * Hs, 0.0, 240.0) * smoothstep(6.0, 16.0, Hs) * uLippe;   // [G]
        // nur die laufende Front über tiefem Wasser der Bucht: im Gilbert Inlet (100–150 m) ist die Rechnung schon steil,
        // dort türmte die Dehnung Kamm auf Kamm zu Zacken (Probe 6); am Hang (Auflauf) bleibt die Rechnung
        aufD *= 1.0 - smoothstep(45.0, 80.0, Hs);
        if (uBodenAn == 1) aufD *= 1.0 - smoothstep(uLevel - 14.0, uLevel - 6.0, texelFetch(uBoden, clamp(c, ivec2(0), uN - 1), 0).r);
        if (aufD <= uGeo.z) aufD = 0.0;
    }
    float aufS = 2.0 * aufD / LAUF_C;
    bool randEcke = false;
#endif
    // Rand an eine gröbere Kachel: auf die Gerade ihrer Scheitel
    int s = 0, achse = 0;
    if (l.x == 0 && uNachbar.x > uSchritt) { s = uNachbar.x; achse = 1; }
    else if (l.x == uQuads && uNachbar.y > uSchritt) { s = uNachbar.y; achse = 1; }
    else if (l.y == 0 && uNachbar.z > uSchritt) { s = uNachbar.z; achse = 0; }
    else if (l.y == uQuads && uNachbar.w > uSchritt) { s = uNachbar.w; achse = 0; }
    if (s > 0) {
        int rel = c[achse] - uKachel[achse];
        int a0 = (rel / s) * s;
        float fr = float(rel - a0) / float(s);
        if (fr > 0.0) {
            ivec2 c1 = c, c2 = c;
            c1[achse] = uKachel[achse] + a0;
            c2[achse] = uKachel[achse] + a0 + s;
            float n1, n2;
            float h1 = hoehe(c1, n1), h2 = hoehe(c2, n2);
#ifndef STROM
            // W4: die Scheitel der gröberen Kachel wie dort aufgesteilt (sonst Risse an der Grenze)
            if (aufD > 0.0) {
                float d1 = float(c1[achse] - c[achse]) * uGeo.z, d2 = float(c2[achse] - c[achse]) * uGeo.z;
                float ta = achse == 1 ? lf.x : -lf.y;   // Anteil von T längs der Achse (Zellen: x Ost, Zeilen Nord)
                h1 = aufsteilen(clamp(c1, ivec2(0), uN - 1), h1, n1, lf.z - d1 * ta / LAUF_C, lf.xy, aufD, aufS);
                h2 = aufsteilen(clamp(c2, ivec2(0), uN - 1), h2, n2, lf.z - d2 * ta / LAUF_C, lf.xy, aufD, aufS);
                randEcke = true;
            }
#endif
            h = mix(h1, h2, fr);
            nass = mix(n1, n2, fr);
        }
    }
    ivec2 cc = clamp(c, ivec2(0), uN - 1);
    float d = float(uSchritt) * uGeo.z;
    float nR, nL, nNo, nSu;
    float hR = hoehe(cc + ivec2(uSchritt, 0), nR), hL = hoehe(cc - ivec2(uSchritt, 0), nL);
    float hNo = hoehe(cc + ivec2(0, uSchritt), nNo), hSu = hoehe(cc - ivec2(0, uSchritt), nSu);   // Zeilen nach Norden = −z
    vec2 xz = vec2(uGeo.x + float(c.x) * uGeo.z, uGeo.y - float(c.y) * uGeo.z);
#ifndef STROM
    vAuf = 0.0;
    {
        vec2 T = lf.xy;
        float Hs = max(lf.w - uLevel, 0.0), D = aufD, S = aufS;
        if (D > 0.0) {
            float h0 = h;
            if (!randEcke) h = aufsteilen(cc, h, nass, lf.z, T, D, S);
            hR = aufsteilen(cc + ivec2(uSchritt, 0), hR, nR, lf.z - d * T.x / LAUF_C, T, D, S);
            hL = aufsteilen(cc - ivec2(uSchritt, 0), hL, nL, lf.z + d * T.x / LAUF_C, T, D, S);
            hNo = aufsteilen(cc + ivec2(0, uSchritt), hNo, nNo, lf.z + d * T.y / LAUF_C, T, D, S);
            hSu = aufsteilen(cc - ivec2(0, uSchritt), hSu, nSu, lf.z - d * T.y / LAUF_C, T, D, S);
            vAuf = clamp((h - h0) / max(Hs, 1.0), 0.0, 1.0);
        }
    }
#endif
    float gx = (hR - hL) / (2.0 * d);
    float gn = (hNo - hSu) / (2.0 * d);
    vN = normalize(vec3(-gx, 1.0, gn));
#ifndef STROM
    // W4 (E7): Lippe; die Nachbarn mit ihrer eigenen Höhe und der Zeit, die die Front bis zu ihnen braucht
    vLippe = 0.0;
    vLauf = lf.xy;
    vSeit = lf.z;
    vStand = lf.w;
    // nur wo vorn die Wand steil abfällt (erste Probe: ohne diese Bedingung faltete sich der ganze Kamm zu Stufen)
    float abfall = 0.0;
    if (dot(lf.xy, lf.xy) > 0.25) {
        // Gefälle in Laufrichtung aus dem stetigen Gradienten (Probe 7: die Zelle „zwei voraus“ sprang mit dem Runden der
        // Richtung von Zeile zu Zeile, die Lippe kam und ging in regelmäßigen Bögen längs der Front)
        float ds = gx * lf.x - gn * lf.y;   // dh/ds längs T (gn ist nach Norden, −z)
        abfall = smoothstep(0.25, 0.7, -ds);
    }
    if (uLippe > 0.0 && nass > 0.5 && abfall > 0.0 && lf.z > -2.0 && lf.z < 10.0) {
        float k = 0.22 * uLippe * abfall;   // Vorlauf in Teilen der Höhe über dem Pegel [G]
        float lp = lippe(lf.z, h - uLevel, lf.w);
        vec2 T = lf.xy;
        float tR = lf.z - d * T.x / LAUF_C, tL = lf.z + d * T.x / LAUF_C, tN = lf.z + d * T.y / LAUF_C, tS = lf.z - d * T.y / LAUF_C;
        vec3 pR = vec3(xz + vec2(d, 0.0) + T * min(k * max(hR - uLevel, 0.0) * lippe(tR, hR - uLevel, lf.w), 2.5 * d + 6.0), hR);
        vec3 pL = vec3(xz - vec2(d, 0.0) + T * min(k * max(hL - uLevel, 0.0) * lippe(tL, hL - uLevel, lf.w), 2.5 * d + 6.0), hL);
        vec3 pN = vec3(xz - vec2(0.0, d) + T * min(k * max(hNo - uLevel, 0.0) * lippe(tN, hNo - uLevel, lf.w), 2.5 * d + 6.0), hNo);
        vec3 pS = vec3(xz + vec2(0.0, d) + T * min(k * max(hSu - uLevel, 0.0) * lippe(tS, hSu - uLevel, lf.w), 2.5 * d + 6.0), hSu);
        // p = (x, z, Höhe); Tangenten in Szenenachsen (x, Höhe, z)
        vec3 tx = vec3(pR.x - pL.x, pR.z - pL.z, pR.y - pL.y), tz = vec3(pS.x - pN.x, pS.z - pN.z, pS.y - pN.y);
        vec3 nn = cross(tz, tx);
        if (dot(nn, nn) > 1e-8) vN = normalize(nn);
        xz += T * min(k * max(h - uLevel, 0.0) * lp, 2.5 * d + 6.0);
        vLippe = lp * abfall;
    }
#endif
    vec2 r = xz - uCam.xz;
    vW = vec3(xz.x, h - dot(r, r) * uKruemmung, xz.y);
    vH = h;
    vNass = nass;
#ifdef STROM
    vSteig = 0.0;
    vTiefe = 99.0;
    vQuelle = 0.0;
#else
    cc = clamp(c, ivec2(0), uN - 1);
    vTiefe = uBodenAn == 1 ? h - texelFetch(uBoden, cc, 0).r : 99.0;
    { float f; vQuelle = 0.0; float nr; wertRoh(cc, nr); if (nr < 0.5 && nahfeld(cc, f)) vQuelle = max(nahStaerke, 0.001); }
    // Steigen zwischen den Bildern (m je Bild); die Zeit je Bild gibt uGeo nicht, der Bildpunkt nimmt es als Maß
    int a = texelFetch(uA, cc, 0).r, b = texelFetch(uB, cc, 0).r;
    vSteig = (a != TROCKEN && b != TROCKEN) ? float(b - a) * uStufe : 0.0;
#endif
    // W0 (K4): wo die Schicht dünn über dem Grund liegt (Auflauf am Hang, Strom), lagen Fläche (Gitter 2–16 m) und Gelände
    // (feiner) fast aufeinander und durchdrangen sich unter Bildpunktgröße: Gesprenkel. Die Ecke rückt deshalb um 0,2 % des
    // Abstands auf dem Sehstrahl zur Kamera (Bildort unverändert, nur die Tiefe gewinnt).
#ifdef STROM
    float zug = (1.0 - smoothstep(3.0, 20.0, h - texelFetch(uBoden, cc, 0).r)) * 0.002;
#else
    float zug = 0.0015;   // die Welle hat hier kein Bodengitter: die ganze Fläche, bei 6 km rund 9 m
#endif
    gl_Position = uVP * vec4(mix(vW, uCam, zug), 1.0);
}
