-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · db/szene 99: Abbau (als Schema)
-- Löscht alle Tabellen, Sequenzen, Sichten und Raum-Metadaten von db/szene,
-- auch die gesicherten Sitzungen, eigenen Regiebücher und Fotos.
-- Nur mit ant szenedb -Dneu=ja; Fehler für Dinge, die es nicht gibt, werden übergangen.
-- ============================================================================
DECLARE
    PROCEDURE still(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN
    FOR v IN (SELECT COLUMN_VALUE AS N FROM TABLE(SYS.ODCIVARCHAR2LIST('LB_V_BEFUND', 'LB_V_BEFUND_ART', 'LB_V_ZEUGE', 'LB_V_BOOT_BAHN', 'LB_V_SCHNAPPSCHUSS',
                                                                  'LB_V_LAUF_VERGLEICH', 'LB_V_SITZUNG'))) LOOP
        still('DROP VIEW ' || v.N);
    END LOOP;
    FOR t IN (SELECT COLUMN_VALUE AS N FROM TABLE(SYS.ODCIVARCHAR2LIST('LB_SCHNAPPSCHUSS', 'LB_SITZUNG', 'LB_TEXTUR', 'LB_KAMERA_FAHRT', 'LB_SZENARIO_SCHRITT', 'LB_SZENARIO',
                                                                  'LB_BOOT_BAHN', 'LB_BOOT_LAGE', 'LB_EREIGNIS_LAUF', 'LB_BOOT', 'LB_ZEUGE', 'LB_BEFUND_QUELLE',
                                                                  'LB_BEFUND', 'LB_QUELLE'))) LOOP
        still('DROP TABLE ' || t.N || ' CASCADE CONSTRAINTS PURGE');
        still('DROP SEQUENCE ' || t.N || '_SEQ');
    END LOOP;
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME IN ('LB_BEFUND', 'LB_ZEUGE', 'LB_BOOT', 'LB_BOOT_BAHN', 'LB_KAMERA_FAHRT', 'LB_SCHNAPPSCHUSS', 'LB_SITZUNG');
    COMMIT;
END;
/
