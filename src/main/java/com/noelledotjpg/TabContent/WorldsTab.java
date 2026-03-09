package com.noelledotjpg.TabContent;

import com.noelledotjpg.BootstrapContent.VarsData;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class WorldsTab extends JPanel {

    private JTable table;
    private DefaultTableModel model;

    private JButton deleteButton;
    private JButton refreshButton;

    private Path worldsDir;

    public WorldsTab(VarsData varsData) {
        setLayout(new BorderLayout());

        // Set the worlds directory based on VarsData
        if (varsData != null && varsData.getLceFolder() != null) {
            worldsDir = Paths.get(varsData.getLceFolder(),
                    "build", "Release", "Windows64", "GameHDD");
        }

        // Table model with non-editable cells
        model = new DefaultTableModel(
                new Object[]{"World Name", "Game Mode", "Seed", "Size", "Created"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);

        // Align headers to the left
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);

        // --- Styling to match ServersTab ---
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setBackground(Color.WHITE);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setShowGrid(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);

        // Optional: set preferred column widths similar to ServersTab
        table.getColumnModel().getColumn(0).setPreferredWidth(300); // World Name
        table.getColumnModel().getColumn(1).setPreferredWidth(80);  // Game Mode
        table.getColumnModel().getColumn(2).setPreferredWidth(80);  // Seed
        table.getColumnModel().getColumn(3).setPreferredWidth(60);  // Size
        table.getColumnModel().getColumn(4).setPreferredWidth(120); // Created

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());

        // Buttons panel
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");

        addButton.setEnabled(false);
        editButton.setEnabled(false);

        leftButtons.add(addButton);
        leftButtons.add(editButton);
        leftButtons.add(deleteButton);
        leftButtons.add(refreshButton);
        buttons.add(leftButtons, BorderLayout.WEST);

        JLabel infoLabel = new JLabel("<html><i>( i ) World data parsing is currently a WIP, so managing your worlds from here is not ideal.</i></html>");
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setForeground(Color.DARK_GRAY);
        buttons.add(infoLabel, BorderLayout.CENTER);

        add(buttons, BorderLayout.SOUTH);

        // Action listeners
        refreshButton.addActionListener(e -> loadWorlds());
        deleteButton.addActionListener(e -> deleteWorld());

        // Initial load
        loadWorlds();
    }

    private void loadWorlds() {
        model.setRowCount(0); // Clear table
        List<WorldsData> worlds = readWorlds();

        // Populate table from WorldsData
        for (WorldsData w : worlds) {
            model.addRow(new Object[]{
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
                Path saveFile = dir.resolve("saveData.ms");
                if (!Files.exists(saveFile)) continue;

                // Use the new WorldsData parser
                worlds.add(new WorldsData(dir));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return worlds;
    }

    private void deleteWorld() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String name = model.getValueAt(row, 0).toString();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this world? '" + name + "' will be lost forever! (A long time!)",
                "Confirm",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            List<WorldsData> worlds = readWorlds();
            WorldsData w = worlds.get(row);

            // Recursively delete all files in the world folder
            Files.walk(w.getFolder())
                    .sorted((a, b) -> b.compareTo(a)) // delete children first
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception ignored) {
                        }
                    });

            loadWorlds(); // Refresh table

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}