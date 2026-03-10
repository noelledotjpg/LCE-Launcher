package com.noelledotjpg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.noelledotjpg.Data.*;
import com.noelledotjpg.MainContent.LaunchArguments;
import com.noelledotjpg.TabContent.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;

public class Main extends JFrame {

    private final JTabbedPane       tabbedPane;
    private final JButton           playButton;
    private final JButton           newProfileButton;
    private final JButton           editProfileButton;
    private final JButton           viewFolderButton;
    private final JLabel            welcomeLabel;
    private final JLabel            readyLabel;
    private final JComboBox<String> profilesCombo;

    private final PlaytimeTracker  playtimeTracker;
    private final VarsData         varsData;
    private final ProfilesData     profilesData;
    private final PreferencesData  preferencesData;
    private final ProfileEditorTab profileEditorTab;
    private final LauncherLogTab   launcherLogTab;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Main() {
        setTitle("LCE Launcher 0.0.1");
        setIconImage(new ImageIcon(getClass().getResource("/img/favicon.png")).getImage());
        setSize(900, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        varsData       = loadVars();
        profilesData   = loadProfiles();
        preferencesData = loadPreferences();
        playtimeTracker = new PlaytimeTracker();

        profilesCombo = new JComboBox<>();

        profileEditorTab = new ProfileEditorTab(
                profilesData.getUsernames(), playtimeTracker, profilesData, gson);
        profileEditorTab.setOnProfilesChanged(
                () -> SwingUtilities.invokeLater(() -> updateProfilesCombo(profilesData.getUsernames())));

        playtimeTracker.syncProfiles(profilesData.getUsernames());

        tabbedPane     = new JTabbedPane();
        launcherLogTab = new LauncherLogTab();

        tabbedPane.addTab("Update Notes",   new UpdateNotesTab());
        tabbedPane.addTab("Launcher Log",   launcherLogTab);
        tabbedPane.addTab("Profile Editor", profileEditorTab);
        tabbedPane.addTab("Preferences",
                new PreferencesTab(preferencesData, profilesCombo, new LaunchArguments(), varsData.getMinecraftExe()));
        tabbedPane.addTab("Worlds",  new WorldsTab(varsData));
        tabbedPane.addTab("Servers", new ServersTab(varsData));

        add(tabbedPane, BorderLayout.CENTER);

        // --- Bottom bar ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(0, 60));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(bottomPanel, BorderLayout.SOUTH);

        // Left: profile selector
        JPanel leftPanel = new JPanel(null);
        leftPanel.setPreferredSize(new Dimension(250, 60));

        JLabel profilesLabel = new JLabel("Profile:");
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

        // Center: play button
        JPanel centerPanel = new JPanel(new GridBagLayout());
        playButton = new JButton("Play");
        playButton.setPreferredSize(new Dimension(290, 49));
        playButton.setFont(new Font("MS Sans Serif", Font.BOLD, 12));
        centerPanel.add(playButton);
        bottomPanel.add(centerPanel, BorderLayout.CENTER);

        // Right: welcome labels + folder button
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
        int btnW = viewFolderButton.getPreferredSize().width;
        int btnX = readyLabel.getX() + (readyLabel.getWidth() - btnW) / 2;
        viewFolderButton.setBounds(btnX, 28, btnW, 21);
        rightPanel.add(viewFolderButton);

        bottomPanel.add(rightPanel, BorderLayout.EAST);

        // --- Actions ---
        playButton.addActionListener(e -> playProfile());
        newProfileButton.addActionListener(e -> addProfile());
        editProfileButton.addActionListener(e -> editProfile());
        viewFolderButton.addActionListener(e -> viewFolder());

        profilesCombo.addActionListener(e -> {
            Object selected = profilesCombo.getSelectedItem();
            if (selected != null && profilesCombo.isEnabled())
                welcomeLabel.setText("Welcome, " + selected);
        });

        // --- Final setup ---
        updateProfilesCombo(profilesData.getUsernames());
        if (profilesData.getLastUsed() != null && !profilesData.getLastUsed().isEmpty())
            profilesCombo.setSelectedItem(profilesData.getLastUsed());
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    // --- Data loading ---

    private VarsData loadVars() {
        try {
            if (Files.exists(AppPaths.VARS_JSON)) {
                try (Reader reader = Files.newBufferedReader(AppPaths.VARS_JSON)) {
                    VarsData data = gson.fromJson(reader, VarsData.class);
                    if (data != null) return data;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new VarsData();
    }

    private ProfilesData loadProfiles() {
        try {
            if (Files.exists(AppPaths.PROFILES_JSON)) {
                try (Reader reader = Files.newBufferedReader(AppPaths.PROFILES_JSON)) {
                    ProfilesData data = gson.fromJson(reader, ProfilesData.class);
                    if (data != null) return data;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        ProfilesData data = new ProfilesData();
        data.setLastUsed("");
        data.setUsernames(new ArrayList<>());
        return data;
    }

    private PreferencesData loadPreferences() {
        try {
            if (Files.exists(AppPaths.PREFERENCES_JSON)) {
                try (Reader reader = Files.newBufferedReader(AppPaths.PREFERENCES_JSON)) {
                    PreferencesData data = gson.fromJson(reader, PreferencesData.class);
                    if (data != null) return data;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PreferencesData();
    }

    private void saveProfiles() {
        try {
            Files.createDirectories(AppPaths.PROFILES_JSON.getParent());
            try (Writer writer = Files.newBufferedWriter(AppPaths.PROFILES_JSON)) {
                gson.toJson(profilesData, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- UI helpers ---

    private void updateProfilesCombo(ArrayList<String> profiles) {
        profilesCombo.removeAllItems();
        if (profiles.isEmpty()) {
            profilesCombo.addItem("No profiles available");
            profilesCombo.setEnabled(false);
        } else {
            for (String p : profiles) profilesCombo.addItem(p);
            profilesCombo.setEnabled(true);
        }
    }

    // --- Button actions ---

    private void addProfile() {
        String newProfile = profileEditorTab.showAddProfileDialog(this);
        if (newProfile != null) {
            updateProfilesCombo(profilesData.getUsernames());
            profilesCombo.setSelectedItem(newProfile);
        }
    }

    private void editProfile() {
        if (!profilesCombo.isEnabled() || profilesCombo.getSelectedItem() == null) return;
        int idx = profilesData.getUsernames().indexOf(profilesCombo.getSelectedItem().toString());
        if (idx >= 0) profileEditorTab.openProfileEditorWindow(idx);
    }

    private void playProfile() {
        if (!profilesCombo.isEnabled() || profilesCombo.getSelectedItem() == null) return;

        String profile = profilesCombo.getSelectedItem().toString();
        profilesData.setLastUsed(profile);
        saveProfiles();

        if (varsData.getMinecraftExe() == null || varsData.getMinecraftExe().isEmpty()) {
            JOptionPane.showMessageDialog(this, "MinecraftClient.exe path not found. Please run setup.");
            return;
        }

        File exeFile = new File(varsData.getMinecraftExe());
        if (!exeFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "MinecraftClient.exe not found:\n" + exeFile.getAbsolutePath());
            return;
        }

        try {
            LaunchArguments args = new LaunchArguments();
            args.setFullscreen(preferencesData.isFullscreen());

            ArrayList<String> command = new ArrayList<>();
            command.add(exeFile.getAbsolutePath());
            command.addAll(args.buildArgs(profile));

            Process gameProcess = new ProcessBuilder(command).start();
            new Thread(new LauncherLogTab.StreamGobbler(gameProcess.getInputStream(), launcherLogTab)).start();
            new Thread(new LauncherLogTab.StreamGobbler(gameProcess.getErrorStream(), launcherLogTab)).start();

            playtimeTracker.startSession(profile);

            String visibility = preferencesData.getLauncherVisibility();
            if ("Hide when game starts".equals(visibility))        setVisible(false);
            else if ("Close when game starts".equals(visibility))  dispose();

            new Thread(() -> {
                try {
                    gameProcess.waitFor();
                    playtimeTracker.endSession();
                    SwingUtilities.invokeLater(() -> {
                        String time = playtimeTracker.getPlaytime(profile);
                        String[] parts = time.split("h|m");
                        profileEditorTab.setTimePlayed(profile,
                                Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim()));

                        if ("Hide when game starts".equals(visibility)) {
                            setVisible(true);
                            toFront();
                        }
                    });
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to launch game:\n" + e.getMessage());
        }
    }

    private void viewFolder() {
        if (varsData.getLceFolder() == null || varsData.getLceFolder().isEmpty()) {
            JOptionPane.showMessageDialog(this, "LCE folder path is not set.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File folder = new File(varsData.getLceFolder());
        if (!folder.exists() || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "LCE folder not found:\n" + folder.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(folder);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open LCE folder:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
