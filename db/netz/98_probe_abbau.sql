-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 98: Probenetze weg (als Schema)
-- Nach der NDM-Funktionsprobe (Entscheidung Phase 3: die Probenetze bleiben
-- nicht stehen, nur LB_NDM_PROBE mit den Ergebnissen).
-- ============================================================================
DECLARE
    PROCEDURE still(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN
    FOR n IN (SELECT NETWORK FROM USER_SDO_NETWORK_METADATA WHERE NETWORK LIKE 'LB\_PROBE\_%' ESCAPE '\') LOOP
        BEGIN SDO_NET.DROP_NETWORK(n.NETWORK); EXCEPTION WHEN OTHERS THEN NULL; END;
    END LOOP;
    FOR t IN (SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'LB\_PROBE\_%' ESCAPE '\') LOOP
        still('DROP TABLE "' || t.TABLE_NAME || '" CASCADE CONSTRAINTS PURGE');
    END LOOP;
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_PROBE\_%' ESCAPE '\';
    COMMIT;
END;
/
