// post.frag — HDR-Bild in das Fensterbild (aus Geiranger): Belichtung, Filmkurve (ACES nach Narkowicz),
// Gamma, feines Rauschen gegen Stufen im Himmelsverlauf, leichte Randabdunklung. Eingaben: uHdr (RGBA16F), uExposure;
// ab Phase 4 uScotopic: nachts sieht das Auge mit den Stäbchen (farblos, ins Blaue verschoben), aus com.dan.sky;
// (Befund aus Rungholt, Phase 11) nur im Dunkeln des Bilds, Helles behält seine Farbe.
// Werkbuch Welle W0 (K2): die Eingabe ist das Ergebnis der Kantenglättung (taa.frag); ist sie aus, glättet hier FXAA
// (Rückfall, uFxaa = 1) auf dem fertig belichteten Bild.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform float uExposure;
uniform float uScotopic;
uniform int uFxaa;
uniform vec2 uTexel;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// das fertige Bild an der Stelle uv (ohne Rauschen)
vec3 tone(vec2 uv) {
    vec3 hdr = texture(uHdr, uv).rgb * uExposure;
    float ys = dot(hdr, vec3(0.02, 0.62, 0.36));
    // Befund aus Rungholt: was im Bild hell ist (in LituyaBay1958 die Laternen der Kutter vor Anker), sehen die Zapfen weiter in Farbe;
    // nur das Dunkle wird farblos und blau
    float lum = dot(hdr, vec3(0.2126, 0.7152, 0.0722));
    hdr = mix(hdr, ys * vec3(0.62, 0.78, 1.08), uScotopic * (1.0 - smoothstep(0.12, 0.7, lum)));
    vec2 q = uv - 0.5;
    hdr *= 1.0 - 0.22 * dot(q, q);
    return pow(aces(hdr), vec3(1.0 / 2.2));
}

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

// FXAA (vereinfacht nach Lottes 3.11, eine Stufe): Kante aus der Helligkeit der Nachbarn, entlang der Kante mischen
vec3 fxaa(vec2 uv) {
    vec3 cM = tone(uv);
    float lM = luma(cM), lN = luma(tone(uv + vec2(0.0, uTexel.y))), lS = luma(tone(uv - vec2(0.0, uTexel.y))),
          lE = luma(tone(uv + vec2(uTexel.x, 0.0))), lW = luma(tone(uv - vec2(uTexel.x, 0.0)));
    float lo = min(lM, min(min(lN, lS), min(lE, lW))), hi = max(lM, max(max(lN, lS), max(lE, lW)));
    if (hi - lo < max(0.0312, hi * 0.125)) return cM;
    float lNE = luma(tone(uv + uTexel)), lNW = luma(tone(uv + vec2(-uTexel.x, uTexel.y))),
          lSE = luma(tone(uv + vec2(uTexel.x, -uTexel.y))), lSW = luma(tone(uv - uTexel));
    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
    float red = max((lN + lS + lE + lW) * 0.03125, 1.0 / 128.0);
    dir = clamp(dir / (min(abs(dir.x), abs(dir.y)) + red), vec2(-8.0), vec2(8.0)) * uTexel;
    vec3 a = 0.5 * (tone(uv + dir * (1.0 / 3.0 - 0.5)) + tone(uv + dir * (2.0 / 3.0 - 0.5)));
    vec3 b = a * 0.5 + 0.25 * (tone(uv - dir * 0.5) + tone(uv + dir * 0.5));
    float lb = luma(b);
    return (lb < lo || lb > hi) ? a : b;
}

void main() {
    vec3 c = uFxaa == 1 ? fxaa(vUV) : tone(vUV);
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    c += (n - 0.5) / 255.0;
    frag = vec4(c, 1.0);
}
