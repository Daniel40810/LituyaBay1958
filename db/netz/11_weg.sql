-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 11: LB_NET_WEG (als Schema)
-- Phase 16a, Werkbuch 7.5: Wege über Gelände, GERICHTET (je Richtung eine
-- Kante: Tobler ist bergauf und bergab verschieden), HIERARCHISCH: Ebene 1 das
-- 10-m-Gitter im Kern, Ebene 2 das 50-m-Gitter der Szene (PARENT_NODE_ID).
-- LOGISCH: Millionen Kanten ohne Linie; die Lage der Knoten steht in LAGE
-- (3001, SRID 26908, Z = Höhe), gefüllt in 12 aus OST, NORD, HOEHE.
-- COST = Gehzeit in s (Tobler × Zuschlag der Geländeklasse).
-- Dazu LB_WEG_UMSTIEG (Fahrwasser LB_NET_SEE → Strand) und LB_WEG_ROUTE
-- (Washburn 1934 und die Landwege der Flucht, Punkte mit Höhe und Zeit),
-- seit 16b LB_LANDFLUCHT. Vorher räumt 97_weg_abbau.sql (ant wegdb).
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_WEG';
    PROCEDURE ddl(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; END;
    PROCEDURE spalte(tab VARCHAR2, s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || tab || ' ADD (' || s || ')';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1430) THEN RAISE; END IF;
    END;
BEGIN
    SDO_NET.CREATE_LOGICAL_NETWORK(t, 2, TRUE, FALSE);
    spalte(t || '_NODE$', 'HIERARCHY_LEVEL NUMBER');
    spalte(t || '_NODE$', 'PARENT_NODE_ID NUMBER');
    spalte(t || '_NODE$', 'KLASSE NUMBER(2)');
    spalte(t || '_NODE$', 'HOEHE NUMBER');
    spalte(t || '_NODE$', 'OST NUMBER');
    spalte(t || '_NODE$', 'NORD NUMBER');
    spalte(t || '_NODE$', 'LAGE SDO_GEOMETRY');
    spalte(t || '_LINK$', 'PARENT_LINK_ID NUMBER');
    ddl('ALTER TABLE ' || t || '_NODE$ NOLOGGING');
    ddl('ALTER TABLE ' || t || '_LINK$ NOLOGGING');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME = t || '_NODE$';
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES (t || '_NODE$', 'LAGE',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                      SDO_DIM_ELEMENT('Z', -500, 5000, 0.005)), 26908);
    ddl('COMMENT ON TABLE ' || t || '_NODE$ IS ''LituyaBay1958: Weg (Werkbuch 7.5): Ebene 1 Kern 10 m, Ebene 2 Szene 50 m (PARENT_NODE_ID); KLASSE wie daten/klassen/KLASSEN.txt; LAGE Punkt mit Hoehe''');
    ddl('COMMENT ON TABLE ' || t || '_LINK$ IS ''LituyaBay1958: Weg: gerichtet, 8 Nachbarn; COST = Gehzeit s nach Tobler mal Zuschlag; Ebenen ohne Kanten zueinander''');
    ddl('CREATE TABLE LB_WEG_UMSTIEG (
            SEE_NODE_ID   NUMBER NOT NULL,
            WEG_NODE_ID   NUMBER NOT NULL,
            ABSTAND       NUMBER,
            CONSTRAINT LB_WEG_UMSTIEG_PK PRIMARY KEY (SEE_NODE_ID))');
    ddl('COMMENT ON TABLE LB_WEG_UMSTIEG IS ''LituyaBay1958: Umstieg vom Fahrwasser (LB_NET_SEE) zum naechsten Strand in LB_NET_WEG (Ebene 1) bis 300 m''');
    ddl('CREATE TABLE LB_WEG_ROUTE (
            ROUTE         VARCHAR2(40) NOT NULL,
            SEQ           NUMBER NOT NULL,
            NODE_ID       NUMBER NOT NULL,
            HOEHE         NUMBER,
            ZEIT_S        NUMBER,
            KLASSE        VARCHAR2(20),
            WEGPUNKT      VARCHAR2(60),
            PUNKT         SDO_GEOMETRY,
            CONSTRAINT LB_WEG_ROUTE_PK PRIMARY KEY (ROUTE, SEQ))');
    ddl('COMMENT ON TABLE LB_WEG_ROUTE IS ''LituyaBay1958: nachgerechnete Wege (WASHBURN_1934: Crillon Lake, South Crillon Glacier, Mount Crillon) als Punkte mit Hoehe und Gehzeit''');
    ddl('CREATE TABLE LB_LANDFLUCHT (
            BOOT          VARCHAR2(20) NOT NULL,
            ABFAHRT_S     NUMBER NOT NULL,
            GERETTET      CHAR(1) NOT NULL CHECK (GERETTET IN (''J'', ''N'')),
            SPAETESTE     CHAR(1) DEFAULT ''N'' NOT NULL CHECK (SPAETESTE IN (''J'', ''N'')),
            SEE_NODE_ID   NUMBER,
            WEG_NODE_ID   NUMBER,
            AN_LAND_S     NUMBER,
            SICHER_NODE_ID NUMBER,
            SICHER_S      NUMBER,
            HOEHE         NUMBER,
            RESERVE_S     NUMBER)');
    ddl('COMMENT ON TABLE LB_LANDFLUCHT IS ''LituyaBay1958: Wer haette sich an Land retten koennen? Boot durch LB_NET_SEE zum Strand, 3 min anlanden, zu Fuss bis 10 m ueber den Auflauf der Vorrechnung, jeder Knoten vor der Front; Zeiten s nach dem Stoss''');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME = 'LB_WEG_ROUTE';
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES ('LB_WEG_ROUTE', 'PUNKT',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                      SDO_DIM_ELEMENT('Z', -500, 5000, 0.005)), 26908);
    COMMIT;
END;
/
