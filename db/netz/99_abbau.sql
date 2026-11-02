-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 99: Abbau (als Schema)
-- Löscht Netze, Sichten, Tabellen und Probenetze von db/netz (seit Phase 7a
-- auch Ankunft, Isochronen und Ufer, seit 15a LB_NET_SEE und LB_FLUCHT). ant netzdb ruft
-- es vor dem Neuaufbau auf; Fehler für Dinge, die es noch nicht gibt, werden
-- übergangen.
-- ============================================================================
DECLARE
    PROCEDURE still(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN
    FOR n IN (SELECT NETWORK FROM USER_SDO_NETWORK_METADATA WHERE NETWORK LIKE 'LB\_%' ESCAPE '\') LOOP
        BEGIN SDO_NET.DROP_NETWORK(n.NETWORK); EXCEPTION WHEN OTHERS THEN NULL; END;
    END LOOP;
    FOR t IN (SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'LB\_NET\_%' ESCAPE '\' OR TABLE_NAME LIKE 'LB\_PROBE\_%' ESCAPE '\') LOOP
        still('DROP TABLE "' || t.TABLE_NAME || '" CASCADE CONSTRAINTS PURGE');
    END LOOP;
    FOR v IN (SELECT VIEW_NAME FROM USER_VIEWS WHERE VIEW_NAME LIKE 'LB\_V\_%' ESCAPE '\') LOOP
        still('DROP VIEW ' || v.VIEW_NAME);
    END LOOP;
    -- Phase 7a: Ergebnisse und Ufer
    FOR t IN (SELECT COLUMN_VALUE AS N FROM TABLE(SYS.ODCIVARCHAR2LIST('LB_SPURLINIE_STUETZ', 'LB_SPURLINIE_LRS', 'LB_AUFLAUF', 'LB_UFER_PUNKT', 'LB_UFER_GEOM',
                                                                  'LB_ISOCHRONE', 'LB_ANKUNFT', 'LB_FLUCHT'))) LOOP
        still('DROP TABLE ' || t.N || ' CASCADE CONSTRAINTS PURGE');
    END LOOP;
    still('DROP SEQUENCE LB_ISOCHRONE_SEQ');
    still('DROP SEQUENCE LB_AUFLAUF_SEQ');
    still('DROP SEQUENCE LB_SPURLINIE_LRS_SEQ');
    still('DROP PROCEDURE LB_PROBE_SETZEN');
    still('DROP TABLE LB_NDM_PROBE PURGE');
    still('DROP TABLE LB_NETZ_STAND PURGE');
    still('DROP SEQUENCE LB_NDM_PROBE_SEQ');
    still('DROP SEQUENCE LB_NETZ_STAND_SEQ');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_%' ESCAPE '\';
    COMMIT;
END;
/
