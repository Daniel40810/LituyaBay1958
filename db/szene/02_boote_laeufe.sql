-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · db/szene 02: Boote und Läufe (als Schema)
-- Phase 19, Werkbuch 13.1: die drei Boote, die Läufe des Ereignisses
-- (vorgerechnet und Echtzeit) mit Kennzahlen, die Lage der Boote je Sekunde
-- und ihre Bahn als 3D-Linie (LB_BOOT_BAHN, für Sichten und QGIS).
-- ============================================================================

-- ---------------------------------------------------------------- LB_BOOT
CREATE SEQUENCE LB_BOOT_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_BOOT (
    BOOT_ID      NUMBER          CONSTRAINT LB_BOOT_PK PRIMARY KEY,
    NAME         VARCHAR2(30)    NOT NULL CONSTRAINT LB_BOOT_UK UNIQUE,
    TYP          VARCHAR2(40),
    LAENGE_FT    NUMBER,
    BESATZUNG    VARCHAR2(200),
    ANKERPLATZ   SDO_GEOMETRY,
    TIEFE_FADEN  NUMBER,
    KETTE_FADEN  NUMBER,
    AUSGANG      VARCHAR2(400),
    QUELLE_ID    NUMBER          CONSTRAINT LB_BOOT_Q REFERENCES LB_QUELLE (QUELLE_ID)
)
/
CREATE OR REPLACE TRIGGER LB_BOOT_BI BEFORE INSERT ON LB_BOOT FOR EACH ROW
BEGIN
    IF :NEW.BOOT_ID IS NULL THEN :NEW.BOOT_ID := LB_BOOT_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_BOOT IS 'LituyaBay1958: Edrie, Badger, Sunmore (Werkbuch 2.3): Besatzung, Ankerplatz, Tiefe und Kette, Ausgang'
/

-- ---------------------------------------------------------------- LB_EREIGNIS_LAUF
CREATE SEQUENCE LB_EREIGNIS_LAUF_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_EREIGNIS_LAUF (
    LAUF_ID          NUMBER          CONSTRAINT LB_EREIGNIS_LAUF_PK PRIMARY KEY,
    NAME             VARCHAR2(60)    NOT NULL CONSTRAINT LB_EREIGNIS_LAUF_UK UNIQUE,
    ART              VARCHAR2(14)    NOT NULL CONSTRAINT LB_EREIGNIS_LAUF_ART CHECK (ART IN ('vorgerechnet', 'Echtzeit')),
    QUELLE           VARCHAR2(200),
    ORT              VARCHAR2(100),
    VOLUMEN_M3       NUMBER,
    PEGEL_M          NUMBER,
    GRUND            VARCHAR2(20),
    GANGLINIE        VARCHAR2(40),
    START_S          NUMBER,
    GITTER           VARCHAR2(400),
    RECHNER          VARCHAR2(1000),
    DAUER_S          NUMBER,
    AUFLAUF_MAX_M    NUMBER,
    AUFLAUF_SPORN_M  NUMBER,
    ANKUNFT_CENOTAPH_S   NUMBER,
    ANKUNFT_ANCHORAGE_S  NUMBER,
    ANKUNFT_EINGANG_S    NUMBER,
    ENERGIEANTEIL    NUMBER,
    EINGABEN         CLOB,
    ERSTELLT         DATE,
    GELADEN          DATE            DEFAULT SYSDATE
)
/
CREATE OR REPLACE TRIGGER LB_EREIGNIS_LAUF_BI BEFORE INSERT ON LB_EREIGNIS_LAUF FOR EACH ROW
BEGIN
    IF :NEW.LAUF_ID IS NULL THEN :NEW.LAUF_ID := LB_EREIGNIS_LAUF_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_EREIGNIS_LAUF IS 'LituyaBay1958: Laeufe des Ereignisses (daten/ereignis/<lauf> und Echtzeit aus der App): Eingaben und Kennzahlen (Auflauf, Ankunft, Energieanteil)'
/

-- ---------------------------------------------------------------- LB_BOOT_LAGE
CREATE TABLE LB_BOOT_LAGE (
    BOOT_ID      NUMBER          NOT NULL CONSTRAINT LB_BOOT_LAGE_B REFERENCES LB_BOOT (BOOT_ID) ON DELETE CASCADE,
    LAUF_ID      NUMBER          NOT NULL CONSTRAINT LB_BOOT_LAGE_L REFERENCES LB_EREIGNIS_LAUF (LAUF_ID) ON DELETE CASCADE,
    T_S          NUMBER          NOT NULL,
    E            NUMBER          NOT NULL,
    N            NUMBER          NOT NULL,
    H            NUMBER          NOT NULL,
    KURS         NUMBER,
    KRAENGUNG    NUMBER,
    TRIMM        NUMBER,
    WASSER_KG    NUMBER,
    ZUSTAND      NUMBER(3),
    CONSTRAINT LB_BOOT_LAGE_PK PRIMARY KEY (BOOT_ID, LAUF_ID, T_S)
)
/
COMMENT ON TABLE LB_BOOT_LAGE IS 'LituyaBay1958: Lage der Boote je Sekunde eines Laufs (UTM 8 N, NAVD88): Kurs, Kraengung, Trimm in Grad; Zustand Bits 1 Kette gerissen, 2 gekentert, 4 gesunken, 8 auf Grund, 16 Maschine'
/

-- ---------------------------------------------------------------- LB_BOOT_BAHN
CREATE TABLE LB_BOOT_BAHN (
    BOOT_ID      NUMBER          NOT NULL CONSTRAINT LB_BOOT_BAHN_B REFERENCES LB_BOOT (BOOT_ID) ON DELETE CASCADE,
    LAUF_ID      NUMBER          NOT NULL CONSTRAINT LB_BOOT_BAHN_L REFERENCES LB_EREIGNIS_LAUF (LAUF_ID) ON DELETE CASCADE,
    VON_S        NUMBER,
    BIS_S        NUMBER,
    LAENGE_M     NUMBER,
    HUB_M        NUMBER,
    ENDE         VARCHAR2(200),
    BAHN         SDO_GEOMETRY,
    CONSTRAINT LB_BOOT_BAHN_PK PRIMARY KEY (BOOT_ID, LAUF_ID)
)
/
COMMENT ON TABLE LB_BOOT_BAHN IS 'LituyaBay1958: Bahn eines Boots in einem Lauf als 3D-Linie (3002, 26908), aus LB_BOOT_LAGE'
/
