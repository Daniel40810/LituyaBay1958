package com.dan.lituya.app;

import com.dan.lituya.gl.Renderer;
import com.dan.lituya.gl.SceneView;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.ui.AppIcon;
import com.dan.lituya.ui.ControlPanel;
import com.dan.lituya.ui.Startbild;
import com.dan.lituya.ui.Theme;
import com.dan.fframe.FFrame;
import com.dan.foptionpane.FOptionPane;
import com.jogamp.opengl.GLProfile;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LituyaBay1958 — die Riesenwelle von Lituya Bay, Alaska, 9. Juli 1958, 22:16 Uhr Ortszeit: das Beben an der
 * Fairweather-Störung, der Felssturz in den Gilbert Inlet, die Welle mit 524 m Auflauf, die Bucht vor, während, nach der
 * Welle und heute, als 3D-Szene auf der Grafikkarte (JOGL, OpenGL 4.3). „Das Projekt der Riesenwelle und des Netzes“
 * (Werkbuch, Abschnitt 1).
 * <p>
 * Einstieg: Startbild, dann FFrame beim Start maximiert (Taskleiste bleibt frei), Größe frei veränderbar; Szene in
 * der Mitte, Bedienfeld rechts, Statuszeile unten. Die Element-Packages kommen aus {@code lib/Renderer.jar}; die 14 neuen
 * entstehen im Projekt Renderer (Werkbuch 5). Das Gerüst ist aus Timmendorf übernommen und entschlackt (F6 A).
 * <p>
 * Schalter: {@code -Dlituya.splash=false} ohne Startbild, {@code -Dlituya.gljpanel=true} leichter GL-Rückfall,
 * {@code -Dlituya.shaders=<ordner>} Shader von der Platte (F5 lädt neu), {@code -Dlituya.daten=<ordner>} Ordner für
 * Dateien der App, {@code -Dlituya.db=false} ohne Datenbank.
 */
public final class LituyaApp {
    /** Für Werkzeuge: Fenster und Szene, sobald sie stehen. */
    public static volatile FFrame frame;
    public static volatile SceneView scene;
    public static volatile ControlPanel controls;
    /** Fehler in Fäden seit dem Start (für den Dauerlauf in Phase 20). */
    public static final AtomicInteger FEHLER = new AtomicInteger();

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        // Der GL-Canvas ist ein schweres AWT-Element: Menüs und Tooltips müssen darüber liegen können
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("Faden " + t.getName() + ": " + e);
            FEHLER.incrementAndGet();
            e.printStackTrace();
            fehlerDatei("Faden " + t.getName(), e);
        });
        GLProfile.initSingleton();
        SwingUtilities.invokeLater(() -> {
            try {
                open();
            } catch (Throwable e) {
                // ohne Konsole (javaw, Verknüpfung) bliebe ein Fehler beim Start sonst unsichtbar (Befund aus Haithabu)
                e.printStackTrace();
                File f = fehlerDatei("Start", e);
                FOptionPane.showMessageDialog(null, "LituyaBay1958 ließ sich nicht starten:\n" + e + (e.getStackTrace().length > 0 ? "\n  bei " + e.getStackTrace()[0] : "")
                        + "\n\nDie ganze Meldung steht in\n" + (f != null ? f.getAbsolutePath() : "der Konsole") + ".", "LituyaBay1958", FOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** Ordner für Dateien der App ({@code daten/}): Fehler, ab Phase 2 die aufbereiteten Daten (nie download/, Werkbuch 3.3). */
    public static File datenOrdner() {
        String d = System.getProperty("lituya.daten");
        return new File(d != null ? d : "daten");
    }

    /** Schreibt einen Fehler mit Stapel nach daten/fehler.txt (angehängt) und gibt die Datei zurück, oder null. */
    static File fehlerDatei(String wo, Throwable e) {
        try {
            File f = new File(datenOrdner(), "fehler.txt");
            f.getParentFile().mkdirs();
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String t = java.time.LocalDateTime.now().withNano(0) + " · " + Renderer.VERSION + " · Java " + System.getProperty("java.version") + " · " + wo + "\n" + sw + "\n";
            java.nio.file.Files.writeString(f.toPath(), t, java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            return f;
        } catch (Exception x) {
            return null;
        }
    }

    private static void open() {
        Startbild.Wahl wahl = "false".equals(System.getProperty("lituya.splash")) ? Startbild.stumm() : Startbild.zeigen();
        if (wahl.beenden()) System.exit(0);
        SceneClock clock = new SceneClock();
        Renderer rd = new Renderer(clock);
        FFrame f = new FFrame("Lituya Bay 1958 · Das Projekt der Riesenwelle und des Netzes · " + Renderer.VERSION);
        AppIcon.install(f);
        if (!Boolean.getBoolean("lituya.gljpanel")) makeOpaque(f);
        SceneView view;
        try {
            view = new SceneView(rd);
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(null, "OpenGL lässt sich nicht starten:\n" + e.getMessage()
                    + "\n\nBitte den Grafiktreiber aktualisieren. Als Rückfall: Start mit -Dlituya.gljpanel=true.", "LituyaBay1958", FOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        ControlPanel controls = new ControlPanel(view);
        JLabel status = new JLabel("Die Szene startet …");
        status.setForeground(new Color(170, 182, 192));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        String gel = "Phase 1: Wasser, Himmel, Marken · Netze: " + (com.dan.lituya.netz.Lod.fehlend().isEmpty() ? "LOD bereit" : "LOD ohne Treiber 21 (ant lod)");
        JLabel right = new JLabel(gel + " · " + Startbild.statuszeile());
        new javax.swing.Timer(2000, e -> right.setText(gel + " · " + Startbild.statuszeile())).start();
        right.setFont(new Font("SansSerif", Font.PLAIN, 12));
        right.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        right.setForeground(Theme.GOLD);
        rd.setStatusListener(s -> SwingUtilities.invokeLater(() -> status.setText(s)));
        rd.setErrorListener(msg -> SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(f, msg, "LituyaBay1958 · Grafik", FOptionPane.ERROR_MESSAGE)));

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(Theme.bg());
        root.add(view, BorderLayout.CENTER);
        JScrollPane side = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.line()));
        side.getViewport().setBackground(Theme.bg());
        side.setBackground(Theme.bg());
        try {
            com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
            bar.setUnitIncrement(16);
            bar.setBlockIncrement(120);
            side.setVerticalScrollBar(bar);
        } catch (RuntimeException | LinkageError e) {
            side.getVerticalScrollBar().setUnitIncrement(16);
        }
        side.setPreferredSize(new Dimension(340, 600));   // Befund aus Timmendorf Phase 19: breiter, sonst brechen die Knöpfe um
        // Werkbuch Welle W9: das Bedienfeld als Schublade, die nach rechts weggleitet (Lasche, F9) und offen oder zu gemerkt wird
        java.util.prefs.Preferences pref = null;
        try { pref = java.util.prefs.Preferences.userRoot().node("com/dan/lituya/bedienfeld"); } catch (RuntimeException | Error e) { /* ohne Merken */ }
        final java.util.prefs.Preferences pf = pref;
        com.dan.lituya.ui.Schublade schub = new com.dan.lituya.ui.Schublade(side, 340, pf == null || pf.getBoolean("schublade.offen", true), g -> rd.festeGroesse = g);
        schub.geschaltet = offen -> { if (pf != null) pf.putBoolean("schublade.offen", offen); };
        root.add(schub, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Theme.status());
        south.add(status, BorderLayout.CENTER);
        south.add(right, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        f.setPreferredFrameSize(new Dimension(1480, 900));
        f.setSize(1480, 900);
        f.setMinimumSize(new Dimension(800, 520));
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        startMaximized(f);
        view.start();
        Runtime.getRuntime().addShutdownHook(new Thread(view::stop, "LituyaBay1958-Ende"));
        // F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)
        view.setKeyAction(java.awt.event.KeyEvent.VK_F11, () -> SwingUtilities.invokeLater(() -> {
            boolean an = !schub.isVisible();
            schub.setVisible(an);
            south.setVisible(an);
            root.revalidate();
        }));
        // W9: F9 lässt das Bedienfeld gleiten (die Lasche bleibt)
        view.setKeyAction(java.awt.event.KeyEvent.VK_F9, () -> SwingUtilities.invokeLater(schub::umschalten));
        // Phase 17a: im Kino Bedienfeld und Statuszeile weg, Zeiger weg; danach wie vorher
        boolean[] vorher = {true};
        rd.kinoFenster = an -> SwingUtilities.invokeLater(() -> {
            if (an) vorher[0] = schub.isVisible();
            schub.setVisible(!an && vorher[0]);
            south.setVisible(!an && vorher[0]);
            view.zeigerVerbergen(an);
            root.revalidate();
            view.requestFocusInWindow();
        });
        view.requestFocusInWindow();
        frame = f;
        scene = view;
        LituyaApp.controls = controls;
        System.out.println("LituyaBay1958 " + Renderer.VERSION + " gestartet · " + clock.label() + " · " + (wahl.datenbank() ? "Datenbank " : "") + Startbild.statuszeile()
                + " · Renderer.jar: " + Bibliothek.kurz());
    }

    /**
     * Startet maximiert (MAXIMIZED_BOTH), ohne die Taskleiste zu verdecken. Die normale Größe von 1480 × 900 bleibt
     * als Rückfall; „Wiederherstellen“ des FFrame stellt sie wieder her (wie in Brocken).
     */
    private static void startMaximized(FFrame f) {
        Rectangle normal = f.getBounds();
        f.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
        try {
            // Die Titelleiste des FFrame führt einen eigenen Maximiert-Zustand; ihn angleichen
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(f);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"),
                        btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true);
                rb.setAccessible(true);
                btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", com.dan.ficons.FIconType.class).invoke(icon, com.dan.ficons.FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                                f.setExtendedState(f.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
                                f.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht angeglichen: " + e);
        }
    }

    /**
     * Das FFrame ist ein durchscheinendes Fenster (runde Ecken). Darin zeigt Windows schwere Elemente wie den
     * GL-Canvas nicht; darum wird es deckend gemacht und die Rundung als Fensterform gesetzt.
     */
    private static void makeOpaque(FFrame f) {
        f.setBackground(Theme.bg());
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { reshape(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reshape(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> reshape(f));
    }

    private static void reshape(FFrame f) {
        try {
            boolean max = (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    private LituyaApp() { }
}
