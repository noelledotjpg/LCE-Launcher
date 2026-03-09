package com.noelledotjpg;

import com.noelledotjpg.BootstrapContent.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class Bootstrap extends JFrame {

    private final Dimension MAIN_SIZE = new Dimension(315, 160);
    private final Dimension FORM_SIZE = new Dimension(315, 420);
    private final Dimension LOADING_SIZE = new Dimension(515, 420);
    private JPanel centerFrame;
    private CardLayout cardLayout;
    private JTextField usernameField;
    private JTextField vsFolderField;
    private JTextField cmakeFolderField;
    private JTextField lceFolderField;
    private JButton continueButton;

    private LoadingScreen loadingPanel;

    public Bootstrap() {
        setTitle("LCE Launcher - Setup");

        ImageIcon icon = new ImageIcon(getClass().getResource("/img/favicon.png"));
        setIconImage(icon.getImage());

        setSize(900, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setContentPane(new JPanel() {
            Image dirt = new ImageIcon(getClass().getResource("/img/dirt.png")).getImage();
            Image scaledDirt = dirt.getScaledInstance(dirt.getWidth(null) * 4, dirt.getHeight(null) * 4, Image.SCALE_FAST);

            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for (int x = 0; x < getWidth(); x += scaledDirt.getWidth(null)) {
                    for (int y = 0; y < getHeight(); y += scaledDirt.getHeight(null)) {
                        g.drawImage(scaledDirt, x, y, this);
                    }
                }
            }
        });

        setLayout(null);

        centerFrame = new JPanel();
        cardLayout = new CardLayout();
        centerFrame.setLayout(cardLayout);
        centerFrame.setBounds(292, 140, MAIN_SIZE.width, MAIN_SIZE.height);
        add(centerFrame);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Component visible = null;
                for (Component c : centerFrame.getComponents()) {
                    if (c.isVisible()) {
                        visible = c;
                        break;
                    }
                }

                if (visible != null) {
                    Dimension size = visible.getSize();
                    int frameWidth = getContentPane().getWidth();
                    int frameHeight = getContentPane().getHeight();
                    int x = (frameWidth - size.width) / 2;
                    int y = (frameHeight - size.height) / 2;
                    centerFrame.setBounds(x, y, size.width, size.height);
                }
            }
        });

        loadingPanel = new LoadingScreen(false);

        boolean skipBootstrap = loadingPanel.isBuildAlreadyDone();

        JPanel buttonPanel = createInitialButtons();
        JPanel formPanel = createSetupForm();

        centerFrame.add(buttonPanel, "buttons");
        centerFrame.add(formPanel, "setupForm");
        centerFrame.add(loadingPanel, "loading");

        setupButtonAction(buttonPanel, formPanel);

        KeyAdapter fieldListener = new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                checkFields();
            }
        };

        usernameField.addKeyListener(fieldListener);
        vsFolderField.addKeyListener(fieldListener);
        cmakeFolderField.addKeyListener(fieldListener);
        lceFolderField.addKeyListener(fieldListener);

        SwingUtilities.invokeLater(() -> centerPanel(centerFrame, MAIN_SIZE));

        if (skipBootstrap) {
            SwingUtilities.invokeLater(this::openLauncher);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Bootstrap window = new Bootstrap();
            window.setVisible(true);
        });
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

    private void centerHorizontally(JComponent comp, int parentWidth, int y, int width, int height) {
        int x = (parentWidth - width) / 2;
        comp.setBounds(x, y, width, height);
    }

    private void centerPanel(JPanel panel, Dimension size) {
        int frameWidth = getContentPane().getWidth();
        int frameHeight = getContentPane().getHeight();
        int x = (frameWidth - size.width) / 2;
        int y = (frameHeight - size.height) / 2;
        panel.setBounds(x, y, size.width, size.height);
    }

    private JTextField addTitledField(JPanel parent, String title, String placeholder, int y, int fieldWidth, int fieldHeight, int extraBorderHeight, boolean hasBrowseButton, String downloadLink, String infoText) {
        int padding = 10;
        int containerHeight = fieldHeight + (hasBrowseButton ? 45 : 25) + extraBorderHeight;
        int containerWidth = FORM_SIZE.width - 20;
        int x = (FORM_SIZE.width - containerWidth) / 2;

        JPanel container = new JPanel(null);
        container.setBounds(x, y, containerWidth, containerHeight);
        container.setBorder(BorderFactory.createTitledBorder(title));
        container.setBackground(UIManager.getColor("Panel.background"));

        JTextField field = new JTextField();
        field.setBounds(padding, padding + 10, fieldWidth, fieldHeight);
        setFieldText(field, null, placeholder);

        field.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(Color.GRAY);
                }
            }
        });

        container.add(field);

        if (hasBrowseButton) {
            JButton browse = new JButton("Browse...");
            browse.setBounds(fieldWidth + padding + 3, padding + 10, 72, fieldHeight);
            browse.addActionListener(e -> chooseFolder(field));
            browse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            browse.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    FontMetrics fm = g.getFontMetrics(b.getFont());
                    String text = b.getText();
                    int width = b.getWidth();
                    int height = b.getHeight();
                    int textWidth = fm.stringWidth(text);
                    int textHeight = fm.getAscent();
                    int x = (width - textWidth) / 2;
                    int y = (height + textHeight) / 2 - 2;
                    g.setFont(b.getFont());
                    g.setColor(b.getForeground());
                    g.drawString(text, x, y);
                }
            });

            container.add(browse);

            if (downloadLink != null && !downloadLink.isEmpty()) {
                JLabel linkLabel = new JLabel("<HTML><U>Download</U></HTML>");
                linkLabel.setForeground(Color.BLUE);
                linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                linkLabel.setBounds(padding, padding + 10 + fieldHeight, 100, 18);
                linkLabel.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        openWebpage(downloadLink);
                    }
                });
                container.add(linkLabel);
            }
        }

        if (infoText != null && !infoText.isEmpty()) {
            JLabel infoLabel = new JLabel(infoText);
            infoLabel.setForeground(Color.DARK_GRAY);
            infoLabel.setFont(infoLabel.getFont().deriveFont(11f));
            infoLabel.setBounds(padding, padding + 10 + fieldHeight + (hasBrowseButton ? 25 : 0), 250, 18);
            container.add(infoLabel);
        }

        parent.add(container);
        return field;
    }

    private JPanel createInitialButtons() {
        JPanel panel = new JPanel(null);
        panel.setBackground(UIManager.getColor("Panel.background"));

        int panelWidth = MAIN_SIZE.width;

        JLabel logoLabel = new JLabel(new ImageIcon(getClass().getResource("/img/logo.png")));
        centerHorizontally(logoLabel, panelWidth, 10, 274, 59);
        panel.add(logoLabel);

        JButton setupButton = new JButton("Setup LCE");
        centerHorizontally(setupButton, panelWidth, 90, 110, 25);
        panel.add(setupButton);

        JButton skipButton = null;
        if (loadingPanel != null && loadingPanel.isTestMode()) {
            skipButton = new JButton("Skip to Launcher");
            centerHorizontally(skipButton, panelWidth, 120, 110, 25);
            panel.add(skipButton);
        }

        panel.putClientProperty("setupButton", setupButton);
        panel.putClientProperty("skipButton", skipButton);

        return panel;
    }

    private JPanel createSetupForm() {
        JPanel panel = new JPanel(null);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setBackground(UIManager.getColor("Panel.background"));

        int panelWidth = FORM_SIZE.width;

        JLabel logoLabel = new JLabel(new ImageIcon(getClass().getResource("/img/logo.png")));
        centerHorizontally(logoLabel, panelWidth, 10, 274, 59);
        panel.add(logoLabel);

        int startY = 70;
        int spacing = 70;

        usernameField = addTitledField(panel, "Username", " eg.: Steve", startY, 276, 20, 20, false, null, "(This will be saved as your profile)");
        vsFolderField = addTitledField(panel, "Visual Studio 2022", " VS2022 Folder", startY + spacing, 200, 20, 0, true, "https://aka.ms/vs/17/release/vs_community.exe", null);
        cmakeFolderField = addTitledField(panel, "CMake", " CMake executable path", startY + spacing * 2, 200, 20, 0, true, "https://cmake.org/download/#latest", null);
        lceFolderField = addTitledField(panel, "LCE Repository", " LCE installation folder", startY + spacing * 3, 200, 20, 0, true, "https://github.com/smartcmd/MinecraftConsoles", null);

        continueButton = new JButton("Continue");
        continueButton.setEnabled(false);
        continueButton.setBounds(50, 380, 100, 25);
        panel.add(continueButton);

        continueButton.addActionListener(e -> runSetup());

        InstallPaths warningHelper = new InstallPaths(FORM_SIZE.width);
        warningHelper.createWarningLabel(panel);
        warningHelper.monitorFields(vsFolderField, cmakeFolderField);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBounds(170, 380, 100, 25);
        panel.add(cancelButton);

        if (loadingPanel != null && loadingPanel.isTestMode()) {
            continueButton.setEnabled(true);
        }

        return panel;
    }

    private void setupButtonAction(JPanel buttonPanel, JPanel formPanel) {
        JButton setupButton = (JButton) buttonPanel.getClientProperty("setupButton");
        JButton skipButton = (JButton) buttonPanel.getClientProperty("skipButton");

        JButton cancelButton = null;
        for (Component comp : formPanel.getComponents()) {
            if (comp instanceof JButton button && button.getText().equals("Cancel")) {
                cancelButton = button;
            }
        }
        JButton finalCancelButton = cancelButton;

        setupButton.addActionListener(e -> {
            centerFrame.setSize(FORM_SIZE);
            centerPanel(centerFrame, FORM_SIZE);
            cardLayout.show(centerFrame, "setupForm");
        });

        if (skipButton != null) {
            skipButton.addActionListener(e -> openLauncher());
        }

        if (finalCancelButton != null) {
            finalCancelButton.addActionListener(e -> {
                centerFrame.setSize(MAIN_SIZE);
                centerPanel(centerFrame, MAIN_SIZE);
                cardLayout.show(centerFrame, "buttons");
            });
        }
    }

    private void checkFields() {
        if (loadingPanel != null && loadingPanel.isTestMode()) {
            continueButton.setEnabled(true);
            return;
        }

        continueButton.setEnabled(
                !usernameField.getText().isEmpty() && !usernameField.getForeground().equals(Color.GRAY) &&
                        !vsFolderField.getText().isEmpty() && !vsFolderField.getForeground().equals(Color.GRAY) &&
                        !cmakeFolderField.getText().isEmpty() && !cmakeFolderField.getForeground().equals(Color.GRAY) &&
                        !lceFolderField.getText().isEmpty() && !lceFolderField.getForeground().equals(Color.GRAY)
        );
    }

    private void chooseFolder(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            targetField.setText(folder.getAbsolutePath());
            targetField.setForeground(Color.BLACK);
            checkFields();
        }
    }

    private void openWebpage(String url) {
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openLauncher() {
        dispose();
        Main launcher = new Main();
        launcher.setVisible(true);
    }

    public void showSetupFormWithFields() {
        cardLayout.show(centerFrame, "setupForm");
        centerFrame.setSize(FORM_SIZE);
        centerPanel(centerFrame, FORM_SIZE);
    }

    private void runSetup() {
        String username = usernameField.getText();
        String vsPath = vsFolderField.getText();
        String cmakePath = cmakeFolderField.getText();
        String lcePath = lceFolderField.getText();

        cardLayout.show(centerFrame, "loading");
        centerFrame.setSize(LOADING_SIZE);
        centerPanel(centerFrame, LOADING_SIZE);

        loadingPanel.startSetup(username, lcePath, vsPath, cmakePath, this::openLauncher);
    }
}