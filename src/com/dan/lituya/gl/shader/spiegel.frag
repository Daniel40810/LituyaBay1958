// spiegel.frag — Werkbuch Welle W2 (L7): Spiegel der Berge und Gletschermilch auf dem Wasser von com.dan.ocean.
// Eingang: das am Wasserspiegel gespiegelt gerechnete Bild (uRefl, halbe Größe, a = 1 wo Gelände oder Wald steht), die
// Tiefe der Szene. Je Bildpunkt: Strahl gegen die (gekrümmte) Wasserfläche; liegt die Szene dort oder dahinter, ist es
// Wasser. Dann ersetzt der Spiegel die Himmelsspiegelung, die com.dan.ocean schon gelegt hat (Fresnel, leichte
// Verzerrung durch die Kräusel, in der Ferne gemittelt wie in W0), und das Wasser bekommt milchiges Türkis zum Inlet hin.
// Ausgabe gemischt (ONE, ONE_MINUS_SRC_ALPHA): rgb = Spiegelbild · F + Milch, a = F dort, wo im Spiegel etwas steht; so
// ersetzt der Spiegel die Himmelsspiegelung samt ihren Kräuseln (erste Probe: Abziehen ließ bunte Streifen stehen).
#include "common.glsl"
in vec2 vUV;
out vec4 frag;
uniform sampler2D uRefl, uDepth;
uniform int uSpiegelAn, uWelleAn;
uniform vec4 uWelleRect;     // x0, z0, x1, z1 (Szene)
uniform float uNear, uFar;
uniform vec4 uMilchA, uMilchB;   // Quelle x, z, Reichweite (m), Stärke
uniform mat4 uSpiegelVP, uJetztVP;   // Runde 7: Blick beim Spiegelbild und jetzt
uniform sampler2D uSpTiefe;   // Runde 8: Tiefe des Spiegelbilds
uniform float uSpNah;
uniform int uSpSchritte;   // Runde 9: 1 oder 2 Schritte mit der Tiefe
uniform vec3 uSpVorn;
uniform float uSpTruebe;   // W7 Runde 1c: Anteil des Spiegelbilds, der im Regen im Dunst des Himmels aufgeht
uniform float uSpGlatt;    // W7 Runde 1d: wie glatt das Wasser für den Spiegel ist (1 still, kleiner im Seegang)
uniform sampler2D uSpRing; // W7b: Wellenfeld des Regens (com.dan.precip): Steigung in RG, fest in der Welt
uniform vec4 uSpRingP;     // Kante (m), Stärke, an (1), Reichweite (m)
uniform vec2 uSpRingQ;     // Mitte des Fensters
uniform vec4 uSpRingR;     // W7b Runde 5: mittlere quadratische Steigung unter dem Raster, die das Feld trägt; Anteile der Fläche dazu
uniform int uSpRingN;      // Bildzähler (die zeitliche Glättung mittelt die Facetten)

// Runde 8: Spiegelbild aus einem früheren Bild an die jetzige Stelle (uv: jetzt, dir: Sehstrahl jetzt, normiert).
// Erst die Richtung umrechnen (Runde 7), dort die Tiefe des Spiegelbilds lesen; dann den gespiegelten Punkt auf dem
// jetzigen Strahl in dieser Entfernung umrechnen: so verschieben sich nahe Ufer im Spiegel gegen den Hintergrund.
vec2 spiegelUV(vec2 uv, vec3 dir) {
    vec4 a = uSpiegelVP * vec4(dir, 0.0), j = uJetztVP * vec4(dir, 0.0);
    if (a.w <= 1e-4 || j.w <= 1e-4) return uv;
    vec2 u0 = uv + (a.xy / a.w - j.xy / j.w) * 0.5;
    float z = texture(uSpTiefe, u0).r;
    float vz = uReversed == 1 ? (z <= 0.0 ? 1e9 : uSpNah / z) : 1e9;   // klassische Tiefe: nur die Richtung
    if (vz > 5e8) return u0;
    vec3 Y = uCam + dir * (vz / max(dot(dir, uSpVorn), 1e-3));   // der gespiegelte Punkt (unter dem Wasser)
    vec4 b = uSpiegelVP * vec4(Y, 1.0), k = uJetztVP * vec4(Y, 1.0);
    if (b.w <= 1e-3 || k.w <= 1e-3) return u0;
    vec2 u1 = uv + (b.xy / b.w - k.xy / k.w) * 0.5;
    // Runde 9: ein zweiter Schritt mit der Tiefe an der neuen Stelle. An der Kante vom nahen Ufer zum Berg dahinter lag
    // die erste Tiefe oft auf der falschen Seite; mit dem zweiten Schritt rastet der Punkt auf die Schicht ein, die dort
    // im Spiegelbild wirklich liegt
    float z1 = texture(uSpTiefe, u1).r;
    if (!(z1 > 0.0)) return u1;
    vec3 Y1 = uCam + dir * ((uSpNah / z1) / max(dot(dir, uSpVorn), 1e-3));
    vec4 b1 = uSpiegelVP * vec4(Y1, 1.0), k1 = uJetztVP * vec4(Y1, 1.0);
    if (b1.w <= 1e-3 || k1.w <= 1e-3) return u1;
    vec2 u2 = uv + (b1.xy / b1.w - k1.xy / k1.w) * 0.5;
    return uSpSchritte >= 2 ? u2 : u1;
}

float h2(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
float vnf(vec2 q, float p, vec2 o, float px) { return mix(vn(q / p + o), 0.5, smoothstep(0.25, 0.6, px / p)); }

float hit(vec3 d, float level) {
    float R = EARTH_R / 0.87;
    float hh = dot(d.xz, d.xz);
    float a = hh / (2.0 * R), b = d.y, c = uCam.y - level;
    if (c < 0.0) return -1.0;
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

float sceneDist(vec3 d, vec3 fwd) {
    float z = texture(uDepth, vUV).r;
    float vz;
    if (uReversed == 1) vz = z <= 0.0 ? 1e9 : uNear / z;
    else { float zn = z * 2.0 - 1.0; vz = z >= 1.0 ? 1e9 : 2.0 * uFar * uNear / ((uFar + uNear) - zn * (uFar - uNear)); }
    return vz / max(dot(d, fwd), 1e-4);
}

void main() {
    frag = vec4(0.0);
    vec3 d = viewRay(vUV);
    float t = hit(d, uLevel);
    if (t < 0.0) return;
    vec3 fwd = viewRay(vec2(0.5));
    float sd = sceneDist(d, fwd);
    if (sd < t - max(1.5, t * 0.004)) return;          // davor steht etwas (Ufer, Boot, Welle)
    vec3 w = uCam + d * t;
    if (uWelleAn == 1 && w.x > min(uWelleRect.x, uWelleRect.z) && w.x < max(uWelleRect.x, uWelleRect.z)
            && w.z > min(uWelleRect.y, uWelleRect.w) && w.z < max(uWelleRect.y, uWelleRect.w)) return;
    // Kräusel für Fresnel und Verzerrung [G], wie das Wasser in der Ferne gemittelt
    vec2 q = w.xz;
    float px = max(length(fwidth(q)), 1e-3);
    vec2 g = vec2(vnf(q, 9.0, vec2(uTime * 0.05), px) - vnf(q, 9.0, vec2(3.1, 0.0) - uTime * 0.04, px),
                  vnf(q, 6.0, vec2(-uTime * 0.045), px) - vnf(q, 6.0, vec2(0.0, 5.3) + uTime * 0.035, px)) * 0.10;
    vec3 n = normalize(vec3(g.x, 1.0, g.y));
    // W7b: die Ringe des Regens kippen den Spiegel (Fresnel und Lage des Spiegelbilds); im Fenster echt, draußen zwei
    // gedrehte Abfragen derselben Fläche (wie com.dan.ocean detailMode 1)
    vec2 rsR = vec2(0.0);
    vec3 nFlat = n;
    float mss = 0.0;
    if (uSpRingP.z > 0.5) {
        vec2 rel = abs(w.xz - uSpRingQ) / uSpRingP.x;
        float win = 1.0 - smoothstep(0.30, 0.47, max(rel.x, rel.y));
        vec2 nearS = texture(uSpRing, w.xz / uSpRingP.x).rg;
        const mat2 R1 = mat2(0.8, 0.6, -0.6, 0.8), R2 = mat2(-0.28, 0.96, -0.96, -0.28);
        vec2 a1 = texture(uSpRing, (R1 * w.xz) / uSpRingP.x + 0.37).rg, a2 = texture(uSpRing, (R2 * w.xz) / uSpRingP.x + 0.71).rg;
        vec2 farS = (transpose(R1) * a1 + transpose(R2) * a2) * 0.7071;
        float fern = exp(-t / max(1.0, uSpRingP.w));
        rsR = (nearS * win + farS * (1.0 - win) * fern) * uSpRingP.y;
        // Runde 5: Varianz erhalten – was das Feld hier nicht zeigt (in der Mip-Stufe gemittelt, in der Ferne
        // ausgeblendet), ist als Neigung einzelner Facetten da; unter dem Raster ohnehin (uSpRingR.x)
        float shown = (win + (1.0 - win) * fern);
        shown *= shown * exp2(-max(textureQueryLod(uSpRing, w.xz / uSpRingP.x).y, 0.0));
        mss = uSpRingR.y * max(0.0, 1.0 - shown);
        n = normalize(n + vec3(-rsR.x, 0.0, -rsR.y));
    }
    vec3 v = -d;
    vec3 add = vec3(0.0);
    float deck = 0.0;
    float hm = max(0.0, uCam.y + d.y * t * 0.5);
    float od = t * (exp(-hm / 8000.0) / 150000.0 + 0.45 * exp(-hm / 1200.0) / uFogDist);
    float durch = exp(-od);
    float F;
    if (uSpRingP.z < 0.5) {
        F = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        if (uSpiegelAn == 1) {
            // Verzerrung im Bild: mit der Neigung, in der Ferne kleiner (Bildpunkte je m)
            vec2 off = n.xz * 0.6 / max(t, 30.0) * vec2(1.0, 1.6);
            vec4 R = texture(uRefl, spiegelUV(vUV, d) + off);
            if (R.a > 0.01) {
                vec3 r = reflect(d, n);
                deck = F * clamp(R.a, 0.0, 1.0) * uSpGlatt;
                add += mix(R.rgb, hazeColor(r), uSpTruebe) * deck;
            }
        }
    } else {
        // W7b Runde 5, Regen: der Spiegel genau. Die geneigte Facette wirft den Strahl nach reflect(d, n); im glatten
        // Spiegelbild liegt diese Richtung dort, wohin der Blick d* = (r.x, −r.y, r.z) fällt – dorthin wird gegriffen
        // (im Gegenlicht zieht das die Bilder lang, wie auf jedem Foto von Regen auf einem See).
        vec4 c0 = uJetztVP * vec4(d, 0.0);
        vec2 off0 = nFlat.xz * 0.6 / max(t, 30.0) * vec2(1.0, 1.6);
        F = 0.0;
        // Die Facetten: nur ein Anteil p der Fläche trägt die verborgene Steigung, dafür mit Varianz/p (Regen ist
        // fleckig: unter dem Raster p ≈ 0,2 um die jungen Einschläge, die Ringe p ≈ 0,34) – zwischen den Einschlägen bleibt
        // der Spiegel glatt: Anteil 1 − p ungestört (genau, ohne Rauschen), Anteil p aus zwei geneigten Facetten, die die
        // zeitliche Glättung mittelt. Was unter dem Raster liegt, gilt erst, wo ein Bildpunkt viele Einschläge deckt
        // (Fußabdruck ab rund 1 cm); näher wäre es Korn statt Ringe [G].
        float s2 = uSpRingP.y * uSpRingP.y, fSub = smoothstep(0.004, 0.03, px);
        float vS = fSub * uSpRingR.x * s2, vR = mss * s2, vT = vS + vR;
        float pE = vT > 0.0 ? clamp((vS * uSpRingR.z + vR * uSpRingR.w) / vT, 0.05, 1.0) : 1.0;
        float sig = sqrt(0.5 * vT / pE);
        for (int k = 0; k < 3; k++) {
            if (k > 0 && vT <= 0.0) break;
            float wk = k == 0 ? (vT > 0.0 ? 1.0 - pE : 1.0) : 0.5 * pE;
            vec2 j = vec2(0.0);
            if (k > 0) {
                uint hs = uint(gl_FragCoord.x) * 1973u ^ uint(gl_FragCoord.y) * 9277u ^ uint(uSpRingN * 2 + k) * 26699u;
                hs = (hs ^ (hs >> 16u)) * 0x45d9f3bu; hs = (hs ^ (hs >> 16u)) * 0x45d9f3bu; hs ^= hs >> 16u;
                vec2 ua = (vec2(hs & 0xffffu, hs >> 16u) + 0.5) / 65536.0;
                j = sig * sqrt(-2.0 * log(ua.x)) * vec2(cos(6.2831853 * ua.y), sin(6.2831853 * ua.y));
            }
            vec3 nk = normalize(n + vec3(-j.x, 0.0, -j.y));
            if (dot(nk, v) < 0.02) nk = normalize(nk + v * (0.02 - dot(nk, v)));
            float Fk = 0.02 + 0.98 * pow(1.0 - max(dot(nk, v), 0.0), 5.0);
            F += wk * Fk;
            if (uSpiegelAn == 1) {
                // genau nur die Neigung des Regens (Ring und Facette); die Kräusel [G] verschieben wie bisher um off0
                vec3 r = reflect(d, nk);
                vec3 rr = reflect(d, normalize(vec3(-(rsR.x + j.x), 1.0, -(rsR.y + j.y))));
                if (rr.y <= 0.0) continue;   // Facette wirft nach unten: kein Spiegelbild (das Wasser selbst)
                vec3 ds = vec3(rr.x, -rr.y, rr.z);
                vec4 c1 = uJetztVP * vec4(ds, 0.0);
                vec2 du = c1.w > 1e-4 && c0.w > 1e-4 ? (c1.xy / c1.w - c0.xy / c0.w) * 0.5 : vec2(0.0);
                vec4 R = texture(uRefl, spiegelUV(vUV + du, ds) + off0);
                if (R.a > 0.01) {
                    float dk = Fk * clamp(R.a, 0.0, 1.0) * uSpGlatt * wk;
                    deck += dk;
                    add += mix(R.rgb, hazeColor(r), uSpTruebe) * dk;
                }
            }
        }
    }
    // Gletschermilch: Streulicht im Wasser (Anteil 1 − F), Grundton 0,35 in der ganzen Bucht, zum Inlet hin bis 1 [G]
    float ma = uMilchA.w * exp(-dot(w.xz - uMilchA.xy, w.xz - uMilchA.xy) / (uMilchA.z * uMilchA.z));
    float mb = uMilchB.w * exp(-dot(w.xz - uMilchB.xy, w.xz - uMilchB.xy) / (uMilchB.z * uMilchB.z));
    float milch = clamp(0.35 + ma + mb, 0.0, 1.0);
    vec3 eDown = uSkyAmb * 3.14159 * 1.1 + sunAt(w) * max(uSun.y, 0.0);
    vec3 milchFarbe = vec3(0.10, 0.20, 0.18);   // Gletschermehl streut grünlich-türkis zurück [G]
    vec3 m = milchFarbe * eDown * 0.11 * milch * (1.0 - F);
    add += m * durch;
    frag = vec4(add, deck);
}
