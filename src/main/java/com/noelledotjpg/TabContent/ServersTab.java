package com.noelledotjpg.TabContent;

import com.google.gson.Gson;
import com.noelledotjpg.BootstrapContent.VarsData;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ServersTab extends JPanel {

    private JTable table;
    private DefaultTableModel model;
    private JButton addButton, removeButton, editButton, refreshButton;

    private Path dbPath;
    private Gson gson = new Gson();

    public static class Server {
        public String ip;
        public int port;
        public String name;
    }

    private void setPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(Color.GRAY);

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(Color.GRAY);
                }
            }
        });
    }

    public ServersTab(VarsData varsData) {
        setLayout(new BorderLayout());

        // Resolve servers.db path using vars.json LCE folder
        if (varsData != null && varsData.getLceFolder() != null) {
            dbPath = Paths.get(varsData.getLceFolder(), "build", "Release", "servers.db");
        } else {
            dbPath = Paths.get("servers.db"); // fallback
        }

        // Table setup
        model = new DefaultTableModel(new Object[]{"Name", "IP", "Port"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(model);

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setBackground(Color.WHITE);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setShowGrid(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(500); // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(60); // IP
        table.getColumnModel().getColumn(2).setPreferredWidth(30);  // Port

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton = new JButton("Add");
        removeButton = new JButton("Remove");
        editButton = new JButton("Edit");
        refreshButton = new JButton("Refresh");

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(refreshButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Load existing servers
        loadServers();

        // Button actions
        addButton.addActionListener(e -> addServer());
        editButton.addActionListener(e -> editServer());
        removeButton.addActionListener(e -> removeServer());
        refreshButton.addActionListener(e -> loadServers());

        // Double-click edit
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editServer();
            }
        });
    }

    private void loadServers() {
        model.setRowCount(0);
        List<Server> servers = readServers();
        for (Server s : servers) {
            model.addRow(new Object[]{s.name, s.ip, s.port});
        }
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
        Server old = servers.get(row);
        Server updated = showServerDialog(old);
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
        JTextField ipField = new JTextField();
        JTextField portField = new JTextField();
        JTextField nameField = new JTextField();

        // Placeholder texts
        String ipPlaceholder = "e.g. 127.0.0.1";
        String portPlaceholder = "25565"; // default port
        String namePlaceholder = "Server Name";

        // Initialize fields
        if (server != null) {
            ipField.setText(server.ip);
            portField.setText(String.valueOf(server.port));
            nameField.setText(server.name);
            ipField.setForeground(Color.BLACK);
            portField.setForeground(Color.BLACK);
            nameField.setForeground(Color.BLACK);
        } else {
            setPlaceholder(ipField, ipPlaceholder);
            setPlaceholder(portField, portPlaceholder);
            setPlaceholder(nameField, namePlaceholder);
        }

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("IP:"));
        panel.add(ipField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);
        panel.add(new JLabel("Name:"));
        panel.add(nameField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                server == null ? "Add Server" : "Edit Server",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String ip = ipField.getText().trim();
            int port;
            String portText = portField.getText().trim();

            // Use default port if empty
            if (portText.isEmpty() || portText.equals(portPlaceholder)) {
                port = 25565;
            } else {
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Invalid port number");
                    return null;
                }
            }

            String name = nameField.getText().trim();
            if (ip.isEmpty() || ip.equals(ipPlaceholder) || name.isEmpty() || name.equals(namePlaceholder)) {
                JOptionPane.showMessageDialog(this, "IP and Name cannot be empty");
                return null;
            }

            Server s = new Server();
            s.ip = ip;
            s.port = port;
            s.name = name;
            return s;
        }
        return null;
    }

    // --- Reading servers.db ---
    private List<Server> readServers() {
        List<Server> servers = new ArrayList<>();
        if (!Files.exists(dbPath)) return servers;

        try (DataInputStream in = new DataInputStream(Files.newInputStream(dbPath))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic).equals("MCSV")) return servers;

            int version = Integer.reverseBytes(in.readInt());
            int count = Integer.reverseBytes(in.readInt());

            for (int i = 0; i < count; i++) {
                int ipLen = Short.reverseBytes(in.readShort()) & 0xFFFF;
                byte[] ipBytes = new byte[ipLen];
                in.readFully(ipBytes);
                String ip = new String(ipBytes);

                int port = Short.reverseBytes(in.readShort()) & 0xFFFF;

                int nameLen = Short.reverseBytes(in.readShort()) & 0xFFFF;
                byte[] nameBytes = new byte[nameLen];
                in.readFully(nameBytes);
                String name = new String(nameBytes);

                Server s = new Server();
                s.ip = ip;
                s.port = port;
                s.name = name;
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
                out.writeInt(Integer.reverseBytes(1));
                out.writeInt(Integer.reverseBytes(servers.size()));

                for (Server s : servers) {
                    byte[] ipBytes = s.ip.getBytes();
                    byte[] nameBytes = s.name.getBytes();

                    out.writeShort(Short.reverseBytes((short) ipBytes.length));
                    out.write(ipBytes);

                    out.writeShort(Short.reverseBytes((short) s.port));

                    out.writeShort(Short.reverseBytes((short) nameBytes.length));
                    out.write(nameBytes);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}