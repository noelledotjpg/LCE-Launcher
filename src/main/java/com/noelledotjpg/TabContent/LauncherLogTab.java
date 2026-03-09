package com.noelledotjpg.TabContent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LauncherLogTab extends JPanel {

    private final JTextArea logArea;
    private final JTextField searchField;
    private final JButton findNextButton, findPrevButton;
    private final JCheckBox caseSensitiveCheck, wrapCheck;

    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss");
    private int lastSearchIndex = 0;

    public LauncherLogTab() {
        setLayout(new BorderLayout());

        // --- Log Area ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // --- Search Panel ---
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        searchField = new JTextField(15); // smaller input area
        searchField.setForeground(Color.GRAY);
        searchField.setText("Search logs...");
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (searchField.getText().equals("Search logs...")) {
                    searchField.setText("");
                    searchField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setText("Search logs...");
                    searchField.setForeground(Color.GRAY);
                }
            }
        });

        findNextButton = new JButton("Find Next");
        findPrevButton = new JButton("Find Prev");
        caseSensitiveCheck = new JCheckBox("Case Sensitive");
        wrapCheck = new JCheckBox("Soft Wrap", true);

        wrapCheck.addActionListener(e -> {
            logArea.setLineWrap(wrapCheck.isSelected());
            logArea.setWrapStyleWord(wrapCheck.isSelected());
        });

        findNextButton.addActionListener(e -> search(true));
        findPrevButton.addActionListener(e -> search(false));

        searchPanel.add(searchField);
        searchPanel.add(findNextButton);
        searchPanel.add(findPrevButton);
        searchPanel.add(caseSensitiveCheck);
        searchPanel.add(wrapCheck);

        add(searchPanel, BorderLayout.SOUTH);
    }

    // --- Log methods ---
    public void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timestampFormat.format(new Date());
            logArea.append("[" + timestamp + "] " + text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void clearLog() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }

    // --- Search method ---
    private void search(boolean forward) {
        String searchText = searchField.getText();
        if (searchText.isEmpty() || searchText.equals("Search logs...")) return;

        String content = logArea.getText();
        if (!caseSensitiveCheck.isSelected()) {
            searchText = searchText.toLowerCase();
            content = content.toLowerCase();
        }

        int index;
        if (forward) {
            index = content.indexOf(searchText, lastSearchIndex);
            if (index == -1) { // wrap around
                index = content.indexOf(searchText, 0);
            }
        } else {
            index = content.lastIndexOf(searchText, lastSearchIndex - 1);
            if (index == -1) { // wrap around
                index = content.lastIndexOf(searchText, content.length());
            }
        }

        if (index != -1) {
            logArea.requestFocus();
            logArea.select(index, index + searchText.length());
            lastSearchIndex = forward ? index + searchText.length() : index;
        }
    }

    // --- StreamGobbler ---
    public static class StreamGobbler implements Runnable {

        private final java.io.InputStream inputStream;
        private final LauncherLogTab logTab;

        public StreamGobbler(java.io.InputStream inputStream, LauncherLogTab logTab) {
            this.inputStream = inputStream;
            this.logTab = logTab;
        }

        @Override
        public void run() {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logTab.appendLog(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}