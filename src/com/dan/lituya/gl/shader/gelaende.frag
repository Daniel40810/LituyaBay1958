// gelaende.frag — das Gelände als Platzhalter (Phase 4, bis surface in Phase 5 und Grundbild in 5a): Farbe nach Höhe und
// Hang [G], beleuchtet mit Sonne und Himmel aus common.glsl, mit den Marken und dem Dunst wie das Wasser.
// Farben [G]: Ufer Kies grau, Regenwald (Sitka-Fichte, Hemlock) dunkelgrün bis rund 500 m (die Spurlinie 1958 reicht bis
// 524 m), darüber Gebüsch und Fels, Fels an steilen Hängen (Schiefer), Schnee und Firn ab rund 1 000 m, wo es flach genug ist.
// Unter dem Pegel zeichnet das Wasser darüber (meer.frag schreibt seine Tiefe).
#include "common.glsl"
#include "marken.glsl"
#include "/com/dan/clipmap/gl/clipmap.glsl"
in vec3 vW;
in vec3 vN;
in float vH;
out vec4 frag;

void main() {
    if (clipmapHidden(vW.xz)) discard;
    vec3 n = normalize(vN);
    float slope = 1.0 - n.y;                          // 0 flach, 0,29 bei 45°
    float h = vH - uLevel;
    vec3 kies = vec3(0.30, 0.29, 0.27), wald = vec3(0.045, 0.075, 0.045), busch = vec3(0.10, 0.12, 0.07),
         fels = vec3(0.21, 0.20, 0.19), schnee = vec3(0.80, 0.83, 0.88), grund = vec3(0.10, 0.11, 0.10);
    vec3 alb = mix(wald, busch, smoothstep(350.0, 650.0, h));
    alb = mix(alb, fels, smoothstep(0.30, 0.45, slope));
    alb = mix(kies, alb, smoothstep(2.0, 8.0, h));
    alb = mix(alb, schnee, smoothstep(900.0, 1300.0, h) * (1.0 - smoothstep(0.40, 0.60, slope)));
    alb = mix(grund, alb, smoothstep(-1.0, 0.5, h));
    float B = skyBrightness(sunElevDeg());
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0));
    vec3 col = alb * (sunLight() * max(dot(n, uSun), 0.0) + sky * (0.55 + 0.45 * n.y));
    vec3 d = vW - uCam;
    float dist = length(d);
    col = applyMarks(col, vW.xz, dist, B);
    col = aerial(col, d / max(dist, 1e-3), dist);
    frag = vec4(col, 1.0);
}
