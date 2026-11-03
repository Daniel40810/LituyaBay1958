// baeche.vert — die Bäche aus LB_NET_WASSER als Bänder auf dem Gelände (Phase 16c, BaecheGl). Je Stützpunkt zwei
// Scheitel (links, rechts); die Breite rechnet der Shader aus dem Abfluss beim Regen uRegen (mm/h):
//   Q = Mittelwasser + Fläche · Regen · Beiwert / 3,6, Mittelwasser = Fläche · 3 000 mm/a · Beiwert (Beiwert Land 0,7, Eis 1,0) [G]
//   b = 2,5 · Q^0,5 (0,5 … 90 m) [G], Fließgeschwindigkeit nach Manning mit d = 0,3 · Q^0,4, n = 0,045 (wie tools.Wasser).
// Fern wird das Band mindestens rund 1,5 Bildpunkte breit und dafür durchsichtiger; es hebt sich mit dem Abstand über die
// gröberen Stufen der Clipmap und folgt der Erdkrümmung wie das Gelände.
#include "common.glsl"
layout(location = 0) in vec4 aP;   // x, y, z, Weg längs (m)
layout(location = 1) in vec4 aS;   // Seite x, Seite z, ±1, Eisanteil
layout(location = 2) in vec4 aQ;   // Fläche (km²), Gefälle, frei, Zufall
uniform float uRegen, uKruemmung, uPx;
out vec3 vW;
out vec2 vT;
out float vU, vSeite, vEis, vV, vAlpha, vZufall, vSchaum, vB;

void main() {
    float eis = aS.w, A = aQ.x;
    float c = mix(0.7, 1.0, eis);
    float q = A * c * (3000.0 / 1000.0 * 1e6 / (365.25 * 86400.0)) + A * uRegen * c / 3.6;
    float b = clamp(2.5 * sqrt(max(q, 0.0)), 0.5, 90.0);
    float d = 0.3 * pow(max(q, 1e-3), 0.4);
    float v = clamp(pow(d, 2.0 / 3.0) * sqrt(max(aQ.y, 1e-5)) / 0.045, 0.1, 6.0);
    vec3 p = aP.xyz;
    vec2 r = p.xz - uCam.xz;
    float dist = length(vec3(r.x, p.y - uCam.y, r.y));
    float hb = 0.5 * b, minHalf = 0.75 * dist * uPx;
    float h = max(hb, minHalf);
    vec2 seite = aS.xy;
    p.xz += seite * aS.z * h;
    p.y += 0.12 + 0.0015 * dist;
    p.y -= dot(r, r) * uKruemmung;
    vW = p;
    vT = vec2(seite.y, -seite.x);
    vU = aP.w;
    vSeite = aS.z;
    vEis = eis;
    vV = v;
    vB = b;
    vZufall = aQ.w;
    vSchaum = clamp((aQ.y - 0.04) * 6.0, 0.0, 1.0) * clamp(q / 8.0, 0.0, 1.0);
    vAlpha = max(0.15, hb / h) * (1.0 - smoothstep(6000.0, 12000.0, dist));
    gl_Position = uVP * vec4(p, 1.0);
}
