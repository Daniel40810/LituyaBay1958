// knick.frag — Werkbuch Welle W5 (E11): Licht auf einem fallenden Baum (gl.KnickGl), wie das Gelände und die Welle aus
// common.glsl: Sonne am Punkt mit den Horizontkarten, Himmelslicht nach der Richtung der Fläche, Dunst. Laub beidseitig,
// gegen die Sonne etwas durchscheinend; nasse Rinde dunkler und mit Schlamm, sobald die Zunge den Baum erreicht [G].
#include "common.glsl"
in vec3 vW;
in vec3 vN;
in vec3 vCol;
in float vLaub;
in float vH;
in float vNass;
out vec4 frag;

void main() {
    vec3 d = vW - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    vec3 v = -d;
    vec3 n = normalize(vN);
    bool laub = vLaub > 0.25;
    if (laub) { if (dot(n, v) < 0.0) n = normalize(n - 1.3 * dot(n, v) * v); }
    else if (!gl_FrontFacing) n = -n;
    vec3 alb = vCol;
    if (!laub) alb = mix(alb, alb * 0.55 + vec3(0.035, 0.028, 0.020), vNass);   // nasse Rinde, Schlamm
    else alb *= 1.0 - 0.25 * vNass;
    vec3 sonne = sunAt(vW);
    float nl = dot(n, uSun);
    float diff = laub ? max(nl, 0.0) * 0.8 + 0.2 * max(-nl, 0.0) : max(nl, 0.0);
    float durch = laub ? pow(max(dot(d, uSun), 0.0), 3.0) * max(-nl, 0.0) * 0.35 : 0.0;
    float innen = laub ? 0.55 + 0.45 * vH : 0.8;   // unten in der Krone weniger Himmel
    vec3 col = alb * (sonne * (diff + durch) + umgebung(n) * innen);
    col = aerial(col, d, dist);
    frag = vec4(col, 1.0);
}
