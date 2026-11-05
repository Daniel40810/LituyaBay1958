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
    // W5 Runde 2: Gischt streut das Licht des ganzen Himmels, im Gegenlicht zur hellen Kimm im Nordwesten am meisten; vorher
    // nur Zenit und Sonne (die um 22:18 fehlt): die Fäden standen dunkel vor dem hellen Himmel und waren kaum zu sehen
    // (Probe W5E: mit der Farbe der Kimm glühten die Fäden orange wie Funken; jetzt nur ihre Helligkeit, kühl)
    vec3 kimm = hazeColor(normalize(vec3(uSun.x, 0.0, uSun.z) + vec3(0.0, 1e-4, 0.0)));
    float kimmL = dot(kimm, vec3(0.2126, 0.7152, 0.0722));
    float gegen = pow(max(dot(d, normalize(vec3(uSun.x, 0.12, uSun.z))), 0.0), 2.0);
    vec3 licht = sunAt(vW) * (0.35 + 1.4 * vor) + umgebung(normalize(N + vec3(0.0, 1.2, 0.0))) * 1.25 + vec3(0.85, 0.9, 1.0) * kimmL * (0.15 + 0.35 * gegen);
    vec3 col = vec3(0.92, 0.93, 0.92) * licht;
    col = aerial(col, d, dist);
    a *= 0.5;   // weich, als Schleier statt Funken
    frag = vec4(col * a, a);
}
