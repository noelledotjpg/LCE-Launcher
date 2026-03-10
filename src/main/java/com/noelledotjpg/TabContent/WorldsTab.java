package com.noelledotjpg.TabContent;

import com.noelledotjpg.BootstrapContent.VarsData;

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

    private static final int THUMB_W  = 64;
    private static final int THUMB_H  = 64;
    private static final int ROW_H    = THUMB_H + 2;

    private final JTable           table;
    private final DefaultTableModel model;
    private final JButton          deleteButton;
    private final JButton          refreshButton;
    private       Path             worldsDir;

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

        refreshButton = findButton("Refresh");
        deleteButton  = findButton("Delete");

        loadWorlds();
    }

    private JTable buildTable() {
        JTable t = new JTable(model);

        t.setRowHeight(ROW_H);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.setBackground(Color.WHITE);
        t.setGridColor(Color.LIGHT_GRAY);
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
            label.setBackground(selected ? tbl.getSelectionBackground() : new Color(30, 30, 30));
            if (value instanceof ImageIcon icon)
                label.setIcon(icon);
            return label;
        });

        return t;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new BorderLayout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn     = new JButton("Add");
        JButton editBtn    = new JButton("Edit");
        JButton deleteBtn  = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");

        addBtn.setEnabled(false);
        editBtn.setEnabled(false);

        left.add(addBtn);
        left.add(editBtn);
        left.add(deleteBtn);
        left.add(refreshBtn);
        bar.add(left, BorderLayout.WEST);

        refreshBtn.addActionListener(e -> loadWorlds());
        deleteBtn.addActionListener(e -> deleteWorld());

        bar.putClientProperty("deleteBtn",  deleteBtn);
        bar.putClientProperty("refreshBtn", refreshBtn);

        return bar;
    }

    private JButton findButton(String label) {
        Component south = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (south instanceof JPanel bar) {
            Component prop = (Component) bar.getClientProperty(label.toLowerCase() + "Btn");
            if (prop instanceof JButton b) return b;
        }
        return new JButton();
    }


    private void loadWorlds() {
        model.setRowCount(0);
        for (WorldsData w : readWorlds()) {
            ImageIcon icon = scaledIcon(w.getThumbnail());
            model.addRow(new Object[]{
                    icon,
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

    private void deleteWorld() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        String worldName = model.getValueAt(row, 1).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + worldName + "'?\nThis cannot be undone.",
                "Delete World",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            List<WorldsData> worlds = readWorlds();
            if (row >= worlds.size()) return;

            Files.walk(worlds.get(row).getFolder())
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });

            loadWorlds();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ImageIcon scaledIcon(BufferedImage img) {
        if (img == null) return null;
        Image scaled = img.getScaledInstance(THUMB_W, THUMB_H, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
}