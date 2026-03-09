package com.noelledotjpg.TabContent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.noelledotjpg.MainContent.LaunchArguments;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PreferencesTab extends JPanel {

    private static final Path PREFS_JSON = Paths.get("src/main/resources/data/launcher/preferences.json");
    private final String exePath;
    private PreferencesData prefs;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JComboBox<String> profileCombo;
    private LaunchArguments launchArgs;

    public PreferencesTab(PreferencesData prefs, JComboBox<String> profileCombo, LaunchArguments launchArgs, String exePath) {
        this.prefs = prefs;
        this.profileCombo = profileCombo;
        this.launchArgs = launchArgs;
        this.exePath = exePath;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Launcher Preferences Section ---
        JPanel launcherPanel = new JPanel();
        launcherPanel.setLayout(new BoxLayout(launcherPanel, BoxLayout.Y_AXIS));
        launcherPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Launcher Preferences", TitledBorder.LEFT, TitledBorder.TOP
        ));
        launcherPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        launcherPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // allow vertical expansion

        launcherPanel.add(createLeftAlignedCheckbox("Launch game in Fullscreen", prefs.isFullscreen(),
                selected -> {
                    prefs.setFullscreen(selected);
                    save();
                }));

        launcherPanel.add(Box.createVerticalStrut(10));

        launcherPanel.add(createLeftAlignedCombo("Launcher Visibility: ",
                new String[]{"Close when game starts", "Hide when game starts", "Keep launcher open"},
                prefs.getLauncherVisibility(), selected -> {
                    prefs.setLauncherVisibility(selected);
                    save();
                }));

        add(launcherPanel);
        add(Box.createVerticalStrut(15));

        // --- Other Section ---
        JPanel otherPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        otherPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Other", TitledBorder.LEFT, TitledBorder.TOP
        ));
        otherPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        otherPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JButton createShortcutButton = new JButton("Create Shortcut");
        createShortcutButton.addActionListener(e -> createDesktopShortcut());
        otherPanel.add(new JLabel("Create desktop shortcut from current profile: "));
        otherPanel.add(createShortcutButton);

        add(otherPanel);
    }

    /** Utility to create a left-aligned checkbox panel */
    private JPanel createLeftAlignedCheckbox(String text, boolean selected, java.util.function.Consumer<Boolean> callback) {
        JCheckBox checkbox = new JCheckBox(text, selected);
        checkbox.addActionListener(e -> callback.accept(checkbox.isSelected()));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.add(checkbox);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /** Utility to create a left-aligned combo box panel */
    private JPanel createLeftAlignedCombo(String labelText, String[] options, String selected,
                                          java.util.function.Consumer<String> callback) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel label = new JLabel(labelText);
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(selected != null && !selected.isEmpty() ? selected : options[0]);
        combo.addActionListener(e -> callback.accept((String) combo.getSelectedItem()));

        panel.add(label);
        panel.add(combo);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private void save() {
        try {
            Files.createDirectories(PREFS_JSON.getParent());
            try (Writer writer = Files.newBufferedWriter(PREFS_JSON)) {
                gson.toJson(prefs, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createWindowsShortcut(File target, File shortcut) throws IOException, InterruptedException {
        String ps = "$s=(New-Object -COM WScript.Shell).CreateShortcut('" + shortcut.getAbsolutePath().replace("\\", "\\\\") + "');" +
                "$s.TargetPath='" + target.getAbsolutePath().replace("\\", "\\\\") + "';" +
                "$s.WorkingDirectory='" + target.getParentFile().getAbsolutePath().replace("\\", "\\\\") + "';" +
                "$s.Save();";

        ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
        Process p = builder.start();
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("Failed to create shortcut: " + shortcut.getName());
        JOptionPane.showMessageDialog(this, "Shortcut created: " + shortcut.getName());
    }

    private void createDesktopShortcut() {
        if (profileCombo.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "No profile selected!");
            return;
        }

        String profile = profileCombo.getSelectedItem().toString();
        File exe = new File(exePath);
        File shortcut = new File(System.getProperty("user.home") + "/Desktop", "LCE - " + profile + ".lnk");

        try {
            createWindowsShortcut(exe, shortcut);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to create shortcut:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}