-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · db/szene 04: Sitzungen und Texturen (als Schema)
-- Phase 19, Werkbuch 13.1: Sitzung (Strg+S) mit dem ganzen Zustand als Text,
-- Texturen mit Herkunft und Lizenz (daten/texturen/LIZENZEN.txt).
-- ============================================================================

-- ---------------------------------------------------------------- LB_SITZUNG
CREATE SEQUENCE LB_SITZUNG_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_SITZUNG (
    SITZUNG_ID   NUMBER          CONSTRAINT LB_SITZUNG_PK PRIMARY KEY,
    NAME         VARCHAR2(200)   NOT NULL,
    GESPEICHERT  DATE            DEFAULT SYSDATE,
    RECHNER      VARCHAR2(200),
    KAMERA       SDO_GEOMETRY,
    HOEHE_M      NUMBER,
    RICHTUNG     NUMBER,
    NEIGUNG      NUMBER,
    MODUS        VARCHAR2(20),
    UHR_MS       NUMBER,
    ZEITSTAND    VARCHAR2(10),
    RAFFER       NUMBER,
    LAUF_ID      NUMBER          CONSTRAINT LB_SITZUNG_L REFERENCES LB_EREIGNIS_LAUF (LAUF_ID) ON DELETE SET NULL,
    SZENARIO_ID  NUMBER          CONSTRAINT LB_SITZUNG_S REFERENCES LB_SZENARIO (SZENARIO_ID) ON DELETE SET NULL,
    ZUSTAND      CLOB            NOT NULL
)
/
CREATE OR REPLACE TRIGGER LB_SITZUNG_BI BEFORE INSERT ON LB_SITZUNG FOR EACH ROW
BEGIN
    IF :NEW.SITZUNG_ID IS NULL THEN :NEW.SITZUNG_ID := LB_SITZUNG_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_SITZUNG IS 'LituyaBay1958: gesicherte Sitzungen (Strg+S): Kamera, Uhr, Zeitstand, Raffer und alle Schalter als Properties-Text (ZUSTAND)'
/
-- Schnappschüsse gehören zu einer Sitzung (erst jetzt, weil LB_SITZUNG nach LB_SCHNAPPSCHUSS kommt)
ALTER TABLE LB_SCHNAPPSCHUSS ADD CONSTRAINT LB_SCHNAPPSCHUSS_SI FOREIGN KEY (SITZUNG_ID) REFERENCES LB_SITZUNG (SITZUNG_ID) ON DELETE SET NULL
/

-- ---------------------------------------------------------------- LB_TEXTUR
CREATE SEQUENCE LB_TEXTUR_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_TEXTUR (
    TEXTUR_ID    NUMBER          CONSTRAINT LB_TEXTUR_PK PRIMARY KEY,
    NAME         VARCHAR2(60)    NOT NULL CONSTRAINT LB_TEXTUR_UK UNIQUE,
    MATERIAL     VARCHAR2(200),
    WO           VARCHAR2(200),
    QUELLE       VARCHAR2(200),
    SATZ         VARCHAR2(60),
    URL          VARCHAR2(400),
    LIZENZ       VARCHAR2(100),
    AUFLOESUNG   VARCHAR2(40),
    DATEI        VARCHAR2(400),
    PRUEFSUMME   VARCHAR2(64),
    QUELLE_ID    NUMBER          CONSTRAINT LB_TEXTUR_Q REFERENCES LB_QUELLE (QUELLE_ID),
    GEPRUEFT     DATE            DEFAULT SYSDATE
)
/
CREATE OR REPLACE TRIGGER LB_TEXTUR_BI BEFORE INSERT ON LB_TEXTUR FOR EACH ROW
BEGIN
    IF :NEW.TEXTUR_ID IS NULL THEN :NEW.TEXTUR_ID := LB_TEXTUR_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_TEXTUR IS 'LituyaBay1958: Texturen in daten/texturen mit Satz, Quelle, Lizenz und Pruefsumme (Werkbuch 8, Paketvertrag 15)'
/
