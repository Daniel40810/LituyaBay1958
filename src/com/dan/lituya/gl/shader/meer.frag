// meer.frag — das Wasser als Platzhalter (Phase 1, aus Sylt und Timmendorf, bis ocean mit Gletschermilch in Phase 5a):
// eine Wasserfläche im Pegel uLevel mit Erdkrümmung (der Strahl trifft die gekrümmte Fläche, daher liegt die Kimm richtig
// unter dem Horizont des Auges), Spiegelung des Himmels nach Fresnel (Schlick), Glitzer der Sonne aus einem Rauschen der
// Normale [G] und die Marken (marken.glsl). Seit Phase 4 schreibt es seine Tiefe: wo das Gelände (clipmap) näher ist,
// verdeckt es das Wasser; unter dem Pegel liegt das Gelände unter dem Wasser. Die Farbe
// des Wassers ist ein kühles Fjordgrün [G]; die Trübe aus Schwebstoff der Gletscher kommt in Phase 5a (9.9, F20 A).
#include "common.glsl"
#include "marken.glsl"
in vec2 vUV;
out vec4 frag;
// ab Phase 13b: im Rechteck des gröbsten Gitters der Welle zeichnet die Welle (welle.frag), hier nichts
uniform int uWelle;
// ab Phase 14a: der Holzteppich am Morgen danach (r schwimmend, g gestrandet, Bedeckung 0 … 1) aus WaldGl; x0, z0, Breite, Tiefe
uniform int uTeppichAn;
uniform sampler2D uTeppich;
uniform vec4 uTeppichRect;
uniform vec4 uWelleRect;   // x0, z0, x1, z1 (Szene)
// Werkbuch Welle W2: zeichnet com.dan.ocean das Wasser, legt dieser Shader nur noch den Holzteppich darüber (Alpha)
uniform int uNurTeppich;
// Werkbuch Welle W6 (E6): Eisbrocken der Gletscherfront im Gilbert Inlet nach der Welle (die Front wurde bis rund 400 m
// abgeschert, FRITZ01); Mitte der Front x, z, Abklingen (m); Dichte 0 … 1 [G]
uniform int uEisAn;
uniform vec3 uEisMitte;
uniform float uEisDichte;


float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
// W0 (K4): Rauschen mit Periode p (m), das zum Mittel 0,5 ausblendet, sobald ein Bildpunkt (px, m) mehr als ein Drittel
// der Periode deckt (im flachen Blick über das Wasser sind die Bildpunkte in der Tiefe lang gestreckt)
float vnf(vec2 q, float p, vec2 o, float px) { return mix(vn(q / p + o), 0.5, smoothstep(0.25, 0.6, px / p)); }

// Zellen (Voronoi) für die Brocken: Kennung, Abstand zur Nachbarzelle (Fuge), Abstand zur Mitte
vec3 eisZelle(vec2 q, float g) {
    vec2 i = floor(q / g), f = q / g - i;
    float d1 = 9.0, d2 = 9.0, id = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 c = vec2(x, y), o = vec2(h2(i + c), h2(i + c + 17.3));
            float dd = length(c + o - f);
            if (dd < d1) { d2 = d1; d1 = dd; id = h2(i + c + 5.1); } else if (dd < d2) d2 = dd;
        }
    return vec3(id, d2 - d1, d1);
}

// Strahl gegen die gekrümmte Wasserfläche y = level − r²/2R' (r waagerecht vom Auge): a t² + b t + c = 0; −1: kein Treffer
float hit(vec3 d, float level) {
    float R = EARTH_R / 0.87;
    float hh = dot(d.xz, d.xz);
    float a = hh / (2.0 * R), b = d.y, c = uCam.y - level;
    if (c < 0.0) return -1.0;
    // stabil gelöst (Befund Phase 1): mit (−b − √D)/2a löschen sich bei kleinem a zwei fast gleiche Zahlen aus, und aus
    // 1,9 km Höhe zitterten Raster und Marken; q = −(b + sign(b)·√D)/2 gibt beide Wurzeln ohne Auslöschung (q/a und c/q)
    float t;
    if (a < 1e-12) t = b < 0.0 ? -c / b : -1.0;
    else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return -1.0;
        float q = -0.5 * (b + (b < 0.0 ? -1.0 : 1.0) * sqrt(disc));
        float t1 = q / a, t2 = abs(q) > 1e-20 ? c / q : -1.0;
        t = min(t1 > 0.0 ? t1 : 1e30, t2 > 0.0 ? t2 : 1e30);
        if (t > 1e29) return -1.0;
    }
    return t > 0.0 ? t : -1.0;
}

void main() {
    vec3 d = viewRay(vUV);
    float t = hit(d, uLevel);
    if (t < 0.0) discard;
    vec3 w = uCam + d * t;
    // ab Phase 13b: im Rechteck der Welle zeichnet welle.frag; hier nur ein Boden 40 m unter dem Pegel, der Lücken
    // füllt, wo die Welle trocken ist, das Gelände von heute aber tiefer liegt als der Grund von 1958
    if (uWelle == 1 && w.x > min(uWelleRect.x, uWelleRect.z) && w.x < max(uWelleRect.x, uWelleRect.z)
            && w.z > min(uWelleRect.y, uWelleRect.w) && w.z < max(uWelleRect.y, uWelleRect.w)) {
        t = hit(d, uLevel - 40.0);
        if (t < 0.0) discard;
        w = uCam + d * t;
    }
    float dist = t;

    // Normale: zwei Rauschlagen, in der Ferne glatt; je Bildpunkt gemittelt, damit nichts körnig glitzert [G]
    vec2 q = w.xz;
    float pxm = dist * 0.0012;
    float px = max(length(fwidth(q)), 1e-4);
    float k1 = (1.0 - smoothstep(300.0, 6000.0, dist)) / (1.0 + pxm / 3.0), k2 = (1.0 - smoothstep(30.0, 600.0, dist)) / (1.0 + pxm);
    vec2 g = vec2(vnf(q, 11.0, vec2(uTime * 0.06), px) - vnf(q, 11.0, vec2(3.1, 0.0) - uTime * 0.045, px),
                  vnf(q, 8.0, vec2(-uTime * 0.05), px) - vnf(q, 8.0, vec2(0.0, 5.3) + uTime * 0.04, px)) * 0.30 * k1
           + vec2(vnf(q, 1.7, vec2(uTime * 0.3), px) - 0.5, vnf(q, 1.3, vec2(-uTime * 0.25), px) - 0.5) * 0.20 * k2;
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
    col += sunAt(w) * pow(max(dot(n, hv), 0.0), shin) * (shin / 400.0) * 3.0;
    // W6 (E6): Eisbrocken (Brocken 14 m, Splitter 4 m, dazwischen dunkles Wasser), in der Ferne gemittelt [G]
    float covE = 0.0;
    vec3 litE = vec3(0.0);
    if (uEisAn == 1) {
        float rr = length(q - uEisMitte.xy);
        float dichte = uEisDichte * exp(-max(0.0, rr - 250.0) / uEisMitte.z);
        if (dichte > 0.01) {
            vec3 b1 = eisZelle(q, 14.0), b2 = eisZelle(q + 31.0, 4.0);
            float gross = step(b1.x, dichte * 0.75) * smoothstep(0.05, 0.14, b1.y);
            float klein = step(b2.x, dichte) * smoothstep(0.06, 0.18, b2.y);
            float fern = smoothstep(1.5, 6.0, px);
            covE = mix(max(gross, klein * 0.85), dichte * 0.6, fern);
            float blau = step(0.72, fract(b1.x * 7.3));
            vec3 eisF = mix(vec3(0.78, 0.84, 0.90), vec3(0.30, 0.52, 0.70), blau * (1.0 - fern) * gross);
            eisF *= 0.85 + 0.3 * vnf(q, 2.0, vec2(9.0), px);
            // Runde 2b: jeder Brocken etwas anders geneigt (Licht), der Rand heller (Kante, Schnee), manche fast unter
            // Wasser (türkis durchscheinend) [G]
            float idB = gross > 0.5 ? b1.x : b2.x, randB = gross > 0.5 ? b1.y : b2.y;
            vec3 nB = normalize(vec3(fract(idB * 13.7) - 0.5, 2.2, fract(idB * 29.1) - 0.5));
            float kante = (1.0 - smoothstep(0.06, 0.20, randB)) * (1.0 - fern);
            eisF = mix(eisF, vec3(0.92, 0.95, 0.98), kante * 0.5);
            float unter = step(0.82, fract(idB * 3.9)) * (1.0 - fern);
            eisF = mix(eisF, vec3(0.10, 0.32, 0.36), unter * 0.7);
            // Himmelslicht aus 35° Höhe (heller als der Zenit; das Wasser daneben spiegelt den hellen Himmel an der Kimm)
            vec3 himmel = skyColor(normalize(vec3(-d.x, 0.7, -d.z)));
            litE = eisF * (sunAt(w) * max(dot(nB, uSun), 0.0) + himmel * 1.4 * (0.75 + 0.25 * nB.y));
            covE *= 1.0 - 0.4 * unter;
            col = mix(col, litE, clamp(covE, 0.0, 0.95));
        }
    }
    float covT = 0.0;
    vec3 litT = vec3(0.0);
    if (uTeppichAn == 1) {
        vec2 tu = (q - uTeppichRect.xy) / uTeppichRect.zw;
        if (tu.x > 0.0 && tu.y > 0.0 && tu.x < 1.0 && tu.y < 1.0) {
            vec2 tp = texture(uTeppich, tu).rg;
            float c = clamp((tp.r + tp.g) * 2.5, 0.0, 1.0);
            // Rinde, Äste und Splitter: zwei Rauschlagen, in der Ferne gemittelt [G]
            float kf = 1.0 - smoothstep(200.0, 3000.0, dist);
            float p = mix(0.5, vnf(q, 2.3, vec2(0.0), px) * 0.6 + vnf(q, 0.7, vec2(17.0), px) * 0.4, kf);
            float cov = smoothstep(0.0, 0.35, c - (1.0 - p) * 0.6 + 0.15) * c;
            vec3 holz = mix(vec3(0.10, 0.075, 0.05), vec3(0.20, 0.16, 0.11), p);
            litT = holz * (sunAt(w) * max(dot(vec3(0.0, 1.0, 0.0), uSun), 0.0) + skyColor(vec3(0.0, 1.0, 0.0)) * 0.8);
            covT = clamp(cov, 0.0, 0.92);
            col = mix(col, litT, covT);
        }
    }
    if (uNurTeppich == 1) {
        // Auflage über com.dan.ocean: Holz über Eis, Alpha zusammen
        float aE = clamp(covE, 0.0, 0.95), a = aE + covT * (1.0 - aE);
        if (a <= 0.001) discard;
        vec3 c = (litE * aE * (1.0 - covT) + litT * covT) / a;
        gl_FragDepth = depthOf(w);
        frag = vec4(aerial(c, d, dist), a);
        return;
    }
    if (uNurTeppich == 1) discard;
    col = applyMarks(col, q, dist, B);
    col = aerial(col, d, dist);
    // ab Phase 4: die Tiefe der Wasserfläche, damit das Gelände davor und darüber das Wasser verdeckt
    gl_FragDepth = depthOf(w);
    frag = vec4(col, 1.0);
}
