#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Werkbuch Welle W6 „Landschaft“: die Farbe der Hänge aus Sentinel-2 und das Eis mit Fließrichtung für gelaende.frag.

Aufruf (im Projektordner):  python3 tools/landschaft/landschaft.py [schritt]
  schritt: mosaik | farbe | eis | relief | fern | bild | alle (Vorgabe alle). Zwischenstände liegen in daten/landschaft/_zwischen.

Was entsteht (daten/landschaft, Zeilen von Nord nach Süd, RGBA8):
  farbe_kern_10m.rgba    2400 × 1800, Kern (E 340 000–364 000, N 6 494 000–6 512 000)
  farbe_szene_20m.rgba   3300 × 3000, Szene (E 322 000–388 000, N 6 480 000–6 540 000)
      RGB: Albedo (Reflexion am Boden, ohne Licht und Schatten der Aufnahme), sRGB-kodiert; A: Schnee 0..255
  eis_kern_10m.rgba16    2400 × 1800 und  eis_szene_50m.rgba16  1320 × 1200 (RGBA, 16 Bit je Kanal, little endian)
      R: Eis (Gletscher nach RGI 7 und den Klassen), G: Spalten (Dehnung aus ITS_LIVE, Klasse 11, geglättet),
      B: geglättete Eisoberfläche (rund 600 m) / 5 000 m; A: frei
  schnee_szene_10m.r8    6600 × 6000 (W6 Runde 5): Schnee der ganzen Szene in 10 m (weich wie im Kern), für die Ferne;
      vorher gab das Alpha von farbe_szene_20m die Kanten aus 20-m-Zellen
  eis_szene_20m.rgba16   3300 × 3000 (W6 Runde 5): wie eis_szene_50m, das Eis aber aus den Umrissen von RGI 7, je Zelle
      der Anteil (2 × 2 Proben), dazu der Schnee der Klassen außerhalb der Gletscher; Spalten und Oberfläche wie in 50 m
  relief_kern_10m.rgba   2400 × 1800 und  relief_szene_20m.rgba  3300 × 3000 (Runde 3, 5)
      R, G: Normale des Geländes x, z (0,5 + 0,5·n) aus der Stufe szene_5m (IfSAR 2012), für die Ferne: dort zeichnet die
      Clipmap mit 64–256 m je Scheitel, und Grate, Rinnen und Kare der Kette gingen verloren; B: Lage (0,5 + Höhe über dem
      Mittel in 40/160 m / 30 m: Grat hell, Rinne dunkel); A: 255
  LANDSCHAFT.txt         Quellen, Raster, Kennzahlen

Wie die Farbe entsteht:
  1. Mosaik aus den klarsten Sommerszenen (Juli zuerst: 28.7.2023 im Norden, 30.7.2020 im Süden), Lücken aus
     September und Juni. Wolken, Wolkenschatten und Fehlstellen nach der Szenenklassifikation (SCL 0, 1, 3, 8, 9, 10).
  2. TCI ist linear: 255 entspricht einer Reflexion von 0,2 (Sentinel-2 L2A). Schnee sättigt; er wird als Schnee
     markiert (SCL 11 oder alle Kanäle gesättigt) und im Shader mit eigenem Material gezeichnet.
  3. Licht und Schatten der Aufnahme heraus [G]: Gewinn = (cos θs + k) / (cos i · Sicht + k · V) je Kanal, mit
     Diffusanteil k (Rot 0,12, Grün 0,18, Blau 0,28 [G]), Einfallswinkel i aus dem Gelände, Schlagschatten aus einem
     Strahl zur Sonne über das Gelände (50 m), Himmelsanteil V = (1 + n_y) / 2. Wo kaum Sonne war, ist das Ergebnis
     unsicher; dort mischt ein gewichteter Weichzeichner (rund 80 m) die sicheren Nachbarn ein.
"""
import glob
import json
import os
import sys
import time

import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

PROJ = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
S2 = os.path.join(PROJ, "download", "luftbild", "sentinel2")
KL = os.path.join(PROJ, "daten", "klassen")
OUT = os.path.join(PROJ, "daten", "landschaft")
ZW = os.path.join(OUT, "_zwischen")

# Raster: Ost der NW-Ecke, Nord der NW-Ecke, Zelle, Breite, Höhe
SZ10 = (322000, 6540000, 10, 6600, 6000)
KERN = (340000, 6512000, 10, 2400, 1800)
SZ20 = (322000, 6540000, 20, 3300, 3000)
SZ50 = (322000, 6540000, 50, 1320, 1200)

# Szenen nach Vorrang (Rang 1 zuerst; innerhalb eines Rangs die Reihenfolge)
SZENEN = [
    ("S2A_8VLL_20230728_0_L2A", 1),
    ("S2A_8VLK_20200730_1_L2A", 1),
    ("S2B_8VLK_20260902_0_L2A", 2),
    ("S2A_8VLL_20190927_1_L2A", 2),
    ("S2A_8VLL_20210608_0_L2A", 3),
    ("S2A_8VLK_20180929_1_L2A", 3),
]
UNGUELTIG = (0, 1, 3, 8, 9, 10)
REFL_255 = 0.2
# Runde 7: höchstens 1,8 (vorher 3: abgewandte Hänge wurden zu hell, Wiesen leuchteten) [G]
GEWINN_MAX = 1.8
K_DIFFUS = np.array([0.12, 0.18, 0.28], np.float32)

T0 = time.time()


def log(*a):
    print("[%5.1f s]" % (time.time() - T0), *a, flush=True)


def meta(sz):
    j = json.load(open(glob.glob(os.path.join(S2, sz, "*.json"))[0]))
    p = j["properties"]
    el = 90.0 - p["s2:mean_solar_zenith"] if "s2:mean_solar_zenith" in p else p["view:sun_elevation"]
    az = p.get("s2:mean_solar_azimuth", p.get("view:sun_azimuth"))
    return float(el), float(az), p.get("datetime", "")


def r32(name, w, h):
    return np.fromfile(os.path.join(KL, name), dtype="<f4").reshape(h, w)


def hochrechnen(a, f):
    """Bilinear um den Faktor f (Zellmitten), Rand gehalten."""
    h, w = a.shape
    ys = (np.arange(h * f) + 0.5) / f - 0.5
    xs = (np.arange(w * f) + 0.5) / f - 0.5
    y0 = np.clip(np.floor(ys).astype(int), 0, h - 1)
    x0 = np.clip(np.floor(xs).astype(int), 0, w - 1)
    y1 = np.clip(y0 + 1, 0, h - 1)
    x1 = np.clip(x0 + 1, 0, w - 1)
    fy = np.clip(ys - np.floor(ys), 0, 1).astype(np.float32)[:, None]
    fx = np.clip(xs - np.floor(xs), 0, 1).astype(np.float32)[None, :]
    r = np.empty((h * f, w * f), np.float32)
    zeilen = 600
    for s in range(0, h * f, zeilen):
        e = min(h * f, s + zeilen)
        a0 = a[y0[s:e]]
        a1 = a[y1[s:e]]
        top = a0[:, x0] * (1 - fx) + a0[:, x1] * fx
        bot = a1[:, x0] * (1 - fx) + a1[:, x1] * fx
        r[s:e] = top * (1 - fy[s:e]) + bot * fy[s:e]
    return r


def kasten(a, r):
    """Kastenmittel mit Radius r (Zellen) über Summen, Rand normiert."""
    def eine(x, ax):
        c = np.cumsum(x, axis=ax, dtype=np.float64)
        n = x.shape[ax]
        idx_hi = np.clip(np.arange(n) + r, 0, n - 1)
        idx_lo = np.arange(n) - r - 1
        hi = np.take(c, idx_hi, axis=ax)
        lo = np.where((idx_lo >= 0).reshape([-1 if i == ax else 1 for i in range(x.ndim)]),
                      np.take(c, np.clip(idx_lo, 0, n - 1), axis=ax), 0.0)
        return (hi - lo).astype(np.float32)
    return eine(eine(a, 0), 1)


def weich(a, w, r, durch=2):
    """Gewichteter Weichzeichner (Kasten, durch Mal): Σ(a·w) / Σw."""
    aw = a * w
    ww = w.copy()
    for _ in range(durch):
        aw = kasten(aw, r)
        ww = kasten(ww, r)
    return aw / np.maximum(ww, 1e-6), ww


def normale(z, zelle):
    gx = np.zeros_like(z)
    gz = np.zeros_like(z)
    gx[:, 1:-1] = (z[:, 2:] - z[:, :-2]) / (2 * zelle)
    gz[1:-1, :] = (z[2:, :] - z[:-2, :]) / (2 * zelle)   # Zeile nach Süd = +z der Szene
    n = np.sqrt(gx * gx + gz * gz + 1)
    return -gx / n, 1 / n, -gz / n


def sonne(el, az):
    """Richtung zur Sonne in der Szene (x Ost, y oben, z Süd)."""
    e, a = np.radians(el), np.radians(az)
    return np.array([np.cos(e) * np.sin(a), np.sin(e), -np.cos(e) * np.cos(a)], np.float32)


def schlagschatten(z, zelle, s, weit):
    """1, wo die Sonne frei ist; Strahl in Schritten einer Zelle bis weit m."""
    h, w = z.shape
    frei = np.ones((h, w), bool)
    horiz = np.hypot(s[0], s[2])
    dx, dz = s[0] / horiz, s[2] / horiz
    tan = s[1] / horiz
    for k in range(1, int(weit / zelle) + 1):
        ox, oz = int(round(k * dx)), int(round(k * dz))
        hoch = k * zelle * tan
        src = np.full((h, w), -1e9, np.float32)
        ys = slice(max(0, oz), min(h, h + oz))
        yd = slice(max(0, -oz), min(h, h - oz))
        xs = slice(max(0, ox), min(w, w + ox))
        xd = slice(max(0, -ox), min(w, w - ox))
        src[yd, xd] = z[ys, xs]
        frei &= src < z + hoch
    return frei.astype(np.float32)


def fenster(sz, raster):
    """Zeilen- und Spaltenversatz eines 10-m-Rasters in der Szene sz (TCI)."""
    e0, n0 = raster[0], raster[1]
    return (6600000 if "_8VLL_" in sz else 6500040) - n0, e0 - 300000


# ---------------------------------------------------------------------------------------------------------------
def mosaik():
    os.makedirs(ZW, exist_ok=True)
    e0, n0, c, W, H = SZ10
    rgb = np.zeros((H, W, 3), np.uint8)
    schnee = np.zeros((H, W), np.uint8)
    quelle = np.full((H, W), 255, np.uint8)
    for q, (sz, rang) in enumerate(SZENEN):
        if not os.path.isdir(os.path.join(S2, sz)):
            log("fehlt", sz)
            continue
        t = np.asarray(Image.open(os.path.join(S2, sz, "TCI.tif")))
        s = np.asarray(Image.open(os.path.join(S2, sz, "SCL.tif")))
        dy, dx = fenster(sz, SZ10)
        dy //= 10
        dx //= 10
        rows = np.arange(H) + dy
        cols = np.arange(W) + dx
        okr = (rows >= 0) & (rows < t.shape[0])
        okc = (cols >= 0) & (cols < t.shape[1])
        R = np.where(okr)[0]
        C = np.where(okc)[0]
        sub = t[rows[R][0]:rows[R][-1] + 1, cols[C][0]:cols[C][-1] + 1]
        scl = s[(rows[R] // 2)][:, cols[C] // 2]
        gut = (sub.max(2) > 0) & ~np.isin(scl, UNGUELTIG)
        offen = quelle[R[0]:R[-1] + 1, C[0]:C[-1] + 1] == 255
        neu = gut & offen
        blk = (slice(R[0], R[-1] + 1), slice(C[0], C[-1] + 1))
        rgb[blk][neu] = sub[neu]
        sn = ((scl == 11) | (sub.min(2) >= 250)).astype(np.uint8) * 255
        schnee[blk][neu] = sn[neu]
        quelle[blk][neu] = q
        log("Szene %s: %.1f %% neu, offen danach %.1f %%" % (sz, 100.0 * neu.sum() / (H * W), 100.0 * (quelle == 255).mean()))
        del t, s, sub, scl
    np.save(os.path.join(ZW, "rgb10.npy"), rgb)
    np.save(os.path.join(ZW, "schnee10.npy"), schnee)
    np.save(os.path.join(ZW, "quelle10.npy"), quelle)
    log("Mosaik fertig")


def abtasten(a, quelle, ziel):
    """Bilinear von Raster quelle (e0, n0, zelle, w, h) auf Raster ziel; außerhalb der Rand."""
    qe, qn, qc, qw, qh = quelle
    ze, zn, zc, zw, zh = ziel
    xs = np.clip(((ze + (np.arange(zw) + 0.5) * zc) - qe) / qc - 0.5, 0, qw - 1)
    ys = np.clip((qn - (zn - (np.arange(zh) + 0.5) * zc)) / qc - 0.5, 0, qh - 1)
    x0 = np.clip(np.floor(xs).astype(int), 0, qw - 1)
    y0 = np.clip(np.floor(ys).astype(int), 0, qh - 1)
    x1 = np.clip(x0 + 1, 0, qw - 1)
    y1 = np.clip(y0 + 1, 0, qh - 1)
    fx = np.clip(xs - np.floor(xs), 0, 1).astype(np.float32)[None, :]
    fy = np.clip(ys - np.floor(ys), 0, 1).astype(np.float32)[:, None]
    r = np.empty((zh, zw), np.float32)
    for s in range(0, zh, 500):
        e = min(zh, s + 500)
        a0 = a[y0[s:e]]
        a1 = a[y1[s:e]]
        r[s:e] = (a0[:, x0] * (1 - fx) + a0[:, x1] * fx) * (1 - fy[s:e]) + (a1[:, x0] * (1 - fx) + a1[:, x1] * fx) * fy[s:e]
    return r


def ausschnitt(ras):
    """Zeilen und Spalten eines 10-m-Rasters (ras) im Mosaik (SZ10)."""
    oy, ox = (SZ10[1] - ras[1]) // 10, (ras[0] - SZ10[0]) // 10
    return slice(oy, oy + ras[4] * ras[2] // 10), slice(ox, ox + ras[3] * ras[2] // 10)


def farbe():
    z50 = r32("gelaende_szene_50m.r32", 1320, 1200)
    zk = r32("gelaende_kern_10m.r32", 2400, 1800)
    rgb = np.load(os.path.join(ZW, "rgb10.npy"), mmap_mode="r")
    schnee = np.load(os.path.join(ZW, "schnee10.npy"), mmap_mode="r")
    quelle = np.load(os.path.join(ZW, "quelle10.npy"), mmap_mode="r")
    os.makedirs(OUT, exist_ok=True)
    # Kern mit 400 m Rand (für den Weichzeichner), 10 m
    rand = 40
    KR = (KERN[0] - rand * 10, KERN[1] + rand * 10, 10, KERN[3] + 2 * rand, KERN[4] + 2 * rand)
    ys, xs = ausschnitt(KR)
    z = abtasten(z50, SZ50, KR)
    z[rand:rand + 1800, rand:rand + 2400] = zk
    a, s = farbe_raster(np.asarray(rgb[ys, xs]).astype(np.float32) * (REFL_255 / 255.0),
                        schnee_weich(np.asarray(schnee[ys, xs]).astype(np.float32) / 255), np.asarray(quelle[ys, xs]), z, KR, z50)
    schreib(a[rand:-rand, rand:-rand], s[rand:-rand, rand:-rand], "farbe_kern_10m.rgba")
    del a, s, z
    # Szene 20 m: Mosaik 2 × 2 gemittelt (linear), Gelände aus 50 m (im Kern aus 10 m)
    H, W = SZ20[4], SZ20[3]
    lin = np.empty((H, W, 3), np.float32)
    for k in range(3):
        lin[..., k] = np.asarray(rgb[..., k]).astype(np.float32).reshape(H, 2, W, 2).mean(axis=(1, 3)) * (REFL_255 / 255.0)
    sn = np.empty((H, W), np.float32)
    for y in range(0, H, 500):   # W6 Runde 4: in Streifen weich (die ganze Szene in 10 m wäre zu groß), mit Rand
        y0, y1 = max(0, 2 * y - 2 * SCHNEE_RAND), min(2 * H, 2 * (y + 500) + 2 * SCHNEE_RAND)
        st = schnee_weich(np.asarray(schnee[y0:y1]).astype(np.float32) / 255)
        o = 2 * y - y0
        sn[y:min(H, y + 500)] = st[o:o + 2 * (min(H, y + 500) - y)].reshape(-1, 2, W, 2).mean(axis=(1, 3))
    qu = np.asarray(quelle[::2, ::2])
    z = abtasten(z50, SZ50, SZ20)
    zk20 = zk.reshape(900, 2, 1200, 2).mean(axis=(1, 3))
    oy, ox = (SZ20[1] - KERN[1]) // 20, (KERN[0] - SZ20[0]) // 20
    z[oy:oy + 900, ox:ox + 1200] = zk20
    a, s = farbe_raster(lin, sn, qu, z, SZ20, z50)
    schreib(a, s, "farbe_szene_20m.rgba")


def farbe_raster(lin, schnee, quelle, z, ras, z50):
    """Albedo ohne das Licht der Aufnahme; lin: Reflexion (linear), schnee 0..1, quelle: Szene je Zelle (255 keine)."""
    H, W = schnee.shape
    c = float(ras[2])
    nx, ny, nz = normale(z, c)
    gewinn = np.ones((H, W, 3), np.float32)
    sicher = np.zeros((H, W), np.float32)
    for q, (sz, rang) in enumerate(SZENEN):
        m = quelle == q
        if not m.any():
            continue
        el, az, _ = meta(sz)
        s = sonne(el, az)
        frei = abtasten(schlagschatten(z50, 50.0, s, 8000.0), SZ50, ras)
        cosi = np.clip(nx * s[0] + ny * s[1] + nz * s[2], 0, 1) * frei
        V = 0.5 * (1 + ny)
        for k in range(3):
            g = (s[1] + K_DIFFUS[k]) / (cosi + K_DIFFUS[k] * V)
            gewinn[..., k] = np.where(m, np.clip(g, 0.6, GEWINN_MAX), gewinn[..., k])
        sicher = np.where(m, np.clip((cosi - 0.05) / 0.30, 0, 1), sicher)
        log("  %s: Sonne %.1f°/%.1f°, frei %.1f %%" % (sz, el, az, 100 * frei[m].mean()))
        del frei, cosi
    del nx, ny, nz
    w = sicher * (quelle != 255) * (1 - schnee)
    r1, r2 = max(1, int(round(80 / c))), max(2, int(round(400 / c)))
    for k in range(3):
        a = lin[..., k] * gewinn[..., k]
        f, ww = weich(a, w, r1, 2)
        f2, _ = weich(a, w, r2, 2)
        f = np.where(ww > 0.02, f, f2)
        lin[..., k] = a * w + f * (1 - w)
    del gewinn
    sn_alb = np.array([0.80, 0.83, 0.88], np.float32)
    lin = lin * (1 - schnee[..., None]) + sn_alb * schnee[..., None]
    log("Farbe %s: sicher %.1f %%, Schnee %.1f %%" % (ras[:3], 100 * (w > 0.5).mean(), 100 * schnee.mean()))
    return np.clip(lin, 0, 1), schnee


def srgb(x):
    x = np.clip(x, 0, 1)
    return np.where(x <= 0.0031308, 12.92 * x, 1.055 * np.power(x, 1 / 2.4) - 0.055)


# W6 Runde 4: der Schnee kommt aus der SCL (20 m) und gesättigten Bildpunkten (10 m), also in Blöcken; als Alpha
# zeichneten die Ränder in der Ferne Treppen aus 20-m-Zellen. Weich mit einem Kasten von 20 m Halbmesser, zweimal
# (Wirkung etwa wie ein Gauß von 15 m): die Kante bleibt bei 0,5, die Ecken der Blöcke runden sich [G].
SCHNEE_RAND = 8


def schnee_weich(s10):
    return np.clip(weich(s10, np.ones_like(s10), 2, 2)[0], 0, 1).astype(np.float32)


def schreib(alb, schnee, name):
    out = np.empty(alb.shape[:2] + (4,), np.uint8)
    out[..., :3] = np.round(srgb(alb) * 255)
    out[..., 3] = np.round(np.clip(schnee, 0, 1) * 255)
    out.tofile(os.path.join(OUT, name))
    log("geschrieben", name, out.shape)


# ---------------------------------------------------------------------------------------------------------------
def eis():
    os.makedirs(OUT, exist_ok=True)
    for name, ras, kl, dem in [("kern_10m", KERN, "kern_10m.u8", "gelaende_kern_10m.r32"),
                               ("szene_50m", SZ50, "szene_50m.u8", "gelaende_szene_50m.r32")]:
        e0, n0, c, W, H = ras
        k = np.fromfile(os.path.join(KL, kl), np.uint8).reshape(H, W)
        z = r32(dem, W, H)
        ist = np.isin(k, (9, 10, 11)).astype(np.float32)
        sp = (k == 11).astype(np.float32)
        # Runde 5: weicher (das 50-m-Raster gab Treppen); das Ausfransen macht der Shader. W6 Runde 4: in der Szene (50 m)
        # 100 m statt 60 m (ein Kasten von einer Zelle ließ die Treppen in der Ferne stehen)
        r = max(1, int(round((60.0 if c <= 10 else 100.0) / c)))
        eisw = weich(ist, np.ones_like(ist), r, 2)[0]
        spw = weich(sp, np.ones_like(sp), max(1, int(round(40.0 / c))), 2)[0]
        # Runde 4: geglättete Eisoberfläche (rund 600 m) statt der Fließrichtung: die Spalten laufen quer zum Fließen,
        # also längs der Höhenlinien dieser Fläche; eine Richtung je Bildpunkt drehte das Muster zu Kreisen [G]
        rz = max(1, int(round(300.0 / c)))
        zg = weich(z, np.maximum(ist, 0.05), rz, 2)[0]
        out = np.empty((H, W, 4), np.uint16)
        out[..., 0] = np.round(np.clip(eisw, 0, 1) * 65535)
        out[..., 1] = np.round(np.clip(spw * 1.6, 0, 1) * 65535)
        out[..., 2] = np.round(np.clip(zg / 5000.0, 0, 1) * 65535)
        out[..., 3] = 0
        out.astype("<u2").tofile(os.path.join(OUT, "eis_%s.rgba16" % name))
        log("Eis", name, "Eis %.1f %%, Spalten %.1f %%" % (100 * ist.mean(), 100 * sp.mean()))


# ---------------------------------------------------------------------------------------------------------------
# W6 Runde 5: die Ferne. Schnee und Eis waren außerhalb des Kerns auf 20- und 50-m-Zellen gemittelt; weich waren nur die
# Ränder. Jetzt der Schnee in 10 m für die ganze Szene und das Eis in 20 m aus den Umrissen selbst.
RGI = os.path.join(KL, "rgi7_szene.geojson")


def utm(lon, lat, zone=8):
    """WGS 84 → UTM Nord (Zone 8), Reihe nach Krüger in n bis zur 4. Ordnung (Karney 2011), auf mm genau."""
    a = 6378137.0
    f = 1 / 298.257223563
    k0 = 0.9996
    n = f / (2 - f)
    A = a / (1 + n) * (1 + n ** 2 / 4 + n ** 4 / 64)
    al = [n / 2 - 2 * n ** 2 / 3 + 5 * n ** 3 / 16 + 41 * n ** 4 / 180, 13 * n ** 2 / 48 - 3 * n ** 3 / 5 + 557 * n ** 4 / 1440,
          61 * n ** 3 / 240 - 103 * n ** 4 / 140, 49561 * n ** 4 / 161280]
    lam = np.radians(lon) - np.radians(-183 + 6 * zone)
    phi = np.radians(lat)
    e = np.sqrt(f * (2 - f))
    t = np.sinh(np.arctanh(np.sin(phi)) - e * np.arctanh(e * np.sin(phi)))
    xi = np.arctan2(t, np.cos(lam))
    eta = np.arctanh(np.sin(lam) / np.sqrt(1 + t * t))
    x, y = eta.copy(), xi.copy()
    for j in range(1, 5):
        x += al[j - 1] * np.cos(2 * j * xi) * np.sinh(2 * j * eta)
        y += al[j - 1] * np.sin(2 * j * xi) * np.cosh(2 * j * eta)
    return 500000 + k0 * A * x, k0 * A * y


def gletscher(ras, ueber=2):
    """Anteil Gletscher je Zelle des Rasters (0..1) aus RGI 7: Umrisse mit Löchern (Nunatakker), ueber × ueber Proben."""
    from PIL import ImageDraw
    e0, n0, c, W, H = ras
    g = json.load(open(RGI, encoding="utf-8"))
    img = Image.new("L", (W * ueber, H * ueber), 0)
    dr = ImageDraw.Draw(img)
    s = ueber / c
    for ft in g["features"]:
        geo = ft["geometry"]
        for poly in (geo["coordinates"] if geo["type"] == "MultiPolygon" else [geo["coordinates"]]):
            for k, ring in enumerate(poly):
                r = np.asarray(ring, float)
                E, N = utm(r[:, 0], r[:, 1])
                if len(r) >= 3:
                    dr.polygon(list(zip(((E - e0) * s).tolist(), ((n0 - N) * s).tolist())), fill=255 if k == 0 else 0)
    a = np.asarray(img, np.float32) / 255.0
    del img
    log("RGI %d Umrisse auf %s" % (len(g["features"]), ras[:3]))
    return a.reshape(H, ueber, W, ueber).mean(axis=(1, 3))


def schnee_fern():
    """schnee_szene_10m.r8: der Schnee des Mosaiks (10 m) wie im Kern weich (schnee_weich), in Streifen mit Rand."""
    schnee = np.load(os.path.join(ZW, "schnee10.npy"), mmap_mode="r")
    H, W = schnee.shape
    out = np.empty((H, W), np.uint8)
    for y in range(0, H, 600):
        y0, y1 = max(0, y - SCHNEE_RAND), min(H, y + 600 + SCHNEE_RAND)
        st = schnee_weich(np.asarray(schnee[y0:y1]).astype(np.float32) / 255)
        out[y:min(H, y + 600)] = np.round(st[y - y0:y - y0 + min(600, H - y)] * 255)
    out.tofile(os.path.join(OUT, "schnee_szene_10m.r8"))
    log("geschrieben schnee_szene_10m.r8", out.shape, "Schnee %.1f %%" % (100 * (out > 127).mean()))


def eis_fern():
    """eis_szene_20m.rgba16: Eis aus RGI 7 (20 m) und dem Schnee der Klassen (50 m) außerhalb, ohne Seen; Spalten und
    Oberfläche aus den Klassen und dem Gelände wie eis() (50 m, hochgerechnet, im Kern 10 m)."""
    e0, n0, c, W, H = SZ20
    k50 = np.fromfile(os.path.join(KL, "szene_50m.u8"), np.uint8).reshape(SZ50[4], SZ50[3])
    z50 = r32("gelaende_szene_50m.r32", SZ50[3], SZ50[4])
    zk = r32("gelaende_kern_10m.r32", KERN[3], KERN[4])
    rg = gletscher(SZ20)
    # Nähte schließen: benachbarte Umrisse von RGI teilen keine Ecken; dazwischen blieben Streifen von 20 bis 60 m ohne
    # Eis, die als helle Linien durch die Eisfelder liefen (die Klassen in 50 m hatten sie verschluckt). Schließen um
    # 3 Zellen (Ausdehnen, dann Abtragen; Lücken unter 120 m) [G]
    b = (rg > 0.5).astype(np.float32)
    r = 3
    zu = (kasten((kasten(b, r) > 0.5).astype(np.float32), r) > (2 * r + 1) ** 2 - 0.5).astype(np.float32)
    log("RGI: Nähte geschlossen %.2f %% der Fläche" % (100 * (zu > b).mean()))
    rg = np.maximum(rg, zu)
    del b, zu
    # Schnee der Klassen (50 m) außerhalb der Gletscher, eine Zelle weit in die Gletscher hinein verlängert: wo Firn an
    # einen Gletscher stößt, fielen sonst beide Anteile am Umriss auf die Hälfte (helle Linien durch die Eisfelder);
    # wo ein Gletscher an Fels stößt, bleibt der Umriss aus RGI (20 m)
    s50 = (k50 == 10).astype(np.float32)
    g50 = np.isin(k50, (9, 11))
    s50 = np.where(g50 & (kasten(s50, 1) > 0.5), 1.0, s50).astype(np.float32)
    sn = np.clip(abtasten(s50, SZ50, SZ20), 0, 1)
    del s50, g50
    see = abtasten((k50 == 1).astype(np.float32), SZ50, SZ20) > 0.5
    ist = np.where(see, 0, np.maximum(rg, sn)).astype(np.float32)
    del rg, sn, see
    sp = np.clip(abtasten((k50 == 11).astype(np.float32), SZ50, SZ20), 0, 1) * (ist > 0.02)
    z = abtasten(z50, SZ50, SZ20)
    oy, ox = (SZ20[1] - KERN[1]) // 20, (KERN[0] - SZ20[0]) // 20
    z[oy:oy + KERN[4] // 2, ox:ox + KERN[3] // 2] = zk.reshape(KERN[4] // 2, 2, KERN[3] // 2, 2).mean(axis=(1, 3))
    eisw = weich(ist, np.ones_like(ist), max(1, int(round(60.0 / c))), 2)[0]   # wie der Kern: 60 m
    spw = weich(sp, np.ones_like(sp), max(1, int(round(40.0 / c))), 2)[0]
    zg = weich(z, np.maximum(ist, 0.05), max(1, int(round(300.0 / c))), 2)[0]
    out = np.empty((H, W, 4), np.uint16)
    out[..., 0] = np.round(np.clip(eisw, 0, 1) * 65535)
    out[..., 1] = np.round(np.clip(spw * 1.6, 0, 1) * 65535)
    out[..., 2] = np.round(np.clip(zg / 5000.0, 0, 1) * 65535)
    out[..., 3] = 0
    out.astype("<u2").tofile(os.path.join(OUT, "eis_szene_20m.rgba16"))
    alt = np.fromfile(os.path.join(OUT, "eis_szene_50m.rgba16"), "<u2").reshape(SZ50[4], SZ50[3], 4)[..., 0] / 65535.0
    log("Eis szene_20m: Eis %.1f %% (szene_50m %.1f %%), Spalten %.1f %%" % (100 * (eisw > 0.5).mean(), 100 * (alt > 0.5).mean(), 100 * (spw > 0.3).mean()))


def relief():
    """Normalen aus szene_5m (Kacheln 2000² zu 5 m, SW-Ecke im Namen) auf 10 m (Kern) und 20 m (Szene)."""
    d = os.path.join(PROJ, "daten", "gelaende", "szene_5m")
    e0, n0, c, W, H = SZ10
    z = np.full((H, W), np.nan, np.float32)
    for f in sorted(glob.glob(os.path.join(d, "E*_N*.r32"))):
        b = os.path.basename(f)[:-4]
        te, tn = int(b.split("_")[0][1:]), int(b.split("_")[1][1:])
        t = np.fromfile(f, "<f4").reshape(2000, 2000)
        t10 = t.reshape(1000, 2, 1000, 2).mean(axis=(1, 3))
        r0 = (n0 - (tn + 10000)) // 10
        c0 = (te - e0) // 10
        rs, cs = max(0, r0), max(0, c0)
        re, ce = min(H, r0 + 1000), min(W, c0 + 1000)
        if rs >= re or cs >= ce:
            continue
        z[rs:re, cs:ce] = t10[rs - r0:re - r0, cs - c0:ce - c0]
    fehlt = np.isnan(z)
    log("Relief: Gelände 10 m, ohne Wert %.2f %%" % (100 * fehlt.mean()))
    if fehlt.any():
        z50 = r32("gelaende_szene_50m.r32", 1320, 1200)
        z[fehlt] = abtasten(z50, SZ50, SZ10)[fehlt]
    os.makedirs(OUT, exist_ok=True)
    def schreib_n(zz, zelle, name):
        nx, ny, nz = normale(zz, zelle)
        # Runde 5: Lage im Relief (Grat hell, Rinne dunkel): Höhe über dem Mittel in rund 40 und 160 m [G]
        r1, r2 = max(1, int(round(20 / zelle))), max(2, int(round(80 / zelle)))
        eins = np.ones_like(zz)
        d = (zz - weich(zz, eins, r1, 2)[0]) * 0.6 + (zz - weich(zz, eins, r2, 2)[0]) * 0.4
        out = np.empty(zz.shape + (4,), np.uint8)
        out[..., 0] = np.round((0.5 + 0.5 * nx) * 255)
        out[..., 1] = np.round((0.5 + 0.5 * nz) * 255)
        out[..., 2] = np.round(np.clip(0.5 + d / 30.0, 0, 1) * 255)
        out[..., 3] = 255
        out.tofile(os.path.join(OUT, name))
        log("geschrieben", name, out.shape, "steiler als 45°: %.1f %%" % (100 * (ny < 0.7071).mean()),
            "Lage: Grat %.1f %%, Rinne %.1f %%" % (100 * (d > 5).mean(), 100 * (d < -5).mean()))
    ys, xs = ausschnitt(KERN)
    schreib_n(z[ys, xs].copy(), 10.0, "relief_kern_10m.rgba")
    schreib_n(z.reshape(H // 2, 2, W // 2, 2).mean(axis=(1, 3)), 20.0, "relief_szene_20m.rgba")


def bild():
    """Vorschau: Farbe der Szene (20 m) auf 1650 × 1500 und Kern ganz, als PNG in daten/landschaft/_zwischen."""
    a = np.fromfile(os.path.join(OUT, "farbe_szene_20m.rgba"), np.uint8).reshape(3000, 3300, 4)
    Image.fromarray(a[::2, ::2, :3]).save(os.path.join(ZW, "farbe_szene.png"))
    a = np.fromfile(os.path.join(OUT, "farbe_kern_10m.rgba"), np.uint8).reshape(1800, 2400, 4)
    Image.fromarray(a[..., :3]).save(os.path.join(ZW, "farbe_kern.png"))
    e = np.fromfile(os.path.join(OUT, "eis_szene_50m.rgba16"), "<u2").reshape(1200, 1320, 4)
    Image.fromarray((e[..., :3] >> 8).astype(np.uint8)).save(os.path.join(ZW, "eis_szene.png"))
    log("Bilder")


def text():
    z = ["# Landschaft (Werkbuch Welle W6, %s): Farbe aus Sentinel-2 L2A (TCI, SCL), Eis aus den Klassen" % time.strftime("%Y-%m-%d"),
         "farbe_kern_10m=340000 6512000 10 2400 1800 rgba8 srgb a=schnee",
         "farbe_szene_20m=322000 6540000 20 3300 3000 rgba8 srgb a=schnee",
         "eis_kern_10m=340000 6512000 10 2400 1800 rgba16 r=eis g=spalten b=oberflaeche/5000",
         "eis_szene_50m=322000 6540000 50 1320 1200 rgba16 r=eis g=spalten b=oberflaeche/5000",
         "eis_szene_20m=322000 6540000 20 3300 3000 rgba16 r=eis (RGI 7, Anteil je Zelle) g=spalten b=oberflaeche/5000 (W6 Runde 5)",
         "schnee_szene_10m=322000 6540000 10 6600 6000 r8 schnee weich (W6 Runde 5)",
         "relief_kern_10m=340000 6512000 10 2400 1800 rgba8 rg=normale x z (0,5 + 0,5 n) b=lage aus szene_5m",
         "relief_szene_20m=322000 6540000 20 3300 3000 rgba8 rg=normale x z (0,5 + 0,5 n) b=lage aus szene_5m",
         "szenen=" + ", ".join("%s (Rang %d, Sonne %.1f°/%.1f°)" % ((s, r) + meta(s)[:2]) for s, r in SZENEN
                               if os.path.isdir(os.path.join(S2, s))),
         "tci=linear, 255 = Reflexion %.2f; Schnee: SCL 11 oder alle Kanäle ab 250" % REFL_255,
         "licht=Gewinn (cos θs + k)/(cos i·Sicht + k·V), k = %s, Schlagschatten 50 m bis 8 km, Gewinn 0,6–%.1f [G]" % (K_DIFFUS.tolist(), GEWINN_MAX),
         "quelle=tools/landschaft/landschaft.py"]
    open(os.path.join(OUT, "LANDSCHAFT.txt"), "w", encoding="utf-8").write("\n".join(z) + "\n")


if __name__ == "__main__":
    schritt = sys.argv[1] if len(sys.argv) > 1 else "alle"
    if schritt in ("mosaik", "alle"):
        mosaik()
    if schritt in ("farbe", "alle"):
        farbe()
    if schritt in ("eis", "alle"):
        eis()
    if schritt in ("relief", "alle"):
        relief()
    if schritt in ("fern", "alle"):
        schnee_fern()
        eis_fern()
    if schritt in ("bild", "alle"):
        bild()
    text()
