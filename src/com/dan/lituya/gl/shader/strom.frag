// strom.frag — der Felssturz der Vorrechnung als Fläche über dem Grund von 1958 (Phase 13b, spur.vert mit STROM):
// Felsfarbe [G] (Schiefer und Gneis der Fairweather-Kette, grau mit braunen Flecken), grob gesprenkelt wie Blöcke, an der
// Front heller vom Staub. Wo die Mächtigkeit unter 0,3 m fällt, liegt nichts.
#include "common.glsl"
in vec3 vW;
in vec3 vN;
in float vNass;
in float vH;
in float vSteig;
out vec4 frag;

float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
// W0 (K4): Rauschen mit Periode p (m), das zum Mittel 0,5 ausblendet, sobald ein Bildpunkt mehr als ein Drittel davon deckt
float vnf(vec2 q, float p, float px) { return mix(vn(q / p), 0.5, smoothstep(0.25, 0.6, px / p)); }

void main() {
    if (vNass < 0.5) discard;
    vec3 n = normalize(vN);
    vec2 q = vW.xz;
    float px = max(length(fwidth(q)), 1e-4);
    float blk = vnf(q, 6.0, px) * 0.5 + vnf(q, 2.1, px) * 0.3 + vnf(q, 0.7, px) * 0.2;
    vec3 grau = vec3(0.20, 0.195, 0.19), braun = vec3(0.23, 0.18, 0.13), hell = vec3(0.42, 0.40, 0.37);
    vec3 alb = mix(grau, braun, smoothstep(0.45, 0.7, vnf(q, 15.0, px)));
    alb *= 0.75 + 0.5 * blk;
    alb = mix(alb, hell, smoothstep(0.75, 0.95, blk) * 0.6);
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0));
    vec3 col = alb * (sunAt(vW) * max(dot(n, uSun), 0.0) + sky * (0.55 + 0.45 * n.y));
    vec3 d = vW - uCam;
    float dist = length(d);
    col = aerial(col, d / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
