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
import java.util.function.Consumer;

public class PreferencesTab extends JPanel {

    private static final Path PREFS_JSON =
            Paths.get("src/main/resources/data/launcher/preferences.json");

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

    public PreferencesTab(PreferencesData prefs, JComboBox<String> profileCombo,
                          LaunchArguments launchArgs, String exePath) {
        this.prefs        = prefs;
        this.profileCombo = profileCombo;
        this.launchArgs   = launchArgs;
        this.exePath      = exePath;

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
        // appearance settings go here
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

    private JPanel comboRow(String labelText, String[] options,
                            String current, Consumer<String> onChange) {
        JPanel row = newRow();
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(current != null && !current.isEmpty() ? current : options[0]);
        combo.setPreferredSize(new Dimension(120, 21));
        combo.setMaximumSize(new Dimension(200, 21));
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
            Files.createDirectories(PREFS_JSON.getParent());
            try (Writer w = Files.newBufferedWriter(PREFS_JSON)) {
                gson.toJson(prefs, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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