"""Kleiner GeoTIFF-Leser ohne Fremdpakete (nur fuer Pruefung und Uebersicht).

Kann: klassisches TIFF und BigTIFF, Little/Big Endian, Kacheln und Streifen,
ohne Kompression, Deflate, LZW und PackBits, Praediktor 1/2/3,
uint8/int16/uint16/int32/uint32/float32/float64, ein oder mehrere Kanaele
(chunky). Dazu die Georeferenz (ModelTiepoint/ModelPixelScale, GeoKeys, EPSG)
und die Uebersichtsstufen eines COG, damit grosse Dateien klein gelesen
werden koennen. Nicht: JPEG, LERC, planare Kanaele.

    t = GeoTiff(pfad)          # oder GeoTiff(pfad, stufe=2) fuer eine Uebersicht
    t.breite, t.hoehe, t.epsg, t.ursprung, t.pixel, t.nodata
    werte = t.lesen()          # Liste von Zeilen (Kanal 0), langsam, nur fuer kleine Stufen
"""

import struct
import zlib

_TYPEN = {1: "B", 2: "s", 3: "H", 4: "I", 5: "II", 6: "b", 7: "B", 8: "h", 9: "i",
          10: "ii", 11: "f", 12: "d", 16: "Q", 17: "q", 18: "Q"}
_GROESSE = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 8: 2, 9: 4, 10: 8, 11: 4, 12: 8, 16: 8, 17: 8, 18: 8}


class GeoTiff:
    def __init__(self, pfad, stufe=0):
        self.f = FernDatei(pfad) if str(pfad).startswith("http") else open(pfad, "rb")
        kopf = self.f.read(16)
        self.e = "<" if kopf[:2] == b"II" else ">"
        version = struct.unpack(self.e + "H", kopf[2:4])[0]
        self.big = version == 43
        if self.big:
            off = struct.unpack(self.e + "Q", kopf[8:16])[0]
        else:
            off = struct.unpack(self.e + "I", kopf[4:8])[0]
        self.ifds = []
        while off:
            ifd, off = self._ifd(off)
            self.ifds.append(ifd)
        voll = self.ifds[0]
        # Uebersichten: weitere IFDs ohne Maske (NewSubfileType Bit 2 nicht gesetzt)
        stufen = [voll] + [i for i in self.ifds[1:] if not (i.get(254, [0])[0] & 4)]
        self.stufen = len(stufen)
        t = stufen[min(stufe, len(stufen) - 1)]
        self.t = t
        self.breite, self.hoehe = t[256][0], t[257][0]
        self.kanaele = t.get(277, [1])[0]
        self.bits = t.get(258, [8])[0]
        self.format = t.get(339, [1])[0]
        self.kompression = t.get(259, [1])[0]
        self.praediktor = t.get(317, [1])[0]
        self.kachel = (t[322][0], t[323][0]) if 322 in t else None
        self.offsets = t.get(324) or t.get(273)
        self.laengen = t.get(325) or t.get(279)
        self.zeilen_je_streifen = t.get(278, [self.hoehe])[0]
        # Georeferenz aus dem vollen IFD, Pixelgroesse auf die Stufe umgerechnet
        fx = voll[256][0] / self.breite
        fy = voll[257][0] / self.hoehe
        self.ursprung = None
        self.pixel = None
        if 33922 in voll and 33550 in voll:
            tp, sc = voll[33922], voll[33550]
            self.ursprung = (tp[3] - tp[0] * sc[0], tp[4] + tp[1] * sc[1])
            self.pixel = (sc[0] * fx, sc[1] * fy)
        self.epsg = None
        self.geokeys = {}
        if 34735 in voll:
            g = voll[34735]
            for i in range(4, 4 + 4 * g[3], 4):
                key, ort, n, wert = g[i:i + 4]
                if ort == 0:
                    self.geokeys[key] = wert
            self.epsg = self.geokeys.get(3072) or self.geokeys.get(2048)
        self.nodata = None
        if 42113 in voll:
            try:
                self.nodata = float(voll[42113].strip("\x00 "))
            except ValueError:
                pass
        self.pixel_ist_punkt = self.geokeys.get(1025) == 2

    def _ifd(self, off):
        e = self.e
        self.f.seek(off)
        if self.big:
            n = struct.unpack(e + "Q", self.f.read(8))[0]
            eintrag, zeiger = 20, "Q"
        else:
            n = struct.unpack(e + "H", self.f.read(2))[0]
            eintrag, zeiger = 12, "I"
        roh = self.f.read(n * eintrag + (8 if self.big else 4))
        ifd = {}
        for i in range(n):
            s = roh[i * eintrag:(i + 1) * eintrag]
            if self.big:
                tag, typ, anzahl = struct.unpack(e + "HHQ", s[:12])
                feld = s[12:20]
            else:
                tag, typ, anzahl = struct.unpack(e + "HHI", s[:8])
                feld = s[8:12]
            groesse = _GROESSE.get(typ, 1) * anzahl
            if groesse > len(feld):
                pos = self.f.tell()
                self.f.seek(struct.unpack(e + zeiger, feld)[0])
                daten = self.f.read(groesse)
                self.f.seek(pos)
            else:
                daten = feld[:groesse]
            if typ == 2:
                ifd[tag] = daten.decode("latin-1")
            elif typ in (5, 10):
                v = struct.unpack(e + _TYPEN[typ][0] * (2 * anzahl), daten)
                ifd[tag] = [v[i] / v[i + 1] if v[i + 1] else 0 for i in range(0, len(v), 2)]
            elif typ in _TYPEN:
                ifd[tag] = list(struct.unpack(e + _TYPEN[typ] * anzahl, daten))
        naechster = struct.unpack(e + zeiger, roh[n * eintrag:])[0]
        return ifd, naechster

    # -- Dekodieren ----------------------------------------------------------
    def _entpacken(self, daten):
        k = self.kompression
        if k == 1:
            return daten
        if k in (8, 32946):
            return zlib.decompress(daten)
        if k == 5:
            return _lzw(daten)
        if k == 32773:
            return _packbits(daten)
        raise ValueError(f"Kompression {k} nicht unterstuetzt")

    def _zeichen(self):
        b, fmt = self.bits, self.format
        if fmt == 3:
            return {32: "f", 64: "d"}[b]
        if fmt == 2:
            return {8: "b", 16: "h", 32: "i"}[b]
        return {8: "B", 16: "H", 32: "I"}[b]

    def _block(self, i, bw, bh):
        self.f.seek(self.offsets[i])
        roh = self._entpacken(self.f.read(self.laengen[i]))
        k, nb = self.kanaele, self.bits // 8
        n = bw * bh * k
        roh = roh[:n * nb]
        if len(roh) < n * nb:
            roh = roh + bytes(n * nb - len(roh))
        if self.praediktor == 3:
            # Gleitkomma: Bytes je Zeile in Ebenen, dann Differenzen
            zeile = bw * k * nb
            ba = bytearray(roh)
            out = bytearray(len(ba))
            for r in range(bh):
                z = ba[r * zeile:(r + 1) * zeile]
                for j in range(1, zeile):
                    z[j] = (z[j] + z[j - 1]) & 0xFF
                w = bw * k
                for x in range(w):
                    for b in range(nb):
                        # Ebenen sind grosse Bytes zuerst
                        src = z[b * w + x]
                        if self.e == "<":
                            out[r * zeile + x * nb + (nb - 1 - b)] = src
                        else:
                            out[r * zeile + x * nb + b] = src
            roh = bytes(out)
        werte = list(struct.unpack(f"{self.e}{n}{self._zeichen()}", roh))
        if self.praediktor == 2:
            maske = (1 << self.bits) - 1
            for r in range(bh):
                basis = r * bw * k
                for x in range(k, bw * k):
                    werte[basis + x] = (werte[basis + x] + werte[basis + x - k]) & maske
            if self.format == 2:
                halb = 1 << (self.bits - 1)
                werte = [v - (1 << self.bits) if v >= halb else v for v in werte]
        return werte

    def lesen(self, kanal=0):
        """Ganzes Raster dieser Stufe als Liste von Zeilen (nur kleine Stufen!)."""
        W, H, k = self.breite, self.hoehe, self.kanaele
        bild = [[0] * W for _ in range(H)]
        if self.kachel:
            tw, th = self.kachel
            je_zeile = (W + tw - 1) // tw
            for i in range(len(self.offsets)):
                if not self.laengen[i]:
                    continue
                ty, tx = divmod(i, je_zeile)
                if ty * th >= H:
                    break
                werte = self._block(i, tw, th)
                for r in range(th):
                    y = ty * th + r
                    if y >= H:
                        break
                    zeile = bild[y]
                    for c in range(tw):
                        x = tx * tw + c
                        if x >= W:
                            break
                        zeile[x] = werte[(r * tw + c) * k + kanal]
        else:
            rps = self.zeilen_je_streifen
            for i in range(len(self.offsets)):
                bh = min(rps, H - i * rps)
                werte = self._block(i, W, bh)
                for r in range(bh):
                    zeile = bild[i * rps + r]
                    for x in range(W):
                        zeile[x] = werte[(r * W + x) * k + kanal]
        return bild

    def ausschnitt(self, x0, y0, x1, y1, kanal=0):
        """Liest nur die Bloecke im Rechteck (Koordinaten der Datei).

        -> (zeilen, (ursprung_x, ursprung_y), (pixel_x, pixel_y)); Pixelkanten.
        """
        gx0, gy0, gx1, gy1 = self.grenzen()
        px, py = self.pixel
        c0 = max(0, int((min(x0, x1) - gx0) // px))
        c1 = min(self.breite, int(-(-(max(x0, x1) - gx0) // px)))
        r0 = max(0, int((gy1 - max(y0, y1)) // py))
        r1 = min(self.hoehe, int(-(-(gy1 - min(y0, y1)) // py)))
        W, H, k = c1 - c0, r1 - r0, self.kanaele
        bild = [[0] * W for _ in range(H)]
        if not self.kachel:
            raise ValueError("ausschnitt braucht ein gekacheltes TIFF")
        tw, th = self.kachel
        je_zeile = (self.breite + tw - 1) // tw
        for ty in range(r0 // th, (r1 - 1) // th + 1):
            for tx in range(c0 // tw, (c1 - 1) // tw + 1):
                i = ty * je_zeile + tx
                if not self.laengen[i]:
                    continue
                werte = self._block(i, tw, th)
                for r in range(th):
                    y = ty * th + r
                    if y < r0 or y >= r1:
                        continue
                    zeile = bild[y - r0]
                    for c in range(tw):
                        x = tx * tw + c
                        if c0 <= x < c1:
                            zeile[x - c0] = werte[(r * tw + c) * k + kanal]
        return bild, (gx0 + c0 * px, gy1 - r0 * py), (px, py)

    def grenzen(self):
        """(x0, y0, x1, y1) in der Projektion der Datei (Pixelkanten)."""
        if not self.ursprung:
            return None
        x0, y0 = self.ursprung
        px, py = self.pixel
        if self.pixel_ist_punkt:
            x0, y0 = x0 - px / 2, y0 + py / 2
        return x0, y0 - self.hoehe * py, x0 + self.breite * px, y0

    def schliessen(self):
        self.f.close()


def _packbits(d):
    out, i = bytearray(), 0
    while i < len(d):
        n = d[i]
        i += 1
        if n < 128:
            out += d[i:i + n + 1]
            i += n + 1
        elif n > 128:
            out += bytes([d[i]]) * (257 - n)
            i += 1
    return bytes(out)


def _lzw(d):
    tab = [bytes([i]) for i in range(256)] + [b"", b""]
    out = bytearray()
    bits, pos, alt = 9, 0, None
    n = len(d) * 8
    while pos + bits <= n:
        byte = pos >> 3
        chunk = int.from_bytes(d[byte:byte + 4].ljust(4, b"\0"), "big")
        code = (chunk >> (32 - bits - (pos & 7))) & ((1 << bits) - 1)
        pos += bits
        if code == 256:
            tab = tab[:258]
            bits, alt = 9, None
            continue
        if code == 257:
            break
        if alt is None:
            eintrag = tab[code]
        elif code < len(tab):
            eintrag = tab[code]
            tab.append(alt + eintrag[:1])
        else:
            eintrag = alt + alt[:1]
            tab.append(eintrag)
        out += eintrag
        alt = eintrag
        if len(tab) + 1 >= (1 << bits) and bits < 12:
            bits += 1
    return bytes(out)


class FernDatei:
    """Liest eine Datei im Netz ueber HTTP-Range, mit Zwischenspeicher je 256 KiB."""

    BLOCK = 1 << 18

    def __init__(self, url):
        import netz
        self.url, self.netz, self.pos, self.cache = url, netz, 0, {}

    def seek(self, pos, woher=0):
        self.pos = pos if woher == 0 else self.pos + pos

    def tell(self):
        return self.pos

    def _blk(self, b):
        if b not in self.cache:
            if len(self.cache) > 512:
                self.cache.clear()
            a = b * self.BLOCK
            self.cache[b] = self.netz.hole(self.url, kopf={"Range": f"bytes={a}-{a + self.BLOCK - 1}"})
        return self.cache[b]

    def read(self, n):
        out = bytearray()
        while n > 0:
            b, o = divmod(self.pos, self.BLOCK)
            d = self._blk(b)[o:o + n]
            if not d:
                break
            out += d
            self.pos += len(d)
            n -= len(d)
        return bytes(out)

    def close(self):
        self.cache.clear()


def schreiben(pfad, zeilen, ursprung, pixel, epsg=4326, nodata=None, typ="f"):
    """Schreibt ein unkomprimiertes GeoTIFF (ein Kanal, Streifen je Zeile).

    typ: 'f' float32, 'h' int16. ursprung = obere linke Pixelkante.
    Geografische EPSG (4xxx) als GeographicType, sonst ProjectedCSType.
    """
    H, W = len(zeilen), len(zeilen[0])
    bits, fmt = (32, 3) if typ == "f" else (16, 2)
    nb = bits // 8
    geo_keys = [1, 1, 0, 3]
    if 4000 <= epsg < 5000:
        geo_keys += [1024, 0, 1, 2, 1025, 0, 1, 1, 2048, 0, 1, epsg]
    else:
        geo_keys += [1024, 0, 1, 1, 1025, 0, 1, 1, 3072, 0, 1, epsg]
    eintraege = [
        (256, 4, [W]), (257, 4, [H]), (258, 3, [bits]), (259, 3, [1]), (262, 3, [1]),
        (273, 4, None), (277, 3, [1]), (278, 4, [1]), (279, 4, [W * nb] * H), (284, 3, [1]),
        (339, 3, [fmt]), (33550, 12, [pixel[0], pixel[1], 0.0]),
        (33922, 12, [0.0, 0.0, 0.0, ursprung[0], ursprung[1], 0.0]), (34735, 3, geo_keys),
    ]
    if nodata is not None:
        eintraege.append((42113, 2, (repr(nodata) + "\0").encode()))
    eintraege.sort()
    fmtz = {3: "H", 4: "I", 12: "d", 2: None}
    gross = {3: 2, 4: 4, 12: 8, 2: 1}
    kopf = 8
    ifd_groesse = 2 + 12 * len(eintraege) + 4
    extra_pos = kopf + ifd_groesse
    daten_pos = extra_pos + sum(gross[t] * len(v if v is not None else range(H))
                                for _, t, v in eintraege
                                if gross[t] * len(v if v is not None else range(H)) > 4) + 16
    eintraege = [(tag, t, v if v is not None else [daten_pos + r * W * nb for r in range(H)])
                 for tag, t, v in eintraege]
    ifd = struct.pack("<H", len(eintraege))
    extra = b""
    for tag, t, v in eintraege:
        roh = v if t == 2 else struct.pack("<" + fmtz[t] * len(v), *v)
        if len(roh) <= 4:
            ifd += struct.pack("<HHI", tag, t, len(v)) + roh.ljust(4, b"\0")
        else:
            ifd += struct.pack("<HHII", tag, t, len(v), extra_pos + len(extra))
            extra += roh
            if len(extra) % 2:
                extra += b"\0"
    ifd += struct.pack("<I", 0)
    with open(pfad, "wb") as f:
        f.write(b"II*\0" + struct.pack("<I", kopf) + ifd + extra)
        f.write(bytes(daten_pos - f.tell()))
        for z in zeilen:
            f.write(struct.pack(f"<{W}{typ}", *z))
