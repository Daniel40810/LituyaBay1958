"""HTTP ohne Fremdpakete: Abrufen mit Wiederholung, S3-Listen, Laden mit Fortsetzen.

Proxy: urllib nimmt HTTPS_PROXY/HTTP_PROXY aus der Umgebung.
"""

import hashlib
import http.client
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

AGENT = "LituyaBay1958-Kartenmaterial/1.0 (+https://github.com/Daniel40810/LituyaBay1958)"
# Netzfehler, nach denen sich ein neuer Versuch lohnt; IncompleteRead (Server bricht ab) ist keine OSError
NETZFEHLER = (urllib.error.URLError, TimeoutError, ConnectionError, OSError, http.client.HTTPException)


def _anfrage(url, kopf=None, methode="GET"):
    k = {"User-Agent": AGENT}
    k.update(kopf or {})
    return urllib.request.Request(url, headers=k, method=methode)


def hole(url, versuche=4, timeout=120, kopf=None):
    """Inhalt als bytes; wiederholt bei Netzfehlern (2, 4, 8 s)."""
    warte = 2
    for i in range(versuche):
        try:
            with urllib.request.urlopen(_anfrage(url, kopf), timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (400, 401, 403, 404, 410) or i == versuche - 1:
                raise
        except NETZFEHLER:
            if i == versuche - 1:
                raise
        time.sleep(warte)
        warte *= 2


def hole_text(url, **kw):
    return hole(url, **kw).decode("utf-8", errors="replace")


def hole_json(url, **kw):
    return json.loads(hole(url, **kw))


def groesse(url):
    """Content-Length per HEAD, None wenn unbekannt."""
    try:
        with urllib.request.urlopen(_anfrage(url, methode="HEAD"), timeout=60) as r:
            n = r.headers.get("Content-Length")
            return int(n) if n else None
    except Exception:
        return None


# ---------------------------------------------------------------------------
# S3 (oeffentliche Buckets, ListObjectsV2 ohne Anmeldung)
# ---------------------------------------------------------------------------
def s3_liste(basis, prefix, trenner=None):
    """-> (dateien [(key, groesse)], ordner [prefix]) unter basis (https://bucket.s3...)."""
    dateien, ordner, token = [], [], None
    while True:
        q = {"list-type": "2", "prefix": prefix}
        if trenner:
            q["delimiter"] = trenner
        if token:
            q["continuation-token"] = token
        x = hole_text(basis.rstrip("/") + "/?" + urllib.parse.urlencode(q))
        for m in re.finditer(r"<Contents>.*?<Key>([^<]*)</Key>.*?<Size>(\d+)</Size>.*?</Contents>", x, re.S):
            dateien.append((_xml(m.group(1)), int(m.group(2))))
        ordner += [_xml(p) for p in re.findall(r"<CommonPrefixes><Prefix>([^<]*)</Prefix>", x)]
        m = re.search(r"<NextContinuationToken>([^<]*)</NextContinuationToken>", x)
        if not m:
            return dateien, ordner
        token = _xml(m.group(1))


def _xml(s):
    return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&apos;", "'")


def s3_url(basis, key):
    return basis.rstrip("/") + "/" + urllib.parse.quote(key)


# ---------------------------------------------------------------------------
# Laden
# ---------------------------------------------------------------------------
def sha256(pfad):
    h = hashlib.sha256()
    with open(pfad, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def laden(url, ziel, soll_groesse=None, soll_sha256=None, versuche=8, melden=None):
    """Laedt url nach ziel; setzt eine angefangene .part-Datei fort.

    Rueckgabe: 'vorhanden', 'geladen'; wirft bei endgueltigem Fehler.
    """
    teil = ziel + ".part"
    if os.path.exists(ziel):
        if soll_groesse is None:
            # Groesse unbekannt: beim Server nachfragen, denn ein frueherer Lauf kann abgeschnitten haben
            server = groesse(url)
            if server is None or server == os.path.getsize(ziel):
                return "vorhanden"
            if os.path.getsize(ziel) < server and not os.path.exists(teil):
                os.replace(ziel, teil)  # abgeschnitten: dort weitermachen
            else:
                os.remove(ziel)
        elif os.path.getsize(ziel) == soll_groesse:
            return "vorhanden"
    os.makedirs(os.path.dirname(ziel) or ".", exist_ok=True)
    warte = 2
    for versuch in range(versuche):
        try:
            schon = os.path.getsize(teil) if os.path.exists(teil) else 0
            kopf = {"Range": f"bytes={schon}-"} if schon else {}
            with urllib.request.urlopen(_anfrage(url, kopf), timeout=180) as r:
                if schon and r.status != 206:  # Server kann kein Range: neu
                    schon = 0
                laenge = r.headers.get("Content-Length")
                gesamt = int(laenge) + schon if laenge else soll_groesse
                with open(teil, "ab" if schon else "wb") as f:
                    n, t0 = schon, time.time()
                    while True:
                        block = r.read(1 << 20)
                        if not block:
                            break
                        f.write(block)
                        n += len(block)
                        if melden and time.time() - t0 > 2:
                            melden(n, gesamt)
                            t0 = time.time()
                # Bricht der Server ab, endet read() je nach Python ohne Fehler: selbst nachzaehlen
                if laenge and n < gesamt:
                    raise http.client.IncompleteRead(b"", gesamt - n)
            if soll_groesse is not None and os.path.getsize(teil) != soll_groesse:
                raise IOError(f"Groesse {os.path.getsize(teil)} statt {soll_groesse}")
            if soll_sha256 and sha256(teil).lower() != soll_sha256.lower():
                os.remove(teil)
                raise IOError("SHA-256 stimmt nicht")
            os.replace(teil, ziel)
            return "geladen"
        except urllib.error.HTTPError as e:
            if e.code == 416 and os.path.exists(teil):  # schon vollstaendig
                os.replace(teil, ziel)
                return "geladen"
            if e.code in (401, 403, 404, 410) or versuch == versuche - 1:
                raise
            print(f"    HTTP {e.code}, neuer Versuch in {warte} s ...", file=sys.stderr)
        except NETZFEHLER as e:
            if versuch == versuche - 1:
                raise
            schon = os.path.getsize(teil) if os.path.exists(teil) else 0
            print(f"    {type(e).__name__} nach {schon / 1e6:.1f} MB, setze in {warte} s fort: "
                  f"{url.rsplit('/', 1)[-1][:60]}", file=sys.stderr)
        time.sleep(warte)
        warte = min(warte * 2, 60)
