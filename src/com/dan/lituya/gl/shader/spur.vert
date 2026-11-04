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

const int TROCKEN = -32768;

// roher Wert einer Zelle, gemischt; nass = 0 trocken, 1 nass
float wert(ivec2 c, out float nass) {
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
    float g = texelFetch(uBoden, clamp(c, ivec2(0), uN - 1), 0).r;
    return g + (nass > 0.5 ? v : -1.0);
#else
    if (nass > 0.5) return v;
    float s = 0.0, n = 0.0, w;
    ivec2 d[4] = ivec2[4](ivec2(uSchritt, 0), ivec2(-uSchritt, 0), ivec2(0, uSchritt), ivec2(0, -uSchritt));
    for (int i = 0; i < 4; i++) { float x = wert(c + d[i], w); s += x * w; n += w; }
    return n > 0.0 ? s / n - 1.5 : uLevel - 30.0;
#endif
}

float hoehe(ivec2 c) { float w; return hoehe(c, w); }

void main() {
    int q = gl_VertexID / 6, e = gl_VertexID % 6;
    int qi = q % uQuads, qj = q / uQuads;
    // zwei Dreiecke je Viereck, gegen den Uhrzeigersinn von oben (z nach Süden, Zeilen nach Norden)
    const ivec2 ECK[6] = ivec2[6](ivec2(0, 0), ivec2(0, 1), ivec2(1, 0), ivec2(1, 0), ivec2(0, 1), ivec2(1, 1));
    ivec2 l = ivec2(qi, qj) + ECK[e];
    ivec2 c = uKachel + l * uSchritt;
    float nass;
    float h = hoehe(c, nass);
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
            h = mix(h1, h2, fr);
            nass = mix(n1, n2, fr);
        }
    }
    ivec2 cc = clamp(c, ivec2(0), uN - 1);
    float d = float(uSchritt) * uGeo.z;
    float gx = (hoehe(cc + ivec2(uSchritt, 0)) - hoehe(cc - ivec2(uSchritt, 0))) / (2.0 * d);
    float gn = (hoehe(cc + ivec2(0, uSchritt)) - hoehe(cc - ivec2(0, uSchritt))) / (2.0 * d);   // nach Norden = −z
    vN = normalize(vec3(-gx, 1.0, gn));
    vec2 xz = vec2(uGeo.x + float(c.x) * uGeo.z, uGeo.y - float(c.y) * uGeo.z);
    vec2 r = xz - uCam.xz;
    vW = vec3(xz.x, h - dot(r, r) * uKruemmung, xz.y);
    vH = h;
    vNass = nass;
#ifdef STROM
    vSteig = 0.0;
    vTiefe = 99.0;
#else
    vTiefe = uBodenAn == 1 ? h - texelFetch(uBoden, cc, 0).r : 99.0;
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
