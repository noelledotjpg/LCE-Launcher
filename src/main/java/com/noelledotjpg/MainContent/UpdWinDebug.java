package com.noelledotjpg.MainContent;

import com.noelledotjpg.Data.AppPaths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;

/**
 * Panels for toggling debug mode.
 *
 *   BuildPanel — full console UI, used when Debug/MinecraftClient.exe doesn't exist yet.
 *                Runs ensureDebugBuild() then swapActiveConfig().
 *
 *   SwapPanel  — minimal bouncing-bar UI, used when the debug exe already exists.
 *                Just calls swapActiveConfig() and auto-closes.
 *
 * Also owns readLceFolder() / writeExePath() shared with UpdateWindow.
 */
public class UpdWinDebug {

    // -------------------------------------------------------------------------
    // BuildPanel
    // -------------------------------------------------------------------------

    public static class BuildPanel extends JPanel {

        private final JTextPane    consoleArea;
        private final JButton      actionButton;
        private final JProgressBar progressBar;
        private final JLabel       timeLabel;
        private final JDialog      owner;
        private final Runnable     onSuccess;
        private final Runnable     onFailure;

        private Style  timestampStyle;
        private Style  logStyle;
        private Thread buildThread;
        private Timer  elapsedTimer;
        private long   startTime;

        public BuildPanel(java.io.File repoRoot, boolean toDebug,
                          JDialog owner, Runnable onSuccess, Runnable onFailure) {
            this.owner     = owner;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;

            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setPreferredSize(new Dimension(515, 420));

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("Starting...");

            timeLabel = new JLabel("Time Elapsed: 00:00:00");

            JPanel topPanel = new JPanel(new BorderLayout(5, 5));
            topPanel.add(progressBar, BorderLayout.CENTER);
            topPanel.add(timeLabel,   BorderLayout.EAST);
            add(topPanel, BorderLayout.NORTH);

            consoleArea = new JTextPane();
            consoleArea.setEditable(false);
            ((DefaultCaret) consoleArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
            add(new JScrollPane(consoleArea), BorderLayout.CENTER);

            actionButton = new JButton("Cancel");
            actionButton.setForeground(Color.RED);
            actionButton.addActionListener(e -> onCancel());
            add(actionButton, BorderLayout.SOUTH);

            initStyles();
            startElapsedTimer();

            buildThread = new Thread(() -> {
                try {
                    appendLog("Building Debug config...");
                    com.noelledotjpg.BootstrapContent.BuildArtifactManager
                            .ensureDebugBuild(repoRoot, this::appendLog, this::appendLog);
                    setProgress(70);

                    appendLog("Moving files...");
                    com.noelledotjpg.BootstrapContent.BuildArtifactManager
                            .swapActiveConfig(repoRoot, toDebug, this::appendLog);
                    setProgress(95);

                    writeExePath(repoRoot);
                    setProgress(100);
                    appendLog("Debug build activated!");
                    stopElapsedTimer();

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        Toolkit.getDefaultToolkit().beep();
                        JOptionPane.showMessageDialog(this,
                                "Debug build activated!", "Done", JOptionPane.INFORMATION_MESSAGE);
                        owner.dispose();
                        onSuccess.run();
                    });
                } catch (InterruptedException ignored) {
                    appendLog("Cancelled.");
                    stopElapsedTimer();
                    SwingUtilities.invokeLater(() -> { onFailure.run(); owner.dispose(); });
                } catch (Exception e) {
                    appendLog("Failed: " + e.getMessage());
                    stopElapsedTimer();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        actionButton.setText("Close");
                        actionButton.setForeground(Color.BLACK);
                        onFailure.run();
                    });
                }
            }, "debug-build-thread");
            buildThread.start();
        }

        private void onCancel() {
            if ("Close".equals(actionButton.getText())) { owner.dispose(); return; }
            int res = JOptionPane.showConfirmDialog(this, "Cancel debug build?",
                    "Warning", JOptionPane.YES_NO_OPTION);
            if (res != JOptionPane.YES_OPTION) return;
            actionButton.setEnabled(false);
            if (buildThread != null) buildThread.interrupt();
            stopElapsedTimer();
        }

        private void setProgress(int pct) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(pct);
                progressBar.setString(pct + "%");
            });
        }

        private void appendLog(String text) {
            SwingUtilities.invokeLater(() -> {
                StyledDocument doc = consoleArea.getStyledDocument();
                String ts = LocalTime.now().withNano(0).toString();
                try {
                    doc.insertString(doc.getLength(), String.format("%10s", ts), timestampStyle);
                    doc.insertString(doc.getLength(), "  " + text + "\n", logStyle);
                } catch (BadLocationException e) { e.printStackTrace(); }
            });
        }

        private void startElapsedTimer() {
            startTime    = System.currentTimeMillis();
            elapsedTimer = new Timer(1000, e -> {
                long s = (System.currentTimeMillis() - startTime) / 1000;
                timeLabel.setText("Time Elapsed: " +
                        String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
            });
            elapsedTimer.start();
        }

        private void stopElapsedTimer() { if (elapsedTimer != null) elapsedTimer.stop(); }

        private void initStyles() {
            StyleContext sc = new StyleContext();
            timestampStyle  = sc.addStyle("timestamp", null);
            StyleConstants.setForeground(timestampStyle, Color.GRAY);
            logStyle = sc.addStyle("log", null);
            StyleConstants.setForeground(logStyle, Color.BLACK);
        }
    }

    // -------------------------------------------------------------------------
    // SwapPanel
    // -------------------------------------------------------------------------

    public static class SwapPanel extends JPanel {

        private final JProgressBar progressBar;
        private final JLabel       timeLabel;
        private final JDialog      owner;

        private Timer elapsedTimer;
        private long  startTime;

        public SwapPanel(java.io.File repoRoot, boolean toDebug,
                         JDialog owner, Runnable onSuccess, Runnable onFailure) {
            this.owner = owner;

            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setPreferredSize(new Dimension(515, 80));

            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(true);
            progressBar.setString("Moving files...");

            timeLabel = new JLabel("Time Elapsed: 00:00:00");

            JPanel topPanel = new JPanel(new BorderLayout(5, 5));
            topPanel.add(progressBar, BorderLayout.CENTER);
            topPanel.add(timeLabel,   BorderLayout.EAST);
            add(topPanel, BorderLayout.CENTER);

            startElapsedTimer();

            new Thread(() -> {
                try {
                    com.noelledotjpg.BootstrapContent.BuildArtifactManager
                            .swapActiveConfig(repoRoot, toDebug, msg -> {});
                    writeExePath(repoRoot);
                    SwingUtilities.invokeLater(() -> {
                        stopElapsedTimer();
                        owner.dispose();
                        onSuccess.run();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        stopElapsedTimer();
                        owner.dispose();
                        JOptionPane.showMessageDialog(null,
                                "Failed to swap files:\n" + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        onFailure.run();
                    });
                }
            }, "debug-swap-thread").start();
        }

        private void startElapsedTimer() {
            startTime    = System.currentTimeMillis();
            elapsedTimer = new Timer(1000, e -> {
                long s = (System.currentTimeMillis() - startTime) / 1000;
                timeLabel.setText("Time Elapsed: " +
                        String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
            });
            elapsedTimer.start();
        }

        private void stopElapsedTimer() { if (elapsedTimer != null) elapsedTimer.stop(); }
    }

    // -------------------------------------------------------------------------
    // Shared helpers (package-visible so UpdateWindow can call readLceFolder)
    // -------------------------------------------------------------------------

    static String readLceFolder() {
        try {
            java.io.File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return null;
            com.noelledotjpg.Data.VarsData vars = new com.google.gson.Gson()
                    .fromJson(java.nio.file.Files.readString(f.toPath()),
                            com.noelledotjpg.Data.VarsData.class);
            return vars != null ? vars.getLceFolder() : null;
        } catch (Exception e) { return null; }
    }

    static void writeExePath(java.io.File repoRoot) {
        try {
            java.io.File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return;
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            com.noelledotjpg.Data.VarsData vars;
            try (java.io.Reader r = new java.io.FileReader(f)) {
                vars = gson.fromJson(r, com.noelledotjpg.Data.VarsData.class);
            }
            if (vars == null) return;
            java.io.File exe = com.noelledotjpg.BootstrapContent.BuildArtifactManager.activeExe(repoRoot);
            if (exe.exists()) {
                vars.setMinecraftExe(exe.getAbsolutePath());
                java.nio.file.Files.writeString(f.toPath(), gson.toJson(vars));
            }
        } catch (Exception ignored) {}
    }
}