// common.glsl — gemeinsame Werte und der Platzhalter-Himmel (Phase 1, aus Geiranger über Brocken, Sylt und Timmendorf).
// Raum: Szene in m, x Ost, y oben (y = 0 ist NAVD88), z Süd, Ursprung in der Mitte des Kerns (UTM 8 N, F2 A).
// Nordhalbkugel, 58,6° N: die Sonne läuft über Süden (+z), am 9. Juli steht sie mittags rund 54° hoch und geht um 22:14
// Ortszeit im Nordwesten über dem Golf von Alaska unter; um Mitternacht sinkt sie nur bis rund −9° (helle Julinacht).
// Der Himmel hier ist ein Verlauf nach der Sonnenhöhe: Tag, Dämmerung mit Abendrot zur Sonne hin, Nacht. Er ist
// mit Absicht einfach; Phase 5a ersetzt skyColor() durch die Streuung aus com.dan.sky. Helligkeiten sind linear,
// Tag = 1; die Belichtung (uExposure) kommt vom Prozessor.
uniform mat4 uVP;
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform vec3 uSun;          // Richtung zur Sonne (Einheitsvektor)
uniform float uTime;        // Laufzeit in s
uniform float uLevel;       // Wasserstand in m über NAVD88 (Phase 1 fest, ab Phase 5a aus tide)
uniform int uReversed;      // 1 = umgekehrte Tiefe (0..1), sonst klassisch (-1..1)
uniform float uBild;        // W0: laufende Nummer des Bilds (0..63) für Rauschen, das von Bild zu Bild wandert (die Kantenglättung mittelt es)
// Ab Phase 5a: Himmel aus com.dan.sky. uSkyMode = 1: Himmelsfarbe aus dem Rundumbild uPano, Sonnen- und Mondlicht aus
// uSunColor/uMoonColor, Dunst aus uFogDist; alles schon mit der Vorbelichtung uPre multipliziert. uSkyMode = 0: der
// Platzhalter-Himmel von Phase 1, Belichtung erst in der Nachbearbeitung. Phase 1 setzt immer 0.
uniform int uSkyMode;
uniform sampler2D uPano;
uniform vec3 uSunColor, uMoonColor, uMoonDir;
uniform float uFogDist, uPre;
uniform vec3 uSkyAmb;     // ab Phase 5a: Himmelslicht als Bestrahlung/π (vorbelichtet), für das Umgebungslicht
// Werkbuch Welle W1 (gl/Licht): Sonne außerhalb der Luft (vorbelichtet) und die Horizontkarten (r = Horizontwinkel in
// Richtung Sonne, g = Boden), fein über den Kern und grob über die Szene; Rechtecke x0, z0, Kante.
uniform vec3 uSunToa;
uniform int uSchattenAn;
uniform sampler2D uSchattenF, uSchattenG;
uniform vec4 uSchattenFRect, uSchattenGRect;
// Werkbuch Welle W7: Wolkenschatten und Nässe (gl/WetterGl)
#include "wolken.glsl"

// Tiefenwert für einen Punkt der Szene, passend zur Projektion.
float depthOf(vec3 p) {
    vec4 clip = uVP * vec4(p, 1.0);
    float z = clip.z / clip.w;
    return clamp(uReversed == 1 ? z : z * 0.5 + 0.5, 0.0, 1.0);
}
const float EARTH_R = 6371000.0;

// Sichtstrahl für einen Bildpunkt (vUV 0..1). Umgekehrte Tiefe: z = 1 ist die Nahebene, klassisch z = -1.
// Befund LituyaBay1958 Phase 1: mit z = 0,5 lagen die beiden Punkte nur 0,3 m auseinander, bei Weltkoordinaten von
// Kilometern ging die Richtung in float auf 1e-3 verloren (aus 1,9 km Höhe zitterten Raster und Marken um Meter); mit
// z = 0,001 liegt der zweite Punkt rund 300 m weit.
vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    float zn = uReversed == 1 ? 1.0 : -1.0, zm = uReversed == 1 ? 0.001 : 0.0;
    vec4 a = uInvVP * vec4(ndc, zn, 1.0);
    vec4 b = uInvVP * vec4(ndc, zm, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
}

float sunVisible() { return smoothstep(-0.015, 0.01, uSun.y); }

vec3 panoSample(vec3 d) {
    float az = atan(d.x, -d.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
    return textureLod(uPano, vec2(az, el), 0.0).rgb;
}

// Farbe des Sonnenlichts: weiß am Tag, golden und schwächer tief am Himmel, aus unter der Kimm.
vec3 sunLight() {
    if (uSkyMode == 1) return uSunColor;
    float e = uSun.y;
    vec3 low = vec3(1.0, 0.55, 0.28), high = vec3(1.0, 0.95, 0.88);
    return mix(low, high, smoothstep(0.02, 0.35, e)) * 2.6 * sunVisible() * smoothstep(-0.02, 0.12, e);
}

// Helligkeit des Himmels nach der Sonnenhöhe in Grad (log10, lineare Stützstellen): Tag 1, Sonnenuntergang 0,1,
// bürgerliche Dämmerung 0,005, nautische 0,0003, Nacht ohne Mond 0,00005. Gleiche Tabelle wie Sun.skyBrightness().
float skyBrightnessPH(float elevDeg);
// W1: mit com.dan.sky aus dem Himmelslicht (vorbelichtet), so dass Formeln mit B / 0,32 in denselben Einheiten bleiben
float skyBrightness(float elevDeg) {
    if (uSkyMode == 1) return 1.85 * dot(uSkyAmb, vec3(0.2126, 0.7152, 0.0722));
    return skyBrightnessPH(elevDeg);
}
float skyBrightnessPH(float elevDeg) {
    const float E[6] = float[6](-18.0, -12.0, -6.0, 0.0, 10.0, 30.0);
    const float V[6] = float[6](-4.3, -3.5, -2.3, -1.0, -0.3, 0.0);
    float v = elevDeg <= E[0] ? V[0] : V[5];
    for (int i = 0; i < 5; i++)
        if (elevDeg > E[i] && elevDeg <= E[i + 1]) v = mix(V[i], V[i + 1], (elevDeg - E[i]) / (E[i + 1] - E[i]));
    return pow(10.0, v);
}

float sunElevDeg() { return degrees(asin(clamp(uSun.y, -1.0, 1.0))); }

// Farbe des Himmels in Richtung d, linear, absolut (Tag ≈ 1). Form und Helligkeit getrennt: die Form mischt Tag,
// Dämmerung und Nacht, die Helligkeit kommt aus skyBrightness(). Zur Sonne hin glüht der Horizont in der Dämmerung.
vec3 skyColorPlaceholder(vec3 d);
vec3 skyColor(vec3 d) { return uSkyMode == 1 ? panoSample(d) : skyColorPlaceholder(d); }

// Mondlicht (nur mit com.dan.sky).
vec3 moonLight() { return uSkyMode == 1 ? uMoonColor : vec3(0.0); }

vec3 skyColorPlaceholder(vec3 d) {
    float e = sunElevDeg();
    float B = skyBrightness(e);
    float day = smoothstep(-2.0, 12.0, e);
    float night = 1.0 - smoothstep(-16.0, -8.0, e);
    float tw = clamp(1.0 - day - night, 0.0, 1.0);
    float up = clamp(d.y, 0.0, 1.0);
    float hw = pow(1.0 - up, 4.0);
    vec2 sh = normalize(uSun.xz + vec2(1e-5));
    vec2 dh = normalize(d.xz + vec2(1e-5));
    // geklemmt: genau gegenüber der Sonne wird dot() minimal kleiner als −1, und pow(negativ, …) ergibt NaN
    // (schwarze Punkte auf einer Linie; Befund aus Haithabu, Phase 1)
    float toward = clamp(dot(sh, dh) * 0.5 + 0.5, 0.0, 1.0);
    vec3 zen = vec3(0.16, 0.34, 0.72) * day + vec3(0.10, 0.17, 0.42) * tw + vec3(0.05, 0.08, 0.20) * night;
    vec3 hor = vec3(0.62, 0.74, 0.88) * day + mix(vec3(0.28, 0.30, 0.46), vec3(0.95, 0.52, 0.26), pow(toward, 3.0)) * tw
             + vec3(0.07, 0.09, 0.18) * night;
    vec3 c = mix(zen, hor, hw);
    // tief stehende Sonne am Tag: warmer Saum zur Sonne
    c += vec3(0.9, 0.55, 0.30) * pow(toward, 6.0) * hw * day * (1.0 - smoothstep(4.0, 20.0, e)) * 0.6;
    // Dämmerungsglühen dicht über der Kimm
    c += vec3(1.0, 0.42, 0.14) * tw * pow(toward, 4.0) * pow(1.0 - up, 14.0) * 1.2;
    c *= B / 0.32;
    float cs = dot(d, uSun);
    float vis = sunVisible();
    c += vec3(1.0, 0.8, 0.55) * pow(max(cs, 0.0), 180.0) * 0.5 * vis * B / 0.32;
    c += vec3(30.0, 26.0, 20.0) * mix(vec3(1.0, 0.45, 0.2), vec3(1.0), smoothstep(0.0, 0.2, uSun.y)) * smoothstep(0.99996, 0.999989, cs) * vis;
    return c;
}

// Dunst zur Ferne hin: Phase 1 fest (120 km, ein klarer Sommerabend am Golf von Alaska [G]), mit com.dan.sky aus der Sichtweite
// (Koschmieder). Der Dunst nimmt mit der Höhe ab (Skalenhöhe 8 km [G]): gezählt wird die Strecke in der mittleren Höhe
// des Wegs, damit der Blick vom Sporn und aus der Draufsicht nicht im Dunst verschwindet.
// Mit com.dan.sky (aus Sylt, dort seit Phase 3): die Farbe des Dunsts aus dem Rundumbild knapp über der Kimm, in der Helligkeit begrenzt,
// damit die Sonnenscheibe und ihr Glanz nicht als heller Fleck aufs Gelände fallen. Aus der Mip-Stufe 4 (Befund Sylt Phase 4)
// (32 × 16, rund 11° je Texel): mit der feinen Stufe zeichneten die Wolken an der Kimm senkrechte Streifen ins Gelände.
vec3 hazeColor(vec3 dir) {
    // Platzhalter: 3° über der Kimm, nicht auf ihr (Befund LituyaBay1958 Phase 1: steht die Sonne tief, lag sonst ihre Scheibe
    // im Dunst, und im Blick senkrecht nach unten zog eine helle Linie zur Sonne durch das Bild); steil nach unten der
    // Zenit statt der Kimm, denn dort hat die Richtung zur Sonne keine Bedeutung mehr (sonst eine Halbebene im Bild)
    if (uSkyMode != 1) return mix(skyColor(normalize(vec3(normalize(dir.xz + vec2(1e-5)), 0.0).xzy + vec3(0.0, 0.05, 0.0))),
                                  skyColor(vec3(0.0, 1.0, 0.0)), smoothstep(0.35, 0.9, -dir.y));
    vec2 h = normalize(dir.xz + vec2(1e-5));
    float az = atan(h.x, -h.y) / 6.28318530718 + 0.5;
    float el = asin(0.03) / 3.14159265359 + 0.5;
    vec3 c = textureLod(uPano, vec2(az, el), 4.0).rgb;
    float cap = 3.0 * dot(uSkyAmb, vec3(0.2126, 0.7152, 0.0722)) + 1e-6, l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return l > cap ? c * (cap / l) : c;
}

vec3 aerial(vec3 col, vec3 dir, float dist) {
    float hm = max(0.0, uCam.y + dir.y * dist * 0.5);
    if (uSkyMode == 1) {
        // W1 (L3): zwei Schichten wie in com.dan.sky: Luft (8 km Skalenhöhe, rund 150 km Sicht) und Dunst (1,2 km, aus der
        // Sicht). Gezählt in der mittleren Höhe des Wegs; so stehen die Gipfel klar über dem Dunst der Bucht.
        // Dunst mit 0,45 gewichtet: com.dan.sky rechnet die Auslöschung am Boden (Licht und Streuung); sichtbar wird
        // davon rund die Hälfte als Schleier (erste Probe auf der Radeon: der Mittelgrund war grau verwaschen)
        float od = dist * (exp(-hm / 8000.0) / 150000.0 + 0.45 * exp(-hm / 1200.0) / uFogDist);
        return mix(col, hazeColor(dir), 1.0 - exp(-od));
    }
    float f = 1.0 - exp(-dist * exp(-hm / 8000.0) / 120000.0);
    return mix(col, hazeColor(dir), f);
}

// ------------------------------------------------------------------ W1: Sonne am Punkt

// Durchlässigkeit der Luft für die Sonne an einem Punkt der Höhe h (m) bei Sonnenhöhe sinEl. Rayleigh (Zenit-Dicke
// 0,046/0,108/0,265, 8 km) und Dunst (0,10, 1,2 km [G]); Luftmasse nach Kasten und Young, unter der Waagerechten der
// streifende Strahl nach Chapman (Tangentenhöhe). Auf 4 000 m und 1° unter der Kimm: rot, rund ein Zehntel der Sonne.
float luftmasse(float h, float sinEl, float H) {
    float el = asin(clamp(sinEl, -1.0, 1.0));
    float R = 6371000.0;
    if (el >= 0.0) {
        float e = degrees(el);
        return exp(-h / H) / (sin(el) + 0.50572 * pow(e + 6.07995, -1.6364));
    }
    float ht = (R + h) * cos(el) - R;                       // Tangentenhöhe
    float voll = sqrt(6.2831853 * R / H);
    return max(voll * exp(-max(ht, -200.0) / H) - 0.5 * voll * exp(-h / H), 0.5 * voll * exp(-h / H));
}
vec3 sonnenDurchlass(float h, float sinEl) {
    vec3 tauR = vec3(0.046, 0.108, 0.265);
    float tauA = 0.10;
    vec3 od = tauR * luftmasse(h, sinEl, 8000.0) + vec3(tauA * luftmasse(h, sinEl, 1200.0));
    return exp(-od);
}

// Sonne ja/nein (0..1) am Punkt w aus den Horizontkarten: Horizontwinkel gegen Sonnenhöhe, Halbschatten der Scheibe
// (0,53°); ein Punkt über dem Boden (Welle, Staub, Baumkrone) sieht etwas mehr (Hindernis in rund 2 km angenommen).
float bergSchatten(vec3 w) {
    if (uSchattenAn == 0) return 1.0;
    vec2 hz;
    vec2 uf = (w.xz - uSchattenFRect.xy) / uSchattenFRect.z;
    if (all(greaterThan(uf, vec2(0.0))) && all(lessThan(uf, vec2(1.0)))) hz = textureLod(uSchattenF, uf, 0.0).rg;
    else {
        vec2 ug = (w.xz - uSchattenGRect.xy) / uSchattenGRect.z;
        if (all(greaterThan(ug, vec2(0.0))) && all(lessThan(ug, vec2(1.0)))) hz = textureLod(uSchattenG, ug, 0.0).rg;
        else return 1.0;
    }
    float el = asin(clamp(uSun.y, -1.0, 1.0));
    float lift = max(w.y - hz.y, 0.0) / 2000.0;
    return smoothstep(-0.0046, 0.0046, el - hz.x + lift);
}

// Werkbuch Welle W3c: Himmelslicht auf eine Fläche mit Normale n. Vorher überall die Farbe des Zenits mal (0,55 + 0,45 n.y):
// in der Dämmerung ein kaltes Violett auf allem, Hang und Wasser in derselben Farbe. Jetzt mit com.dan.sky aus dem
// Rundumbild, stark gemittelt (Mip-Stufe 6, rund 45°) in Richtung der Normale (etwas angehoben): Hänge zum hellen Himmel im
// Nordwesten bekommen das warme Licht der Kimm, Hänge zur Erdschattenseite das kühle; dazu Licht vom Boden zurück [G].
vec3 umgebung(vec3 n) {
    vec3 z = skyColor(vec3(0.0, 1.0, 0.0));
    float sicht = 0.55 + 0.45 * n.y;
    if (uSkyMode != 1) return z * sicht;
    vec3 dd = normalize(vec3(n.x, max(n.y, 0.0) + 0.35, n.z));
    float az = atan(dd.x, -dd.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(dd.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
    vec3 c = textureLod(uPano, vec2(az, el), 6.0).rgb;
    // in der Helligkeit an das bisherige Mittel gebunden (die Szene bleibt gleich hell, nur die Farbe folgt der Richtung)
    float lz = dot(z, vec3(0.2126, 0.7152, 0.0722)) + 1e-6, lc = dot(c, vec3(0.2126, 0.7152, 0.0722)) + 1e-6;
    c *= clamp(lz / lc, 0.6, 1.6);
    vec3 boden = uSkyAmb * vec3(1.05, 0.95, 0.8) * 0.35;   // Licht vom Boden (warm, schwach)
    return mix(z, c, 0.75) * sicht + boden * (1.0 - sicht);
}

// Licht der Sonne am Punkt w: mit com.dan.sky Durchlässigkeit nach Höhe und Bergschatten, sonst der Platzhalter.
vec3 sunAt(vec3 w) {
    if (uSkyMode != 1) return sunLight();
    // W7: dazu der Schatten der Wolken (ohne Wolken 1)
    return uSunToa * sonnenDurchlass(max(w.y, 0.0), uSun.y) * bergSchatten(w) * wolkenSchatten(w, uSun);
}
