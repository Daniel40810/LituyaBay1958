-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 04: nach dem Laden (als Schema)
-- Indizes, Partitionen und Partition-BLOBs für LOD (F26 A,
-- oracle.spatial.network.lod mit dem Treiber 21 aus lib/lod). Logisches Netz:
-- SDO_NET.LOGICAL_PARTITION. Protokoll in DATA_PUMP_DIR (lb_netz.log).
-- ============================================================================
-- Indizes; hat SDO_NET schon einen auf der Spalte (ORA-01408) oder gibt es ihn (ORA-00955), bleibt es dabei
DECLARE
    PROCEDURE idx(s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE s;
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1408, -955) THEN RAISE; END IF;
    END;
BEGIN
    idx('CREATE INDEX LB_NET_HERKUNFT_LINK_S ON LB_NET_HERKUNFT_LINK$ (START_NODE_ID)');
    idx('CREATE INDEX LB_NET_HERKUNFT_LINK_E ON LB_NET_HERKUNFT_LINK$ (END_NODE_ID)');
    idx('CREATE INDEX LB_NET_HERKUNFT_NODE_A ON LB_NET_HERKUNFT_NODE$ (ART)');
END;
/
BEGIN
    SDO_NET.LOGICAL_PARTITION('LB_NET_HERKUNFT', 'LB_NET_HERKUNFT_PART$', 5000, 'DATA_PUMP_DIR', 'lb_netz.log', 'a', 1);
    SDO_NET.GENERATE_PARTITION_BLOBS('LB_NET_HERKUNFT', 1, 'LB_NET_HERKUNFT_PBLOB$', FALSE, TRUE, 'DATA_PUMP_DIR', 'lb_netz.log', 'a');
    UPDATE USER_SDO_NETWORK_METADATA
       SET PARTITION_TABLE_NAME = NVL(PARTITION_TABLE_NAME, 'LB_NET_HERKUNFT_PART$'),
           PARTITION_BLOB_TABLE_NAME = NVL(PARTITION_BLOB_TABLE_NAME, 'LB_NET_HERKUNFT_PBLOB$')
     WHERE NETWORK = 'LB_NET_HERKUNFT';
    COMMIT;
END;
/
