"""Texturen (Phase 5, Werkbuch 8): CC0-Materialsätze von ambientCG, je Satz ein Zip in 2K (JPG).

Inhalt je Zip: <Id>_2K-JPG_Color.jpg, _NormalGL.jpg, _Roughness.jpg, _AmbientOcclusion.jpg (nicht immer),
_Displacement.jpg. Die Aufbereitung (Schritt texturen) backt daraus daten/texturen/<name>/ fuer com.dan.surface.
Die Liste entspricht Katalog.fjord() in com.dan.surface (gleiche Ids). Seetang und Kelp: kein CC0-Satz gefunden,
wird in der Aufbereitung aus dem Moos abgeleitet.

Lizenz aller Saetze: CC0 1.0 (https://docs.ambientcg.com/license/). Adressen geprueft am 01.11.2026 ueber die
ambientCG-API (full_json, downloadData).
"""

from quellen import statisch

AMBIENTCG = "https://ambientcg.com/get?file={id}_2K-JPG.zip"

# (Quelle, ambientCG-Id, Titel, wofuer)
SAETZE = [
    ("tex_strandkies", "Gravel041", "Strandkies und Gerölle", "Ufer, La Chaussee Spit; nass und trocken"),
    ("tex_sand", "Ground054", "Sand und Gletschermehl", "Flachwasser, Deltas, Fish Lake"),
    ("tex_schiefer_verwittert", "Rock063", "Schiefer, verwittert (geschichtet, Flechten)", "Wände und Grate"),
    ("tex_schiefer_frisch", "Rock058", "Fels, frischer Bruch", "Rutschnarbe 1958, abgeräumte Hänge"),
    ("tex_moraene", "Ground108", "Moräne, Schutt, Geröll", "Gletschervorfeld, Schuttkegel"),
    ("tex_waldboden", "Ground037", "Waldboden mit Moos", "Regenwald, mit understory"),
    ("tex_moos", "Moss002", "Moos und Bartflechte", "Boden, Stämme, Steine; Grundlage für Seetang"),
    ("tex_firn", "Snow014", "Firn und Schnee", "Gipfel, Firnfelder"),
    ("tex_eis", "Ice003", "Gletschereis", "Fronten und Spalten (blau getönt in der Aufbereitung)"),
    ("tex_rinde", "Bark014", "Rinde (Tanne, für Sitka-Fichte und Hemlock)", "Bäume"),
    ("tex_holz_entrindet", "Wood035", "Holz, alt und rau (für entrindetes Holz, Treibholz)", "Stämme im Band 1958"),
    ("tex_kutter_holz", "PaintedWood009C", "Lackiertes Holz, hell und alt", "Kutter der 1950er (vessel)"),
    ("tex_kutter_tau", "Rope001", "Tau", "Kutter (vessel)"),
]

for qid, aid, titel, wofuer in SAETZE:
    statisch(qid, "texturen", f"Textur {titel} (ambientCG {aid})", "ambientCG (Lennart Demes)", "CC0 1.0",
             "2K (2048²)", "2024", "kern", "standard",
             f"""PBR-Satz für com.dan.surface: Farbe, Normale (OpenGL), Rauheit, Verdeckung, Höhe. Für: {wofuer}.
             Geprüft.""",
             f"https://ambientcg.com/view?id={aid}",
             [(AMBIENTCG.format(id=aid), f"{aid}_2K-JPG.zip")])
