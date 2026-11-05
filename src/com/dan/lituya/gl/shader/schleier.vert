// schleier.vert — Werkbuch Welle W5 (E11): ein Faden Wasser, der oben von der Zunge abreißt und zurückfällt (gl.SchleierGl)
// [G]. Bahn mit Luftwiderstand (Gesetz linear, k = 0,55/s): x(τ) = x0 + v0·(1 − e^(−kτ))/k, in der Höhe dazu die Schwere.
// Gezeichnet als schmales Viereck längs der Bewegung im Bild (Länge = Weg in rund 1/12 s, wie eine Belichtung), Breite
// nach Abstand und Brennweite, mindestens rund ein Bildpunkt (dünner: durchsichtiger statt schmaler).
#include "common.glsl"
layout(location = 0) in vec4 aA;   // Abwurf x, y, z, Zeit
layout(location = 1) in vec4 aB;   // Geschwindigkeit x, y, z, Kennung
uniform float uEreignis, uKruemmung, uPxProM;
out vec3 vW;
out float vA;
out vec2 vQ;
out float vK;
const float K = 0.55, G = 9.81;

vec3 ort(float tau) {
    float e = (1.0 - exp(-K * tau)) / K;
    vec3 p = aA.xyz + aB.xyz * e;
    p.y += -G / K * (tau - e);
    return p;
}

void main() {
    int c = gl_VertexID;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    float tau = uEreignis - aA.w;
    float leben = 4.5 + 4.0 * aB.w;
    vA = 0.0;
    vQ = q;
    vK = aB.w;
    if (tau <= 0.0 || tau > leben) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    vec3 p1 = ort(tau), p0 = ort(max(tau - 0.085, 0.0));
    vec2 r = p1.xz - uCam.xz;
    p1.y -= dot(r, r) * uKruemmung;
    p0.y -= dot(r, r) * uKruemmung;
    float dist = max(length(p1 - uCam), 1.0);
    // Breite des Fadens: Tropfenwolke von rund 0,5–1,5 m [G]
    float bm = 0.5 + 1.0 * fract(aB.w * 7.3);
    float px = bm * uPxProM / dist;
    float breit = max(bm, 1.1 * dist / uPxProM);
    vec3 ach = p1 - p0;
    if (length(ach) < 0.05) ach = vec3(0.0, -0.05, 0.0);
    vec3 zur = normalize(uCam - p1);
    vec3 quer = normalize(cross(ach, zur) + vec3(1e-6));
    vec3 P = mix(p0, p1, q.y * 0.5 + 0.5) + quer * q.x * breit * 0.5 + normalize(ach) * q.y * breit * 0.5;
    vW = P;
    // ein-, ausblenden; dünne Fäden durchsichtiger statt schmaler
    vA = smoothstep(0.0, 0.35, tau) * (1.0 - smoothstep(leben - 1.5, leben, tau)) * clamp(px / 1.1, 0.15, 1.0);
    gl_Position = uVP * vec4(P, 1.0);
}
