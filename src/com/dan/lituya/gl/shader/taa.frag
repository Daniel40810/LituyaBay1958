// taa.frag — Werkbuch Welle W0 (K2): zeitliche Kantenglättung (TAA). Jedes Bild wird um einen Bruchteil eines
// Bildpunkts versetzt gerechnet (Halton 2/3, 8 Lagen); dieses Bild mischt das neue Bild mit dem Verlauf der vorigen.
// Der Verlauf wird über die Tiefe und die Blickmatrix des vorigen Bilds an die richtige Stelle geholt (Kamera bewegt
// sich, die Szene darin nicht); was sich in der Szene selbst bewegt (Welle, Staub, Bäume), hält die Klammer an der
// Nachbarschaft des neuen Bilds im YCoCg-Raum fest, so dass nichts nachzieht. Der Verlauf wird mit Catmull-Rom gelesen
// (bleibt scharf), Helles wird vor dem Mischen gedämpft (keine flackernden Glanzpunkte).
// Ergebnis: Treppen an Graten, Bäumen und Gischt werden glatt, Rauschen feiner als ein Bildpunkt wird zu Fläche.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uCur;      // neues HDR-Bild (versetzt gerechnet)
uniform sampler2D uHist;     // Verlauf (HDR) des vorigen Bilds
uniform sampler2D uDepth;    // Tiefe des neuen Bilds
uniform mat4 uReproj;        // Bildraum jetzt -> Clip des vorigen Bilds (ohne Versatz)
uniform int uReversed;
uniform int uValid;          // 0: kein Verlauf (Start, neue Größe, Sprung der Kamera)
uniform vec2 uTexel;         // 1 / Größe
uniform float uAlpha;        // Anteil des neuen Bilds (0,1)

vec3 toY(vec3 c) { return vec3(0.25 * c.r + 0.5 * c.g + 0.25 * c.b, 0.5 * c.r - 0.5 * c.b, -0.25 * c.r + 0.5 * c.g - 0.25 * c.b); }
vec3 fromY(vec3 y) { return vec3(y.x + y.y - y.z, y.x + y.z, y.x - y.y - y.z); }
// Helles dämpfen: so zählt ein greller Bildpunkt beim Mischen nicht mehr als ein heller
vec3 tm(vec3 c) { return c / (1.0 + max(c.r, max(c.g, c.b))); }
vec3 itm(vec3 c) { return c / max(1e-4, 1.0 - max(c.r, max(c.g, c.b))); }

vec3 histCR(vec2 uv) {
    // Catmull-Rom mit fünf bilinearen Zugriffen (nach Jimenez)
    vec2 sz = 1.0 / uTexel;
    vec2 p = uv * sz, t1 = floor(p - 0.5) + 0.5, f = p - t1;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2, o12 = w2 / w12;
    vec2 a = (t1 - 1.0) * uTexel, b = (t1 + 2.0) * uTexel, c = (t1 + o12) * uTexel;
    vec3 r = texture(uHist, vec2(c.x, a.y)).rgb * w12.x * w0.y
           + texture(uHist, vec2(a.x, c.y)).rgb * w0.x * w12.y
           + texture(uHist, c).rgb * w12.x * w12.y
           + texture(uHist, vec2(b.x, c.y)).rgb * w3.x * w12.y
           + texture(uHist, vec2(c.x, b.y)).rgb * w12.x * w3.y;
    float n = w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
    return max(r / n, vec3(0.0));
}

void main() {
    vec3 cur = max(texture(uCur, vUV).rgb, vec3(0.0));
    if (uValid == 0) { frag = vec4(cur, 1.0); return; }
    // Nachbarschaft 3 × 3 (gedämpft, YCoCg): Mittel und Streuung
    vec3 m1 = vec3(0.0), m2 = vec3(0.0), mn = vec3(1e9), mx = vec3(-1e9);
    float dmin = uReversed == 1 ? 0.0 : 1.0;
    vec2 best = vec2(0.0);
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++) {
            vec2 o = vec2(i, j) * uTexel;
            vec3 y = toY(tm(max(texture(uCur, vUV + o).rgb, vec3(0.0))));
            m1 += y; m2 += y * y;
            mn = min(mn, y); mx = max(mx, y);
            // die nächste Tiefe der Nachbarschaft: Kanten nehmen die Bewegung des Vordergrunds mit
            float d = texture(uDepth, vUV + o).r;
            if (uReversed == 1 ? d > dmin : d < dmin) { dmin = d; best = o; }
        }
    m1 /= 9.0; m2 /= 9.0;
    vec3 sig = sqrt(max(m2 - m1 * m1, vec3(0.0)));
    vec3 lo = max(mn, m1 - 1.25 * sig), hi = min(mx, m1 + 1.25 * sig);
    // Ort im vorigen Bild
    vec2 ndc = (vUV + best) * 2.0 - 1.0;
    float z = uReversed == 1 ? dmin : dmin * 2.0 - 1.0;
    vec4 pc = uReproj * vec4(ndc, z, 1.0);
    vec2 puv = (abs(pc.w) > 1e-12 ? pc.xy / pc.w : ndc) * 0.5 + 0.5 - best;
    if (any(lessThan(puv, vec2(0.0))) || any(greaterThan(puv, vec2(1.0)))) { frag = vec4(cur, 1.0); return; }
    vec3 h = toY(tm(histCR(puv)));
    // an die Nachbarschaft klammern (Strahl zur Mitte statt Würfelecke: weniger Farbstich)
    vec3 c = 0.5 * (hi + lo), e = 0.5 * (hi - lo) + 1e-5, v = h - c;
    vec3 u = abs(v / e);
    float k = max(u.x, max(u.y, u.z));
    if (k > 1.0) h = c + v / k;
    // je weiter der Verlauf wandern musste, desto mehr vom neuen Bild (Bewegung)
    float mv = length((puv - vUV) / uTexel);
    float a = clamp(uAlpha + mv * 0.02, uAlpha, 0.5);
    vec3 yc = toY(tm(cur));
    vec3 res = itm(fromY(mix(h, yc, a)));
    frag = vec4(max(res, vec3(0.0)), 1.0);
}
