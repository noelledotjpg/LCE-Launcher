package com.noelledotjpg.BootstrapContent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;

public class LoadingScreen extends JPanel {

    private final int WIDTH = 515;
    private final int HEIGHT = 420;

    private final JTextPane consoleArea;
    private final JButton stopButton;
    private final JProgressBar progressBar;
    private final JLabel timeLabel;
    private Thread setupThread;
    private LCESetup currentSetup;

    private Timer elapsedTimer;

    private final String launcherVarsPath = "src/main/resources/data/launcher/vars.json";
    private final boolean testMode;

    private long startTime;

    private Style timestampStyle;
    private Style logStyle;

    public LoadingScreen(boolean testMode) {
        this.testMode = testMode;

        setLayout(new BorderLayout(0, 10));
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(UIManager.getColor("Panel.background"));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        topPanel.add(progressBar, BorderLayout.CENTER);

        timeLabel = new JLabel("Time Elapsed: 00:00:00");
        topPanel.add(timeLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        consoleArea = new JTextPane();
        consoleArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(consoleArea);
        add(scroll, BorderLayout.CENTER);

        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        StyleContext sc = new StyleContext();
        timestampStyle = sc.addStyle("timestamp", null);
        StyleConstants.setForeground(timestampStyle, Color.GRAY);
        StyleConstants.setAlignment(timestampStyle, StyleConstants.ALIGN_RIGHT);

        logStyle = sc.addStyle("log", null);
        StyleConstants.setForeground(logStyle, Color.BLACK);
        StyleConstants.setAlignment(logStyle, StyleConstants.ALIGN_LEFT);

        stopButton = new JButton("<html>Cancel Build</html>");
        stopButton.addActionListener(e -> stopBuildOrLaunch());
        stopButton.setForeground(Color.RED);
        add(stopButton, BorderLayout.SOUTH);
    }

    public boolean isTestMode() {
        return testMode;
    }

    private void startElapsedTimer() {
        startTime = System.currentTimeMillis();
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
        long hrs = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hrs, mins, secs);
    }

    public boolean isBuildAlreadyDone() {
        try {
            File f = new File(launcherVarsPath);
            if (!f.exists()) return false;
            Gson gson = new Gson();
            LauncherVars vars = gson.fromJson(Files.readString(f.toPath()), LauncherVars.class);
            return vars.setupDone;
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface Step { void run() throws Exception; }

    public void startSetup(String username, String lcePath, String vsPath, String cmakePath, Runnable onLauncherStart) {
        if (isBuildAlreadyDone()) {
            appendLog("Build already completed. Launching directly...");
            SwingUtilities.invokeLater(onLauncherStart);
            return;
        }

        startElapsedTimer();
        setupThread = new Thread(() -> {
            try {
                currentSetup = new LCESetup(username, lcePath, vsPath, cmakePath, false);
                currentSetup.setOutputConsumer(this::appendLog);
                currentSetup.setProgressConsumer(this::setProgressDynamic);

                currentSetup.runSetup();

                markBuildComplete();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,"Build Complete!","Info",JOptionPane.INFORMATION_MESSAGE);
                    stopButton.setText("Go to Launcher");
                    stopButton.setForeground(Color.BLUE);
                });

                stopElapsedTimer();
            } catch (Exception e) {
                stopElapsedTimer();
                appendLog("Setup failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
        setupThread.start();
    }

    private void setProgressDynamic(int percent) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(Math.min(percent, 100));
            progressBar.setString(progressBar.getValue() + "%");
        });
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = consoleArea.getStyledDocument();
            String timestamp = LocalTime.now().withNano(0).toString();
            try {
                doc.insertString(doc.getLength(), String.format("%10s", timestamp), timestampStyle);
                doc.insertString(doc.getLength(), "  " + text + "\n", logStyle);
            } catch (BadLocationException e) { e.printStackTrace(); }
        });
    }

    private void stopBuildOrLaunch() {
        if (stopButton.getText().equals("Go to Launcher")) {
            appendLog("Launching main launcher...");
            SwingUtilities.invokeLater(() -> {
                try {
                    com.noelledotjpg.Main launcher = new com.noelledotjpg.Main();
                    launcher.setVisible(true);
                } catch (Exception e) {
                    appendLog("Failed to launch main: " + e.getMessage());
                    e.printStackTrace();
                }
                SwingUtilities.getWindowAncestor(this).dispose();
            });
        } else {
            int res = JOptionPane.showConfirmDialog(this,"Cancel build?","Warning",JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION){
                if(currentSetup != null) currentSetup.stopProcess();
                if(setupThread != null && setupThread.isAlive()) setupThread.interrupt();
                appendLog("Build stopped by user.");
                stopElapsedTimer();
            }
        }
    }

    private void markBuildComplete() {
        try {
            File file = new File(launcherVarsPath);
            file.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            VarsData existingData = new VarsData();
            if (file.exists()) {
                existingData = gson.fromJson(Files.readString(file.toPath()), VarsData.class);
            }

            existingData.setSetupDone(true);

            Files.writeString(file.toPath(), gson.toJson(existingData));
            appendLog("Build marked as complete in vars.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class LauncherVars {
        boolean setupDone;
        public LauncherVars(boolean setupDone) { this.setupDone = setupDone; }
    }
}