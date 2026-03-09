package com.noelledotjpg.BootstrapContent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class InstallPaths {

    private final int formWidth;
    private JLabel warningLabel;
    private JTextField vsField;
    private JTextField cmakeField;

    public InstallPaths(int formWidth) {
        this.formWidth = formWidth;
    }

    private void setFieldText(JTextField field, String text, String placeholder) {
        if (text == null || text.isEmpty()) {
            field.setText(placeholder);
            field.setForeground(Color.GRAY);
        } else {
            field.setText(text);
            field.setForeground(Color.BLACK);
        }
    }

    public void createWarningLabel(JPanel panel) {
        warningLabel = new JLabel();
        warningLabel.setForeground(Color.RED);

        int labelWidth = formWidth - 20;
        int x = (formWidth - labelWidth) / 2;
        int y = 350;
        int height = 20;

        warningLabel.setBounds(x, y, labelWidth, height);
        warningLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(warningLabel);
    }

    public void monitorFields(JTextField vsField, JTextField cmakeField) {
        this.vsField = vsField;
        this.cmakeField = cmakeField;

        autoDetectPaths();

        KeyAdapter listener = new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                updateWarning();
            }
        };

        vsField.addKeyListener(listener);
        cmakeField.addKeyListener(listener);

        updateWarning();
    }

    public void autoDetectPaths() {
        String vsDetectedPath = "";
        String cmakeDetectedPath = "";

        try {
            File vswhere = new File("C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe");

            if (vswhere.exists()) {
                Process process = new ProcessBuilder(vswhere.getAbsolutePath(), "-latest", "-products", "*", "-requires", "Microsoft.Component.MSBuild", "-property", "installationPath").start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                vsDetectedPath = reader.readLine();
                process.waitFor();
            }
        } catch (Exception ignored) {
        }

        File cmakeDefault = new File("C:\\Program Files\\CMake\\bin\\cmake.exe");
        if (cmakeDefault.exists()) {
            cmakeDetectedPath = cmakeDefault.getAbsolutePath();
        }

        String pathEnv = System.getenv("PATH");

        if (vsDetectedPath == null || vsDetectedPath.isEmpty()) {
            for (String p : pathEnv.split(";")) {
                File f = new File(p, "devenv.exe");
                if (f.exists()) {
                    vsDetectedPath = f.getParentFile().getParentFile().getParent();
                    break;
                }
            }
        }

        if (cmakeDetectedPath.isEmpty()) {
            for (String p : pathEnv.split(";")) {
                File f = new File(p, "cmake.exe");
                if (f.exists()) {
                    cmakeDetectedPath = f.getAbsolutePath();
                    break;
                }
            }
        }

        setFieldText(vsField, vsDetectedPath, "");
        setFieldText(cmakeField, cmakeDetectedPath, "");

        updateWarning();
    }

    private void updateWarning() {
        String vsText = vsField.getText().trim();
        String cmakeText = cmakeField.getText().trim();

        if (vsText.isEmpty() || cmakeText.isEmpty()) {
            setWarning("Please make sure Visual Studio and CMake are installed!");
        } else {
            clearWarning();
        }
    }

    public void setWarning(String text) {
        if (warningLabel != null) {
            warningLabel.setText("<html><u>" + text + "</u></html>");
        }
    }

    public void clearWarning() {
        if (warningLabel != null) {
            warningLabel.setText("");
        }
    }
}