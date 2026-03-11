package com.noelledotjpg.TabContent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.noelledotjpg.Data.AppPaths;
import com.noelledotjpg.Data.PreferencesData;
import com.noelledotjpg.Data.VarsData;
import com.noelledotjpg.Data.LauncherUpdateChecker;
import com.noelledotjpg.Data.LauncherUpdateChecker.LauncherReleaseInfo;
import com.noelledotjpg.MainContent.LaunchArguments;
import com.noelledotjpg.MainContent.LauncherUpdateDialog;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.function.Consumer;

public class PreferencesTab extends JPanel {

    private static final int LABEL_WIDTH = 200;
    private static final int FIELD_WIDTH = 220;
    private static final int ROW_HEIGHT  = 21;
    private static final int SECTION_GAP = 16;
    private static final int ROW_GAP     = 8;

    private final String            exePath;
    private final PreferencesData   prefs;
    private final Gson              gson = new GsonBuilder().setPrettyPrinting().create();
    private final JComboBox<String> profileCombo;
    private final LaunchArguments   launchArgs;
    private final UpdateNotesTab    updateNotesTab;

    public PreferencesTab(PreferencesData prefs, JComboBox<String> profileCombo,
                          LaunchArguments launchArgs, String exePath,
                          UpdateNotesTab updateNotesTab) {
        this.prefs          = prefs;
        this.profileCombo   = profileCombo;
        this.launchArgs     = launchArgs;
        this.exePath        = exePath;
        this.updateNotesTab = updateNotesTab;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(buildLauncherSection());
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(buildAppearanceSection());
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(buildOtherSection());

        add(content, BorderLayout.NORTH);
    }

    private JPanel buildLauncherSection() {
        SectionBuilder s = new SectionBuilder("Launcher Preferences");
        s.addRow(checkboxRow(
                "Launch game in fullscreen",
                prefs.isFullscreen(),
                selected -> { prefs.setFullscreen(selected); save(); }
        ));
        s.addRow(comboRow(
                "Launcher visibility",
                new String[]{"Close when game starts", "Hide when game starts", "Keep launcher open"},
                prefs.getLauncherVisibility(),
                selected -> { prefs.setLauncherVisibility(selected); save(); }
        ));
        return s.build();
    }

    private JPanel buildAppearanceSection() {
        SectionBuilder s = new SectionBuilder("Appearance");
        s.addRow(comboFieldRow(
                "Update News page",
                new String[]{"Default", "Custom"},
                prefs.getNewsPageMode(),
                prefs.getNewsPageCustomUrl(),
                selected -> {
                    prefs.setNewsPageMode(selected);
                    save();
                    updateNotesTab.reload(prefs.getResolvedNewsUrl());
                },
                text -> {
                    prefs.setNewsPageCustomUrl(text);
                    save();
                    updateNotesTab.reload(prefs.getResolvedNewsUrl());
                }
        ));
        return s.build();
    }

    private JPanel buildOtherSection() {
        SectionBuilder s = new SectionBuilder("Other");
        s.addRow(buttonRow(
                "Desktop shortcut",
                "Create shortcut for current profile",
                "Create",
                e -> createDesktopShortcut()
        ));
        s.addRow(checkboxComboRow(
                "Check for LCE updates",
                prefs.isCheckForUpdates(),
                new String[]{"When launcher opens", "Every 24 hours", "Every week"},
                prefs.getUpdateFrequency(),
                checked  -> { prefs.setCheckForUpdates(checked); save(); },
                selected -> { prefs.setUpdateFrequency(selected); save(); }
        ));
        s.addRow(checkboxComboRow(
                "Check for launcher updates",
                prefs.isCheckForLauncherUpdates(),
                new String[]{"When launcher opens", "Every 24 hours", "Every week"},
                prefs.getLauncherUpdateFrequency(),
                checked  -> { prefs.setCheckForLauncherUpdates(checked); save(); },
                selected -> { prefs.setLauncherUpdateFrequency(selected); save(); }
        ));
        s.addRow(buttonRow(
                "Launcher update",
                "Check for a new launcher version",
                "Check Now",
                e -> checkForLauncherUpdate()
        ));
        s.addRow(buttonRow(
                "Redownload LCE",
                "Delete and re-clone the repo",
                "Redownload",
                e -> redownloadRepo()
        ));
        return s.build();
    }

    private JPanel checkboxRow(String text, boolean selected, Consumer<Boolean> onChange) {
        JPanel row = newRow();
        JCheckBox box = new JCheckBox();
        box.setSelected(selected);
        box.addActionListener(e -> onChange.accept(box.isSelected()));
        JLabel label = new JLabel(text);
        label.setLabelFor(box);
        row.add(fixedLabel(label));
        row.add(box);
        return row;
    }

    private JPanel checkboxComboRow(String labelText, boolean checked, String[] options,
                                    String current, Consumer<Boolean> onCheck,
                                    Consumer<String> onCombo) {
        JPanel row = newRow();

        JCheckBox box = new JCheckBox();
        box.setSelected(checked);

        JLabel label = new JLabel(labelText);
        label.setLabelFor(box);

        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(current != null && !current.isEmpty() ? current : options[0]);
        combo.setEnabled(checked);
        combo.setPreferredSize(new Dimension(160, ROW_HEIGHT));
        combo.setMaximumSize(new Dimension(160, ROW_HEIGHT));

        box.addActionListener(e -> {
            combo.setEnabled(box.isSelected());
            onCheck.accept(box.isSelected());
        });
        combo.addActionListener(e -> onCombo.accept((String) combo.getSelectedItem()));

        JPanel checkLabel = new JPanel();
        checkLabel.setLayout(new BoxLayout(checkLabel, BoxLayout.X_AXIS));
        checkLabel.setOpaque(false);
        checkLabel.setPreferredSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
        checkLabel.setMinimumSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
        checkLabel.setMaximumSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
        checkLabel.add(box);
        checkLabel.add(Box.createHorizontalStrut(4));
        checkLabel.add(label);

        row.add(checkLabel);
        row.add(combo);
        return row;
    }

    private JPanel comboFieldRow(String labelText, String[] options, String current,
                                 String currentText, Consumer<String> onCombo,
                                 Consumer<String> onField) {
        JPanel row = newRow();

        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(current != null && !current.isEmpty() ? current : options[0]);
        combo.setPreferredSize(new Dimension(80, ROW_HEIGHT));
        combo.setMaximumSize(new Dimension(80, ROW_HEIGHT));

        JTextField field = new JTextField(currentText != null ? currentText : "");
        field.setEnabled("Custom".equals(combo.getSelectedItem()));
        field.setPreferredSize(new Dimension(200, ROW_HEIGHT));
        field.setMaximumSize(new Dimension(200, ROW_HEIGHT));

        combo.addActionListener(e -> {
            boolean custom = "Custom".equals(combo.getSelectedItem());
            field.setEnabled(custom);
            onCombo.accept((String) combo.getSelectedItem());
        });
        field.addActionListener(e -> onField.accept(field.getText().trim()));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                onField.accept(field.getText().trim());
            }
        });

        row.add(fixedLabel(new JLabel(labelText)));
        row.add(combo);
        row.add(Box.createHorizontalStrut(6));
        row.add(field);
        return row;
    }

    private JPanel comboRow(String labelText, String[] options,
                            String current, Consumer<String> onChange) {
        JPanel row = newRow();
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(current != null && !current.isEmpty() ? current : options[0]);
        combo.setPreferredSize(new Dimension(200, 21));
        combo.setMaximumSize(new Dimension(230, 21));
        combo.addActionListener(e -> onChange.accept((String) combo.getSelectedItem()));
        JLabel label = new JLabel(labelText);
        label.setLabelFor(combo);
        row.add(fixedLabel(label));
        row.add(combo);
        return row;
    }

    private JPanel buttonRow(String labelText, String description,
                             String buttonText, java.awt.event.ActionListener action) {
        JPanel row = newRow();
        JLabel desc = new JLabel(description);
        JButton button = new JButton(buttonText);
        button.setPreferredSize(new Dimension(FIELD_WIDTH / 2, ROW_HEIGHT));
        button.addActionListener(action);
        row.add(fixedLabel(new JLabel(labelText)));
        row.add(desc);
        row.add(Box.createHorizontalStrut(12));
        row.add(button);
        return row;
    }

    private static class SectionBuilder {
        private final JPanel panel;
        private boolean firstRow = true;

        SectionBuilder(String title) {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createEtchedBorder(),
                            title,
                            TitledBorder.LEFT,
                            TitledBorder.TOP),
                    BorderFactory.createEmptyBorder(6, 8, 10, 8)
            ));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void addRow(JPanel row) {
            if (!firstRow) panel.add(Box.createVerticalStrut(ROW_GAP));
            panel.add(row);
            firstRow = false;
        }

        JPanel build() {
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
            return panel;
        }
    }

    private static JPanel newRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        row.setOpaque(false);
        return row;
    }

    private static JLabel fixedLabel(JLabel label) {
        label.setPreferredSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
        label.setMinimumSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
        label.setMaximumSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
        return label;
    }

    private void save() {
        try {
            Files.createDirectories(AppPaths.PREFERENCES_JSON.getParent());
            try (Writer w = Files.newBufferedWriter(AppPaths.PREFERENCES_JSON)) {
                gson.toJson(prefs, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkForLauncherUpdate() {
        // Disable button while checking to avoid double-clicks
        // We find it via the parent row, so just run on a background thread and re-enable after
        new Thread(() -> {
            try {
                LauncherReleaseInfo release = LauncherUpdateChecker.fetchIfNewer();

                SwingUtilities.invokeLater(() -> {
                    if (release == null) {
                        JOptionPane.showMessageDialog(this,
                                "You are already on the latest version (" + VarsData.VERSION + ").",
                                "No Update Available", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    int choice = JOptionPane.showConfirmDialog(this,
                            "A new launcher version is available: " + release.tagName() + "\n" +
                                    "Your version: v" + VarsData.VERSION + "\n\n" +
                                    "Download and install now?",
                            "Launcher Update Available", JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);

                    if (choice == JOptionPane.YES_OPTION)
                        LauncherUpdateDialog.show(this, release);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                "Could not check for updates:\n" + ex.getMessage(),
                                "Update Check Failed", JOptionPane.ERROR_MESSAGE));
            }
        }, "launcher-update-check").start();
    }

    private void redownloadRepo() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "This will delete the entire LCE repo folder and re-run setup from scratch.\n" +
                        "Make sure you have backups of any worlds or saves you want to keep.\n\n" +
                        "Are you sure?",
                "Redownload LCE", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            VarsData vars;
            try (Reader r = Files.newBufferedReader(AppPaths.VARS_JSON)) {
                vars = new Gson().fromJson(r, VarsData.class);
            }
            if (vars == null || vars.getLceFolder() == null || vars.getLceFolder().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "LCE folder path not found in vars.json.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            File repoFolder = new File(vars.getLceFolder());
            if (repoFolder.exists()) deleteDirectory(repoFolder);

            vars.setSetupDone(false);
            vars.setInstalledCommitHash(null);
            Files.writeString(AppPaths.VARS_JSON,
                    new GsonBuilder().setPrettyPrinting().create().toJson(vars));

            JOptionPane.showMessageDialog(this,
                    "Repo deleted. Restart the launcher to re-run setup.",
                    "Done", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to delete repo:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteDirectory(File dir) throws IOException {
        Files.walk(dir.toPath())
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException ignored) {}
                });
    }

    private void createDesktopShortcut() {
        if (profileCombo.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "No profile selected.");
            return;
        }
        String profile  = profileCombo.getSelectedItem().toString();
        File   exe      = new File(exePath);
        File   shortcut = new File(System.getProperty("user.home") + "/Desktop",
                "LCE - " + profile + ".lnk");
        try {
            createWindowsShortcut(exe, shortcut);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to create shortcut:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createWindowsShortcut(File target, File shortcut)
            throws IOException, InterruptedException {
        String ps = "$s=(New-Object -COM WScript.Shell).CreateShortcut('"
                + shortcut.getAbsolutePath().replace("\\", "\\\\") + "');"
                + "$s.TargetPath='" + target.getAbsolutePath().replace("\\", "\\\\") + "';"
                + "$s.WorkingDirectory='" + target.getParentFile().getAbsolutePath().replace("\\", "\\\\") + "';"
                + "$s.Save();";
        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
        int exit = pb.start().waitFor();
        if (exit != 0) throw new IOException("PowerShell exited with code " + exit);
        JOptionPane.showMessageDialog(this, "Shortcut created: " + shortcut.getName());
    }
}