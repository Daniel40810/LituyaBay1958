// gelaende.vert — das Gelände aus com.dan.clipmap (Phase 4): die Scheitel aller Stufen mit Übergang und Erdkrümmung.
// Eingang aClipGrid (Stelle 0) aus ClipmapGl; die Höhen liegen im Texturfeld uClipH (Stelle 3).
#include "common.glsl"
#define CLIPMAP_VERTEX
#include "/com/dan/clipmap/gl/clipmap.glsl"
out vec3 vW;
out vec3 vN;
out float vH;
void main() {
    ClipVertex c = clipmapVertex();
    vW = c.world;
    vN = c.normal;
    vH = c.height;
    gl_Position = uVP * vec4(c.world, 1.0);
}
