package com.noelledotjpg.BootstrapContent.SetupSteps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
        container.add(usernameField);

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
                continueButton.setEnabled(!usernameField.getText().trim().isEmpty());
            }
        });
    }

    public String getUsername()        { return usernameField.getText().trim(); }
    public JButton getContinueButton() { return continueButton; }
    public JButton getCancelButton()   { return cancelButton; }
}