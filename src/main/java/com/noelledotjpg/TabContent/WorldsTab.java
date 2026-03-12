package com.noelledotjpg.TabContent;

import com.noelledotjpg.Data.VarsData;
import com.noelledotjpg.Data.WorldsData;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WorldsTab extends JPanel {

    private static final int THUMB_W = 64;
    private static final int THUMB_H = 64;
    private static final int ROW_H   = THUMB_H + 2;

    private final JTable            table;
    private final DefaultTableModel model;
    private       Path              worldsDir;

    private final List<WorldsData> loadedWorlds = new ArrayList<>();

    public WorldsTab(VarsData varsData) {
        setLayout(new BorderLayout());

        if (varsData != null && varsData.getLceFolder() != null)
            worldsDir = Paths.get(varsData.getLceFolder(),
                    "build", "Release", "Windows64", "GameHDD");

        model = new DefaultTableModel(
                new Object[]{"", "World Name", "Game Mode", "Seed", "Size", "Created"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? ImageIcon.class : String.class;
            }
        };

        table = buildTable();

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildButtonBar(), BorderLayout.SOUTH);

        loadWorlds();
    }

    private JTable buildTable() {
        JTable t = new JTable(model);

        t.setRowHeight(ROW_H);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.setShowGrid(true);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);

        DefaultTableCellRenderer headerLeft = new DefaultTableCellRenderer();
        headerLeft.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < t.getColumnCount(); i++)
            t.getColumnModel().getColumn(i).setHeaderRenderer(headerLeft);

        t.getColumnModel().getColumn(0).setPreferredWidth(THUMB_W);
        t.getColumnModel().getColumn(0).setMaxWidth(THUMB_W);
        t.getColumnModel().getColumn(0).setMinWidth(THUMB_W);
        t.getColumnModel().getColumn(1).setPreferredWidth(260);
        t.getColumnModel().getColumn(2).setPreferredWidth(80);
        t.getColumnModel().getColumn(3).setPreferredWidth(100);
        t.getColumnModel().getColumn(4).setPreferredWidth(60);
        t.getColumnModel().getColumn(5).setPreferredWidth(120);

        t.getColumnModel().getColumn(0).setCellRenderer((tbl, value, selected, focused, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(selected ? tbl.getSelectionBackground() : tbl.getBackground());
            if (value instanceof ImageIcon icon) label.setIcon(icon);
            return label;
        });

        t.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && t.getSelectedRow() >= 0)
                    openWorldEditor(t.getSelectedRow());
            }
        });

        return t;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        JButton addBtn     = new JButton("Add");
        JButton editBtn    = new JButton("Edit");
        JButton deleteBtn  = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");

        addBtn.setEnabled(false);
        editBtn.setEnabled(false);

        bar.add(addBtn);
        bar.add(editBtn);
        bar.add(deleteBtn);
        bar.add(refreshBtn);

        table.getSelectionModel().addListSelectionListener(e ->
                editBtn.setEnabled(table.getSelectedRow() >= 0));

        editBtn.addActionListener(e -> openWorldEditor(table.getSelectedRow()));
        refreshBtn.addActionListener(e -> loadWorlds());
        deleteBtn.addActionListener(e -> deleteWorld());

        return bar;
    }

    private void loadWorlds() {
        model.setRowCount(0);
        loadedWorlds.clear();
        for (WorldsData w : readWorlds()) {
            loadedWorlds.add(w);
            model.addRow(new Object[]{
                    scaledIcon(w.getThumbnail()),
                    w.getName(),
                    w.getGamemode(),
                    w.getSeed(),
                    w.getSize(),
                    w.getCreated()
            });
        }
    }

    private List<WorldsData> readWorlds() {
        List<WorldsData> worlds = new ArrayList<>();
        if (worldsDir == null || !Files.exists(worldsDir)) return worlds;

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(worldsDir)) {
            for (Path dir : dirs) {
                if (Files.exists(dir.resolve("saveData.ms")))
                    worlds.add(new WorldsData(dir));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return worlds;
    }

    private static final String[] GAMEMODE_LABELS = {"Survival", "Creative", "Adventure", "Spectator"};

    private void openWorldEditor(int row) {
        if (row < 0 || row >= loadedWorlds.size()) return;
        WorldsData world = loadedWorlds.get(row);

        JFrame editorFrame = new JFrame("World Editor");
        try {
            editorFrame.setIconImage(new ImageIcon(getClass().getResource("/img/favicon.png")).getImage());
        } catch (Exception ignored) {}

        editorFrame.setSize(400, 480);
        editorFrame.setLocationRelativeTo(null);
        editorFrame.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("World Info"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(world.getName());
        addInfoRow(infoPanel, gbc, 0, "World Name:", nameField);

        JComboBox<String> gamemodeCombo = new JComboBox<>(GAMEMODE_LABELS);
        int currentId = world.getGamemodeId();
        if (currentId >= 0 && currentId < GAMEMODE_LABELS.length)
            gamemodeCombo.setSelectedIndex(currentId);
        else
            gamemodeCombo.setEnabled(false);
        addInfoRow(infoPanel, gbc, 1, "Game Mode:", gamemodeCombo);

        JTextField seedField = new JTextField(world.getSeed());
        seedField.setEditable(false);
        JButton copyBtn = new JButton("Copy");
        copyBtn.setPreferredSize(new Dimension(60, seedField.getPreferredSize().height));
        copyBtn.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel =
                    new java.awt.datatransfer.StringSelection(world.getSeed());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            copyBtn.setText("✓");
            Timer t = new Timer(1200, ev -> copyBtn.setText("Copy"));
            t.setRepeats(false);
            t.start();
        });
        JPanel seedRow = new JPanel(new BorderLayout(4, 0));
        seedRow.setOpaque(false);
        seedRow.add(seedField, BorderLayout.CENTER);
        seedRow.add(copyBtn,   BorderLayout.EAST);
        addInfoRow(infoPanel, gbc, 2, "Seed:", seedRow);

        addInfoRow(infoPanel, gbc, 3, "Size:",    new JLabel(world.getSize()));
        addInfoRow(infoPanel, gbc, 4, "Created:", new JLabel(world.getCreated()));

        mainPanel.add(infoPanel);

        JPanel thumbPanel = new JPanel(new BorderLayout());
        thumbPanel.setBorder(BorderFactory.createTitledBorder("Thumbnail"));
        thumbPanel.setPreferredSize(new Dimension(0, 200));
        thumbPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        BufferedImage thumb = world.getThumbnail();
        if (thumb != null) {
            Image scaled = thumb.getScaledInstance(-1, 160, Image.SCALE_SMOOTH);
            JLabel thumbLabel = new JLabel(new ImageIcon(scaled));
            thumbLabel.setHorizontalAlignment(SwingConstants.CENTER);
            thumbPanel.add(thumbLabel, BorderLayout.CENTER);
        } else {
            JLabel noThumb = new JLabel("No thumbnail available");
            noThumb.setHorizontalAlignment(SwingConstants.CENTER);
            noThumb.setForeground(Color.GRAY);
            thumbPanel.add(noThumb, BorderLayout.CENTER);
        }

        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(thumbPanel);

        editorFrame.add(mainPanel, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new BorderLayout());
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 8, 8, 8));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(80, 21));
        cancelButton.addActionListener(ev -> editorFrame.dispose());
        buttonsPanel.add(cancelButton, BorderLayout.WEST);

        JButton saveButton = new JButton("Save World");
        saveButton.setPreferredSize(new Dimension(120, 21));
        saveButton.addActionListener(ev -> {
            String newName       = nameField.getText().trim();
            int    newGamemodeId = gamemodeCombo.isEnabled() ? gamemodeCombo.getSelectedIndex() : -1;
            boolean nameChanged  = !newName.isEmpty() && !newName.equals(world.getName());
            boolean modeChanged  = newGamemodeId >= 0 && newGamemodeId != world.getGamemodeId();

            if (modeChanged) {
                int confirm = JOptionPane.showConfirmDialog(editorFrame,
                        "Changing the game mode will directly edit the save file on disk.\n" +
                                "Make sure you have a backup before proceeding.\n\n" +
                                "Change game mode to " + GAMEMODE_LABELS[newGamemodeId] + "?",
                        "Confirm Game Mode Change",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            if (nameChanged) {
                try {
                    world.rename(newName);
                    model.setValueAt(newName, row, 1);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(editorFrame,
                            "Failed to rename world:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            if (modeChanged) {
                try {
                    world.setGamemode(newGamemodeId);
                    model.setValueAt(GAMEMODE_LABELS[newGamemodeId], row, 2);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(editorFrame,
                            "Failed to change game mode:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            editorFrame.dispose();
        });
        buttonsPanel.add(saveButton, BorderLayout.EAST);

        editorFrame.add(buttonsPanel, BorderLayout.SOUTH);
        editorFrame.setVisible(true);
    }

    private static void addInfoRow(JPanel panel, GridBagConstraints gbc,
                                   int row, String labelText, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void deleteWorld() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= loadedWorlds.size()) return;

        String worldName = model.getValueAt(row, 1).toString();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + worldName + "'?\nThis cannot be undone.",
                "Delete World", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            Files.walk(loadedWorlds.get(row).getFolder())
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            loadWorlds();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ImageIcon scaledIcon(BufferedImage img) {
        if (img == null) return null;
        return new ImageIcon(img.getScaledInstance(THUMB_W, THUMB_H, Image.SCALE_SMOOTH));
    }
}