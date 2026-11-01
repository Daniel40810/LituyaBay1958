// meer.frag — das Wasser als Platzhalter (Phase 1, aus Sylt und Timmendorf, bis ocean mit Gletschermilch in Phase 5a):
// eine Wasserfläche im Pegel uLevel mit Erdkrümmung (der Strahl trifft die gekrümmte Fläche, daher liegt die Kimm richtig
// unter dem Horizont des Auges), Spiegelung des Himmels nach Fresnel (Schlick), Glitzer der Sonne aus einem Rauschen der
// Normale [G] und die Marken (marken.glsl). Phase 1 hat kein Gelände: das Wasser reicht überall bis zur Kimm. Die Farbe
// des Wassers ist ein kühles Fjordgrün [G]; die Trübe aus Schwebstoff der Gletscher kommt in Phase 5a (9.9, F20 A).
#include "common.glsl"
#include "marken.glsl"
in vec2 vUV;
out vec4 frag;

float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    vec3 d = viewRay(vUV);
    // Strahl gegen die gekrümmte Wasserfläche y = uLevel − r²/2R' (r waagerecht vom Auge): a t² + b t + c = 0
    float R = EARTH_R / 0.87;
    float hh = dot(d.xz, d.xz);
    float a = hh / (2.0 * R), b = d.y, c = uCam.y - uLevel;
    // stabil gelöst (Befund Phase 1): mit (−b − √D)/2a löschen sich bei kleinem a zwei fast gleiche Zahlen aus, und aus
    // 1,9 km Höhe zitterten Raster und Marken; q = −(b + sign(b)·√D)/2 gibt beide Wurzeln ohne Auslöschung (q/a und c/q)
    float t;
    if (a < 1e-12) t = b < 0.0 ? -c / b : -1.0;
    else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) discard;
        float q = -0.5 * (b + (b < 0.0 ? -1.0 : 1.0) * sqrt(disc));
        float t1 = q / a, t2 = abs(q) > 1e-20 ? c / q : -1.0;
        t = min(t1 > 0.0 ? t1 : 1e30, t2 > 0.0 ? t2 : 1e30);
        if (t > 1e29) discard;
    }
    if (!(t > 0.0) || c < 0.0) discard;
    vec3 w = uCam + d * t;
    float dist = t;

    // Normale: zwei Rauschlagen, in der Ferne glatt; je Bildpunkt gemittelt, damit nichts körnig glitzert [G]
    vec2 q = w.xz;
    float pxm = dist * 0.0012;
    float k1 = (1.0 - smoothstep(300.0, 6000.0, dist)) / (1.0 + pxm / 3.0), k2 = (1.0 - smoothstep(30.0, 600.0, dist)) / (1.0 + pxm);
    vec2 g = vec2(vn(q / 11.0 + uTime * 0.06) - vn(q / 11.0 + vec2(3.1, 0.0) - uTime * 0.045),
                  vn(q / 8.0 - uTime * 0.05) - vn(q / 8.0 + vec2(0.0, 5.3) + uTime * 0.04)) * 0.30 * k1
           + vec2(vn(q / 1.7 + uTime * 0.3) - 0.5, vn(q / 1.3 - uTime * 0.25) - 0.5) * 0.20 * k2;
    vec3 n = normalize(vec3(g.x, 1.0, g.y));
    vec3 v = -d;
    float fres = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
    vec3 r = reflect(d, n);
    vec3 sky = skyColor(vec3(r.x, max(r.y, 0.01), r.z));
    float B = skyBrightness(sunElevDeg());
    vec3 body = vec3(0.025, 0.065, 0.065) * (B / 0.32) * (0.35 + 0.65 * max(uSun.y, 0.0));
    vec3 col = mix(body, sky, fres);
    vec3 hv = normalize(uSun + v);
    float shin = mix(400.0, 60.0, smoothstep(100.0, 8000.0, dist));
    col += sunLight() * pow(max(dot(n, hv), 0.0), shin) * (shin / 400.0) * 3.0;
    col = applyMarks(col, q, dist, B);
    col = aerial(col, d, dist);
    frag = vec4(col, 1.0);
}
