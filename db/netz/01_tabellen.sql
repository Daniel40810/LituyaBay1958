-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 01: Tabellen der Netze (als Schema)
-- Phase 3, Werkbuch 7 und 13: nur, was die Netze brauchen. Alles Weitere aus
-- Abschnitt 13 (LB_QUELLE, LB_BEFUND, LB_ZEUGE …) kommt mit Phase 19.
-- Schlüssel aus Sequenz und Trigger, nie IDENTITY.
-- ============================================================================

-- ---------------------------------------------------------------- LB_NETZ_STAND
CREATE SEQUENCE LB_NETZ_STAND_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_NETZ_STAND (
    STAND_ID     NUMBER        CONSTRAINT LB_NETZ_STAND_PK PRIMARY KEY,
    NETZ         VARCHAR2(30)  NOT NULL,
    INHALT       VARCHAR2(200),
    KNOTEN       NUMBER,
    KANTEN       NUMBER,
    PRUEFSUMME   VARCHAR2(64),
    QUELLE       VARCHAR2(400),
    GELADEN      DATE          DEFAULT SYSDATE,
    GUELTIG      VARCHAR2(400),
    CONSTRAINT LB_NETZ_STAND_UK UNIQUE (NETZ)
)
/
CREATE OR REPLACE TRIGGER LB_NETZ_STAND_BI BEFORE INSERT ON LB_NETZ_STAND FOR EACH ROW
BEGIN
    IF :NEW.STAND_ID IS NULL THEN :NEW.STAND_ID := LB_NETZ_STAND_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_NETZ_STAND IS 'LituyaBay1958: je Netz Zahlen, Pruefsumme der Dateien in daten/netz und Ladedatum (Oracle und Datei passen, wenn die Pruefsumme gleich ist)'
/

-- ---------------------------------------------------------------- LB_NDM_PROBE
CREATE SEQUENCE LB_NDM_PROBE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_NDM_PROBE (
    PROBE_ID     NUMBER         CONSTRAINT LB_NDM_PROBE_PK PRIMARY KEY,
    NR           NUMBER(3)      NOT NULL,
    FAEHIGKEIT   VARCHAR2(200)  NOT NULL,
    PRUEFUNG     VARCHAR2(400),
    ERGEBNIS     VARCHAR2(4000),
    OK           VARCHAR2(1)    CONSTRAINT LB_NDM_PROBE_OK CHECK (OK IN ('J', 'N', '-')),
    WEG          VARCHAR2(10)   CONSTRAINT LB_NDM_PROBE_WEG CHECK (WEG IN ('SQL', 'JAVA')),
    GEPRUEFT     DATE           DEFAULT SYSDATE,
    CONSTRAINT LB_NDM_PROBE_UK UNIQUE (NR, FAEHIGKEIT)
)
/
CREATE OR REPLACE TRIGGER LB_NDM_PROBE_BI BEFORE INSERT ON LB_NDM_PROBE FOR EACH ROW
BEGIN
    IF :NEW.PROBE_ID IS NULL THEN :NEW.PROBE_ID := LB_NDM_PROBE_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_NDM_PROBE IS 'LituyaBay1958: NDM-Funktionsprobe (Werkbuch 2.8): je Faehigkeit Pruefung, Ergebnis, J/N; was fehlt, ist ein Wunsch (F39)'
/

-- Ergebnis einer Probe eintragen oder ersetzen (eigene Transaktion: auch nach Fehlern der Probe bleibt es stehen)
CREATE OR REPLACE PROCEDURE LB_PROBE_SETZEN(p_nr NUMBER, p_faehigkeit VARCHAR2, p_pruefung VARCHAR2, p_ergebnis VARCHAR2, p_ok VARCHAR2,
                                            p_weg VARCHAR2 DEFAULT 'SQL') IS
    PRAGMA AUTONOMOUS_TRANSACTION;
BEGIN
    MERGE INTO LB_NDM_PROBE t
    USING (SELECT p_nr NR, p_faehigkeit FAEHIGKEIT FROM DUAL) s
       ON (t.NR = s.NR AND t.FAEHIGKEIT = s.FAEHIGKEIT)
     WHEN MATCHED THEN UPDATE SET t.PRUEFUNG = p_pruefung, t.ERGEBNIS = SUBSTR(p_ergebnis, 1, 4000), t.OK = p_ok, t.WEG = p_weg, t.GEPRUEFT = SYSDATE
     WHEN NOT MATCHED THEN INSERT (NR, FAEHIGKEIT, PRUEFUNG, ERGEBNIS, OK, WEG) VALUES (p_nr, p_faehigkeit, p_pruefung, SUBSTR(p_ergebnis, 1, 4000), p_ok, p_weg);
    COMMIT;
END;
/

-- ============================================================================
-- Phase 7a (Werkbuch 7.1, 7.2): Ergebnisse der Welle, das Ufer als Messstrecke
-- ============================================================================

-- ---------------------------------------------------------------- LB_ANKUNFT
-- Ankunft der Front je Knoten von LB_NET_WELLE und Kostenart: aus LOD (withinCost von QUELLE) und aus route (Datei)
CREATE TABLE LB_ANKUNFT (
    KOSTEN       VARCHAR2(20)  NOT NULL CONSTRAINT LB_ANKUNFT_KOSTEN CHECK (KOSTEN IN ('T_HEUTE', 'T_1940', 'T_1959', 'T_NICHTLINEAR')),
    NODE_ID      NUMBER        NOT NULL,
    T_LOD        NUMBER,
    T_ROUTE      NUMBER,
    CONSTRAINT LB_ANKUNFT_PK PRIMARY KEY (KOSTEN, NODE_ID)
)
/
COMMENT ON TABLE LB_ANKUNFT IS 'LituyaBay1958: Ankunft der Welle (s nach dem Einschlag) je Knoten von LB_NET_WELLE und Kostenart; T_LOD aus Oracle NDM (LOD), T_ROUTE aus com.dan.route (daten/netz/welle/ankunft.tsv)'
/

-- ---------------------------------------------------------------- LB_ISOCHRONE
CREATE SEQUENCE LB_ISOCHRONE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_ISOCHRONE (
    ISOCHRONE_ID NUMBER        CONSTRAINT LB_ISOCHRONE_PK PRIMARY KEY,
    KOSTEN       VARCHAR2(20)  NOT NULL,
    T            NUMBER        NOT NULL,
    ART          VARCHAR2(10)  NOT NULL CONSTRAINT LB_ISOCHRONE_ART CHECK (ART IN ('KONTUR', 'PUFFER')),
    GEOMETRY     SDO_GEOMETRY
)
/
CREATE OR REPLACE TRIGGER LB_ISOCHRONE_BI BEFORE INSERT ON LB_ISOCHRONE FOR EACH ROW
BEGIN
    IF :NEW.ISOCHRONE_ID IS NULL THEN :NEW.ISOCHRONE_ID := LB_ISOCHRONE_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_ISOCHRONE IS 'LituyaBay1958: Isochronen der Welle je Kostenart und Zeit T (s): KONTUR = Linie aus der Ankunft von LOD (Marching Squares), PUFFER = Flaeche aus fastWithinCostPolygon (F23)'
/

-- ---------------------------------------------------------------- Ufer (LRS)
CREATE TABLE LB_UFER_GEOM (
    GEOM_ID      NUMBER        CONSTRAINT LB_UFER_GEOM_PK PRIMARY KEY,
    NAME         VARCHAR2(60)  NOT NULL,
    RING         VARCHAR2(1)   CONSTRAINT LB_UFER_GEOM_RING CHECK (RING IN ('J', 'N')),
    LAENGE       NUMBER,
    GEOMETRY     SDO_GEOMETRY
)
/
COMMENT ON TABLE LB_UFER_GEOM IS 'LituyaBay1958: Straenge von LB_NET_UFER als LRS-Linien (Mass m in Metern, ab La Chaussee Spit im Uhrzeigersinn); aus LB_UFER_PUNKT gebaut'
/
CREATE TABLE LB_UFER_PUNKT (
    GEOM_ID      NUMBER        NOT NULL,
    SEQ          NUMBER        NOT NULL,
    OST          NUMBER        NOT NULL,
    NORD         NUMBER        NOT NULL,
    M            NUMBER        NOT NULL,
    CONSTRAINT LB_UFER_PUNKT_PK PRIMARY KEY (GEOM_ID, SEQ)
)
/
COMMENT ON TABLE LB_UFER_PUNKT IS 'LituyaBay1958: Punkte der Ufer-Straenge (daten/netz/ufer/punkte.tsv), Mass m aus der Rechnung in Java'
/
CREATE SEQUENCE LB_AUFLAUF_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_AUFLAUF (
    AUFLAUF_ID   NUMBER        CONSTRAINT LB_AUFLAUF_PK PRIMARY KEY,
    GEOM_ID      NUMBER        NOT NULL,
    M            NUMBER        NOT NULL,
    HOEHE        NUMBER        NOT NULL,
    JAHR         NUMBER(4)     NOT NULL,
    NAME         VARCHAR2(200),
    QUELLE       VARCHAR2(200),
    ART          VARCHAR2(60),
    GEOMETRY     SDO_GEOMETRY
)
/
CREATE OR REPLACE TRIGGER LB_AUFLAUF_BI BEFORE INSERT ON LB_AUFLAUF FOR EACH ROW
BEGIN
    IF :NEW.AUFLAUF_ID IS NULL THEN :NEW.AUFLAUF_ID := LB_AUFLAUF_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_AUFLAUF IS 'LituyaBay1958: Auflauf als Punkt-Ereignis auf LB_NET_UFER (Strang, Mass m, Hoehe ueber dem Wasser, Jahr, Quelle); Lage aus SDO_LRS.LOCATE_PT'
/
CREATE SEQUENCE LB_SPURLINIE_LRS_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_SPURLINIE_LRS (
    SPUR_ID      NUMBER        CONSTRAINT LB_SPURLINIE_LRS_PK PRIMARY KEY,
    GEOM_ID      NUMBER        NOT NULL,
    M1           NUMBER        NOT NULL,
    M2           NUMBER        NOT NULL,
    JAHR         NUMBER(4)     NOT NULL,
    QUELLE       VARCHAR2(200)
)
/
CREATE OR REPLACE TRIGGER LB_SPURLINIE_LRS_BI BEFORE INSERT ON LB_SPURLINIE_LRS FOR EACH ROW
BEGIN
    IF :NEW.SPUR_ID IS NULL THEN :NEW.SPUR_ID := LB_SPURLINIE_LRS_SEQ.NEXTVAL; END IF;
END;
/
CREATE TABLE LB_SPURLINIE_STUETZ (
    SPUR_ID      NUMBER        NOT NULL CONSTRAINT LB_SPURLINIE_STUETZ_FK REFERENCES LB_SPURLINIE_LRS (SPUR_ID) ON DELETE CASCADE,
    M            NUMBER        NOT NULL,
    HOEHE        NUMBER        NOT NULL,
    CONSTRAINT LB_SPURLINIE_STUETZ_PK PRIMARY KEY (SPUR_ID, M)
)
/
COMMENT ON TABLE LB_SPURLINIE_LRS IS 'LituyaBay1958: Spurlinie als Linien-Ereignis (m1 bis m2, Jahr) mit Hoehen an Stuetzstellen (LB_SPURLINIE_STUETZ); gefuellt ab dem Schritt spurlinie'
/
-- Raum-Metadaten der Tabellen mit Geometrie (2D; das Ufer mit Mass M)
BEGIN
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME IN ('LB_ISOCHRONE', 'LB_UFER_GEOM', 'LB_AUFLAUF');
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES ('LB_ISOCHRONE', 'GEOMETRY',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005)), 26908);
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES ('LB_AUFLAUF', 'GEOMETRY',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005)), 26908);
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES ('LB_UFER_GEOM', 'GEOMETRY',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                      SDO_DIM_ELEMENT('M', 0, 100000, 0.005)), 26908);
    COMMIT;
END;
/
