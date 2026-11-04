-- ============================================================================
-- LituyaBay1958 · Schema LITUYABAY1958 · 06: Ufer als LRS (als Schema)
-- Phase 7a, Werkbuch 7.2. Nach dem Laden von LB_UFER_PUNKT (tools.NetzDb):
-- je Strang die Linie aus den Punkten, mit SDO_LRS.CONVERT_TO_LRS_GEOM auf das
-- Maß von 0 bis zur Länge gebracht (wie com.dan.route.Measure: Maß = Länge
-- entlang der Linie); dann die Lage der Aufläufe aus ihrem Maß (LOCATE_PT).
-- ============================================================================
DECLARE
    o SDO_ORDINATE_ARRAY;
    g SDO_GEOMETRY;
BEGIN
    FOR s IN (SELECT GEOM_ID FROM LB_UFER_GEOM ORDER BY GEOM_ID) LOOP
        SELECT x BULK COLLECT INTO o FROM (
            SELECT p.OST AS x, p.SEQ, 1 AS k FROM LB_UFER_PUNKT p WHERE p.GEOM_ID = s.GEOM_ID
            UNION ALL
            SELECT p.NORD, p.SEQ, 2 FROM LB_UFER_PUNKT p WHERE p.GEOM_ID = s.GEOM_ID)
         ORDER BY SEQ, k;
        g := SDO_GEOMETRY(2002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1), o);
        UPDATE LB_UFER_GEOM
           SET GEOMETRY = SDO_LRS.CONVERT_TO_LRS_GEOM(g, 0, SDO_GEOM.SDO_LENGTH(g, 0.005)),
               LAENGE = SDO_GEOM.SDO_LENGTH(g, 0.005)
         WHERE GEOM_ID = s.GEOM_ID;
    END LOOP;
    UPDATE LB_AUFLAUF a
       SET GEOMETRY = (SELECT SDO_LRS.CONVERT_TO_STD_GEOM(SDO_LRS.LOCATE_PT(u.GEOMETRY, a.M)) FROM LB_UFER_GEOM u WHERE u.GEOM_ID = a.GEOM_ID);
    COMMIT;
END;
/
