package com.noelledotjpg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.noelledotjpg.BootstrapContent.VarsData;
import com.noelledotjpg.MainContent.LaunchArguments;
import com.noelledotjpg.TabContent.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class Main extends JFrame {

    private final List<Process> activeProcesses = new CopyOnWriteArrayList<>();

    private static final Path VARS_JSON = Paths.get("src/main/resources/data/launcher/vars.json");
    private static final Path PROFILES_JSON = Paths.get("src/main/resources/data/usernames.json");

    private final JTabbedPane tabbedPane;

    private final JButton playButton;
    private final JButton newProfileButton;
    private final JButton editProfileButton;
    private final JButton viewFolderButton;

    private final JLabel profilesLabel;
    private final JLabel welcomeLabel;
    private final JLabel readyLabel;

    private final JComboBox<String> profilesCombo;

    private LaunchArguments launchArgs;

    private PlaytimeTracker playtimeTracker;
    private VarsData varsData;
    private ProfilesData profilesData;
    private PreferencesData preferencesData;

    private ProfileEditorTab profileEditorTab;

    private LauncherLogTab launcherLogTab;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private PreferencesData loadPreferences() {

        Path PREFS_JSON = Paths.get("src/main/resources/data/launcher/preferences.json");

        try {

            if (Files.exists(PREFS_JSON)) {

                try (Reader reader = Files.newBufferedReader(PREFS_JSON)) {

                    PreferencesData data = gson.fromJson(reader, PreferencesData.class);

                    if (data != null)
                        return data;

                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new PreferencesData();
    }

    public Main() {

        setTitle("LCE Launcher 0.0.1");

        ImageIcon icon = new ImageIcon(getClass().getResource("/img/favicon.png"));
        setIconImage(icon.getImage());

        setSize(900, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Load vars and profiles
        loadVars();
        loadProfiles();

        // Load preferences
        preferencesData = loadPreferences();
        if (preferencesData.getLauncherVisibility() == null || preferencesData.getLauncherVisibility().isEmpty()) {
            preferencesData.setLauncherVisibility("Hide when game starts");
        }

        playtimeTracker = new PlaytimeTracker();

        // --- Initialize components used by PreferencesTab first ---
        profilesCombo = new JComboBox<>();
        launchArgs = new LaunchArguments(); // store once to reuse

        // Profile editor tab
        profileEditorTab = new ProfileEditorTab(
                profilesData.getUsernames(),
                playtimeTracker,
                profilesData,
                gson
        );

        profileEditorTab.setOnProfilesChanged(() ->
                SwingUtilities.invokeLater(() -> updateProfiles(profilesData.getUsernames()))
        );

        playtimeTracker.syncProfiles(profilesData.getUsernames());

        // --- Create tabbed pane after initializing required components ---
        tabbedPane = new JTabbedPane();
        launcherLogTab = new LauncherLogTab();

        tabbedPane.addTab("Update Notes", new UpdateNotesTab());
        tabbedPane.addTab("Launcher Log", launcherLogTab);
        tabbedPane.addTab("Profile Editor", profileEditorTab);
        tabbedPane.addTab("Preferences",
                new PreferencesTab(preferencesData, profilesCombo, new LaunchArguments(), varsData.getMinecraftExe()));
        tabbedPane.addTab("Worlds", new WorldsTab(varsData));
        tabbedPane.addTab("Servers", new ServersTab(varsData));

        add(tabbedPane, BorderLayout.CENTER);

        // --- Bottom panel ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(0, 60));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(bottomPanel, BorderLayout.SOUTH);

        // Left panel: profile selection
        JPanel leftPanel = new JPanel(null);
        leftPanel.setPreferredSize(new Dimension(250, 60));

        profilesLabel = new JLabel("Profile:");
        profilesLabel.setBounds(0, 5, 50, 20);
        leftPanel.add(profilesLabel);

        profilesCombo.setBounds(37, 5, 135, 20);
        leftPanel.add(profilesCombo);

        newProfileButton = new JButton("New Profile");
        newProfileButton.setBounds(0, 28, 85, 21);
        leftPanel.add(newProfileButton);

        editProfileButton = new JButton("Edit Profile");
        editProfileButton.setBounds(87, 28, 85, 21);
        leftPanel.add(editProfileButton);

        bottomPanel.add(leftPanel, BorderLayout.WEST);

        // Center panel: play button
        JPanel centerPanel = new JPanel(new GridBagLayout());
        playButton = new JButton("Play");
        playButton.setPreferredSize(new Dimension(290, 49));
        playButton.setFont(new Font("MS Sans Serif", Font.BOLD, 12));
        centerPanel.add(playButton);
        bottomPanel.add(centerPanel, BorderLayout.CENTER);

        // Right panel: welcome / ready labels + folder button
        JPanel rightPanel = new JPanel(null);
        rightPanel.setPreferredSize(new Dimension(250, 60));

        welcomeLabel = new JLabel("Welcome, Player");
        welcomeLabel.setBounds(30, -5, 250, 20);
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rightPanel.add(welcomeLabel);

        readyLabel = new JLabel("Ready to play Minecraft: Legacy Edition");
        readyLabel.setBounds(30, 8, 250, 20);
        readyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rightPanel.add(readyLabel);

        viewFolderButton = new JButton("View LCE Folder");
        int buttonWidth = viewFolderButton.getPreferredSize().width;
        int buttonHeight = 21;
        int buttonX = readyLabel.getX() + (readyLabel.getWidth() - buttonWidth) / 2;
        viewFolderButton.setBounds(buttonX, 28, buttonWidth, buttonHeight);
        rightPanel.add(viewFolderButton);

        bottomPanel.add(rightPanel, BorderLayout.EAST);

        // --- Button actions ---
        playButton.addActionListener(e -> playProfile());
        newProfileButton.addActionListener(e -> addProfile());
        editProfileButton.addActionListener(e -> editProfile());
        viewFolderButton.addActionListener(e -> viewFolder());

        newProfileButton.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
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

        // --- Final setup ---
        updateProfiles(profilesData.getUsernames());
        if (!profilesData.getLastUsed().isEmpty()) {
            profilesCombo.setSelectedItem(profilesData.getLastUsed());
        }
    }

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            Main launcher = new Main();
            launcher.setVisible(true);
        });
    }

    private void loadVars() {

        try {

            if (!Files.exists(VARS_JSON)) {

                varsData = new VarsData();

            } else {

                try (Reader reader = Files.newBufferedReader(VARS_JSON)) {
                    varsData = gson.fromJson(reader, VarsData.class);
                }

            }

        } catch (IOException e) {

            e.printStackTrace();
            varsData = new VarsData();

        }
    }

    private void loadProfiles() {

        try {

            if (!Files.exists(PROFILES_JSON)) {

                profilesData = new ProfilesData();
                profilesData.setLastUsed("");
                profilesData.setUsernames(new ArrayList<>());

            } else {

                try (Reader reader = Files.newBufferedReader(PROFILES_JSON)) {
                    profilesData = gson.fromJson(reader, ProfilesData.class);
                }

            }

        } catch (IOException e) {

            e.printStackTrace();

            profilesData = new ProfilesData();
            profilesData.setLastUsed("");
            profilesData.setUsernames(new ArrayList<>());

        }
    }

    private void updateProfiles(ArrayList<String> profiles) {

        profilesCombo.removeAllItems();

        if (profiles.isEmpty()) {

            profilesCombo.addItem("No profiles available");
            profilesCombo.setEnabled(false);

        } else {

            for (String p : profiles)
                profilesCombo.addItem(p);

            profilesCombo.setEnabled(true);

        }
    }

    private void saveProfiles() {

        try {

            Files.createDirectories(PROFILES_JSON.getParent());

            try (Writer writer = Files.newBufferedWriter(PROFILES_JSON)) {
                gson.toJson(profilesData, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addProfile() {

        String newProfile = profileEditorTab.showAddProfileDialog(this);

        if (newProfile != null) {

            updateProfiles(profilesData.getUsernames());
            profilesCombo.setSelectedItem(newProfile);

        }
    }

    private void editProfile() {

        if (!profilesCombo.isEnabled() || profilesCombo.getSelectedItem() == null)
            return;

        int idx = profilesData.getUsernames()
                .indexOf(profilesCombo.getSelectedItem().toString());

        if (idx >= 0)
            profileEditorTab.openProfileEditorWindow(idx);
    }

    private void playProfile() {

        if (!profilesCombo.isEnabled() || profilesCombo.getSelectedItem() == null)
            return;

        String profile = profilesCombo.getSelectedItem().toString();

        profilesData.setLastUsed(profile);
        saveProfiles();

        if (varsData == null || varsData.getMinecraftExe() == null || varsData.getMinecraftExe().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "MinecraftClient.exe path not found! Please run setup.");
            return;
        }

        File exeFile = new File(varsData.getMinecraftExe());

        if (!exeFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "MinecraftClient.exe not found in: " + exeFile.getAbsolutePath());
            return;
        }

        try {
            LaunchArguments argsBuilder = new LaunchArguments();
            argsBuilder.setFullscreen(preferencesData.isFullscreen());

            ArrayList<String> command = new ArrayList<>();
            command.add(exeFile.getAbsolutePath());
            command.addAll(argsBuilder.buildArgs(profile));

            Process gameProcess = new ProcessBuilder(command).start();
            new Thread(new LauncherLogTab.StreamGobbler(gameProcess.getInputStream(), launcherLogTab)).start();
            new Thread(new LauncherLogTab.StreamGobbler(gameProcess.getErrorStream(), launcherLogTab)).start();

            playtimeTracker.startSession(profile);

            // Handle launcher visibility preferences
            String visibility = preferencesData.getLauncherVisibility();
            if (visibility.equals("Hide when game starts")) {
                setVisible(false); // hide launcher
            } else if (visibility.equals("Close when game starts")) {
                dispose(); // close launcher
            }

            // Wait for process to finish
            new Thread(() -> {
                try {
                    gameProcess.waitFor();
                    playtimeTracker.endSession();
                    SwingUtilities.invokeLater(() -> {
                        String time = playtimeTracker.getPlaytime(profile);
                        String[] parts = time.split("h|m");
                        int hours = Integer.parseInt(parts[0].trim());
                        int minutes = Integer.parseInt(parts[1].trim());
                        profileEditorTab.setTimePlayed(profile, hours, minutes);

                        if (visibility.equals("Hide when game starts")) {
                            setVisible(true);
                            toFront();
                        }
                    });
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to launch game: " + e.getMessage());
        }
    }

    private void viewFolder() {
        if (varsData != null && varsData.getLceFolder() != null && !varsData.getLceFolder().isEmpty()) {
            File folder = new File(varsData.getLceFolder());
            if (folder.exists() && folder.isDirectory()) {
                try {
                    Desktop.getDesktop().open(folder);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to open LCE folder:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "LCE folder not found:\n" + folder.getAbsolutePath(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "LCE folder path is not set in vars.json.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}