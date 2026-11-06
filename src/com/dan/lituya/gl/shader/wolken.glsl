// wolken.glsl — Werkbuch Welle W7 „Wetter“: Wolkenschatten aus com.dan.cloud und Nässe aus com.dan.weather.
// Die Schattenkarte (RG16F, gl/WetterGl, 72 km über die Szene) hat in R das Licht, das ab der Fläche uWkRect.w durch
// alles kommt (Schicht, Bank, Kappe, Linsen, hohe Schicht), in G nur durch das, was über Schicht und Bank liegt. Ein
// Punkt unter allen Wolken bekommt R an der Stelle, an der sein Sonnenstrahl die Fläche schneidet (genau, denn
// darunter ist keine Wolke); über Schicht und Bank G, dazwischen gemischt.
uniform int uWkAn;
uniform sampler2D uWkSchatten;
uniform vec4 uWkRect;   // x0, z0, Kante (m), Höhe der Fläche (m)
uniform vec4 uWkH;      // unterste Höhe der Wolken, Obergrenze von Schicht und Bank (m), —, —
uniform float uNass;    // Nässe der Oberflächen 0..1

float wolkenSchatten(vec3 w, vec3 sun) {
    if (uWkAn == 0) return 1.0;
    vec3 d = normalize(vec3(sun.x, max(0.05, sun.y), sun.z));
    vec2 q = w.xz + d.xz / d.y * (uWkRect.w - w.y);
    vec2 u = (q - uWkRect.xy) / uWkRect.z;
    if (u.x < 0.0 || u.y < 0.0 || u.x > 1.0 || u.y > 1.0) return 1.0;
    vec2 s = texture(uWkSchatten, u).rg;
    return mix(s.r, s.g, smoothstep(uWkH.x, uWkH.y, w.y));
}
