// welle.frag — die Welle als Höhenfeld (Phase 13b, Werkbuch Welle W2 bis W5). Die Oberfläche kommt seit dem 07.11.2026
// aus com.dan.surge (surge_surface.glsl, surgeShade: Körper mit Gletschermilch und Schlamm, Kräusel, Durchlicht im Kamm,
// Weißwasser, Walze, Kamm, Sturz, Schaumspur, Treibgut, die Zunge am Hang, die Rückwand der Röhre); das Licht aus
// common.glsl über den Lichtvertrag (paketlicht.glsl). Hier nur, was LituyaBay1958 dazugibt: das feinere Gitter deckt
// das gröbere (verworfen), und der Spiegel der Berge aus W2/W3c (Spiegelbild aus einem früheren Bild umgerechnet,
// Runden 7–9).
#include "paketlicht.glsl"
in vec3 vW;
in vec3 vN;
in float vNass;
in float vH;
in float vSteig;
in float vTiefe;
in float vQuelle;
in float vLippe;      // W4: Anteil der Lippe (aus spur.vert)
in vec2 vLauf;        // W4: Laufrichtung der Front (Szene x, z)
in float vSeit;       // W4: s seit der Ankunft der Front
in float vStand;      // W4: höchster Stand hier (m)
out vec4 frag;
uniform vec4 uFeiner;     // Rechteck des feineren Gitters (x0, z0, x1, z1), x0 > x1: keins
// W3c: Spiegel der Berge aus W2
uniform sampler2D uRefl;
uniform mat4 uSpiegelVP, uJetztVP;   // Runde 7: Blick beim Spiegelbild und jetzt (Spiegel jedes zweite Bild)
uniform sampler2D uSpTiefe;   // Runde 8: Tiefe des Spiegelbilds
uniform float uSpNah;
uniform int uSpSchritte;   // Runde 9: 1 oder 2 Schritte mit der Tiefe
uniform vec3 uSpVorn;
uniform int uSpiegelAn;
uniform vec2 uBildPx;
#define SURGE_MIRROR
#include "/com/dan/surge/gl/surge_surface.glsl"

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

// Spiegel der Berge (W2-Bild): nur nahe am Pegel und wo die Fläche flach liegt; Verzerrung mit den Kräuseln
vec3 surgeMirror(vec3 refl, vec3 w, vec3 n, float dist, float aufruhr, float hoch, float steil) {
    if (uSpiegelAn == 1) {
        float nahe = (1.0 - smoothstep(6.0, 22.0, abs(hoch))) * (1.0 - smoothstep(0.05, 0.16, steil));
        if (nahe > 0.0) {
            vec2 suv = spiegelUV(gl_FragCoord.xy / uBildPx, normalize(w - uCam));   // Runde 7/8: umgerechnet
            vec2 off = n.xz * 0.6 / max(dist, 30.0) * vec2(1.0, 1.6) * (1.0 + 2.0 * aufruhr);
            vec4 R = texture(uRefl, suv + off);
            refl = mix(refl, R.rgb, clamp(R.a, 0.0, 1.0) * nahe);
        }
    }
    return refl;
}

void main() {
    if (vW.x > min(uFeiner.x, uFeiner.z) && vW.x < max(uFeiner.x, uFeiner.z) && vW.z > min(uFeiner.y, uFeiner.w) && vW.z < max(uFeiner.y, uFeiner.w)) discard;
    frag = surgeShade(SurgeSurf(vW, vN, vNass, vH, vSteig, vTiefe, vQuelle, vLippe, vLauf, vSeit, vStand));
}
