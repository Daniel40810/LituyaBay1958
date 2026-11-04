// marken.glsl — die Marken zur Ausrichtung (seit Phase 1, aus Sylt und Timmendorf): das UTM-Raster zu 1 km (alle 5 km
// kräftiger, alle 10 km für den Fern), die Ränder der Gebiete (gold der Kern, weiß die Szene, blau der Fern), der Ursprung
// in der Mitte des Kerns mit Nordpfeil und die Orte als Ringe nach Art (weiß Buchten, grün Inseln, sandfarben Kaps und
// Nehrungen, blau Seen und Bäche, eisblau Gletscher, hell Gipfel, rot der Sporn mit dem Auflauf von 524 m).
// Linien stehen in Bildpunkten fest und blenden aus, wo sie im Bild enger als rund 12 Bildpunkte liegen; steht das Auge
// an einem Ort oder am Ursprung, blendet dessen Marke aus (Befunde Sylt Phase 1).
uniform int uMarks;
uniform vec4 uRectKern, uRectSzene, uRectFern;   // {xMin, zMin, xMax, zMax} in der Szene
uniform vec2 uGridOff;     // Ostwert E0 und −N0 des Ursprungs, je modulo 10 km: das Raster liegt auf ganzen UTM-Kilometern
uniform vec4 uPoints[32];  // x, z, Halbmesser m, Art
uniform int uPointCount;

float gridLine(float v, float step, float w) {
    float f = abs(fract(v / step + 0.5) - 0.5) * step;
    float px = fwidth(v);
    float fade = 1.0 - smoothstep(step / 24.0, step / 8.0, px);
    return (1.0 - smoothstep(w * 0.5 * px, (w * 0.5 + 1.0) * px, f)) * fade;
}

float rectLine(vec2 p, vec4 r, float w) {
    vec2 px = fwidth(p);
    float dx = min(abs(p.x - r.x), abs(p.x - r.z)) / max(px.x, 1e-4);
    float dz = min(abs(p.y - r.y), abs(p.y - r.w)) / max(px.y, 1e-4);
    bool inX = p.x >= r.x - px.x && p.x <= r.z + px.x, inZ = p.y >= r.y - px.y && p.y <= r.w + px.y;
    float a = inZ ? 1.0 - smoothstep(w * 0.5, w * 0.5 + 1.0, dx) : 0.0;
    float b = inX ? 1.0 - smoothstep(w * 0.5, w * 0.5 + 1.0, dz) : 0.0;
    return max(a, b);
}

vec3 markColor(float art) {
    if (art < 0.5) return vec3(0.95, 0.95, 0.92);     // Bucht
    if (art < 1.5) return vec3(0.55, 0.85, 0.55);     // Insel
    if (art < 2.5) return vec3(0.90, 0.78, 0.52);     // Kap, Nehrung
    if (art < 3.5) return vec3(0.45, 0.70, 0.95);     // See, Bach
    if (art < 4.5) return vec3(0.70, 0.90, 1.00);     // Gletscher
    if (art < 5.5) return vec3(1.00, 0.97, 0.90);     // Gipfel
    return vec3(0.95, 0.35, 0.25);                    // Sporn
}

// Marken über die Farbe col am Ort p (x, z); B = Helligkeit des Himmels (skyBrightness), dist = Abstand vom Auge.
vec3 applyMarks(vec3 col, vec2 p, float dist, float B) {
    if (uMarks != 1) return col;
    vec2 u = p + uGridOff;
    float g1 = gridLine(u.x, 1000.0, 1.0) + gridLine(u.y, 1000.0, 1.0);
    float g5 = gridLine(u.x, 5000.0, 1.6) + gridLine(u.y, 5000.0, 1.6);
    float g10 = gridLine(u.x, 10000.0, 2.0) + gridLine(u.y, 10000.0, 2.0);
    vec3 ink = vec3(0.62, 0.75, 0.81) * B / 0.32;
    col = mix(col, ink * 0.55, clamp(g1, 0.0, 1.0) * 0.30 * (1.0 - smoothstep(20000.0, 40000.0, dist)));
    col = mix(col, ink * 0.8, clamp(g5, 0.0, 1.0) * 0.40 * (1.0 - smoothstep(60000.0, 120000.0, dist)));
    col = mix(col, ink, clamp(g10, 0.0, 1.0) * 0.45 * (1.0 - smoothstep(150000.0, 260000.0, dist)));
    col = mix(col, vec3(0.55, 0.80, 1.6) * B / 0.32, rectLine(p, uRectFern, 2.5));
    col = mix(col, vec3(1.6) * B / 0.32, rectLine(p, uRectSzene, 2.5));
    col = mix(col, vec3(2.0, 1.45, 0.62) * B / 0.32, rectLine(p, uRectKern, 2.5));
    float px = length(fwidth(p));
    float cross = max(1.0 - smoothstep(4.0, 12.0, abs(p.x)), 1.0 - smoothstep(4.0, 12.0, abs(p.y))) * (1.0 - step(160.0, length(p)))
                * (1.0 - smoothstep(10.0, 30.0, px));
    vec2 a = p - vec2(0.0, -400.0);
    float arrow = step(0.0, a.y + 240.0) * step(a.y, 0.0) * step(abs(a.x), (a.y + 240.0) * 0.35) * (1.0 - smoothstep(30.0, 90.0, px));
    col = mix(col, vec3(2.0, 1.45, 0.62) * B / 0.32, max(cross, arrow) * smoothstep(200.0, 600.0, length(uCam.xz)));
    for (int i = 0; i < uPointCount; i++) {
        vec4 q = uPoints[i];
        float d = length(p - q.xy), w = max(q.z, fwidth(d) * 3.0);
        float ring = 1.0 - smoothstep(w, w + fwidth(d) * 1.5, d);
        float hole = 1.0 - smoothstep(w * 0.7, w * 0.7 + fwidth(d) * 1.5, d);
        float hier = smoothstep(q.z * 1.5, q.z * 4.0, length(uCam.xz - q.xy));
        col = mix(col, markColor(q.w) * 1.4 * B / 0.32, ring * (1.0 - hole * 0.75) * hier);
    }
    return col;
}
