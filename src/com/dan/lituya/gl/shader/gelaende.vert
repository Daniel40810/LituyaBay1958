// gelaende.vert — das Gelände aus com.dan.clipmap (Phase 4): die Scheitel aller Stufen mit Übergang und Erdkrümmung.
// Eingang aClipGrid (Stelle 0) aus ClipmapGl; die Höhen liegen im Texturfeld uClipH (Stelle 3).
#include "common.glsl"
#define CLIPMAP_VERTEX
#include "/com/dan/clipmap/gl/clipmap.glsl"
out vec3 vW;
out vec3 vN;
out float vH;
// ab Phase 13b: der Rückbau des Gilbert Inlet auf 1958 wie in scene.Boden1958 (Land unter uRueckbau.x wird Wasser der Tiefe
// uRueckbau.y) im Rechteck uRueckbauRect, solange die Szene 1958 zeigt [G]
uniform int uRueckbauAn;
uniform vec4 uRueckbauRect;   // x0, z0, x1, z1 (Szene)
uniform vec2 uRueckbau;       // Grenze, Tiefe (m über NAVD88)
// Werkbuch Welle W2: für das Spiegelbild am Wasserspiegel nur, was darüber liegt (GL_CLIP_DISTANCE0 nur dort an)
uniform float uClipY;
void main() {
    ClipVertex c = clipmapVertex();
    if (uRueckbauAn == 1 && c.height < uRueckbau.x && c.world.x > min(uRueckbauRect.x, uRueckbauRect.z) && c.world.x < max(uRueckbauRect.x, uRueckbauRect.z)
            && c.world.z > min(uRueckbauRect.y, uRueckbauRect.w) && c.world.z < max(uRueckbauRect.y, uRueckbauRect.w)) {
        c.world.y += uRueckbau.y - c.height;
        c.height = uRueckbau.y;
    }
    vW = c.world;
    vN = c.normal;
    vH = c.height;
    gl_ClipDistance[0] = c.world.y - uClipY;
    gl_Position = uVP * vec4(c.world, 1.0);
}
