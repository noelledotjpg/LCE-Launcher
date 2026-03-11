package com.noelledotjpg.MainContent;

import com.noelledotjpg.Data.LauncherUpdateChecker.LauncherReleaseInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.Duration;

public class LauncherUpdateDialog {

    public static void show(Component parent, LauncherReleaseInfo release) {
        JDialog dialog = new JDialog(
                parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent),
                "Updating Launcher \u2014 " + release.tagName(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);

        LauncherUpdatePanel panel = new LauncherUpdatePanel(release, dialog);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    static class LauncherUpdatePanel extends JPanel {

        private final JTextPane    consoleArea;
        private final JButton      cancelButton;
        private final JProgressBar progressBar;
        private final JLabel       timeLabel;
        private final JDialog      owner;

        private Style timestampStyle;
        private Style logStyle;

        private Thread downloadThread;
        private Timer  elapsedTimer;
        private long   startTime;

        LauncherUpdatePanel(LauncherReleaseInfo release, JDialog owner) {
            this.owner = owner;

            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setPreferredSize(new Dimension(515, 300));

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("Connecting...");

            timeLabel = new JLabel("Time Elapsed: 00:00:00");

            JPanel topPanel = new JPanel(new BorderLayout(5, 5));
            topPanel.add(progressBar, BorderLayout.CENTER);
            topPanel.add(timeLabel,   BorderLayout.EAST);
            add(topPanel, BorderLayout.NORTH);

            consoleArea = new JTextPane();
            consoleArea.setEditable(false);
            consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            ((DefaultCaret) consoleArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
            add(new JScrollPane(consoleArea), BorderLayout.CENTER);

            cancelButton = new JButton("Cancel");
            cancelButton.setForeground(Color.RED);
            cancelButton.addActionListener(e -> onCancel());
            add(cancelButton, BorderLayout.SOUTH);

            initStyles();
            startElapsedTimer();
            startDownload(release);
        }

        private void startDownload(LauncherReleaseInfo release) {
            downloadThread = new Thread(() -> {
                Path msiPath = null;
                try {
                    appendLog("Downloading " + release.tagName() + "...");

                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.ALWAYS)
                            .build();

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(release.msiDownloadUrl()))
                            .timeout(Duration.ofMinutes(5))
                            .GET()
                            .build();

                    HttpRequest headReq = HttpRequest.newBuilder()
                            .uri(URI.create(release.msiDownloadUrl()))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    long totalBytes = -1;
                    try {
                        HttpResponse<Void> headResp = client.send(headReq, HttpResponse.BodyHandlers.discarding());
                        totalBytes = headResp.headers().firstValueAsLong("content-length").orElse(-1);
                    } catch (Exception ignored) {}

                    final long total = totalBytes;

                    msiPath = Path.of(System.getenv("TEMP"),
                            "LCE-Launcher-" + release.version() + ".msi");

                    HttpResponse<InputStream> resp = client.send(req,
                            HttpResponse.BodyHandlers.ofInputStream());

                    if (resp.statusCode() != 200)
                        throw new Exception("Download failed: HTTP " + resp.statusCode());

                    try (InputStream in = resp.body()) {
                        long downloaded = 0;
                        byte[] buf = new byte[8192];
                        int read;

                        try (var out = Files.newOutputStream(msiPath,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            while ((read = in.read(buf)) != -1) {
                                if (Thread.currentThread().isInterrupted())
                                    throw new InterruptedException();
                                out.write(buf, 0, read);
                                downloaded += read;
                                if (total > 0) {
                                    int pct = (int) (downloaded * 100 / total);
                                    setProgress(pct);
                                    long mb = downloaded / (1024 * 1024);
                                    long totalMb = total / (1024 * 1024);
                                    setProgressString(mb + " / " + totalMb + " MB");
                                }
                            }
                        }
                    }

                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedException();

                    setProgress(100);
                    appendLog("Download complete. Launching installer...");
                    stopElapsedTimer();

                    final Path finalMsiPath = msiPath;
                    SwingUtilities.invokeLater(() -> {
                        cancelButton.setEnabled(false);
                        progressBar.setIndeterminate(true);
                        progressBar.setString("Installing...");
                    });

                    new ProcessBuilder(
                            "msiexec", "/i", msiPath.toString(), "/passive"
                    ).start();

                    Thread.sleep(1500);
                    System.exit(0);

                } catch (InterruptedException ignored) {
                    appendLog("Update cancelled.");
                    stopElapsedTimer();
                    if (msiPath != null) {
                        try { Files.deleteIfExists(msiPath); } catch (Exception ignored2) {}
                    }
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        cancelButton.setText("Close");
                        cancelButton.setForeground(Color.BLACK);
                        cancelButton.setEnabled(true);
                    });
                } catch (Exception e) {
                    appendLog("Update failed: " + e.getMessage());
                    e.printStackTrace();
                    stopElapsedTimer();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        cancelButton.setText("Close");
                        cancelButton.setForeground(Color.BLACK);
                        cancelButton.setEnabled(true);
                    });
                }
            }, "launcher-update-download");
            downloadThread.setDaemon(true);
            downloadThread.start();
        }

        private void onCancel() {
            if ("Close".equals(cancelButton.getText())) { owner.dispose(); return; }
            int res = JOptionPane.showConfirmDialog(this, "Cancel launcher update?",
                    "Warning", JOptionPane.YES_NO_OPTION);
            if (res != JOptionPane.YES_OPTION) return;
            cancelButton.setEnabled(false);
            if (downloadThread != null) downloadThread.interrupt();
        }

        private void setProgress(int pct) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(pct);
                progressBar.setString(pct + "%");
            });
        }

        private void setProgressString(String text) {
            SwingUtilities.invokeLater(() -> progressBar.setString(text));
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