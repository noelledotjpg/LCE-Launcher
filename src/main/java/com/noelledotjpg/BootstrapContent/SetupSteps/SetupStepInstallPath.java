package com.noelledotjpg.BootstrapContent.SetupSteps;

import com.noelledotjpg.BootstrapContent.SetupPaths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class SetupStepInstallPath extends JPanel {

    private static final long REQUIRED_BYTES = 20L * 1024 * 1024 * 1024; // 25 GB

    private final JTextField pathField;
    private final JButton    continueButton;
    private final JButton    cancelButton;
    private final JLabel     gitWarning;
    private final JLabel     spaceWarning;

    private final boolean gitAvailable;

    private static final boolean TEST_NO_GIT   = false;
    private static final boolean TEST_NO_SPACE = false;

    public SetupStepInstallPath() {
        setLayout(null);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel logo = new JLabel(new ImageIcon(getClass().getResource("/img/logo.png")));
        logo.setBounds(SetupLayout.CENTER_X, 10, 274, 59);
        add(logo);

        JPanel container = new JPanel(null);
        container.setBounds(10, 80, SetupLayout.INNER_WIDTH, 65);
        container.setBorder(BorderFactory.createTitledBorder("Install Location"));
        container.setBackground(UIManager.getColor("Panel.background"));

        pathField = new JTextField(SetupPaths.defaultRepoDir().getAbsolutePath());
        pathField.setForeground(Color.BLACK);
        pathField.setBounds(10, 20, SetupLayout.FIELD_WIDTH, 20);
        container.add(pathField);

        JButton browse = new JButton("Browse...");
        browse.setBounds(SetupLayout.BROWSE_X, 20, 72, 20);
        browse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browse.addActionListener(e -> chooseFolder());
        container.add(browse);

        String drive = new File(SetupPaths.defaultRepoDir().getAbsolutePath()).toPath().getRoot().toString();
        JLabel info = new JLabel("(LCE will be installed on '" + drive + "', please have at least ~20GB of free space)");
        info.setForeground(Color.DARK_GRAY);
        info.setFont(info.getFont().deriveFont(11f));
        info.setBounds(10, 43, SetupLayout.INNER_WIDTH, 15);
        container.add(info);

        add(container);

        spaceWarning = new JLabel();
        spaceWarning.setBounds(10, 150, SetupLayout.INNER_WIDTH, 18);
        spaceWarning.setForeground(Color.RED);
        spaceWarning.setHorizontalAlignment(SwingConstants.CENTER);
        spaceWarning.setVisible(false);
        add(spaceWarning);

        gitAvailable = isGitInstalled();

        gitWarning = new JLabel();
        gitWarning.setBounds(10, 170, SetupLayout.INNER_WIDTH, 30);
        gitWarning.setHorizontalAlignment(SwingConstants.CENTER);
        gitWarning.setVisible(false);
        add(gitWarning);

        if (!gitAvailable) showGitWarning();

        continueButton = new JButton("Continue");
        continueButton.setBounds(SetupLayout.BTN_LEFT_X, 210, 100, 25);
        add(continueButton);

        cancelButton = new JButton("Cancel");
        cancelButton.setBounds(SetupLayout.BTN_RIGHT_X, 210, 100, 25);
        add(cancelButton);

        pathField.addActionListener(e -> revalidate_());
        browse.addActionListener(e -> revalidate_());

        revalidate_();
    }

    public String getInstallPath()     { return pathField.getText().trim(); }
    public JButton getContinueButton() { return continueButton; }
    public JButton getCancelButton()   { return cancelButton; }

    private void revalidate_() {
        boolean enoughSpace = checkSpace();
        boolean repoExists  = !gitAvailable && new File(new File(pathField.getText().trim()), ".git").exists();
        continueButton.setEnabled(enoughSpace && (gitAvailable || repoExists));
    }

    private boolean checkSpace() {
        if (TEST_NO_SPACE) {
            spaceWarning.setText("<html><center>Not enough space — 3 GB free, 25 GB required.</center></html>");
            spaceWarning.setVisible(true);
            return false;
        }

        File path = new File(pathField.getText().trim());
        while (path != null && !path.exists()) path = path.getParentFile();

        if (path == null) {
            spaceWarning.setText("Invalid path.");
            spaceWarning.setVisible(true);
            return false;
        }

        long free = path.getFreeSpace();
        if (free < REQUIRED_BYTES) {
            long freeGb     = free / (1024 * 1024 * 1024);
            long requiredGb = REQUIRED_BYTES / (1024 * 1024 * 1024);
            spaceWarning.setText("<html><center>Not enough space — " + freeGb + " GB free, " + requiredGb + " GB required.</center></html>");
            spaceWarning.setVisible(true);
            return false;
        }

        spaceWarning.setVisible(false);
        return true;
    }

    private void showGitWarning() {
        gitWarning.setText("<html><center><font color='red'>Git not found.<br></font>"
                + "<a href=''>Download</a> git or select an existing repo folder.</center></html>");
        gitWarning.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gitWarning.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(new java.net.URI("https://git-scm.com/downloads")); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
        });
        gitWarning.setVisible(true);
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            pathField.setForeground(Color.BLACK);
            revalidate_();
        }
    }

    private static boolean isGitInstalled() {
        if (TEST_NO_GIT) return false;
        try {
            Process p = new ProcessBuilder("git", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}