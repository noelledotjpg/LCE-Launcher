package com.noelledotjpg.MainContent;

import com.noelledotjpg.Data.AppPaths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;

/**
 * Progress panel for the LCE reinstall flow:
 * deletes build/ → cmake configure → cmake build Release → move artifacts → write vars.json
 */
public class UpdWinLauncher extends JPanel {

    private final JTextPane    consoleArea;
    private final JButton      actionButton;
    private final JProgressBar progressBar;
    private final JLabel       timeLabel;
    private final JDialog      owner;

    private Style           timestampStyle;
    private Style           logStyle;
    private Thread          rebuildThread;
    private volatile Process activeProcess;
    private Timer           elapsedTimer;
    private long            startTime;

    public UpdWinLauncher(JDialog owner) {
        this.owner = owner;

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
        startRebuild();
    }

    private void startRebuild() {
        rebuildThread = new Thread(() -> {
            try {
                String lceFolder = readLceFolder();
                if (lceFolder == null)
                    throw new Exception("LCE folder not found in vars.json.");

                appendLog("Deleting build folder...");
                java.io.File buildDir = com.noelledotjpg.BootstrapContent.SetupPaths
                        .buildDir(new java.io.File(lceFolder));
                if (buildDir.exists()) deleteDirectory(buildDir);
                appendLog("Build folder deleted.");
                setProgress(10);

                appendLog("Running CMake configure...");
                runCommand(new String[]{
                        "cmake", "-S", ".", "-B", "build",
                        "-G", "Visual Studio 17 2022", "-A", "x64"
                }, "cmake configure", lceFolder);
                setProgress(40);

                appendLog("Building Release...");
                runCommand(new String[]{
                        "cmake", "--build", "build",
                        "--config", "Release", "--target", "MinecraftClient"
                }, "build", lceFolder);
                setProgress(90);

                appendLog("Moving artifacts to build/ ...");
                com.noelledotjpg.BootstrapContent.BuildArtifactManager
                        .moveReleaseToBuild(new java.io.File(lceFolder), this::appendLog);
                setProgress(95);

                appendLog("Writing vars.json...");
                markSetupDone();
                setProgress(100);

                appendLog("Reinstall complete!");
                stopElapsedTimer();

                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(this,
                            "Reinstall complete!", "Done", JOptionPane.INFORMATION_MESSAGE);
                    owner.dispose();
                });

            } catch (InterruptedException ignored) {
                appendLog("Reinstall cancelled.");
                stopElapsedTimer();
            } catch (Exception e) {
                appendLog("Reinstall failed: " + e.getMessage());
                e.printStackTrace();
                stopElapsedTimer();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    actionButton.setText("Close");
                    actionButton.setForeground(Color.BLACK);
                });
            }
        }, "rebuild-thread");
        rebuildThread.start();
    }

    private void runCommand(String[] cmd, String label, String workingDir) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new java.io.File(workingDir));
        pb.redirectErrorStream(true);
        activeProcess = pb.start();

        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    activeProcess.destroyForcibly();
                    throw new InterruptedException();
                }
                appendLog(line);
            }
        }

        int exit = activeProcess.waitFor();
        activeProcess = null;
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (exit != 0) throw new Exception(label + " exited with code " + exit);
    }

    private void deleteDirectory(java.io.File dir) throws java.io.IOException {
        java.nio.file.Files.walk(dir.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { java.nio.file.Files.delete(p); }
                    catch (java.io.IOException ignored) {}
                });
    }

    private void onCancel() {
        if ("Close".equals(actionButton.getText())) { owner.dispose(); return; }
        int res = JOptionPane.showConfirmDialog(this, "Cancel reinstall?",
                "Warning", JOptionPane.YES_NO_OPTION);
        if (res != JOptionPane.YES_OPTION) return;
        actionButton.setEnabled(false);
        if (activeProcess != null) activeProcess.destroyForcibly();
        if (rebuildThread != null) rebuildThread.interrupt();
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

    // -------------------------------------------------------------------------
    // vars.json helpers
    // -------------------------------------------------------------------------

    private static String readLceFolder() {
        try {
            java.io.File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return null;
            com.noelledotjpg.Data.VarsData vars = new com.google.gson.Gson()
                    .fromJson(java.nio.file.Files.readString(f.toPath()),
                            com.noelledotjpg.Data.VarsData.class);
            return vars != null ? vars.getLceFolder() : null;
        } catch (Exception e) { return null; }
    }

    private void markSetupDone() {
        try {
            java.io.File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return;
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            com.noelledotjpg.Data.VarsData vars;
            try (java.io.Reader r = new java.io.FileReader(f)) {
                vars = gson.fromJson(r, com.noelledotjpg.Data.VarsData.class);
            }
            if (vars == null) vars = new com.noelledotjpg.Data.VarsData();
            vars.setSetupDone(true);

            String lceFolder = vars.getLceFolder();
            if (lceFolder != null && !lceFolder.isEmpty()) {
                java.io.File exe = com.noelledotjpg.BootstrapContent.BuildArtifactManager
                        .activeExe(new java.io.File(lceFolder));
                if (exe.exists()) vars.setMinecraftExe(exe.getAbsolutePath());
            }

            java.nio.file.Files.writeString(f.toPath(), gson.toJson(vars));
        } catch (Exception e) {
            appendLog("Warning: could not update vars.json: " + e.getMessage());
        }
    }
}