-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 01: Tabellen der Netze (als Schema)
-- Phase 3, Werkbuch 7 und 13: nur, was die Netze brauchen. Alles Weitere aus
-- Abschnitt 13 (LB_QUELLE, LB_BEFUND, LB_ZEUGE …) kommt mit Phase 19.
-- Schlüssel aus Sequenz und Trigger, nie IDENTITY.
-- ============================================================================

-- ---------------------------------------------------------------- LB_NETZ_STAND
CREATE SEQUENCE LB_NETZ_STAND_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_NETZ_STAND (
    STAND_ID     NUMBER        CONSTRAINT LB_NETZ_STAND_PK PRIMARY KEY,
    NETZ         VARCHAR2(30)  NOT NULL,
    INHALT       VARCHAR2(200),
    KNOTEN       NUMBER,
    KANTEN       NUMBER,
    PRUEFSUMME   VARCHAR2(64),
    QUELLE       VARCHAR2(400),
    GELADEN      DATE          DEFAULT SYSDATE,
    GUELTIG      VARCHAR2(400),
    CONSTRAINT LB_NETZ_STAND_UK UNIQUE (NETZ)
)
/
CREATE OR REPLACE TRIGGER LB_NETZ_STAND_BI BEFORE INSERT ON LB_NETZ_STAND FOR EACH ROW
BEGIN
    IF :NEW.STAND_ID IS NULL THEN :NEW.STAND_ID := LB_NETZ_STAND_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_NETZ_STAND IS 'LituyaBay1958: je Netz Zahlen, Pruefsumme der Dateien in daten/netz und Ladedatum (Oracle und Datei passen, wenn die Pruefsumme gleich ist)'
/

-- ---------------------------------------------------------------- LB_NDM_PROBE
CREATE SEQUENCE LB_NDM_PROBE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE TABLE LB_NDM_PROBE (
    PROBE_ID     NUMBER         CONSTRAINT LB_NDM_PROBE_PK PRIMARY KEY,
    NR           NUMBER(3)      NOT NULL,
    FAEHIGKEIT   VARCHAR2(200)  NOT NULL,
    PRUEFUNG     VARCHAR2(400),
    ERGEBNIS     VARCHAR2(4000),
    OK           VARCHAR2(1)    CONSTRAINT LB_NDM_PROBE_OK CHECK (OK IN ('J', 'N', '-')),
    WEG          VARCHAR2(10)   CONSTRAINT LB_NDM_PROBE_WEG CHECK (WEG IN ('SQL', 'JAVA')),
    GEPRUEFT     DATE           DEFAULT SYSDATE,
    CONSTRAINT LB_NDM_PROBE_UK UNIQUE (NR, FAEHIGKEIT)
)
/
CREATE OR REPLACE TRIGGER LB_NDM_PROBE_BI BEFORE INSERT ON LB_NDM_PROBE FOR EACH ROW
BEGIN
    IF :NEW.PROBE_ID IS NULL THEN :NEW.PROBE_ID := LB_NDM_PROBE_SEQ.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE LB_NDM_PROBE IS 'LituyaBay1958: NDM-Funktionsprobe (Werkbuch 2.8): je Faehigkeit Pruefung, Ergebnis, J/N; was fehlt, ist ein Wunsch (F39)'
/

-- Ergebnis einer Probe eintragen oder ersetzen (eigene Transaktion: auch nach Fehlern der Probe bleibt es stehen)
CREATE OR REPLACE PROCEDURE LB_PROBE_SETZEN(p_nr NUMBER, p_faehigkeit VARCHAR2, p_pruefung VARCHAR2, p_ergebnis VARCHAR2, p_ok VARCHAR2,
                                            p_weg VARCHAR2 DEFAULT 'SQL') IS
    PRAGMA AUTONOMOUS_TRANSACTION;
BEGIN
    MERGE INTO LB_NDM_PROBE t
    USING (SELECT p_nr NR, p_faehigkeit FAEHIGKEIT FROM DUAL) s
       ON (t.NR = s.NR AND t.FAEHIGKEIT = s.FAEHIGKEIT)
     WHEN MATCHED THEN UPDATE SET t.PRUEFUNG = p_pruefung, t.ERGEBNIS = SUBSTR(p_ergebnis, 1, 4000), t.OK = p_ok, t.WEG = p_weg, t.GEPRUEFT = SYSDATE
     WHEN NOT MATCHED THEN INSERT (NR, FAEHIGKEIT, PRUEFUNG, ERGEBNIS, OK, WEG) VALUES (p_nr, p_faehigkeit, p_pruefung, SUBSTR(p_ergebnis, 1, 4000), p_ok, p_weg);
    COMMIT;
END;
/
