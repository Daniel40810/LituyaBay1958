-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · db/szene 06: Nachträge Phase 19a (als Schema)
-- Befund aus dem Test der App (Phase 19): „Lauf sichern“ schrieb einen Namen von
-- rund 95 Zeichen und die Grundart mit Umlaut; NAME (60) und GRUND (20 Byte)
-- waren zu kurz. Jetzt in Zeichen statt Byte. Läuft bei jedem ant szenedb (ein
-- MODIFY auf dieselbe Breite ist kein Fehler).
-- ============================================================================
ALTER TABLE LB_EREIGNIS_LAUF MODIFY (NAME VARCHAR2(200 CHAR), QUELLE VARCHAR2(400 CHAR), ORT VARCHAR2(200 CHAR), GRUND VARCHAR2(60 CHAR), GANGLINIE VARCHAR2(60 CHAR))
/
ALTER TABLE LB_SITZUNG MODIFY (NAME VARCHAR2(400 CHAR))
/
ALTER TABLE LB_SCHNAPPSCHUSS MODIFY (NOTIZ VARCHAR2(1000 CHAR), WELTZEIT VARCHAR2(80 CHAR))
/
