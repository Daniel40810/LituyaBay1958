-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 97: Abbau von LB_NET_WEG (als Schema)
-- Phase 16b: LB_NET_WEG hat ein eigenes Ziel (ant wegdb), weil Laden, Kacheln
-- und BLOBs lange dauern; 99_abbau lässt es stehen. Dieses Skript räumt das Netz,
-- LB_WEG_UMSTIEG, LB_WEG_ROUTE, LB_LANDFLUCHT, die Sichten LB_V_WEG_* und die
-- Raum-Metadaten; Fehler für Dinge, die es noch nicht gibt, werden übergangen.
-- ============================================================================
DECLARE
    PROCEDURE still(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN
    BEGIN SDO_NET.DROP_NETWORK('LB_NET_WEG'); EXCEPTION WHEN OTHERS THEN NULL; END;
    FOR t IN (SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'LB\_NET\_WEG\_%' ESCAPE '\' OR TABLE_NAME LIKE 'LB\_WEG\_%' ESCAPE '\' OR TABLE_NAME = 'LB_LANDFLUCHT') LOOP
        still('DROP TABLE "' || t.TABLE_NAME || '" CASCADE CONSTRAINTS PURGE');
    END LOOP;
    FOR v IN (SELECT VIEW_NAME FROM USER_VIEWS WHERE VIEW_NAME LIKE 'LB\_V\_WEG\_%' ESCAPE '\') LOOP
        still('DROP VIEW ' || v.VIEW_NAME);
    END LOOP;
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_NET\_WEG\_%' ESCAPE '\' OR TABLE_NAME LIKE 'LB\_WEG\_%' ESCAPE '\';
    COMMIT;
END;
/
