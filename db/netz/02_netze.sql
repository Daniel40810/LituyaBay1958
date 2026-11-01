-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 02: Netze (als Schema)
-- Phase 3: LB_NET_HERKUNFT (Werkbuch 7.7), logisch und gerichtet, ohne
-- Geometrie: Quelle -> Rohdatei -> Schritt der Aufbereitung -> Ausgabe in
-- daten/ -> Szenenobjekt; dazu Befunde. NODE_ID und LINK_ID sind die Nummern
-- aus daten/netz/herkunft. COST = 1 je Kante (Zahl der Schritte).
-- Phase 7a: LB_NET_WELLE (räumlich, ungerichtet) und LB_NET_UFER (LRS), unten.
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_HERKUNFT';
    PROCEDURE ddl(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; END;
    PROCEDURE spalte(tab VARCHAR2, s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || tab || ' ADD (' || s || ')';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1430) THEN RAISE; END IF;   -- Spalte gibt es schon
    END;
BEGIN
    SDO_NET.CREATE_LOGICAL_NETWORK(t, 1, TRUE, FALSE);
    ddl('ALTER TABLE ' || t || '_NODE$ MODIFY (NODE_NAME VARCHAR2(400))');
    spalte(t || '_NODE$', 'ART VARCHAR2(20)');
    spalte(t || '_NODE$', 'PFAD VARCHAR2(400)');
    spalte(t || '_NODE$', 'BYTES NUMBER');
    spalte(t || '_NODE$', 'SHA256 VARCHAR2(64)');
    spalte(t || '_NODE$', 'QUELLE VARCHAR2(60)');
    ddl('ALTER TABLE ' || t || '_LINK$ MODIFY (LINK_NAME VARCHAR2(200))');
    spalte(t || '_LINK$', 'ART VARCHAR2(20)');
    ddl('COMMENT ON TABLE ' || t || '_NODE$ IS ''LituyaBay1958: Herkunft (Werkbuch 7.7): ART = QUELLE, ROHDATEI, SCHRITT, AUSGABE, BEFUND, OBJEKT''');
    ddl('COMMENT ON TABLE ' || t || '_LINK$ IS ''LituyaBay1958: Herkunft, gerichtet von der Quelle zum Szenenobjekt; COST = 1''');
    COMMIT;
END;
/

-- ============================================================================
-- Phase 7a, Werkbuch 7.1: LB_NET_WELLE. Räumlich mit Höhe (3001/3002, SRID 26908),
-- ungerichtet. Knoten: Zellen zu 20 m (daten/netz/welle/knoten.tsv) und QUELLE;
-- Kanten: 16 Nachbarn und QUELLE zu den Knoten der Einschlaglinie. COST =
-- T_HEUTE; die vier Kosten T_HEUTE, T_1940, T_1959, T_NICHTLINEAR (s) stehen als
-- Benutzerdaten (Kategorie 0) in LINK$ und kommen über GENERATE_PARTITION_BLOBS
-- in die Partitionen, damit ein LinkCostCalculator sie lesen kann (F22).
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_WELLE';
    PROCEDURE ddl(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; END;
    PROCEDURE spalte(tab VARCHAR2, s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || tab || ' ADD (' || s || ')';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1430) THEN RAISE; END IF;
    END;
BEGIN
    SDO_NET.CREATE_SDO_NETWORK(t, 1, FALSE, FALSE);
    spalte(t || '_NODE$', 'ART VARCHAR2(12)');
    spalte(t || '_NODE$', 'ZELLE_I NUMBER(5)');
    spalte(t || '_NODE$', 'ZELLE_J NUMBER(5)');
    spalte(t || '_NODE$', 'TIEFE_HEUTE NUMBER');
    spalte(t || '_NODE$', 'TIEFE_1940 NUMBER');
    spalte(t || '_NODE$', 'TIEFE_1959 NUMBER');
    spalte(t || '_NODE$', 'AMPLITUDE NUMBER');
    spalte(t || '_LINK$', 'LAENGE NUMBER');
    spalte(t || '_LINK$', 'T_HEUTE NUMBER');
    spalte(t || '_LINK$', 'T_1940 NUMBER');
    spalte(t || '_LINK$', 'T_1959 NUMBER');
    spalte(t || '_LINK$', 'T_NICHTLINEAR NUMBER');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_NET\_WELLE\_%' ESCAPE '\';
    FOR x IN (SELECT COLUMN_VALUE AS N FROM TABLE(SYS.ODCIVARCHAR2LIST(t || '_NODE$', t || '_LINK$', t || '_PATH$'))) LOOP
        INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES (x.N, 'GEOMETRY',
            SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                          SDO_DIM_ELEMENT('Z', -500, 5000, 0.005)), 26908);
    END LOOP;
    -- Benutzerdaten der Kanten (Kategorie 0) für LOD; geht das nicht, rechnet LOD mit den Kosten aus der Datei (Probe 26)
    BEGIN
        DELETE FROM USER_SDO_NETWORK_USER_DATA WHERE NETWORK = t;
        FOR x IN (SELECT COLUMN_VALUE AS N FROM TABLE(SYS.ODCIVARCHAR2LIST('T_HEUTE', 'T_1940', 'T_1959', 'T_NICHTLINEAR'))) LOOP
            INSERT INTO USER_SDO_NETWORK_USER_DATA (NETWORK, TABLE_TYPE, DATA_NAME, DATA_TYPE) VALUES (t, 'LINK', x.N, 'NUMBER');
        END LOOP;
    EXCEPTION WHEN OTHERS THEN
        LB_PROBE_SETZEN(26, 'LinkCostCalculator mit Benutzerdaten (LB_NET_WELLE)', 'USER_SDO_NETWORK_USER_DATA für T_*', SQLERRM, 'N');
    END;
    ddl('COMMENT ON TABLE ' || t || '_NODE$ IS ''LituyaBay1958: Welle (Werkbuch 7.1): Zellen zu 20 m ueber dem Wasser der Bucht und 1 km See, ART = WASSER, EINSCHLAG, QUELLE; Tiefen unter Mittelwasser''');
    ddl('COMMENT ON TABLE ' || t || '_LINK$ IS ''LituyaBay1958: Welle: 16 Nachbarn, ungerichtet; COST = T_HEUTE; T_* Laufzeit in s je Grund (linear) und nichtlinear''');
    COMMIT;
END;
/
-- ============================================================================
-- Phase 7a, Werkbuch 7.2: LB_NET_UFER, LRS. Geometrie in LB_UFER_GEOM (Maß in
-- Metern); Knoten und Kanten verweisen mit GEOM_ID und Maß darauf. Knoten alle
-- 500 m und an den Enden, Cenotaph Island als Ring.
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_UFER';
    PROCEDURE spalte(tab VARCHAR2, s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || tab || ' ADD (' || s || ')';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1430) THEN RAISE; END IF;
    END;
BEGIN
    SDO_NET.CREATE_LRS_NETWORK(t, 'LB_UFER_GEOM', 'GEOMETRY', 1, FALSE, FALSE);
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME = t || '_PATH$';
    -- Befund Phase 3: VALIDATE_NETWORK verlangt Raum-Metadaten auch für die Pfadtabelle
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES (t || '_PATH$', 'GEOMETRY',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                      SDO_DIM_ELEMENT('M', 0, 100000, 0.005)), 26908);
    spalte(t || '_NODE$', 'ORT VARCHAR2(60)');
    COMMIT;
END;
/
