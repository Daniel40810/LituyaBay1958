-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 07: LB_NET_SEE (als Schema)
-- Phase 15a, Werkbuch 7.3: das Fahrwasser eines Trollers in der Bucht und durch
-- das Tor. Räumlich (3001/3002, SRID 26908), GERICHTET: je Richtung eine Kante,
-- weil der Gezeitenstrom die Fahrt hinaus und herein verschieden lang macht.
-- Knoten aus LB_NET_WELLE: 40 m am Tor, 100 m auf See, 200 m in der Bucht; nur
-- Wasser, das für 1,65 m Tiefgang + 0,5 m reicht (Tiefe 1940 + Gezeit beim Stoß).
-- COST = Länge / 8 kn (ohne Strom); die Kosten nach der Zeit rechnet ein
-- LinkCostCalculator (netz.SeeLod) aus LAENGE, RICHTUNG_* und STROM_FAKTOR. Die
-- Front je Knoten (FRONT_LINEAR, FRONT_EREIGNIS: s nach dem Stoß) prüft eine
-- LODNetworkConstraint. Dazu LB_FLUCHT: „Wer hätte entkommen können?“
-- ============================================================================
DECLARE
    t VARCHAR2(30) := 'LB_NET_SEE';
    PROCEDURE ddl(s VARCHAR2) IS BEGIN EXECUTE IMMEDIATE s; END;
    PROCEDURE spalte(tab VARCHAR2, s VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || tab || ' ADD (' || s || ')';
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE NOT IN (-1430) THEN RAISE; END IF;
    END;
BEGIN
    SDO_NET.CREATE_SDO_NETWORK(t, 1, TRUE, FALSE);
    spalte(t || '_NODE$', 'ABSTAND NUMBER(4)');
    spalte(t || '_NODE$', 'TIEFE_1940 NUMBER');
    spalte(t || '_NODE$', 'STROM_FAKTOR NUMBER');
    spalte(t || '_NODE$', 'DRAUSSEN NUMBER');
    spalte(t || '_NODE$', 'FRONT_LINEAR NUMBER');
    spalte(t || '_NODE$', 'FRONT_EREIGNIS NUMBER');
    spalte(t || '_LINK$', 'LAENGE NUMBER');
    spalte(t || '_LINK$', 'RICHTUNG_OST NUMBER');
    spalte(t || '_LINK$', 'RICHTUNG_NORD NUMBER');
    spalte(t || '_LINK$', 'STROM_FAKTOR NUMBER');
    DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME LIKE 'LB\_NET\_SEE\_%' ESCAPE '\';
    FOR x IN (SELECT COLUMN_VALUE AS N FROM TABLE(SYS.ODCIVARCHAR2LIST(t || '_NODE$', t || '_LINK$', t || '_PATH$'))) LOOP
        INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES (x.N, 'GEOMETRY',
            SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                          SDO_DIM_ELEMENT('Z', -500, 5000, 0.005)), 26908);
    END LOOP;
    ddl('COMMENT ON TABLE ' || t || '_NODE$ IS ''LituyaBay1958: See (Werkbuch 7.3): Fahrwasser 40/100/200 m; DRAUSSEN = m jenseits des Tors; FRONT_* = Ankunft der Front, s nach dem Stoss''');
    ddl('COMMENT ON TABLE ' || t || '_LINK$ IS ''LituyaBay1958: See: gerichtet; COST = LAENGE / 8 kn; Kosten nach der Zeit mit dem Gezeitenstrom im Tor mal STROM_FAKTOR (netz.SeeLod)''');
    ddl('CREATE TABLE LB_FLUCHT (
            BOOT          VARCHAR2(20) NOT NULL,
            FRONT         VARCHAR2(10) NOT NULL,
            ABFAHRT_S     NUMBER NOT NULL,
            ENTKOMMEN     CHAR(1) NOT NULL CHECK (ENTKOMMEN IN (''J'', ''N'')),
            ANKUNFT_S     NUMBER,
            RESERVE_S     NUMBER,
            ZIEL_NODE_ID  NUMBER,
            SPAETESTE     CHAR(1) DEFAULT ''N'' NOT NULL CHECK (SPAETESTE IN (''J'', ''N'')),
            WEG           SDO_GEOMETRY)');
    ddl('COMMENT ON TABLE LB_FLUCHT IS ''LituyaBay1958: Wer haette entkommen koennen? Abfahrt s nach dem Stoss, entkommen = 1 km jenseits des Tors, jeder Knoten vor der Front; SPAETESTE = J: die spaeteste Abfahrt mit ihrem Weg (Z = Ankunft in s)''');
    INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID) VALUES ('LB_FLUCHT', 'WEG',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('X', 220000, 480000, 0.005), SDO_DIM_ELEMENT('Y', 6385000, 6625000, 0.005),
                      SDO_DIM_ELEMENT('Z', -100000, 100000, 0.005)), 26908);
    COMMIT;
END;
/
