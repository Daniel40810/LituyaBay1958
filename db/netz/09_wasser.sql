-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 09: LB_NET_WASSER (als Schema)
-- Phase 16, Werkbuch 7.4: Gewässer und Gletscher der Szene, GERICHTET stromab,
-- HIERARCHISCH in zwei Ebenen. Ebene 1: Bäche ab 0,5 km² (Quelle, Zusammen-
-- fluss, Gletschertor, Seeauslauf, Teilung, Mündung, Stütz je 200 m), Fließ-
-- linien der Gletscher (ITS_LIVE) und die Ziele (Gilbert Inlet, Crillon Inlet,
-- Bucht, Golf von Alaska, Rand der Szene). Ebene 2: Teileinzugsgebiete als
-- Elternknoten (PARENT_NODE_ID der Ebene 1) mit Kanten stromab.
-- Abweichung von 7: Geometrie 2D (2001/2002, SRID 26908); die Höhe steht in
-- HOEHE, weil die Linien der Bäche aus WKT kommen.
-- COST = Fließzeit in s (Bach bei Regen nach Manning, Eis nach ITS_LIVE).
-- Dazu LB_EINZUG (Umrisse der Gebiete), LB_SEE und LB_SENKE.
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_WASSER';
    PROCEDURE ddl(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; END;
    PROCEDURE spalte(tab VARCHAR2, s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || tab || ' ADD (' || s || ')';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1430) THEN RAISE; END IF;
    END;
    PROCEDURE meta(tab VARCHAR2, col VARCHAR2) IS
    BEGIN
        DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME = tab AND COLUMN_NAME = col;
        INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES (tab, col,
            SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005)), 26908);
    END;
BEGIN
    SDO_NET.CREATE_SDO_NETWORK(t, 2, TRUE, FALSE);
    spalte(t || '_NODE$', 'HIERARCHY_LEVEL NUMBER');
    spalte(t || '_NODE$', 'PARENT_NODE_ID NUMBER');
    spalte(t || '_NODE$', 'ART VARCHAR2(20)');
    spalte(t || '_NODE$', 'HOEHE NUMBER');
    spalte(t || '_NODE$', 'FLAECHE_KM2 NUMBER');
    spalte(t || '_NODE$', 'EIS_ANTEIL NUMBER');
    spalte(t || '_LINK$', 'PARENT_LINK_ID NUMBER');
    spalte(t || '_LINK$', 'ART VARCHAR2(20)');
    spalte(t || '_LINK$', 'LAENGE NUMBER');
    spalte(t || '_LINK$', 'GEFAELLE NUMBER');
    spalte(t || '_LINK$', 'FLAECHE_KM2 NUMBER');
    spalte(t || '_LINK$', 'ABFLUSS NUMBER');
    spalte(t || '_LINK$', 'GESCHW NUMBER');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_NET\_WASSER\_%' ESCAPE '\';
    meta(t || '_NODE$', 'GEOMETRY');
    meta(t || '_LINK$', 'GEOMETRY');
    meta(t || '_PATH$', 'GEOMETRY');
    ddl('COMMENT ON TABLE ' || t || '_NODE$ IS ''LituyaBay1958: Wasser (Werkbuch 7.4): Ebene 1 Bachknoten, Eisknoten, Ziele; Ebene 2 Teileinzugsgebiete (PARENT_NODE_ID); FLAECHE_KM2 = Einzugsflaeche''');
    ddl('COMMENT ON TABLE ' || t || '_LINK$ IS ''LituyaBay1958: Wasser: gerichtet stromab; COST = Fliesszeit s (Bach bei Regen 10 mm/h nach Manning, Gletscher nach ITS_LIVE); ABFLUSS m3/s bei Regen''');
    ddl('CREATE TABLE LB_EINZUG (
            NODE_ID           NUMBER CONSTRAINT LB_EINZUG_PK PRIMARY KEY,
            ART               VARCHAR2(20) NOT NULL,
            AUSLAUF_NODE_ID   NUMBER,
            ZIEL              VARCHAR2(40) NOT NULL,
            FLAECHE_EIGEN_KM2 NUMBER NOT NULL,
            FLAECHE_KM2       NUMBER NOT NULL,
            EIS_ANTEIL        NUMBER,
            NAME              VARCHAR2(100),
            UMRISS            SDO_GEOMETRY)');
    ddl('COMMENT ON TABLE LB_EINZUG IS ''LituyaBay1958: Teileinzugsgebiete (Ebene 2 von LB_NET_WASSER); FLAECHE_EIGEN aus 10 m, UMRISS aus 50 m; ZIEL = wohin das Wasser am Ende fliesst''');
    meta('LB_EINZUG', 'UMRISS');
    ddl('CREATE TABLE LB_SEE (
            SEE_ID            NUMBER CONSTRAINT LB_SEE_PK PRIMARY KEY,
            NAME              VARCHAR2(100),
            HOEHE             NUMBER,
            FLAECHE_KM2       NUMBER NOT NULL,
            AUSLAUF_NODE_ID   NUMBER,
            PUNKT             SDO_GEOMETRY)');
    ddl('COMMENT ON TABLE LB_SEE IS ''LituyaBay1958: Seen (Flaechen gleicher Hoehe im IfSAR-Gelaende ab 0,05 km2); PUNKT = Auslauf''');
    meta('LB_SEE', 'PUNKT');
    ddl('CREATE TABLE LB_SENKE (
            SENKE_ID          NUMBER CONSTRAINT LB_SENKE_PK PRIMARY KEY,
            FLAECHE_KM2       NUMBER NOT NULL,
            TIEFE_M           NUMBER NOT NULL,
            VOLUMEN_M3        NUMBER,
            AUF_EIS           CHAR(1) CHECK (AUF_EIS IN (''J'', ''N'')),
            PUNKT             SDO_GEOMETRY)');
    ddl('COMMENT ON TABLE LB_SENKE IS ''LituyaBay1958: abflusslose Senken im Gelaende (aufgefuellt ab 2 m Tiefe und 0,01 km2); PUNKT = tiefste Stelle''');
    meta('LB_SENKE', 'PUNKT');
    COMMIT;
END;
/
