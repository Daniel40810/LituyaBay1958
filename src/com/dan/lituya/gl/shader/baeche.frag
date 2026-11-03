// baeche.frag — Wasser der Bäche (Phase 16c): Gletschermilch nach dem Eisanteil, mit Regen trüber, Oberfläche läuft mit der
// Fließgeschwindigkeit stromab (Rauschen längs des Bands), Fresnel gegen den Himmel, Glanz der Sonne, Schaum an steilen
// Stücken; Rand weich. Linear ins HDR wie Gelände und Meer, mit dem Dunst aus common.glsl.
#include "common.glsl"
in vec3 vW;
in vec2 vT;
in float vU, vSeite, vEis, vV, vAlpha, vZufall, vSchaum, vB;
uniform float uRegen;
out vec4 frag;

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    vec3 d = vW - uCam;
    float dist = length(d);
    vec3 dir = d / max(dist, 1e-3);
    // Fließen: Koordinaten im Band (längs stromab, quer), längs mit der Zeit verschoben
    float t = uTime;
    vec2 q = vec2(vU - vV * t, vSeite * vB * 0.5 + vZufall * 37.0);
    float k = 1.0 - smoothstep(80.0, 1500.0, dist);
    float n1 = vn(q / vec2(2.4, 0.9)), n2 = vn(q / vec2(0.8, 0.4) + 11.0);
    float gx = (vn(q / vec2(2.4, 0.9) + vec2(0.05, 0.0)) - n1) * 8.0 + (n2 - 0.5) * 0.6;
    float gz = (vn(q / vec2(2.4, 0.9) + vec2(0.0, 0.05)) - n1) * 8.0;
    vec2 tang = normalize(vT), quer = vec2(-tang.y, tang.x);
    vec2 g = (tang * gx + quer * gz) * 0.12 * k;
    vec3 n = normalize(vec3(g.x, 1.0, g.y));
    // Farbe: klares Wasser über Kies, Gletschermilch (Schluff), bei Regen trüber
    float B = skyBrightness(sunElevDeg());
    vec3 klar = vec3(0.035, 0.07, 0.065), milch = vec3(0.12, 0.19, 0.20), trueb = vec3(0.13, 0.11, 0.08);
    vec3 alb = mix(klar, milch, smoothstep(0.1, 0.6, vEis));
    alb = mix(alb, trueb, clamp(uRegen / 20.0, 0.0, 1.0) * 0.55 * (1.0 - vEis));
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0));
    vec3 body = alb * (sunLight() * max(uSun.y, 0.0) + sky * 0.9);
    vec3 v = -dir;
    float fres = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
    vec3 r = reflect(dir, n);
    vec3 col = mix(body, skyColor(vec3(r.x, max(r.y, 0.01), r.z)), fres * 0.8);
    vec3 hv = normalize(uSun + v);
    col += sunLight() * pow(max(dot(n, hv), 0.0), 180.0) * 1.2 * k;
    // Schaum an steilen Stücken mit viel Wasser
    float f = 0.65 * vn(q / vec2(1.6, 0.7) + 5.0) + 0.35 * vn(q / vec2(0.5, 0.3) + 9.0);
    float s = smoothstep(0.62, 0.9, f) * vSchaum * 0.6 * k;
    col = mix(col, vec3(0.75) * (sunLight() * max(uSun.y, 0.0) + sky), s);
    float rand = 1.0 - smoothstep(0.55, 1.0, abs(vSeite));
    col = aerial(col, dir, dist);
    frag = vec4(col, vAlpha * mix(0.35, 0.92, rand));
}
