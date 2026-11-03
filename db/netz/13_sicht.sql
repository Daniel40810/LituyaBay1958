-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 13: LB_NET_SICHT (als Schema)
-- Phase 17, Werkbuch 7.6: Sicht und Kamera, UNGERICHTET, eine Ebene.
-- Knoten: Kamerapunkte in Augenhöhe (Ansichten, Schaustücke, Orte, Anker-
-- plätze, Aufläufe, Ufer, Gipfel, Kamerapunkte über dem Wasser); ART, MOTIV
-- (J = im Rundflug), BLICK und NEIGUNG (Grad, nur Ansichten und Schaustücke),
-- NAME voll, NODE_NAME auf 30 Zeichen gekürzt; LINK_LEVEL = 1 (wie LB_NET_SEE).
-- Kanten: freie Sicht im Gelände (Strahl mit Erdkrümmung, k = 0,13),
-- COST = Flugzeit der Kamera in s, LAENGE waagerecht in m, FREI_M = kleinster
-- Abstand des Strahls über dem Gelände.
-- Geometrie 3D (3001/3002, SRID 26908): Punkte und Linien in Augenhöhe.
-- Dazu LB_SICHT_FAHRT (Rundflug und Kamerafahrt Knoten für Knoten).
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_SICHT';
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
            SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                          SDO_DIM_ELEMENT('Z', -500, 20000, 0.005)), 26908);
    END;
BEGIN
    SDO_NET.CREATE_SDO_NETWORK(t, 1, FALSE, FALSE);
    spalte(t || '_NODE$', 'ART VARCHAR2(20)');
    spalte(t || '_NODE$', 'NAME VARCHAR2(100)');
    spalte(t || '_NODE$', 'HOEHE NUMBER');
    spalte(t || '_NODE$', 'GRUND NUMBER');
    spalte(t || '_NODE$', 'BLICK NUMBER');
    spalte(t || '_NODE$', 'NEIGUNG NUMBER');
    spalte(t || '_NODE$', 'MOTIV CHAR(1)');
    spalte(t || '_LINK$', 'LAENGE NUMBER');
    spalte(t || '_LINK$', 'FREI_M NUMBER');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_NET\_SICHT\_%' ESCAPE '\';
    meta(t || '_NODE$', 'GEOMETRY');
    meta(t || '_LINK$', 'GEOMETRY');
    meta(t || '_PATH$', 'GEOMETRY');
    ddl('COMMENT ON TABLE ' || t || '_NODE$ IS ''LituyaBay1958: Sicht (Werkbuch 7.6): Kamerapunkte in Augenhoehe; ART, MOTIV = J im Rundflug''');
    ddl('COMMENT ON TABLE ' || t || '_LINK$ IS ''LituyaBay1958: Sicht: ungerichtet, freie Sicht im Gelaende mit Erdkruemmung; COST = Flugzeit der Kamera s; FREI_M = kleinster Abstand ueber Grund''');
    ddl('CREATE TABLE LB_SICHT_FAHRT (
            FAHRT    VARCHAR2(20) NOT NULL,
            SEQ      NUMBER NOT NULL,
            NODE_ID  NUMBER NOT NULL,
            ZEIT_S   NUMBER NOT NULL,
            MOTIV    VARCHAR2(100),
            PUNKT    SDO_GEOMETRY,
            CONSTRAINT LB_SICHT_FAHRT_PK PRIMARY KEY (FAHRT, SEQ))');
    ddl('COMMENT ON TABLE LB_SICHT_FAHRT IS ''LituyaBay1958: Rundflug (tsp ueber die Motive) und Kamerafahrt (Spannbaum) Knoten fuer Knoten; ZEIT_S = Flugzeit seit dem Start''');
    meta('LB_SICHT_FAHRT', 'PUNKT');
    COMMIT;
END;
/
