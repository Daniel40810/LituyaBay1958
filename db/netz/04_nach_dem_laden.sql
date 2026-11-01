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
-- Partitionen. Befund Phase 3: SDO_NET.LOGICAL_PARTITION bricht auf 21c bei LB_NET_HERKUNFT mit ORA-06502 in
-- MDSYS.SDO_NET ab. Darum: erst mit 5000, dann mit 1000 Knoten je Partition versuchen; geht beides nicht, eine
-- Partition von Hand (alle Knoten in Partition 1; bei unter 5000 Knoten wäre das auch das Ergebnis von
-- LOGICAL_PARTITION). Was gelaufen ist, steht in LB_NDM_PROBE unter Nr 23.
DECLARE
    weg VARCHAR2(4000);
    n NUMBER;
    PROCEDURE x(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; END;
    PROCEDURE weg_damit IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP TABLE LB_NET_HERKUNFT_PART$ PURGE';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
BEGIN
    BEGIN
        SDO_NET.LOGICAL_PARTITION('LB_NET_HERKUNFT', 'LB_NET_HERKUNFT_PART$', 5000, 'DATA_PUMP_DIR', 'lb_netz.log', 'a', 1);
        weg := 'LOGICAL_PARTITION (5000) ja';
    EXCEPTION WHEN OTHERS THEN
        weg := 'LOGICAL_PARTITION (5000) nein: ' || SQLERRM;
        weg_damit;
        BEGIN
            SDO_NET.LOGICAL_PARTITION('LB_NET_HERKUNFT', 'LB_NET_HERKUNFT_PART$', 1000, 'DATA_PUMP_DIR', 'lb_netz.log', 'a', 1);
            weg := weg || '; LOGICAL_PARTITION (1000) ja';
        EXCEPTION WHEN OTHERS THEN
            weg := weg || '; (1000) nein: ' || SQLERRM || '; eine Partition von Hand';
            weg_damit;
            x('CREATE TABLE LB_NET_HERKUNFT_PART$ (NODE_ID NUMBER PRIMARY KEY, PARTITION_ID NUMBER, LINK_LEVEL NUMBER)');
            x('INSERT INTO LB_NET_HERKUNFT_PART$ (NODE_ID, PARTITION_ID, LINK_LEVEL) SELECT NODE_ID, 1, 1 FROM LB_NET_HERKUNFT_NODE$');
        END;
    END;
    UPDATE USER_SDO_NETWORK_METADATA
       SET PARTITION_TABLE_NAME = 'LB_NET_HERKUNFT_PART$'
     WHERE NETWORK = 'LB_NET_HERKUNFT';
    COMMIT;
    SDO_NET.GENERATE_PARTITION_BLOBS('LB_NET_HERKUNFT', 1, 'LB_NET_HERKUNFT_PBLOB$', FALSE, TRUE, 'DATA_PUMP_DIR', 'lb_netz.log', 'a');
    UPDATE USER_SDO_NETWORK_METADATA
       SET PARTITION_BLOB_TABLE_NAME = NVL(PARTITION_BLOB_TABLE_NAME, 'LB_NET_HERKUNFT_PBLOB$')
     WHERE NETWORK = 'LB_NET_HERKUNFT';
    COMMIT;
    EXECUTE IMMEDIATE 'SELECT COUNT(DISTINCT PARTITION_ID) FROM LB_NET_HERKUNFT_PART$' INTO n;
    LB_PROBE_SETZEN(23, 'Partition LB_NET_HERKUNFT', 'LOGICAL_PARTITION, sonst von Hand; GENERATE_PARTITION_BLOBS',
                    weg || '; ' || n || ' Partition(en), BLOBs erzeugt', CASE WHEN weg LIKE 'LOGICAL_PARTITION (5000) ja%' THEN 'J' ELSE 'N' END);
END;
/
