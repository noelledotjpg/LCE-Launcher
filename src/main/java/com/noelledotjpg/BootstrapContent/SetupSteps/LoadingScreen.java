package com.noelledotjpg.BootstrapContent.SetupSteps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.noelledotjpg.BootstrapContent.LCESetup;
import com.noelledotjpg.Data.AppPaths;
import com.noelledotjpg.Data.VarsData;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;

public class LoadingScreen extends JPanel {

    private static final int WIDTH  = 515;
    private static final int HEIGHT = 420;

    private final JTextPane    consoleArea;
    private final JButton      actionButton;
    private final JProgressBar progressBar;
    private final JLabel       timeLabel;

    private Thread   setupThread;
    private LCESetup currentSetup;
    private Timer    elapsedTimer;
    private long     startTime;

    private Runnable onCancelBuild;
    private Runnable onLaunch;

    private Style timestampStyle;
    private Style logStyle;

    private static final boolean TEST_MODE = false;

    public LoadingScreen() {
        setLayout(new BorderLayout(0, 10));
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        timeLabel = new JLabel("Time Elapsed: 00:00:00");

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(UIManager.getColor("Panel.background"));
        topPanel.add(progressBar, BorderLayout.CENTER);
        topPanel.add(timeLabel,   BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        consoleArea = new JTextPane();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ((DefaultCaret) consoleArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        add(new JScrollPane(consoleArea), BorderLayout.CENTER);

        initStyles();

        actionButton = new JButton("Cancel Build");
        actionButton.setForeground(Color.RED);
        actionButton.addActionListener(e -> onActionButton());
        add(actionButton, BorderLayout.SOUTH);
    }

    public boolean isTestMode() { return TEST_MODE; }

    public void setOnCancelBuild(Runnable r) { this.onCancelBuild = r; }
    public void setOnLaunch(Runnable r)      { this.onLaunch = r; }

    public boolean isBuildAlreadyDone() {
        try {
            File f = AppPaths.VARS_JSON.toFile();
            if (!f.exists()) return false;
            return new Gson().fromJson(Files.readString(f.toPath()), VarsData.class).isSetupDone();
        } catch (Exception e) {
            return false;
        }
    }

    public void startSetup(String username, String lcePath, String vsPath, String cmakePath) {
        reset();

        if (isBuildAlreadyDone()) {
            appendLog("Build already completed. Launching directly...");
            SwingUtilities.invokeLater(() -> { if (onLaunch != null) onLaunch.run(); });
            return;
        }

        startElapsedTimer();
        setupThread = new Thread(() -> {
            try {
                currentSetup = new LCESetup(username, lcePath, vsPath, cmakePath, TEST_MODE);
                currentSetup.setOutputConsumer(this::appendLog);
                currentSetup.setProgressConsumer(this::setProgress);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(true);
                    progressBar.setString("Cloning...");
                });

                currentSetup.runSetup();

                markBuildComplete();
                stopElapsedTimer();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    bringToFront();
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(this, "Build Complete!", "Info", JOptionPane.INFORMATION_MESSAGE);
                    actionButton.setText("Go to Launcher");
                    actionButton.setForeground(Color.BLUE);
                });
            } catch (Exception e) {
                stopElapsedTimer();
                cleanup();
                appendLog("Setup failed: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    actionButton.setEnabled(true);
                });
            }
        });
        setupThread.start();
    }

    private void bringToFront() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.setVisible(true);
            if (window instanceof Frame f && f.getState() == Frame.ICONIFIED)
                f.setState(Frame.NORMAL);
            window.toFront();
            window.requestFocus();
        }
    }

    private void onActionButton() {
        if ("Go to Launcher".equals(actionButton.getText())) {
            if (onLaunch != null) onLaunch.run();
            return;
        }

        int res = JOptionPane.showConfirmDialog(this, "Cancel build?", "Warning", JOptionPane.YES_NO_OPTION);
        if (res != JOptionPane.YES_OPTION) return;

        actionButton.setEnabled(false);
        appendLog("Cancelling...");
        cleanup();

        if (onCancelBuild != null) onCancelBuild.run();
    }

    private void cleanup() {
        if (currentSetup != null) currentSetup.stopProcess();
        if (setupThread != null && setupThread.isAlive()) {
            setupThread.interrupt();
            try { setupThread.join(3000); } catch (InterruptedException ignored) {}
        }
        stopElapsedTimer();
    }

    private void reset() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("0%");
        timeLabel.setText("Time Elapsed: 00:00:00");
        actionButton.setText("Cancel Build");
        actionButton.setForeground(Color.RED);
        actionButton.setEnabled(true);
        try {
            consoleArea.getStyledDocument().remove(0, consoleArea.getStyledDocument().getLength());
        } catch (BadLocationException ignored) {}
    }

    private void markBuildComplete() {
        try {
            File f = AppPaths.VARS_JSON.toFile();
            f.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            VarsData data = f.exists()
                    ? gson.fromJson(Files.readString(f.toPath()), VarsData.class)
                    : new VarsData();
            data.setSetupDone(true);
            Files.writeString(f.toPath(), gson.toJson(data));
            appendLog("Build marked as complete.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setProgress(int percent) {
        SwingUtilities.invokeLater(() -> {
            if (progressBar.isIndeterminate()) progressBar.setIndeterminate(false);
            progressBar.setValue(Math.min(percent, 100));
            progressBar.setString(progressBar.getValue() + "%");
        });
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = consoleArea.getStyledDocument();
            String ts = LocalTime.now().withNano(0).toString();
            try {
                doc.insertString(doc.getLength(), String.format("%10s", ts), timestampStyle);
                doc.insertString(doc.getLength(), "  " + text + "\n", logStyle);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void startElapsedTimer() {
        startTime    = System.currentTimeMillis();
        elapsedTimer = new Timer(1000, e -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            timeLabel.setText("Time Elapsed: " + formatElapsed(elapsed));
        });
        elapsedTimer.start();
    }

    private void stopElapsedTimer() {
        if (elapsedTimer != null) elapsedTimer.stop();
    }

    private String formatElapsed(long seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private void initStyles() {
        StyleContext sc = new StyleContext();
        timestampStyle  = sc.addStyle("timestamp", null);
        StyleConstants.setForeground(timestampStyle, Color.GRAY);
        logStyle = sc.addStyle("log", null);
        StyleConstants.setForeground(logStyle, Color.BLACK);
    }
}