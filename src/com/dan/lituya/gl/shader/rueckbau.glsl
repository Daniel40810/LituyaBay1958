// rueckbau.glsl — Werkbuch Welle W3c: der Rückbau des Gilbert Inlet auf 1958 im Bild, wie scene.Boden1958.rueckbauBild
// (Gelände in gelaende.vert, Felssturz in spur.vert: beide auf demselben Grund). Braucht uLevel aus common.glsl.
uniform int uRueckbauAn;
uniform vec4 uRueckbauRect;   // x0, z0, x1, z1 (Szene)
uniform vec2 uRueckbau;       // Grenze der Vorrechnung (nicht mehr benutzt), Tiefe (m über NAVD88)
uniform vec3 uRueckbauW;      // unten, oben (m heute), Ufer außen (m)
uniform vec4 uGletscherRect;  // W3c: Rechteck der Zunge (Szene x0, z0, x1, z1)
uniform vec2 uGletscherW;     // Grenze (m heute), Höhe der Front (m)
// Grund 1958 der Vorrechnung (feinstes Gitter der Welle): wo er unter dem Pegel liegt, steht das Bild nicht darüber
uniform int uGrund58An;
uniform sampler2D uGrund58;
uniform vec4 uGrund58Rect;    // Szene x der Westkante, z der Südkante, Breite, Tiefe (m)

// Grund 1958 am Ort, oder 1e4 außerhalb und über Wasser nicht nötig
float grund58(vec2 xz) {
    if (uGrund58An == 0) return 1e4;
    vec2 u = vec2((xz.x - uGrund58Rect.x) / uGrund58Rect.z, (uGrund58Rect.y - xz.y) / uGrund58Rect.w);
    if (any(lessThan(u, vec2(0.002))) || any(greaterThan(u, vec2(0.998)))) return 1e4;
    float b = textureLod(uGrund58, u, 0.0).r;
    return b < uLevel - 0.5 ? b : 1e4;
}

// Anteil der Zunge: 1 innen, über 120 m am Rand auf 0 (im Norden endet das Rechteck mit dem Rückbau); Szene z nach Süden
float gletscher(vec2 xz) {
    float x0 = min(uGletscherRect.x, uGletscherRect.z), x1 = max(uGletscherRect.x, uGletscherRect.z);
    float zN = min(uGletscherRect.y, uGletscherRect.w), zS = max(uGletscherRect.y, uGletscherRect.w);
    if (xz.y <= zN) return 0.0;
    return smoothstep(0.0, 120.0, min(min(xz.x - x0, x1 - xz.x), zS - xz.y));
}

// Abstand zum Rand des Rechtecks (innen positiv)
float rbRand(vec2 xz) {
    float x0 = min(uRueckbauRect.x, uRueckbauRect.z), x1 = max(uRueckbauRect.x, uRueckbauRect.z);
    float z0 = min(uRueckbauRect.y, uRueckbauRect.w), z1 = max(uRueckbauRect.y, uRueckbauRect.w);
    return min(min(xz.x - x0, x1 - xz.x), min(xz.y - z0, z1 - xz.y));
}
// wie Boden1958.rueckbauGewicht: innen wie die Vorrechnung (Uferlinie bei rund 60 m heute, darüber ausflachend);
// außen ein Ufer von 60 m Breite bis 1 m über den Pegel, wo innen Wasser ist (statt einer geraden Wand am Rand)
float rbGewicht(vec2 xz, float h) {
    float d = rbRand(xz);
    if (d <= -60.0) return 0.0;
    if (d < 0.0) return (1.0 - smoothstep(0.0, 60.0, -d)) * (1.0 - smoothstep(60.0, 80.0, h)) * 0.2;
    float weich = 1.0 - smoothstep(uRueckbauW.x, uRueckbauW.y, h);
    float eis = 1.0 - smoothstep(uGletscherW.x, uGletscherW.x + uGletscherW.y, h);
    return smoothstep(0.0, 4.0, d) * mix(weich, eis, gletscher(xz));
}
float rbHoehe(vec2 xz, float h) {
    float d = rbRand(xz);
    if (d <= -60.0) return h;
    if (d < 0.0) return mix(h, min(h, uLevel + 1.0), (1.0 - smoothstep(0.0, 60.0, -d)) * (1.0 - smoothstep(60.0, 80.0, h)));
    // innen: weich, und nie über dem Grund 1958, wo die Vorrechnung Wasser hat (bis 2 m unter den Pegel, darunter dieser Grund)
    float v = mix(h, uRueckbau.y, rbGewicht(xz, h));
    float g = grund58(xz);
    return g < 1e3 ? min(v, max(g, uLevel - 2.0 - 0.15 * max(v - uLevel, 0.0))) : v;
}

