-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 02: Netze (als Schema)
-- Phase 3: LB_NET_HERKUNFT (Werkbuch 7.7), logisch und gerichtet, ohne
-- Geometrie: Quelle -> Rohdatei -> Schritt der Aufbereitung -> Ausgabe in
-- daten/ -> Szenenobjekt; dazu Befunde. NODE_ID und LINK_ID sind die Nummern
-- aus daten/netz/herkunft. COST = 1 je Kante (Zahl der Schritte).
-- Die räumlichen Netze LB_NET_WELLE usw. kommen mit Phase 7a.
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
