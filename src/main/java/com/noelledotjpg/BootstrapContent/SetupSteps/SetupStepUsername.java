package com.noelledotjpg.BootstrapContent.SetupSteps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class SetupStepUsername extends JPanel {

    private final JTextField usernameField;
    private final JButton    continueButton;
    private final JButton    cancelButton;

    public SetupStepUsername() {
        setLayout(null);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel logo = new JLabel(new ImageIcon(getClass().getResource("/img/logo.png")));
        logo.setBounds(SetupLayout.CENTER_X, 10, 274, 59);
        add(logo);

        JPanel container = new JPanel(null);
        container.setBounds(10, 80, SetupLayout.INNER_WIDTH, 65);
        container.setBorder(BorderFactory.createTitledBorder("Username"));
        container.setBackground(UIManager.getColor("Panel.background"));

        usernameField = new JTextField();
        usernameField.setBounds(10, 20, SetupLayout.INNER_WIDTH - 20, 20);
        usernameField.setToolTipText("e.g. Steve");
        ((AbstractDocument) usernameField.getDocument()).setDocumentFilter(new UsernameFilter());
        container.add(usernameField);

        JLabel charCount = new JLabel("0/16");
        charCount.setForeground(Color.DARK_GRAY);
        charCount.setFont(charCount.getFont().deriveFont(11f));
        charCount.setBounds(SetupLayout.INNER_WIDTH - 50, 43, 40, 15);
        charCount.setHorizontalAlignment(SwingConstants.RIGHT);
        container.add(charCount);

        JLabel info = new JLabel("(This will be saved as your profile)");
        info.setForeground(Color.DARK_GRAY);
        info.setFont(info.getFont().deriveFont(11f));
        info.setBounds(10, 43, 200, 15);
        container.add(info);

        add(container);

        continueButton = new JButton("Continue");
        continueButton.setBounds(SetupLayout.BTN_LEFT_X, 200, 100, 25);
        continueButton.setEnabled(false);
        add(continueButton);

        cancelButton = new JButton("Back");
        cancelButton.setBounds(SetupLayout.BTN_RIGHT_X, 200, 100, 25);
        add(cancelButton);

        usernameField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                int len = usernameField.getText().trim().length();
                charCount.setText(len + "/16");
                continueButton.setEnabled(len > 0);
            }
        });
    }

    public String getUsername()        { return usernameField.getText().trim(); }
    public JButton getContinueButton() { return continueButton; }
    public JButton getCancelButton()   { return cancelButton; }

    /** Restricts input to alphanumeric + underscore, max 16 characters. */
    public static class UsernameFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            if (text == null) return;
            String filtered = text.replaceAll("[^a-zA-Z0-9_]", "");
            int newLen = fb.getDocument().getLength() + filtered.length();
            if (newLen > 16) filtered = filtered.substring(0, 16 - fb.getDocument().getLength());
            super.insertString(fb, offset, filtered, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr)
                throws BadLocationException {
            if (text == null) return;
            String filtered = text.replaceAll("[^a-zA-Z0-9_]", "");
            int newLen = fb.getDocument().getLength() - length + filtered.length();
            if (newLen > 16) filtered = filtered.substring(0, Math.max(0, 16 - (fb.getDocument().getLength() - length)));
            super.replace(fb, offset, length, filtered, attr);
        }
    }
}