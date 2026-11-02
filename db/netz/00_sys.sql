-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 00: Rechte (als SYS AS SYSDBA)
-- Phase 3 (Werkbuch 7.9, wie Timmendorf). Das Schema ist der vorhandene Benutzer
-- LituyaBay1958 (db/info/USER.sql, Entscheidung Phase 3: F40 B statt LITUYA).
--
-- In SQL Developer: als SYS (Rolle SYSDBA) mit dem Dienst PDBORCL verbinden und
-- das Skript laufen lassen (F5). Einmal nötig, danach nicht mehr.
--
-- Jede Anweisung endet mit einer Zeile "/" (SQL Developer, SQL*Plus und
-- ant netzdb lesen dasselbe Format).
-- ============================================================================

-- Rechte direkt, nicht nur über die Rolle DB_CONNECT: SDO_NET legt Tabellen aus
-- PL/SQL heraus an, und dort gelten Rollen nicht (Befund Timmendorf).
GRANT CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER, CREATE PROCEDURE, CREATE TYPE TO LITUYABAY1958
/

-- Protokolle der Partitionierung und der Zusammenhangskomponenten
GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO LITUYABAY1958
/

-- Platz im Standard-Tablespace des Schemas
DECLARE
    ts VARCHAR2(128);
BEGIN
    SELECT default_tablespace INTO ts FROM dba_users WHERE username = 'LITUYABAY1958';
    EXECUTE IMMEDIATE 'ALTER USER LITUYABAY1958 QUOTA UNLIMITED ON ' || ts;
END;
/
