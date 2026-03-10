package com.noelledotjpg.TabContent;

import com.google.gson.Gson;
import com.noelledotjpg.Data.AppPaths;
import com.noelledotjpg.Data.PlaytimeTracker;
import com.noelledotjpg.Data.ProfilesData;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;

public class ProfileEditorTab extends JPanel {

    private final ProfilesData    profilesData;
    private final Gson            gson;
    private final PlaytimeTracker playtimeTracker;
    private       Runnable        onProfilesChanged;
    private final DefaultTableModel tableModel;
    private final JTable          profileTable;

    public ProfileEditorTab(ArrayList<String> profiles, PlaytimeTracker tracker,
                            ProfilesData profilesData, Gson gson) {
        this.playtimeTracker = tracker;
        this.profilesData    = profilesData;
        this.gson            = gson;

        setLayout(new BorderLayout());

        tableModel = new DefaultTableModel(new Object[]{"Profile", "Time Played"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        profileTable = new JTable(tableModel);

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < profileTable.getColumnModel().getColumnCount(); i++)
            profileTable.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);

        profileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileTable.setFillsViewportHeight(true);
        profileTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        profileTable.setBackground(Color.WHITE);
        profileTable.setGridColor(Color.LIGHT_GRAY);
        profileTable.setShowGrid(true);
        profileTable.setShowHorizontalLines(true);
        profileTable.setShowVerticalLines(false);
        profileTable.getColumnModel().getColumn(0).setPreferredWidth(700);
        profileTable.getColumnModel().getColumn(1).setPreferredWidth(60);

        add(new JScrollPane(profileTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton    = new JButton("Add");
        JButton editButton   = new JButton("Edit");
        JButton removeButton = new JButton("Remove");
        editButton.setEnabled(false);
        removeButton.setEnabled(false);

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        setProfiles(profiles);

        profileTable.getSelectionModel().addListSelectionListener(e -> {
            boolean selected = profileTable.getSelectedRow() >= 0;
            editButton.setEnabled(selected);
            removeButton.setEnabled(selected);
        });

        addButton.addActionListener(e -> showAddProfileDialog(this));
        editButton.addActionListener(e -> openProfileEditorWindow(profileTable.getSelectedRow()));
        removeButton.addActionListener(e -> removeProfile(profileTable.getSelectedRow()));

        profileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && profileTable.getSelectedRow() >= 0)
                    openProfileEditorWindow(profileTable.getSelectedRow());
            }
        });
    }

    public void setOnProfilesChanged(Runnable callback) {
        this.onProfilesChanged = callback;
    }

    private void saveProfiles() {
        try {
            Files.createDirectories(AppPaths.PROFILES_JSON.getParent());
            try (Writer writer = Files.newBufferedWriter(AppPaths.PROFILES_JSON)) {
                gson.toJson(profilesData, writer);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void addProfile(String name) {
        if (name == null || name.isBlank()) return;

        tableModel.addRow(new Object[]{name, "0h 0m"});
        playtimeTracker.registerProfile(name);
        profilesData.getUsernames().add(name);
        profilesData.setLastUsed(name);
        saveProfiles();

        profileTable.setRowSelectionInterval(tableModel.getRowCount() - 1, tableModel.getRowCount() - 1);
        if (onProfilesChanged != null) onProfilesChanged.run();
    }

    public void removeProfile(int idx) {
        if (idx < 0 || idx >= tableModel.getRowCount()) return;

        String name = (String) tableModel.getValueAt(idx, 0);
        playtimeTracker.unregisterProfile(name);
        tableModel.removeRow(idx);
        profilesData.getUsernames().remove(name);
        saveProfiles();

        if (onProfilesChanged != null) onProfilesChanged.run();
    }

    public void renameProfile(int idx, String newName) {
        if (idx < 0 || idx >= tableModel.getRowCount() || newName == null || newName.isBlank()) return;

        String oldName = (String) tableModel.getValueAt(idx, 0);
        if (oldName.equals(newName)) return;

        tableModel.setValueAt(newName, idx, 0);
        playtimeTracker.renameProfile(oldName, newName);
        profilesData.getUsernames().set(idx, newName);
        if (oldName.equals(profilesData.getLastUsed())) profilesData.setLastUsed(newName);

        saveProfiles();
        if (onProfilesChanged != null) onProfilesChanged.run();
    }

    public String showAddProfileDialog(Component parent) {
        String name = JOptionPane.showInputDialog(parent, "New profile name:");
        if (name != null && !name.isBlank()) {
            addProfile(name);
            return name;
        }
        return null;
    }

    public ArrayList<String> getProfiles() {
        ArrayList<String> profiles = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++)
            profiles.add((String) tableModel.getValueAt(i, 0));
        return profiles;
    }

    public void setProfiles(ArrayList<String> profiles) {
        tableModel.setRowCount(0);
        for (String profile : profiles) {
            tableModel.addRow(new Object[]{profile, playtimeTracker.getPlaytime(profile)});
            playtimeTracker.registerProfile(profile);
        }
    }

    public void setTimePlayed(String profile, int hours, int minutes) {
        String time = hours + "h " + minutes + "m";
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(profile)) {
                tableModel.setValueAt(time, i, 1);
                break;
            }
        }
    }

    public void openProfileEditorWindow(int idx) {
        if (idx < 0 || idx >= tableModel.getRowCount()) return;

        String username   = (String) tableModel.getValueAt(idx, 0);
        String timePlayed = (String) tableModel.getValueAt(idx, 1);

        JFrame editorFrame = new JFrame("Profile Editor");
        try {
            editorFrame.setIconImage(new ImageIcon(getClass().getResource("/img/favicon.png")).getImage());
        } catch (Exception ignored) {}

        editorFrame.setSize(400, 440);
        editorFrame.setLocationRelativeTo(null);
        editorFrame.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel profileInfo = new JPanel(new GridBagLayout());
        profileInfo.setBorder(BorderFactory.createTitledBorder("Profile Info"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        profileInfo.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JTextField usernameField = new JTextField(username);
        profileInfo.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0;
        profileInfo.add(new JLabel("Time Played: " + timePlayed), gbc);

        mainPanel.add(profileInfo);

        JPanel wipPanel = new JPanel(new BorderLayout());
        wipPanel.setBorder(BorderFactory.createTitledBorder("WIP"));
        JLabel wipLabel = new JLabel("More options Soon ™");
        wipLabel.setHorizontalAlignment(SwingConstants.CENTER);
        wipPanel.add(wipLabel, BorderLayout.CENTER);
        wipPanel.setPreferredSize(new Dimension(0, 300));
        wipPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(wipPanel);

        editorFrame.add(mainPanel, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new BorderLayout());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(80, 21));
        cancelButton.addActionListener(ev -> editorFrame.dispose());
        buttonsPanel.add(cancelButton, BorderLayout.WEST);

        JButton saveButton = new JButton("Save Profile");
        saveButton.setPreferredSize(new Dimension(120, 21));
        saveButton.addActionListener(ev -> {
            String newUsername = usernameField.getText().trim();
            if (!newUsername.isEmpty() && !newUsername.equals(username))
                renameProfile(idx, newUsername);
            editorFrame.dispose();
        });
        buttonsPanel.add(saveButton, BorderLayout.EAST);

        editorFrame.add(buttonsPanel, BorderLayout.SOUTH);
        editorFrame.setVisible(true);
    }
}
