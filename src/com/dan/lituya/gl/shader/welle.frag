// welle.frag — die Welle der Vorrechnung (Phase 13b): Fläche aus spur.vert, gefärbt wie das Wasser in meer.frag
// (Spiegelung des Himmels nach Fresnel, Glanz der Sonne, Körper fjordgrün), dazu Gischt [G]: wo die Fläche steil ist
// und wo sie schnell steigt (die Front), und Trübe aus Schlamm und Geröll, wo sie hoch über dem Pegel steht.
// Von unten (Auge unter der Fläche): dunkler Körper, Totalreflexion außerhalb des Snellschen Fensters.
// Wo das feinere Gitter liegt, zeichnet dieses (uFeiner), und was zu weit vom Nassen liegt, fällt weg.
#include "common.glsl"
in vec3 vW;
in vec3 vN;
in float vNass;
in float vH;
in float vSteig;
out vec4 frag;
uniform vec4 uFeiner;     // Rechteck des feineren Gitters (x0, z0, x1, z1), x0 > x1: keins

float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
// W0 (K4): Rauschen mit Periode p (m), das ausblendet (zum Mittel 0,5 geht), sobald ein Bildpunkt mehr als rund ein
// Drittel der Periode deckt; vorher sprenkelte es in der Ferne weiß und flimmerte beim Bewegen.
float vnf(vec2 q, float p, vec2 o, float px) { return mix(vn(q / p + o), 0.5, smoothstep(0.25, 0.6, px / p)); }

void main() {
    if (vNass < 0.35) discard;
    if (vW.x > min(uFeiner.x, uFeiner.z) && vW.x < max(uFeiner.x, uFeiner.z) && vW.z > min(uFeiner.y, uFeiner.w) && vW.z < max(uFeiner.y, uFeiner.w)) discard;
    vec3 d = vW - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    vec2 q = vW.xz;
    // kleine Wellen wie in meer.frag (zwei Lagen, in der Ferne glatt), auf die Normale der Welle gesetzt
    float pxm = dist * 0.0012;
    float px = max(length(fwidth(q)), 1e-4);   // Bildpunkt auf der Fläche (m)
    float k1 = (1.0 - smoothstep(300.0, 6000.0, dist)) / (1.0 + pxm / 3.0), k2 = (1.0 - smoothstep(30.0, 600.0, dist)) / (1.0 + pxm);
    vec2 g = vec2(vnf(q, 11.0, vec2(uTime * 0.06), px) - vnf(q, 11.0, vec2(3.1, 0.0) - uTime * 0.045, px),
                  vnf(q, 8.0, vec2(-uTime * 0.05), px) - vnf(q, 8.0, vec2(0.0, 5.3) + uTime * 0.04, px)) * 0.30 * k1
           + vec2(vnf(q, 1.7, vec2(uTime * 0.3), px) - 0.5, vnf(q, 1.3, vec2(-uTime * 0.25), px) - 0.5) * 0.20 * k2;
    vec3 n = normalize(normalize(vN) + vec3(g.x, 0.0, g.y));
    vec3 v = -d;
    float B = skyBrightness(sunElevDeg());
    float steil = 1.0 - normalize(vN).y;
    // Gischt: steile Flanke, schnell steigende Front, mit Rauschen ausgefranst [G]
    float roh = vnf(q, 3.0, vec2(uTime * 0.7, 0.0), px) * 0.6 + vnf(q, 0.9, vec2(0.0), px) * 0.4;
    float gischt = clamp(smoothstep(0.08, 0.35, steil) + smoothstep(0.8, 4.0, vSteig), 0.0, 1.0) * smoothstep(0.25, 0.75, roh + 0.3);
    float trueb = smoothstep(2.0, 25.0, vH - uLevel);
    vec3 body = mix(vec3(0.025, 0.065, 0.065), vec3(0.09, 0.085, 0.07), trueb) * (B / 0.32) * (0.35 + 0.65 * max(uSun.y, 0.0));
    vec3 col;
    if (dot(v, normalize(vN)) >= 0.0) {
        float fres = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        vec3 r = reflect(d, n);
        vec3 sky = skyColor(vec3(r.x, max(r.y, 0.01), r.z));
        col = mix(body, sky, fres);
        vec3 hv = normalize(uSun + v);
        float shin = mix(400.0, 60.0, smoothstep(100.0, 8000.0, dist));
        col += sunLight() * pow(max(dot(n, hv), 0.0), shin) * (shin / 400.0) * 3.0 * (1.0 - gischt);
    } else {
        // von unten: im Snellschen Fenster (48,6°) der Himmel gebrochen, sonst der Körper gespiegelt
        float c = dot(-v, n);
        float fenster = smoothstep(0.62, 0.70, c);
        col = mix(body * 0.6, skyColor(normalize(vec3(-v.x, 1.0, -v.z))) * 0.5, fenster);
    }
    vec3 weiss = (sunLight() * max(dot(n, uSun), 0.0) + skyColor(vec3(0.0, 1.0, 0.0)) * 0.8) * mix(0.8, 0.55, trueb);
    col = mix(col, weiss, gischt * 0.85);
    col = aerial(col, d, dist);
    frag = vec4(col, 1.0);
}
