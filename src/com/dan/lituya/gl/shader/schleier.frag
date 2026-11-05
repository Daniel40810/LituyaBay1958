// schleier.frag — Werkbuch Welle W5 (E11): ein fallender Faden aus Wasser und Gischt, weich gemischt (vormultipliziert);
// Licht wie die Tropfen der Lippe: Sonne am Punkt und Himmel, Gischt streut nach vorn (im Gegenlicht heller) [G].
#include "common.glsl"
in vec3 vW;
in float vA;
in vec2 vQ;
in float vK;
out vec4 frag;

void main() {
    float a = vA * (1.0 - smoothstep(0.35, 1.0, abs(vQ.x))) * (1.0 - smoothstep(0.6, 1.0, abs(vQ.y)));
    if (a < 0.003) discard;
    vec3 d = vW - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    float vor = pow(max(dot(d, uSun), 0.0), 4.0);
    vec3 N = normalize(vec3(0.0, 1.0, 0.0) - d * 0.5);
    vec3 licht = sunAt(vW) * (0.35 + 1.4 * vor) + umgebung(normalize(N + vec3(0.0, 1.2, 0.0))) * 1.05;
    vec3 col = vec3(0.86, 0.87, 0.86) * licht;
    col = aerial(col, d, dist);
    a *= 0.55;
    frag = vec4(col * a, a);
}
