package com.noelledotjpg.TabContent;

import com.noelledotjpg.Data.VarsData;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ServersTab extends JPanel {

    private static final int THUMB_W = 64;
    private static final int THUMB_H = 64;
    private static final int ROW_H   = THUMB_H + 2;

    private final JTable            table;
    private final DefaultTableModel model;
    private final Path              dbPath;

    private static final BufferedImage UNKNOWN_THUMBNAIL = loadUnknownThumbnail();

    private static BufferedImage loadUnknownThumbnail() {
        try (InputStream is = ServersTab.class.getResourceAsStream("/img/unknown.png")) {
            if (is != null) return ImageIO.read(is);
        } catch (Exception ignored) {}
        return null;
    }

    public static class Server {
        public String ip;
        public int    port;
        public String name;
    }

    public ServersTab(VarsData varsData) {
        setLayout(new BorderLayout());

        dbPath = (varsData != null && varsData.getLceFolder() != null)
                ? Paths.get(varsData.getLceFolder(), "build", "Release", "servers.db")
                : Paths.get("servers.db");

        model = new DefaultTableModel(new Object[]{"", "Name", "IP", "Port"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 0 ? ImageIcon.class : String.class;
            }
        };

        table = new JTable(model);

        table.setRowHeight(ROW_H);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setBackground(Color.WHITE);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setShowGrid(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);

        table.getColumnModel().getColumn(0).setPreferredWidth(THUMB_W);
        table.getColumnModel().getColumn(0).setMaxWidth(THUMB_W);
        table.getColumnModel().getColumn(0).setMinWidth(THUMB_W);
        table.getColumnModel().getColumn(0).setCellRenderer((tbl, value, selected, focused, row, col) -> {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(selected ? tbl.getSelectionBackground() : new Color(30, 30, 30));
            if (value instanceof ImageIcon icon) label.setIcon(icon);
            return label;
        });

        table.getColumnModel().getColumn(1).setPreferredWidth(500);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(30);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton addButton     = new JButton("Add");
        JButton editButton    = new JButton("Edit");
        JButton removeButton  = new JButton("Remove");
        JButton refreshButton = new JButton("Refresh");
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(refreshButton);
        add(buttonPanel, BorderLayout.SOUTH);

        loadServers();

        addButton.addActionListener(e -> addServer());
        editButton.addActionListener(e -> editServer());
        removeButton.addActionListener(e -> removeServer());
        refreshButton.addActionListener(e -> loadServers());

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editServer();
            }
        });
    }

    private static ImageIcon scaledIcon(BufferedImage img) {
        if (img == null) return null;
        return new ImageIcon(img.getScaledInstance(THUMB_W, THUMB_H, Image.SCALE_SMOOTH));
    }

    private void loadServers() {
        model.setRowCount(0);
        ImageIcon icon = scaledIcon(UNKNOWN_THUMBNAIL);
        for (Server s : readServers())
            model.addRow(new Object[]{icon, s.name, s.ip, s.port});
    }

    private void addServer() {
        Server s = showServerDialog(null);
        if (s != null) {
            List<Server> servers = readServers();
            servers.add(s);
            writeServers(servers);
            loadServers();
        }
    }

    private void editServer() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        List<Server> servers = readServers();
        Server updated = showServerDialog(servers.get(row));
        if (updated != null) {
            servers.set(row, updated);
            writeServers(servers);
            loadServers();
        }
    }

    private void removeServer() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        List<Server> servers = readServers();
        servers.remove(row);
        writeServers(servers);
        loadServers();
    }

    private Server showServerDialog(Server server) {
        JTextField ipField   = new JTextField();
        JTextField portField = new JTextField();
        JTextField nameField = new JTextField();

        final String IP_PLACEHOLDER   = "e.g. 127.0.0.1";
        final String PORT_PLACEHOLDER = "25565";
        final String NAME_PLACEHOLDER = "Server Name";

        if (server != null) {
            ipField.setText(server.ip);
            portField.setText(String.valueOf(server.port));
            nameField.setText(server.name);
        } else {
            setPlaceholder(ipField,   IP_PLACEHOLDER);
            setPlaceholder(portField, PORT_PLACEHOLDER);
            setPlaceholder(nameField, NAME_PLACEHOLDER);
        }

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("IP:"));   panel.add(ipField);
        panel.add(new JLabel("Port:")); panel.add(portField);
        panel.add(new JLabel("Name:")); panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                server == null ? "Add Server" : "Edit Server",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String ip       = ipField.getText().trim();
        String portText = portField.getText().trim();
        String name     = nameField.getText().trim();

        int port;
        if (portText.isEmpty() || portText.equals(PORT_PLACEHOLDER)) {
            port = 25565;
        } else {
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid port number");
                return null;
            }
        }

        if (ip.isEmpty() || ip.equals(IP_PLACEHOLDER) || name.isEmpty() || name.equals(NAME_PLACEHOLDER)) {
            JOptionPane.showMessageDialog(this, "IP and Name cannot be empty");
            return null;
        }

        Server s = new Server();
        s.ip   = ip;
        s.port = port;
        s.name = name;
        return s;
    }

    private void setPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(Color.GRAY);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(Color.GRAY);
                }
            }
        });
    }

    private List<Server> readServers() {
        List<Server> servers = new ArrayList<>();
        if (!Files.exists(dbPath)) return servers;

        try (DataInputStream in = new DataInputStream(Files.newInputStream(dbPath))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic).equals("MCSV")) return servers;

            readIntLE(in);
            int count = readIntLE(in);

            for (int i = 0; i < count; i++) {
                byte[] ipBytes = new byte[readShortLE(in)];
                in.readFully(ipBytes);

                int port = readShortLE(in);

                byte[] nameBytes = new byte[readShortLE(in)];
                in.readFully(nameBytes);

                Server s = new Server();
                s.ip   = new String(ipBytes);
                s.port = port;
                s.name = new String(nameBytes);
                servers.add(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return servers;
    }

    private void writeServers(List<Server> servers) {
        try {
            Files.createDirectories(dbPath.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(dbPath))) {
                out.writeBytes("MCSV");
                writeIntLE(out, 1);
                writeIntLE(out, servers.size());

                for (Server s : servers) {
                    byte[] ipBytes   = s.ip.getBytes();
                    byte[] nameBytes = s.name.getBytes();
                    writeShortLE(out, ipBytes.length);
                    out.write(ipBytes);
                    writeShortLE(out, s.port);
                    writeShortLE(out, nameBytes.length);
                    out.write(nameBytes);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int readShortLE(DataInputStream in) throws IOException {
        int lo = in.readUnsignedByte();
        int hi = in.readUnsignedByte();
        return (hi << 8) | lo;
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    private static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }
}