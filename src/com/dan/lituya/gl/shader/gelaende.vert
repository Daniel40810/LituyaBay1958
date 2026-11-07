// gelaende.vert — das Gelände aus com.dan.clipmap (Phase 4): die Scheitel aller Stufen mit Übergang und Erdkrümmung.
// Eingang aClipGrid (Stelle 0) aus ClipmapGl; die Höhen liegen im Texturfeld uClipH (Stelle 3).
#include "common.glsl"
#define CLIPMAP_VERTEX
#include "/com/dan/clipmap/gl/clipmap.glsl"
out vec3 vW;
out vec3 vN;
out float vH;
// ab Phase 13b: der Rückbau des Gilbert Inlet auf 1958 wie in scene.Boden1958 im Rechteck uRueckbauRect, solange die
// Szene 1958 zeigt [G]. Seit Werkbuch Welle W3c weich (Boden1958.rueckbauBild): Gewicht nach der Höhe heute (voll unter
// uRueckbauW.x, keins über uRueckbauW.y) mal Saum am Rand (uRueckbauW.z m); vorher wurde Land unter 60 m hart zu −122 m,
// das gab senkrechte Wände an der 60-m-Linie und gerade Kanten am Rand („Schwimmbecken“). Die Normale wird aus den
// zurückgebauten Höhen der Nachbarn neu gerechnet.
// Werkbuch Welle W2: für das Spiegelbild am Wasserspiegel nur, was darüber liegt (GL_CLIP_DISTANCE0 nur dort an)
uniform float uClipY;
out float vRueckbau;          // W3c: Gewicht des Rückbaus (0 … 1), für die Farbe des Grunds
out float vGletscher;         // W3c: Eis der Gletscherzunge 1958 (0 … 1) [G]
uniform vec4 uZungeRect;      // W3c: die Zunge im Norden (nur Farbe), Szene x0, z0, x1, z1
#include "rueckbau.glsl"
// W9c Runde 2 („Schwebe“): das Kronendach fern als Fläche. Wo die Einzelbäume enden (uKroneAb), hebt sich das Gelände um die
// Kronenhöhe (Alpha von uKrone mal uKroneHoch.x m), so weit der Wald dicht ist (rgb). Vorher lag fern IfSAR, das unter dichtem
// Wald etwa in halber Kronenhöhe liegt; seit dem Angleich ans Lidar liegt dort der Boden. uKroneHoch.y: Zelle der Textur (m).
uniform int uKroneAn;
uniform sampler2D uKrone;
uniform vec4 uKroneRect;
uniform vec2 uKroneAb;
uniform vec2 uKroneHoch;

void main() {
    ClipVertex c = clipmapVertex();
    vRueckbau = 0.0;
    vGletscher = 0.0;
    if (uRueckbauAn == 1) {
        // die Zunge im Nordwesten: Eis über der Grenze; steile, hohe Hänge (die Wand im Westen) bleiben Fels und Wald
        vGletscher = gletscher(c.world.xz) * smoothstep(uGletscherW.x - 6.0, uGletscherW.x + 2.0, c.height)
                   * mix(1.0, 1.0 - smoothstep(0.12, 0.24, 1.0 - c.normal.y), smoothstep(90.0, 140.0, c.height));
        // die Zunge im Norden: Eis über der Front bis rund 400 m, Felswände (steil und hoch) bleiben Fels
        float x0 = min(uZungeRect.x, uZungeRect.z), x1 = max(uZungeRect.x, uZungeRect.z);
        float zN = min(uZungeRect.y, uZungeRect.w), zS = max(uZungeRect.y, uZungeRect.w);
        float dz = min(min(c.world.x - x0, x1 - c.world.x), min(c.world.z - zN, zS - c.world.z));
        if (dz > 0.0) {
            float sl = 1.0 - c.normal.y;
            float z = smoothstep(0.0, 120.0, dz) * smoothstep(uGletscherW.x - 6.0, uGletscherW.x + 2.0, c.height)
                    * (1.0 - smoothstep(330.0, 450.0, c.height)) * mix(1.0, 1.0 - smoothstep(0.10, 0.20, sl), smoothstep(80.0, 120.0, c.height));
            vGletscher = max(vGletscher, z);
        }
    }
    if (uRueckbauAn == 1) {
        float w = rbGewicht(c.world.xz, c.height);
        if (w > 0.0) {
            float h1 = rbHoehe(c.world.xz, c.height);
            c.world.y += h1 - c.height;
            // Normale aus den zurückgebauten Nachbarn derselben Stufe (der Übergang zur gröberen ist hier vernachlässigt)
            int k = int(aClipGrid.x + 0.5), l = int(aClipGrid.y + 0.5);
            int i = uClipIdx.x + k, j = uClipIdx.y + l;
            float cz = uClipGeo.z;
            vec2 xz = uClipGeo.xy + vec2(float(i), float(j)) * cz;
            float hl = rbHoehe(xz - vec2(cz, 0.0), clipFetch(uClipLevel, i - 1, j));
            float hr = rbHoehe(xz + vec2(cz, 0.0), clipFetch(uClipLevel, i + 1, j));
            float hu = rbHoehe(xz - vec2(0.0, cz), clipFetch(uClipLevel, i, j - 1));
            float hd = rbHoehe(xz + vec2(0.0, cz), clipFetch(uClipLevel, i, j + 1));
            vec3 nr = normalize(vec3(-(hr - hl) / (2.0 * cz), 1.0, -(hd - hu) / (2.0 * cz)));
            c.normal = normalize(mix(nr, c.normal, c.morph));
            c.height = h1;
            vRueckbau = w;
        }
    }
    if (uKroneAn == 1 && uKroneHoch.x > 0.0) {
        vec2 ku = (c.world.xz - uKroneRect.xy) / uKroneRect.zw;
        if (ku.x > 0.0 && ku.y > 0.0 && ku.x < 1.0 && ku.y < 1.0) {
            float dist = length(c.world - uClipCam);
            float ramp = smoothstep(uKroneAb.x, mix(uKroneAb.x, uKroneAb.y, 0.85), dist);
            if (ramp > 0.0) {
                vec4 dn = textureLod(uKrone, ku, max(0.0, log2(uClipGeo.z / max(uKroneHoch.y, 1e-3))));
                float dichte = clamp(dn.r + dn.g + dn.b, 0.0, 1.0);
                c.world.y += dn.a * uKroneHoch.x * smoothstep(0.1, 0.6, dichte) * ramp * smoothstep(1.0, 4.0, c.height);
            }
        }
    }
    vW = c.world;
    vN = c.normal;
    vH = c.height;
    gl_ClipDistance[0] = c.world.y - uClipY;
    gl_Position = uVP * vec4(c.world, 1.0);
}
