package com.noelledotjpg.BootstrapContent.SetupSteps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class SetupStepBuildTools extends JPanel {

    private final JTextField vsField;
    private final JTextField cmakeField;
    private final JButton    continueButton;
    private final JButton    cancelButton;
    private final JLabel     warningLabel;

    public SetupStepBuildTools() {
        setLayout(null);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel logo = new JLabel(new ImageIcon(getClass().getResource("/img/logo.png")));
        logo.setBounds(SetupLayout.CENTER_X, 10, 274, 59);
        add(logo);

        vsField    = addTitledField("Visual Studio 2022", " VS2022 folder",    80,  "https://aka.ms/vs/17/release/vs_community.exe");
        cmakeField = addTitledField("CMake",              " CMake executable", 150, "https://cmake.org/download/#latest");

        warningLabel = new JLabel();
        warningLabel.setBounds(10, 215, SetupLayout.INNER_WIDTH, 18);
        warningLabel.setForeground(Color.RED);
        warningLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(warningLabel);

        continueButton = new JButton("Continue");
        continueButton.setBounds(SetupLayout.BTN_LEFT_X, 238, 100, 25);
        add(continueButton);

        cancelButton = new JButton("Back");
        cancelButton.setBounds(SetupLayout.BTN_RIGHT_X, 238, 100, 25);
        add(cancelButton);

        autoDetect();
        updateWarning();
    }

    public String getVsPath()          { return vsField.getText().trim(); }
    public String getCmakePath()       { return cmakeField.getText().trim(); }
    public JButton getContinueButton() { return continueButton; }
    public JButton getCancelButton()   { return cancelButton; }

    private void autoDetect() {
        String vsPath    = "";
        String cmakePath = "";

        try {
            File vswhere = new File("C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe");
            if (vswhere.exists()) {
                Process p = new ProcessBuilder(vswhere.getAbsolutePath(),
                        "-latest", "-products", "*",
                        "-requires", "Microsoft.Component.MSBuild",
                        "-property", "installationPath").start();
                vsPath = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
                p.waitFor();
            }
        } catch (Exception ignored) {}

        if (vsPath == null || vsPath.isEmpty()) {
            for (String p : System.getenv("PATH").split(";")) {
                if (new File(p, "devenv.exe").exists()) {
                    vsPath = new File(p).getParentFile().getParent();
                    break;
                }
            }
        }

        File cmakeDefault = new File("C:\\Program Files\\CMake\\bin\\cmake.exe");
        if (cmakeDefault.exists()) {
            cmakePath = cmakeDefault.getAbsolutePath();
        } else {
            for (String p : System.getenv("PATH").split(";")) {
                if (new File(p, "cmake.exe").exists()) { cmakePath = new File(p, "cmake.exe").getAbsolutePath(); break; }
            }
        }

        setField(vsField,    vsPath,    " VS2022 folder");
        setField(cmakeField, cmakePath, " CMake executable");
    }

    private void setField(JTextField field, String value, String placeholder) {
        if (value == null || value.isEmpty()) {
            field.setText(placeholder);
            field.setForeground(Color.GRAY);
        } else {
            field.setText(value);
            field.setForeground(Color.BLACK);
        }
    }

    private void updateWarning() {
        boolean vsMissing    = vsField.getForeground().equals(Color.GRAY)    || vsField.getText().isBlank();
        boolean cmakeMissing = cmakeField.getForeground().equals(Color.GRAY) || cmakeField.getText().isBlank();
        warningLabel.setText(vsMissing || cmakeMissing
                ? "<html><u>Please make sure Visual Studio and CMake are installed!</u></html>"
                : "");
    }

    private JTextField addTitledField(String title, String placeholder, int y, String downloadUrl) {
        int padding = 10;

        JPanel container = new JPanel(null);
        container.setBounds(10, y, SetupLayout.INNER_WIDTH, 60);
        container.setBorder(BorderFactory.createTitledBorder(title));
        container.setBackground(UIManager.getColor("Panel.background"));

        JTextField field = new JTextField();
        field.setBounds(padding, padding + 10, SetupLayout.FIELD_WIDTH, 20);
        field.setText(placeholder);
        field.setForeground(Color.GRAY);
        field.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (field.getForeground().equals(Color.GRAY)) { field.setText(""); field.setForeground(Color.BLACK); }
            }
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) { field.setText(placeholder); field.setForeground(Color.GRAY); }
                updateWarning();
            }
        });
        container.add(field);

        JButton browse = new JButton("Browse...");
        browse.setBounds(SetupLayout.BROWSE_X, padding + 10, 72, 20);
        browse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browse.addActionListener(e -> chooseFolder(field));
        container.add(browse);

        JLabel link = new JLabel("<HTML><U>Download</U></HTML>");
        link.setForeground(Color.BLUE);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setBounds(padding, padding + 32, 100, 15);
        link.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { openWebpage(downloadUrl); }
        });
        container.add(link);

        add(container);
        return field;
    }

    private void chooseFolder(JTextField target) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
            target.setForeground(Color.BLACK);
            updateWarning();
        }
    }

    private void openWebpage(String url) {
        try { Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception e) { e.printStackTrace(); }
    }
}