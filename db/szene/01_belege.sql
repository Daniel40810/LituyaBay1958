-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · db/szene 01: Belege (als Schema)
-- Phase 19, Werkbuch 13.1: Quellen, Befunde, ihre Verknüpfung und die Zeugen.
-- Schlüssel aus Sequenz und Trigger, nie IDENTITY; Orte als Punkt in 26908.
-- Befüllt von tools.SzeneDb (ant szenedb) aus com/dan/lituya/data/*.tsv.
-- ============================================================================

-- ---------------------------------------------------------------- LB_QUELLE
CREATE SEQUENCE LB_QUELLE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_QUELLE (
    QUELLE_ID    NUMBER          CONSTRAINT LB_QUELLE_PK PRIMARY KEY,
    KURZ         VARCHAR2(30)    NOT NULL CONSTRAINT LB_QUELLE_UK UNIQUE,
    AUTOREN      VARCHAR2(400),
    JAHR         NUMBER(4),
    TITEL        VARCHAR2(1000)  NOT NULL,
    ART          VARCHAR2(20)    CONSTRAINT LB_QUELLE_ART CHECK (ART IN ('Aufsatz', 'Bericht', 'Buch', 'Handbuch', 'Daten', 'Karte', 'Webseite', 'eigene')),
    FUNDORT      VARCHAR2(400),
    LIZENZ       VARCHAR2(200),
    GEPRUEFT     VARCHAR2(20)    DEFAULT 'J' CONSTRAINT LB_QUELLE_GEPRUEFT CHECK (GEPRUEFT IN ('J', 'N', 'zu prüfen')),
    BEMERKUNG    VARCHAR2(1000),
    GEAENDERT    DATE            DEFAULT SYSDATE
)
/
CREATE OR REPLACE TRIGGER LB_QUELLE_BI BEFORE INSERT ON LB_QUELLE FOR EACH ROW
BEGIN
    IF :NEW.QUELLE_ID IS NULL THEN :NEW.QUELLE_ID := LB_QUELLE_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_QUELLE IS 'LituyaBay1958: Quellen mit Kurzzeichen (Werkbuch 2.9): Aufsaetze, Berichte, Daten, eigene Rechnung (APP)'
/

-- ---------------------------------------------------------------- LB_BEFUND
CREATE SEQUENCE LB_BEFUND_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_BEFUND (
    BEFUND_ID    NUMBER          CONSTRAINT LB_BEFUND_PK PRIMARY KEY,
    SCHLUESSEL   VARCHAR2(60)    NOT NULL CONSTRAINT LB_BEFUND_UK UNIQUE,
    THEMA        VARCHAR2(40)    NOT NULL,
    AUSSAGE      VARCHAR2(2000)  NOT NULL,
    WERT         NUMBER,
    EINHEIT      VARCHAR2(20),
    ART          VARCHAR2(10)    NOT NULL CONSTRAINT LB_BEFUND_ART CHECK (ART IN ('belegt', 'gemessen', 'gerechnet', 'gesetzt')),
    ABSCHNITT    VARCHAR2(20),
    ORT          SDO_GEOMETRY,
    ORT_NAME     VARCHAR2(100),
    ZEIT_S       NUMBER,
    GILT         VARCHAR2(10)    DEFAULT 'beide' CONSTRAINT LB_BEFUND_GILT CHECK (GILT IN ('1958', 'heute', 'beide')),
    HERKUNFT     VARCHAR2(20)    DEFAULT 'mitgeliefert' CONSTRAINT LB_BEFUND_HERKUNFT CHECK (HERKUNFT IN ('mitgeliefert', 'gerechnet', 'eigen')),
    PHASE        VARCHAR2(10),
    GEAENDERT    DATE            DEFAULT SYSDATE
)
/
CREATE OR REPLACE TRIGGER LB_BEFUND_BI BEFORE INSERT ON LB_BEFUND FOR EACH ROW
BEGIN
    IF :NEW.BEFUND_ID IS NULL THEN :NEW.BEFUND_ID := LB_BEFUND_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_BEFUND IS 'LituyaBay1958: Befunde (Werkbuch 2 und die gerechneten Kennzahlen): Aussage, Wert, Art belegt/gemessen/gerechnet/gesetzt (= [G]), Ort und Simulationszeit'
/

-- ---------------------------------------------------------------- LB_BEFUND_QUELLE
CREATE TABLE LB_BEFUND_QUELLE (
    BEFUND_ID    NUMBER          NOT NULL CONSTRAINT LB_BEFUND_QUELLE_B REFERENCES LB_BEFUND (BEFUND_ID) ON DELETE CASCADE,
    QUELLE_ID    NUMBER          NOT NULL CONSTRAINT LB_BEFUND_QUELLE_Q REFERENCES LB_QUELLE (QUELLE_ID),
    SEITE        VARCHAR2(40),
    ROLLE        VARCHAR2(12)    DEFAULT 'Beleg' CONSTRAINT LB_BEFUND_QUELLE_ROLLE CHECK (ROLLE IN ('Beleg', 'Vergleich', 'Widerspruch')),
    CONSTRAINT LB_BEFUND_QUELLE_PK PRIMARY KEY (BEFUND_ID, QUELLE_ID)
)
/
COMMENT ON TABLE LB_BEFUND_QUELLE IS 'LituyaBay1958: welche Quelle einen Befund belegt (Seite, Rolle)'
/

-- ---------------------------------------------------------------- LB_ZEUGE
CREATE SEQUENCE LB_ZEUGE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_ZEUGE (
    ZEUGE_ID     NUMBER          CONSTRAINT LB_ZEUGE_PK PRIMARY KEY,
    NAME         VARCHAR2(100)   NOT NULL,
    ROLLE        VARCHAR2(100),
    BOOT         VARCHAR2(30),
    ZEIT_VON_S   NUMBER,
    ZEIT_BIS_S   NUMBER,
    ORT          SDO_GEOMETRY,
    ORT_NAME     VARCHAR2(100),
    AUSSAGE      VARCHAR2(2000)  NOT NULL,
    ART          VARCHAR2(20)    CONSTRAINT LB_ZEUGE_ART CHECK (ART IN ('wörtlich kurz', 'sinngemäß')),
    QUELLE_ID    NUMBER          CONSTRAINT LB_ZEUGE_Q REFERENCES LB_QUELLE (QUELLE_ID),
    SEITE        VARCHAR2(40),
    NR           NUMBER          NOT NULL,
    CONSTRAINT LB_ZEUGE_UK UNIQUE (NAME, NR)
)
/
CREATE OR REPLACE TRIGGER LB_ZEUGE_BI BEFORE INSERT ON LB_ZEUGE FOR EACH ROW
BEGIN
    IF :NEW.ZEUGE_ID IS NULL THEN :NEW.ZEUGE_ID := LB_ZEUGE_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_ZEUGE IS 'LituyaBay1958: Aussagen der Zeugen mit Simulationszeit (von/bis), Ort und Quelle; woertlich nur kurz, sonst sinngemaess'
/
