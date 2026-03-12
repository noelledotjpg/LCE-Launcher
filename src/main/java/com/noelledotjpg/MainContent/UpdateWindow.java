package com.noelledotjpg.MainContent;

import com.noelledotjpg.Data.AppPaths;
import com.noelledotjpg.Data.UpdateChecker;
import com.noelledotjpg.Data.UpdateChecker.NightlyInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;

public class UpdateWindow {

    public static void showRebuild(Component parent) {
        JDialog progressDialog = new JDialog(
                parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent),
                "Reinstalling LCE",
                Dialog.ModalityType.APPLICATION_MODAL);
        progressDialog.setResizable(false);

        RebuildProgressPanel panel = new RebuildProgressPanel(progressDialog);
        progressDialog.setContentPane(panel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(parent);
        progressDialog.setVisible(true);
    }

    public static void checkAndPrompt(Component parent, String installedHash) {
        Thread t = new Thread(() -> {
            NightlyInfo latest;
            try {
                latest = UpdateChecker.fetchLatest();
            } catch (Exception e) {
                System.err.println("Update check failed: " + e.getMessage());
                return;
            }

            if (latest.commitHash().equalsIgnoreCase(
                    installedHash == null ? "" : installedHash)) return;

            String skipped = readSkippedHash();
            if (latest.commitHash().equalsIgnoreCase(
                    skipped == null ? "" : skipped)) return;

            SwingUtilities.invokeLater(() -> showUpdateDialog(parent, latest));
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private static String readSkippedHash() {
        try {
            java.io.File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return null;
            com.noelledotjpg.Data.VarsData vars = new com.google.gson.Gson()
                    .fromJson(java.nio.file.Files.readString(f.toPath()),
                            com.noelledotjpg.Data.VarsData.class);
            return vars != null ? vars.getSkippedCommitHash() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeSkippedHash(String hash) {
        try {
            java.io.File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return;
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            com.noelledotjpg.Data.VarsData vars;
            try (java.io.Reader r = new java.io.FileReader(f)) {
                vars = gson.fromJson(r, com.noelledotjpg.Data.VarsData.class);
            }
            if (vars == null) vars = new com.noelledotjpg.Data.VarsData();
            vars.setSkippedCommitHash(hash);
            java.nio.file.Files.writeString(f.toPath(), gson.toJson(vars));
        } catch (Exception e) {
            System.err.println("Could not write skipped hash: " + e.getMessage());
        }
    }

    private static void showUpdateDialog(Component parent, NightlyInfo latest) {
        JDialog dialog = new JDialog(
                parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent),
                "LCE Update Available",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setLayout(new BorderLayout());

        JPanel msgPanel = new JPanel();
        msgPanel.setLayout(new BoxLayout(msgPanel, BoxLayout.Y_AXIS));
        msgPanel.setBorder(new EmptyBorder(16, 20, 10, 20));

        JLabel bodyLabel = new JLabel(
                "<html>There is a new build available:<br>" +
                        "Nightly - " + latest.shortHash() + "</html>");
        bodyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

        msgPanel.add(bodyLabel);
        dialog.add(msgPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        JButton updateBtn = new JButton("Update");
        JButton skipBtn   = new JButton("Skip");
        JButton cancelBtn = new JButton("Cancel");

        updateBtn.setPreferredSize(new Dimension(90, 26));
        skipBtn.setPreferredSize(new Dimension(90, 26));
        cancelBtn.setPreferredSize(new Dimension(90, 26));

        btnPanel.add(updateBtn);
        btnPanel.add(skipBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dialog.dispose());
        skipBtn.addActionListener(e -> {
            writeSkippedHash(latest.commitHash());
            dialog.dispose();
        });
        updateBtn.addActionListener(e -> {
            dialog.dispose();
            showUpdateProgress(parent, latest);
        });

        dialog.pack();
        dialog.setMinimumSize(new Dimension(340, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void showUpdateProgress(Component parent, NightlyInfo latest) {
        JDialog progressDialog = new JDialog(
                parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent),
                "Updating \u2014 Nightly " + latest.shortHash(),
                Dialog.ModalityType.APPLICATION_MODAL);
        progressDialog.setResizable(false);

        UpdateProgressPanel panel = new UpdateProgressPanel(latest, progressDialog);
        progressDialog.setContentPane(panel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(parent);
        progressDialog.setVisible(true);
    }

    static class UpdateProgressPanel extends JPanel {

        private final JTextPane    consoleArea;
        private final JButton      actionButton;
        private final JProgressBar progressBar;
        private final JLabel       timeLabel;
        private final JDialog      owner;

        private Style timestampStyle;
        private Style logStyle;

        private Thread updateThread;
        private Timer  elapsedTimer;
        private long   startTime;

        UpdateProgressPanel(NightlyInfo latest, JDialog owner) {
            this.owner = owner;

            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setPreferredSize(new Dimension(515, 420));

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("Pulling update...");

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
            startUpdate(latest);
        }

        private void startUpdate(NightlyInfo latest) {
            updateThread = new Thread(() -> {
                try {
                    appendLog("Fetching latest nightly (" + latest.shortHash() + ")...");
                    runCommand(new String[]{"git", "fetch", "origin", "nightly"}, "git fetch");
                    setProgress(20);

                    appendLog("Checking out latest nightly...");
                    runCommand(new String[]{"git", "checkout", "FETCH_HEAD"}, "git checkout");
                    setProgress(35);

                    appendLog("Running CMake configure...");
                    runCommand(new String[]{"cmake", "-S", ".", "-B", "build",
                            "-G", "Visual Studio 17 2022", "-A", "x64"}, "cmake");
                    setProgress(55);

                    appendLog("Building Release...");
                    runCommand(new String[]{"cmake", "--build", "build",
                            "--config", "Release", "--target", "MinecraftClient"}, "build");
                    setProgress(90);

                    appendLog("Writing updated vars.json...");
                    writeUpdatedHash(latest.commitHash());
                    setProgress(100);

                    appendLog("Update complete!");
                    stopElapsedTimer();

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        Toolkit.getDefaultToolkit().beep();
                        JOptionPane.showMessageDialog(this,
                                "Update complete!\nNightly " + latest.shortHash() + " is now installed.",
                                "Done", JOptionPane.INFORMATION_MESSAGE);
                        owner.dispose();
                    });

                } catch (InterruptedException ignored) {
                    appendLog("Update cancelled.");
                    stopElapsedTimer();
                } catch (Exception e) {
                    appendLog("Update failed: " + e.getMessage());
                    e.printStackTrace();
                    stopElapsedTimer();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        actionButton.setText("Close");
                        actionButton.setForeground(Color.BLACK);
                    });
                }
            }, "update-thread");
            updateThread.start();
        }

        private void runCommand(String[] cmd, String label) throws Exception {
            String lceFolder = readLceFolder();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (lceFolder != null) pb.directory(new java.io.File(lceFolder));
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null)
                    appendLog(line);
            }

            int exit = proc.waitFor();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (exit != 0) throw new Exception(label + " exited with code " + exit);
        }

        private String readLceFolder() {
            try {
                java.io.File f = AppPaths.VARS_JSON.toFile();
                if (!f.exists()) return null;
                com.noelledotjpg.Data.VarsData vars = new com.google.gson.Gson()
                        .fromJson(java.nio.file.Files.readString(f.toPath()),
                                com.noelledotjpg.Data.VarsData.class);
                return vars != null ? vars.getLceFolder() : null;
            } catch (Exception e) {
                return null;
            }
        }

        private void writeUpdatedHash(String newHash) {
            try {
                java.io.File f = AppPaths.VARS_JSON.toFile();
                if (!f.exists()) return;

                com.google.gson.Gson gson =
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                com.noelledotjpg.Data.VarsData vars;
                try (java.io.Reader r = new java.io.FileReader(f)) {
                    vars = gson.fromJson(r, com.noelledotjpg.Data.VarsData.class);
                }
                if (vars == null) vars = new com.noelledotjpg.Data.VarsData();
                vars.setInstalledCommitHash(newHash);
                java.nio.file.Files.writeString(f.toPath(), gson.toJson(vars));
            } catch (Exception e) {
                appendLog("Warning: could not update vars.json hash: " + e.getMessage());
            }
        }

        private void onCancel() {
            if ("Close".equals(actionButton.getText())) { owner.dispose(); return; }
            int res = JOptionPane.showConfirmDialog(this, "Cancel update?",
                    "Warning", JOptionPane.YES_NO_OPTION);
            if (res != JOptionPane.YES_OPTION) return;
            actionButton.setEnabled(false);
            if (updateThread != null) updateThread.interrupt();
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

        private void stopElapsedTimer() {
            if (elapsedTimer != null) elapsedTimer.stop();
        }

        private void initStyles() {
            StyleContext sc    = new StyleContext();
            timestampStyle     = sc.addStyle("timestamp", null);
            StyleConstants.setForeground(timestampStyle, Color.GRAY);
            logStyle           = sc.addStyle("log", null);
            StyleConstants.setForeground(logStyle, Color.BLACK);
        }
    }

    static class RebuildProgressPanel extends JPanel {

        private final JTextPane    consoleArea;
        private final JButton      actionButton;
        private final JProgressBar progressBar;
        private final JLabel       timeLabel;
        private final JDialog      owner;

        private Style timestampStyle;
        private Style logStyle;

        private Thread rebuildThread;
        private Timer  elapsedTimer;
        private long   startTime;

        RebuildProgressPanel(JDialog owner) {
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
                                "Reinstall complete!",
                                "Done", JOptionPane.INFORMATION_MESSAGE);
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
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(workingDir));
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) appendLog(line);
            }

            int exit = proc.waitFor();
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

        private String readLceFolder() {
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
                com.google.gson.Gson gson =
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                com.noelledotjpg.Data.VarsData vars;
                try (java.io.Reader r = new java.io.FileReader(f)) {
                    vars = gson.fromJson(r, com.noelledotjpg.Data.VarsData.class);
                }
                if (vars == null) vars = new com.noelledotjpg.Data.VarsData();
                vars.setSetupDone(true);
                java.nio.file.Files.writeString(f.toPath(), gson.toJson(vars));
            } catch (Exception e) {
                appendLog("Warning: could not update vars.json: " + e.getMessage());
            }
        }

        private void onCancel() {
            if ("Close".equals(actionButton.getText())) { owner.dispose(); return; }
            int res = JOptionPane.showConfirmDialog(this, "Cancel reinstall?",
                    "Warning", JOptionPane.YES_NO_OPTION);
            if (res != JOptionPane.YES_OPTION) return;
            actionButton.setEnabled(false);
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

        private void stopElapsedTimer() {
            if (elapsedTimer != null) elapsedTimer.stop();
        }

        private void initStyles() {
            StyleContext sc = new StyleContext();
            timestampStyle  = sc.addStyle("timestamp", null);
            StyleConstants.setForeground(timestampStyle, Color.GRAY);
            logStyle = sc.addStyle("log", null);
            StyleConstants.setForeground(logStyle, Color.BLACK);
        }
    }
}