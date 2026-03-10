package com.noelledotjpg;

import com.noelledotjpg.BootstrapContent.SetupSteps.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class Bootstrap extends JFrame {

    private final Dimension MAIN_SIZE        = SetupLayout.MAIN_SIZE;
    private final Dimension STEP_SIZE        = SetupLayout.STEP_SIZE;
    private final Dimension BUILD_TOOLS_SIZE = SetupLayout.BUILD_TOOLS_SIZE;
    private final Dimension LOADING_SIZE     = SetupLayout.LOADING_SIZE;

    private JPanel     centerFrame;
    private CardLayout cardLayout;

    private LoadingScreen        loadingPanel;
    private SetupStepInstallPath stepInstallPath;
    private SetupStepUsername    stepUsername;
    private SetupStepBuildTools  stepBuildTools;

    public Bootstrap() {
        setTitle("LCE Launcher - Setup");
        setIconImage(new ImageIcon(getClass().getResource("/img/favicon.png")).getImage());
        setSize(900, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        UIManager.put("ButtonUI", BrowsePlainButtonUI.class.getName());

        setContentPane(buildBackground());
        setLayout(null);

        centerFrame = new JPanel();
        cardLayout  = new CardLayout();
        centerFrame.setLayout(cardLayout);
        centerFrame.setBounds(0, 0, MAIN_SIZE.width, MAIN_SIZE.height);
        add(centerFrame);

        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                for (Component c : centerFrame.getComponents())
                    if (c.isVisible()) { centerPanel(centerFrame, c.getSize()); break; }
            }
        });

        loadingPanel    = new LoadingScreen();
        stepInstallPath = new SetupStepInstallPath();
        stepUsername    = new SetupStepUsername();
        stepBuildTools  = new SetupStepBuildTools();

        JPanel buttonPanel = createMainButtons();

        centerFrame.add(buttonPanel,     "main");
        centerFrame.add(stepInstallPath, "stepInstallPath");
        centerFrame.add(stepUsername,    "stepUsername");
        centerFrame.add(stepBuildTools,  "stepBuildTools");
        centerFrame.add(loadingPanel,    "loading");

        wireNavigation(buttonPanel);

        SwingUtilities.invokeLater(() -> centerPanel(centerFrame, MAIN_SIZE));

        if (loadingPanel.isBuildAlreadyDone())
            SwingUtilities.invokeLater(this::openLauncher);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Bootstrap().setVisible(true));
    }

    private void wireNavigation(JPanel buttonPanel) {
        JButton setupBtn = (JButton) buttonPanel.getClientProperty("setupButton");

        setupBtn.addActionListener(e -> show("stepInstallPath", STEP_SIZE));

        stepInstallPath.getContinueButton().addActionListener(e -> show("stepUsername", STEP_SIZE));
        stepInstallPath.getCancelButton().addActionListener(e   -> show("main", MAIN_SIZE));

        stepUsername.getContinueButton().addActionListener(e -> show("stepBuildTools", BUILD_TOOLS_SIZE));
        stepUsername.getCancelButton().addActionListener(e  -> show("stepInstallPath", STEP_SIZE));

        stepBuildTools.getContinueButton().addActionListener(e -> runSetup());
        stepBuildTools.getCancelButton().addActionListener(e   -> show("stepUsername", STEP_SIZE));

        loadingPanel.setOnCancelBuild(() -> show("stepBuildTools", BUILD_TOOLS_SIZE));
        loadingPanel.setOnLaunch(this::openLauncher);
    }

    private void show(String card, Dimension size) {
        centerFrame.setSize(size);
        centerPanel(centerFrame, size);
        cardLayout.show(centerFrame, card);
    }

    private void runSetup() {
        show("loading", LOADING_SIZE);
        loadingPanel.startSetup(
                stepUsername.getUsername(),
                stepInstallPath.getInstallPath(),
                stepBuildTools.getVsPath(),
                stepBuildTools.getCmakePath()
        );
    }

    private JPanel createMainButtons() {
        JPanel panel = new JPanel(null);
        panel.setBackground(UIManager.getColor("Panel.background"));

        JLabel logo = new JLabel(new ImageIcon(getClass().getResource("/img/logo.png")));
        logo.setBounds((MAIN_SIZE.width - 274) / 2, 10, 274, 59);
        panel.add(logo);

        JButton setupButton = new JButton("Setup LCE");
        setupButton.setBounds((MAIN_SIZE.width - 110) / 2, 90, 110, 25);
        panel.add(setupButton);

        if (loadingPanel.isTestMode()) {
            JButton skipButton = new JButton("Skip to Launcher");
            skipButton.setBounds((MAIN_SIZE.width - 110) / 2, 120, 110, 25);
            skipButton.addActionListener(e -> openLauncher());
            panel.add(skipButton);
        }

        panel.putClientProperty("setupButton", setupButton);
        return panel;
    }

    private JPanel buildBackground() {
        return new JPanel() {
            final Image dirt       = new ImageIcon(getClass().getResource("/img/dirt.png")).getImage();
            final Image scaledDirt = dirt.getScaledInstance(
                    dirt.getWidth(null) * 4, dirt.getHeight(null) * 4, Image.SCALE_FAST);
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for (int x = 0; x < getWidth(); x += scaledDirt.getWidth(null))
                    for (int y = 0; y < getHeight(); y += scaledDirt.getHeight(null))
                        g.drawImage(scaledDirt, x, y, this);
            }
        };
    }

    private void centerPanel(JPanel panel, Dimension size) {
        int x = (getContentPane().getWidth()  - size.width)  / 2;
        int y = (getContentPane().getHeight() - size.height) / 2;
        panel.setBounds(x, y, size.width, size.height);
    }

    private void openLauncher() {
        dispose();
        new Main().setVisible(true);
    }

    public void showSetupFormWithFields() {
        show("stepBuildTools", BUILD_TOOLS_SIZE);
    }

    public static class BrowsePlainButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) {
            return new BrowsePlainButtonUI();
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            if ("Browse...".equals(b.getText())) {
                FontMetrics fm = g.getFontMetrics(b.getFont());
                int x = (b.getWidth()  - fm.stringWidth(b.getText())) / 2;
                int y = (b.getHeight() + fm.getAscent()) / 2 - 2;
                g.setFont(b.getFont());
                g.setColor(b.getForeground());
                g.drawString(b.getText(), x, y);
            } else {
                super.paint(g, c);
            }
        }
    }
}