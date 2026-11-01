#!/usr/bin/env python3
"""Kartenmaterial fuer LituyaBay1958: Quellen finden, planen, laden, Inventar schreiben.

Nur Python 3.8+ ohne Fremdpakete (Windows, Linux, macOS).

    python tools/kartenmaterial/kartenmaterial.py liste
    python tools/kartenmaterial/kartenmaterial.py plan                 # was "laden" holen wuerde, mit Groessen
    python tools/kartenmaterial/kartenmaterial.py laden                # alle Quellen der Stufe "standard"
    python tools/kartenmaterial/kartenmaterial.py laden --gross        # dazu die grossen (ArcticDEM 2 m, IfSAR-DSM ...)
    python tools/kartenmaterial/kartenmaterial.py laden --quellen usgs_lidar_glacierbay,noaa_bluetopo
    python tools/kartenmaterial/kartenmaterial.py laden --gruppe karten --ziel C:\\Daten\\LituyaBay1958
    python tools/kartenmaterial/kartenmaterial.py inventar             # INVENTAR.md und inventar.csv neu
    python tools/kartenmaterial/kartenmaterial.py gebiete              # Gebiete als GeoJSON
    python tools/kartenmaterial/kartenmaterial.py katalog              # Quellenkatalog als Markdown

Ziel: Vorgabe ist der Ordner download/ im Projekt (in .gitignore). Je Quelle ein Ordner
<ziel>/<gruppe>/<quelle>/. Abgebrochene Dateien werden beim naechsten Lauf fortgesetzt,
vorhandene uebersprungen.
"""

import argparse
import concurrent.futures as cf
import csv
import json
import os
import sys
import time
import traceback

HIER = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HIER)

import geo  # noqa: E402
import netz  # noqa: E402
import quellen  # noqa: E402
import tiff  # noqa: E402

PROJEKT = os.path.dirname(os.path.dirname(HIER))
GRUPPEN = ["hoehe", "bathymetrie", "karten", "luftbild", "vegetation", "gletscher", "geologie", "vektor",
           "namen", "ereignis", "dokumente", "fotos", "klima"]
GRUPPEN_TITEL = {"hoehe": "Höhe", "bathymetrie": "Wassertiefe", "karten": "Karten", "luftbild": "Luft- und Satellitenbild",
                 "vegetation": "Bewuchs", "gletscher": "Gletscher", "geologie": "Geologie", "vektor": "Vektordaten",
                 "namen": "Namen und Grenzen", "ereignis": "Ereignis 1958", "dokumente": "Berichte",
                 "fotos": "Fotos", "klima": "Gezeiten und Klima"}


def mb(n):
    if n is None:
        return "?"
    if n >= 1e9:
        return f"{n / 1e9:.2f} GB"
    if n < 1e6:
        return f"{n / 1e3:.0f} kB"
    return f"{n / 1e6:.1f} MB"


def auswahl(args):
    q = list(quellen.QUELLEN.values())
    if args.quellen:
        namen = [x.strip() for x in args.quellen.split(",") if x.strip()]
        unbekannt = [n for n in namen if n not in quellen.QUELLEN]
        if unbekannt:
            sys.exit("Unbekannte Quelle(n): " + ", ".join(unbekannt) + " (siehe: liste)")
        return [quellen.QUELLEN[n] for n in namen]
    if args.gruppe:
        gr = set(args.gruppe.split(","))
        q = [x for x in q if x.gruppe in gr]
    stufen = {"standard"} | ({"gross"} if args.gross else set())
    return [x for x in q if x.stufe in stufen]


def plane(q, args):
    g = geo.gebiet(args.gebiet or q.gebiet)
    ctx = quellen.Ctx(g, {"gross": args.gross or bool(args.quellen), "szenen": args.szenen})
    dateien = q.plan(ctx) if q.plan else []
    return g, dateien, ctx.opt.get("_beilagen", {})


def ordner(ziel, q):
    return os.path.join(ziel, q.gruppe, q.id)


# ---------------------------------------------------------------------------
def cmd_liste(args):
    for gr in GRUPPEN:
        qs = [q for q in quellen.QUELLEN.values() if q.gruppe == gr]
        if not qs:
            continue
        print(f"\n{GRUPPEN_TITEL[gr]}")
        for q in qs:
            print(f"  {q.id:28s} {q.stufe:8s} {q.gebiet:6s} {q.titel}")
    print(f"\n{len(quellen.QUELLEN)} Quellen. Stufen: standard (laden), gross (laden --gross), manuell (nur Hinweis).")


def cmd_plan(args, laden=False):
    ziel = os.path.abspath(args.ziel)
    gesamt, fehler = 0, []
    os.makedirs(os.path.join(ziel, "_status"), exist_ok=True)
    for q in auswahl(args):
        t0 = time.time()
        try:
            g, dateien, beilagen = plane(q, args)
        except Exception as e:
            print(f"! {q.id}: Planung fehlgeschlagen: {e}")
            fehler.append((q.id, "Planung", str(e)))
            _status(ziel, q, None, [], [("Planung", str(e))])
            continue
        summe = sum(d.groesse or 0 for d in dateien)
        unbekannt = sum(1 for d in dateien if d.groesse is None)
        gesamt += summe
        print(f"{q.id:28s} {g.name:6s} {len(dateien):5d} Dateien  {mb(summe):>10s}"
              + (f"  (+{unbekannt} ohne Größe)" if unbekannt else "") + f"  [{time.time() - t0:.0f} s]")
        o = ordner(ziel, q)
        for pfad, text in beilagen.items():
            p = os.path.join(ziel, q.gruppe, pfad)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(text)
        dateien = dateien + [quellen.Datei("beilage:" + pfad, pfad.split("/", 1)[1], len(text.encode("utf-8")),
                                           notiz="vom Werkzeug erzeugt") for pfad, text in beilagen.items()
                             if pfad.startswith(q.id + "/")]
        if not laden:
            _status(ziel, q, g, dateien, [], nur_plan=True)
            continue
        if args.max_gb and summe > args.max_gb * 1e9:
            print(f"  übersprungen: {mb(summe)} > --max-gb {args.max_gb}")
            _status(ziel, q, g, dateien, [("Grenze", f"{mb(summe)} > {args.max_gb} GB")], nur_plan=True)
            continue
        f_q = _lade_alle(o, dateien, args.parallel)
        fehler += [(q.id, d, e) for d, e in f_q]
        _status(ziel, q, g, dateien, f_q)
    print(f"\nSumme der bekannten Größen: {mb(gesamt)}")
    if laden:
        cmd_inventar(args)
    if fehler:
        print(f"\n{len(fehler)} Fehler, siehe {os.path.join(ziel, 'INVENTAR.md')}")


def _lade_eine(o, d):
    p = os.path.join(o, *d.pfad.split("/"))
    if d.url.startswith("beilage:"):
        return "vorhanden"
    if d.ausschnitt:
        if os.path.exists(p):
            return "vorhanden"
        t = tiff.GeoTiff(d.url)
        zeilen, ursprung, pixel = t.ausschnitt(*d.ausschnitt)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        typ = "h" if (t.format == 2 and t.bits == 16) else "f"
        tiff.schreiben(p + ".part", zeilen, ursprung, pixel, t.epsg or 4326, t.nodata, typ)
        os.replace(p + ".part", p)
        return "ausgeschnitten"
    return netz.laden(d.url, p, d.groesse, d.sha256)


def _lade_alle(o, dateien, parallel):
    fehler = []
    n, fertig = len(dateien), 0
    with cf.ThreadPoolExecutor(max(1, parallel)) as ex:
        zukunft = {ex.submit(_lade_eine, o, d): d for d in dateien}
        for z in cf.as_completed(zukunft):
            d = zukunft[z]
            fertig += 1
            try:
                r = z.result()
                if r != "vorhanden":
                    print(f"  [{fertig}/{n}] {r:14s} {d.pfad} {mb(d.groesse) if d.groesse else ''}")
            except Exception as e:
                fehler.append((d.url, f"{type(e).__name__}: {e}"))
                print(f"  [{fertig}/{n}] FEHLER         {d.pfad}: {e}")
    return fehler


def _status(ziel, q, g, dateien, fehler, nur_plan=False):
    st = {"quelle": q.id, "gebiet": g.name if g else None, "zeit": time.strftime("%Y-%m-%d %H:%M"),
          "nur_plan": nur_plan, "fehler": [list(f) for f in fehler],
          "dateien": [{"url": d.url, "pfad": d.pfad, "groesse": d.groesse, "notiz": d.notiz} for d in dateien]}
    with open(os.path.join(ziel, "_status", q.id + ".json"), "w", encoding="utf-8") as f:
        json.dump(st, f, ensure_ascii=False, indent=1)


def cmd_laden(args):
    cmd_plan(args, laden=True)


# ---------------------------------------------------------------------------
def _zeige(pfad):
    """Pfad relativ zum Projekt, wenn er darin liegt."""
    r = os.path.relpath(pfad, PROJEKT)
    return pfad if r.startswith("..") else r.replace(os.sep, "/") + "/"


def cmd_inventar(args):
    ziel = os.path.abspath(args.ziel)
    zeilen, abschnitte, fehler_alle = [], [], []
    summe_alle = 0
    for gr in GRUPPEN:
        qs = [q for q in quellen.QUELLEN.values() if q.gruppe == gr]
        teil = []
        for q in qs:
            o = ordner(ziel, q)
            st_pfad = os.path.join(ziel, "_status", q.id + ".json")
            st = json.load(open(st_pfad, encoding="utf-8")) if os.path.exists(st_pfad) else None
            vorhanden, summe = 0, 0
            for wurzel, _, dateien in os.walk(o):
                for f in dateien:
                    if f.endswith(".part"):
                        continue
                    p = os.path.join(wurzel, f)
                    n = os.path.getsize(p)
                    vorhanden += 1
                    summe += n
                    zeilen.append([q.gruppe, q.id, os.path.relpath(p, ziel).replace(os.sep, "/"), n])
            summe_alle += summe
            geplant = len(st["dateien"]) if st else 0
            plan_summe = sum(d["groesse"] or 0 for d in st["dateien"]) if st else 0
            plan_offen = sum(1 for d in st["dateien"] if d["groesse"] is None) if st else 0
            fehler = st["fehler"] if st else []
            fehler_alle += [(q.id, *f) for f in fehler]
            if q.stufe == "manuell":
                zustand = "manuell"
            elif not st:
                zustand = "nicht geplant"
            elif st["nur_plan"] and not vorhanden:
                zustand = "geplant"
            elif fehler and vorhanden == 0:
                zustand = "fehlgeschlagen"
            elif fehler:
                zustand = "teilweise"
            else:
                zustand = "vollständig" if vorhanden >= geplant else "teilweise"
            plan_text = " + ".join(x for x in ((mb(plan_summe) if plan_summe else ""),
                                               (f"{plan_offen} ohne Angabe" if plan_offen else "")) if x)
            teil.append(f"| `{q.id}` | {q.titel} | {q.stufe} | {st['gebiet'] if st else q.gebiet} | {vorhanden}/{geplant} | "
                        f"{mb(summe)} | {plan_text or '–'} | {zustand} |")
        if teil:
            abschnitte.append(f"\n### {GRUPPEN_TITEL[gr]}\n\n| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |\n"
                              "|---|---|---|---|---|---|---|---|\n" + "\n".join(teil))
    with open(os.path.join(ziel, "inventar.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["gruppe", "quelle", "datei", "bytes"])
        w.writerows(sorted(zeilen))
    md = ["# Kartenmaterial Lituya Bay – Inventar\n",
          f"Stand {time.strftime('%d.%m.%Y %H:%M')}, Ordner `{_zeige(ziel)}`. Erzeugt von "
          "`tools/kartenmaterial/kartenmaterial.py inventar`.\n",
          f"**{len(zeilen)} Dateien, {mb(summe_alle)}.** Jede Datei mit Größe in `inventar.csv`, "
          "Adressen und Fehler je Quelle in `_status/<quelle>.json`.\n",
          "## Gebiete\n", "| Gebiet | UTM 8N (m) | Grad (W, S, O, N) | Fläche | Zweck |", "|---|---|---|---|---|"]
    for g in geo.GEBIETE.values():
        w, s, o, n = g.lonlat
        def z(v):
            return f"{v:,.0f}".replace(",", " ")
        md.append(f"| {g.name} | O {z(g.ost0)}–{z(g.ost1)}, N {z(g.nord0)}–{z(g.nord1)} | "
                  f"{w:.4f}, {s:.4f}, {o:.4f}, {n:.4f} | {z(g.flaeche_km2())} km² | {g.zweck} |")
    md.append("\n## Quellen")
    md += abschnitte
    if fehler_alle:
        md.append("\n## Fehlgeschlagen\n\n| Quelle | Adresse | Fehler |\n|---|---|---|")
        for qid, url, e in fehler_alle:
            md.append(f"| `{qid}` | {url} | {str(e)[:160]} |")
    with open(os.path.join(ziel, "INVENTAR.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")
    print(f"Inventar: {len(zeilen)} Dateien, {mb(summe_alle)} -> {os.path.join(ziel, 'INVENTAR.md')}")


# ---------------------------------------------------------------------------
def cmd_gebiete(args):
    feats = []
    for g in geo.GEBIETE.values():
        ring = [list(map(lambda v: round(v, 6), geo.utm_rueckwaerts(e, n))) for e, n in g.rand_utm(10)]
        ring.append(ring[0])
        w, s, o, n = g.lonlat
        feats.append({"type": "Feature", "properties": {
            "name": g.name, "zweck": g.zweck, "utm8n": [g.ost0, g.nord0, g.ost1, g.nord1],
            "bbox_grad": [round(w, 5), round(s, 5), round(o, 5), round(n, 5)], "flaeche_km2": g.flaeche_km2()},
            "geometry": {"type": "Polygon", "coordinates": [ring]}})
    p = args.datei or os.path.join(PROJEKT, "geodaten", "gebiete.geojson")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump({"type": "FeatureCollection", "features": feats}, f, ensure_ascii=False, indent=1)
    print(p)


def cmd_katalog(args):
    zeilen = []
    for gr in GRUPPEN:
        qs = [q for q in quellen.QUELLEN.values() if q.gruppe == gr]
        if not qs:
            continue
        zeilen.append(f"\n### {GRUPPEN_TITEL[gr]}\n")
        for q in qs:
            zeilen.append(f"**{q.titel}** (`{q.id}`, Stufe {q.stufe}, Gebiet {q.gebiet})  ")
            zeilen.append(f"{q.anbieter} · {q.aufloesung} · Stand {q.stand} · Lizenz: {q.lizenz}  ")
            zeilen.append(f"{q.text}  ")
            zeilen.append(f"<{q.seite}>\n")
    text = "\n".join(zeilen)
    if args.datei:
        with open(args.datei, "w", encoding="utf-8") as f:
            f.write(text + "\n")
        print(args.datei)
    else:
        print(text)


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True, errors="replace")
    except AttributeError:
        pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("befehl", choices=["liste", "plan", "laden", "inventar", "gebiete", "katalog"])
    ap.add_argument("--ziel", default=os.environ.get("LITUYA_DOWNLOAD", os.path.join(PROJEKT, "download")),
                    help="Zielordner (Vorgabe: download/ im Projekt oder Umgebungsvariable LITUYA_DOWNLOAD)")
    ap.add_argument("--quellen", help="nur diese Quellen (Komma), auch grosse")
    ap.add_argument("--gruppe", help="nur diese Gruppen (Komma): " + ",".join(GRUPPEN))
    ap.add_argument("--gebiet", choices=list(geo.GEBIETE), help="Gebiet aller gewaehlten Quellen ueberschreiben")
    ap.add_argument("--gross", action="store_true", help="auch Quellen und Dateien der Stufe 'gross'")
    ap.add_argument("--max-gb", type=float, help="Quellen ueber dieser Groesse auslassen")
    ap.add_argument("--parallel", type=int, default=4, help="gleichzeitige Downloads (Vorgabe 4)")
    ap.add_argument("--szenen", type=int, default=3, help="Sentinel-2: klarste Szenen je Kachel (Vorgabe 3)")
    ap.add_argument("--datei", help="Ausgabedatei (gebiete, katalog)")
    args = ap.parse_args()
    try:
        {"liste": cmd_liste, "plan": cmd_plan, "laden": cmd_laden, "inventar": cmd_inventar,
         "gebiete": cmd_gebiete, "katalog": cmd_katalog}[args.befehl](args)
    except KeyboardInterrupt:
        print("\nabgebrochen – der naechste Lauf setzt fort.")
    except Exception:
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
