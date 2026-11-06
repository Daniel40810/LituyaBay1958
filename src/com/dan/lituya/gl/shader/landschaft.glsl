// landschaft.glsl — Werkbuch Welle W6 „Landschaft“ (LandschaftGl): die Farbe der Hänge aus Sentinel-2 (ohne das Licht der
// Aufnahme), Fels mit Schichtung und Klüften aus com.dan.cliff, Schnee mit Form, Gletscher mit Spalten quer zum Fließen.
// Braucht kh, kn, knf aus gelaende.frag (vor der Include-Zeile) und cliff.glsl.
uniform int uLandAn;
uniform sampler2D uLandFK, uLandFS, uLandEK, uLandES;
uniform vec4 uLandFKRect, uLandFSRect, uLandEKRect, uLandESRect;   // x0, z0 (Nordrand), Breite, Tiefe (Szene, m)
uniform sampler2D uLandRK, uLandRS;                                // Runde 3: Normalen aus szene_5m (10 / 20 m)
uniform vec4 uLandRKRect, uLandRSRect;
uniform vec4 uLand;   // x Gewinn der Farbe, y Fels aus cliff (0/1), z Spalten (0/1), w Ausfransen der Schneekante

// 1 im Raster, zum Rand über b (Anteil) auf 0
float landRand(vec2 uv, vec2 b) { vec2 d = min(uv, 1.0 - uv) / b; return clamp(min(d.x, d.y), 0.0, 1.0); }

// Farbe (linear) und Schnee (a); w: Anteil der Daten (0 außerhalb der Szene). Beide Raster immer abgetastet (Mip-Stufen).
vec4 landFarbe(vec2 xz, out float w) {
    vec2 us = (xz - uLandFSRect.xy) / uLandFSRect.zw, uk = (xz - uLandFKRect.xy) / uLandFKRect.zw;
    vec4 s = texture(uLandFS, us), k = texture(uLandFK, uk);
    w = landRand(us, 1500.0 / uLandFSRect.zw);
    return mix(s, k, landRand(uk, 500.0 / uLandFKRect.zw));
}

// Eis (r), Spalten (g), Fließrichtung (ba, 0,5 + 0,5·Richtung)
vec4 landEis(vec2 xz) {
    vec2 us = (xz - uLandESRect.xy) / uLandESRect.zw, uk = (xz - uLandEKRect.xy) / uLandEKRect.zw;
    vec4 s = texture(uLandES, us), k = texture(uLandEK, uk);
    vec4 e = mix(s, k, landRand(uk, 500.0 / uLandEKRect.zw));
    e.rg *= landRand(us, 1500.0 / uLandESRect.zw);
    return e;
}

/*
 * Spalten [G]: Scharen quer zum Fließen, also längs der Höhenlinien der geglätteten Eisoberfläche hs (m) mit Gefälle gs;
 * Abstand rund 24 m längs des Fließens, gebogen und stückweise (en échelon); Breite nach der Dehnung st (0..1). Wo st groß
 * ist (Eisfall), kreuzt eine zweite Schar: Seracs. In der Ferne das Mittel. Runde 4: vorher eine Richtung je Bildpunkt
 * (dot(xz, Richtung)), das drehte das Muster zu Kreisen und Maserung. Rückgabe: x Spalte (0..1, dunkles Inneres), y heller
 * Rand (blaue Wand), z Anteil Ferne.
 */
vec3 landSpalten(vec2 xz, float hs, float gs, float st, float px) {
    float fern = smoothstep(1.2, 5.0, px);
    float mittel = st * 0.12;
    if (fern >= 1.0) return vec3(mittel, 0.0, 1.0);
    float innen = 0.0, rand = 0.0, aa = max(px, 0.25);
    // Runde 5: feste Stufe in der Höhe (1,4 m Gefälle je Spalte) statt hs / Gefälle: das teilte eine große Höhe durch ein
    // Gefälle, das sich ändert, und gab feine Linien wie Höhenlinien alle paar Meter. Abstand am Boden = 1,4 m / Gefälle
    float dh = 1.4;
    float abstBoden = dh / max(gs, 0.005);
    for (int s = 0; s < 2; s++) {
        float w = s == 0 ? 1.0 : smoothstep(0.75, 0.95, st);   // Runde 6: Seracs nur im Eisfall
        if (w <= 0.0) break;
        float abst = s == 0 ? clamp(abstBoden, 8.0, 80.0) : 17.0;
        float u = s == 0 ? hs / dh : dot(xz, vec2(0.62, 0.78)) / abst + hs / dh * 0.5;
        u += (kn(xz / 220.0 + 3.1 + float(s)) - 0.5) * 1.2 + (kn(xz / 70.0 + 9.7) - 0.5) * 0.25;
        float id = floor(u);
        float seg = smoothstep(0.45, 0.62, kn(xz / 55.0 + vec2(id * 1.7 + float(s) * 31.0, id * 0.9)));   // Runde 6: weniger, in Stücken
        float br = min(mix(2.0, 8.0, st) * (0.55 + 0.9 * kh(vec2(id, 3.3 + float(s)))), 0.35 * abst) * seg * w;
        float d = abs(fract(u) - 0.5) * abst;
        float i = (1.0 - smoothstep(0.5 * br - aa, 0.5 * br + aa, d)) * step(0.05, br);
        float r = (1.0 - smoothstep(0.5 * br, 0.5 * br + 1.5 + aa, d)) * step(0.05, br);
        innen = max(innen, i);
        rand = max(rand, r - i);
    }
    return vec3(mix(innen, mittel, fern), rand * (1.0 - fern), fern);
}

/*
 * W6 Runde 3: die Normale aus dem Gelände 5 m (IfSAR 2012), auf 10 m (Kern) und 20 m (Szene) gemittelt. Die Clipmap
 * zeichnet in d Metern Abstand mit rund d/96 m je Scheitel (193 Scheitel je Stufe, Stufen verdoppeln sich): in 30 km
 * über 300 m. Wo das gröber ist als die Karte, gilt die Normale der Karte: Grate, Rinnen, Kare und Wände der Kette, wie
 * sie gemessen sind (vorher Knetmasse; die gerippten Rinnen aus Runde 2 drehten sich mit der Falllinie zu Mustern wie
 * Holzmaserung und sind ersetzt).
 */
// lage: Runde 5, Grat (1) oder Rinne (0) aus der Karte (0,5 ohne Karte)
vec3 landRelief(vec2 xz, vec3 n, float dist, out float lage) {
    vec2 us = (xz - uLandRSRect.xy) / uLandRSRect.zw, uk = (xz - uLandRKRect.xy) / uLandRKRect.zw;
    vec4 ta = texture(uLandRS, us), tb = texture(uLandRK, uk);
    float wk = landRand(uk, 300.0 / uLandRKRect.zw), ws = landRand(us, 1500.0 / uLandRSRect.zw);
    vec2 r = mix(ta.rg, tb.rg, wk) * 2.0 - 1.0;
    vec3 nm = vec3(r.x, sqrt(max(0.0, 1.0 - dot(r, r))), r.y);
    float zelle = mix(20.0, 10.0, wk);
    float w = ws * smoothstep(0.6 * zelle, 2.0 * zelle, dist / 96.0);
    lage = mix(0.5, mix(ta.b, tb.b, wk), ws);
    return normalize(mix(n, nm, w));
}

/*
 * W6 Runde 2: Rippen und Rinnen in der Falllinie [G]. Das Gelände der Kette (in der Ferne 30–80 m je Scheitel) ist glatt
 * wie Knetmasse; echte Flanken sind in Rippen aus Fels und Rinnen voll Schnee zerlegt. Ein gerippter Verlauf quer zur
 * Falllinie in drei Maßstäben (quer 150, 55, 20 m, längs viermal so lang), nur an steilen Hängen; Rückgabe: x Höhe der
 * Rippe 0..1 (0 Rinne, 1 Grat), y Neigung quer (m/m) für die Normale. Was unter einen Bildpunkt fällt, mittelt knf weg.
 */
float landGrat1(vec2 q, float pa, float px, float seed) {
    float wf = smoothstep(0.25, 0.6, px / pa);
    float v = wf >= 1.0 ? 0.5 : mix(kn(vec2(q.x / pa, q.y / (pa * 4.0)) + seed), 0.5, wf);
    return 1.0 - abs(2.0 * v - 1.0);
}
vec2 landGrat(vec2 xz, vec2 fall, float px) {
    vec2 quer = vec2(-fall.y, fall.x);
    vec2 q = vec2(dot(xz, quer), dot(xz, fall));
    float h0 = 0.0, h1 = 0.0, d = 2.5;
    float P[3] = float[3](150.0, 55.0, 20.0), A[3] = float[3](0.55, 0.30, 0.15);
    for (int o = 0; o < 3; o++) {
        h0 += A[o] * landGrat1(q, P[o], px, float(o) * 17.0);
        h1 += A[o] * landGrat1(q + vec2(d, 0.0), P[o], px, float(o) * 17.0);
    }
    // Höhe in m: jede Stufe rund 0,12 ihrer Periode hoch [G]
    float s = (h1 - h0) / d * 0.16 * 90.0;
    return vec2(h0, s);
}

/*
 * Die Landschaft an einem Punkt des Geländes [G]. alb: Farbe vorher (nach Höhe und Hang), wird mit der Farbe aus
 * Sentinel-2 ersetzt; nL: Normale für das Licht (Relief des Fels); schatten: Anteil, der keine Sonne bekommt (Inneres der
 * Spalten). Rückgabe 1, wo die Landschaft greift.
 */
struct Land { vec3 alb; vec3 n; float schatten; float schnee; float eis; float fels; float w; float lage; };

Land landschaft(vec3 p, vec3 n, float h, float px, vec3 albAlt) {
    Land L;
    L.alb = albAlt;
    L.n = n;
    L.schatten = 0.0;
    L.schnee = 0.0;
    L.eis = 0.0;
    L.fels = 0.0;
    L.w = 0.0;
    L.lage = 0.5;
    float lw;
    vec4 f = landFarbe(p.xz, lw);
    vec4 e = landEis(p.xz);
    float eX = landEis(p.xz + vec2(20.0, 0.0)).b, eZ = landEis(p.xz + vec2(0.0, 20.0)).b;   // Runde 4: Gefälle der Eisoberfläche
    // cliff im gleichmäßigen Ablauf (Ableitungen im Bild); ohne Fels rechnet es nichts
    CliffSample cs;
    cs.albedo = vec3(1.0); cs.normal = n; cs.rock = 0.0; cs.wet = 0.0; cs.snow = 0.0;
    if (uLand.y > 0.5) cs = cliffMaterial(p, n, 0.0, 0.0, px);
    lw *= smoothstep(-3.0, 1.0, h);
    if (lw <= 0.0) return L;
    // Rippen und Rinnen an steilen Hängen: Normale gekippt, Neigung danach für Schnee und Fels
    // Runde 3: die gemessene Normale statt der gerippten Rinnen (landGrat bleibt für Proben, Schalter uLand.y > 1,5)
    float lage;
    vec3 nG = landRelief(p.xz, n, length(p - uCam), lage);
    float steilG = smoothstep(0.05, 0.22, 1.0 - nG.y);
    float rippe = lage;   // Runde 5: Grate werfen den Schnee ab, Rinnen halten ihn
    if (uLand.y > 1.5) {
        vec2 fall = length(nG.xz) > 1e-3 ? normalize(nG.xz) : vec2(0.0, 1.0);
        vec2 g = landGrat(p.xz, fall, px);
        nG = normalize(nG - vec3(-fall.y, 0.0, fall.x) * g.y * steilG);
        rippe = mix(0.5, g.x, steilG);
    }
    float slope = 1.0 - nG.y;
    vec3 s2 = f.rgb * uLand.x;
    // Schneekante ausgefranst (die 20-m-Zellen sollen nicht zu sehen sein); auf steilen Rippen und Wänden hält kein Schnee,
    // in Rinnen bleibt er [G]
    // Runde 2: dazu 160 und 55 m (sonst bleibt in der Ferne die Kante der 20-m-Zellen als Vieleck)
    float fr = knf(p.xz + 97.0, 160.0, px) * 0.30 + knf(p.xz + 23.0, 55.0, px) * 0.25 + knf(p.xz + 311.0, 17.0, px) * 0.25
             + knf(p.xz + 71.0, 5.5, px) * 0.13 + knf(p.xz + 5.0, 1.8, px) * 0.07;
    float sn = smoothstep(0.34, 0.66, f.a + (fr - 0.5) * uLand.w);
    float rip = knf(p.xz + 19.0, 38.0, px);
    // Runde 2: auf den Rippen weht der Schnee ab, in den Rinnen bleibt er
    float abwurf = smoothstep(0.26, 0.44, slope + (rippe - 0.5) * 0.5 * steilG + (fr - 0.5) * 0.12);   // ab rund 45°
    sn *= 1.0 - abwurf;
    // grün (Wald, Gebüsch, Wiese) und Fels aus der Farbe der Aufnahme
    float gr = (s2.g - max(s2.r, s2.b)) / max(s2.g, 1e-3);
    float veg = smoothstep(0.06, 0.22, gr) * (1.0 - f.a);
    float felsA = clamp(max(smoothstep(0.24, 0.40, slope) * (1.0 - veg), f.a * (1.0 - sn)), 0.0, 1.0);
    // Grundfarbe: wo die Aufnahme Schnee sah, er aber nicht liegt, dunkler Fels der Kette (Schiefer, Gneis) [G]
    vec3 felsGrund = vec3(0.070, 0.068, 0.064) * uLand.x * 1.3;
    vec3 c = mix(s2, felsGrund, f.a * (1.0 - sn));
    // Fels: Muster aus cliff (Bänder, Klüfte, Flechten) als Verhältnis zum Mittel des Gneis, Relief für das Licht
    if (uLand.y > 0.5 && felsA > 0.0) {
        vec3 mittel = mix(uCliffDark, uCliffLight, 0.5);
        // Runde 7: sanfter (die dunklen Bänder des Gneis standen wie Striche auf hellem Kunststoff, Probe 71)
        vec3 det = clamp(cs.albedo / max(mittel, vec3(0.02)), vec3(0.6), vec3(1.5));
        c = mix(c, c * det, felsA * 0.75);
        L.n = normalize(mix(n, cs.normal, felsA));
    }
    L.n = normalize(L.n + (nG - n));   // Rippen und Rinnen
    // Runde 2: Gebüsch und Wiese mit Büschen (Erlen, Weiden) statt Rasen: Klumpen 7 und 22 m, dunkle Lücken [G]
    if (veg > 0.0) {
        float kl = knf(p.xz + 41.0, 22.0, px) * 0.5 + knf(p.xz + 3.0, 7.0, px) * 0.35 + knf(p.xz + 13.0, 2.4, px) * 0.15;
        vec3 v = c * (0.45 + 1.1 * kl);
        v = mix(v, v * vec3(1.15, 1.05, 0.7), smoothstep(0.6, 0.85, knf(p.xz + 77.0, 40.0, px)) * 0.5);
        c = mix(c, v, veg);
    }
    // Schnee: hell, leicht bläulich in Mulden; feine Rippeln in der Nähe [G]
    vec3 schnee = vec3(0.86, 0.89, 0.93) * (0.93 + 0.07 * knf(p.xz + 9.0, 3.0, px)) * mix(1.0, 0.94, smoothstep(0.5, 0.0, rip));
    // Runde 2: unter der Schneegrenze der Gletscher (rund 1 000 m [G]) ist es blankes Eis, kein Schnee (die Aufnahme sättigt
    // bei 0,2 Reflexion und trennt beides nicht): grau-blau, mit den Moränen der Aufnahme
    // Runde 4: Rand des Eises ausgefranst (das 50-m-Raster zeigte Treppen)
    float eis = smoothstep(0.30, 0.70, e.r + (fr - 0.5) * 0.6) * (1.0 - smoothstep(0.42, 0.62, slope));
    float blank = eis * (1.0 - smoothstep(850.0, 1150.0, h + (fr - 0.5) * 300.0));
    vec3 blankeis = vec3(0.46, 0.54, 0.60) * (0.82 + 0.36 * knf(p.xz + 33.0, 30.0, px)) * (0.9 + 0.2 * knf(p.xz + 8.0, 6.0, px));
    schnee = mix(schnee, blankeis, blank);
    c = mix(c, schnee, sn);
    if (eis > 0.0) {
        c = mix(c, c * vec3(0.86, 0.98, 1.12), eis * (1.0 - sn) * (1.0 - veg) * 0.6);
        // Runde 4: Höhenlinien der geglätteten Eisoberfläche (B) und ihr Gefälle aus zwei Nachbarn in 20 m
        float hs = e.b * 5000.0;
        float gs = length(vec2(eX, eZ) * 5000.0 - hs) / 20.0;
        // Runde 2: auch ohne Dehnung aus ITS_LIVE einzelne Spalten, im blanken Eis mehr [G]
        float st = max(e.g, 0.15 * eis + 0.25 * blank);
        if (uLand.z > 0.5 && st > 0.02) {
            vec3 sp = landSpalten(p.xz, hs, gs, clamp(st, 0.0, 1.0), px);
            // Runde 6: im Firn (Schnee über der Schneegrenze) sind die meisten Spalten zugeweht
            float k = eis * clamp(st * 2.5, 0.0, 1.0) * mix(1.0, 0.35, sn * (1.0 - blank));
            vec3 tief = vec3(0.012, 0.035, 0.060);
            vec3 wand = vec3(0.42, 0.62, 0.78);
            c = mix(c, tief, sp.x * k);
            c = mix(c, mix(c, wand, 0.55), sp.y * k);
            L.schatten = sp.x * k * (1.0 - sp.z);
        }
    }
    L.alb = mix(albAlt, c, lw);
    L.n = normalize(mix(n, L.n, lw));
    L.schnee = sn * lw;
    L.eis = eis * lw;
    L.fels = felsA * lw;
    L.w = lw;
    L.lage = mix(0.5, lage, lw);
    return L;
}
