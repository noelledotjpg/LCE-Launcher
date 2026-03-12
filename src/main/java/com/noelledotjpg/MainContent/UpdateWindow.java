package com.noelledotjpg.MainContent;

import com.noelledotjpg.Data.UpdateChecker.NightlyInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Entry-point facade — creates dialogs and hands off to the three worker classes:
 *   UpdWinLCE      — LCE nightly update (check + prompt + progress)
 *   UpdWinLauncher — LCE reinstall (cmake + build)
 *   UpdWinDebug    — Debug mode switch (build if needed, then swap)
 */
public class UpdateWindow {

    // -------------------------------------------------------------------------
    // LCE reinstall
    // -------------------------------------------------------------------------

    public static void showRebuild(Component parent) {
        JDialog dialog = makeDialog(parent, "Reinstalling LCE");
        dialog.setContentPane(new UpdWinLauncher(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // LCE nightly update check + prompt
    // -------------------------------------------------------------------------

    public static void checkAndPrompt(Component parent, String installedHash) {
        Thread t = new Thread(() -> {
            NightlyInfo latest;
            try {
                latest = com.noelledotjpg.Data.UpdateChecker.fetchLatest();
            } catch (Exception e) {
                System.err.println("Update check failed: " + e.getMessage());
                return;
            }

            if (latest.commitHash().equalsIgnoreCase(installedHash == null ? "" : installedHash))
                return;

            String skipped = UpdWinLCE.readSkippedHash();
            if (latest.commitHash().equalsIgnoreCase(skipped == null ? "" : skipped))
                return;

            SwingUtilities.invokeLater(() -> showUpdatePrompt(parent, latest));
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private static void showUpdatePrompt(Component parent, NightlyInfo latest) {
        JDialog dialog = makeDialog(parent, "LCE Update Available");
        dialog.setLayout(new BorderLayout());

        JPanel msgPanel = new JPanel();
        msgPanel.setLayout(new BoxLayout(msgPanel, BoxLayout.Y_AXIS));
        msgPanel.setBorder(new EmptyBorder(16, 20, 10, 20));

        JLabel body = new JLabel("<html>There is a new build available:<br>Nightly - "
                + latest.shortHash() + "</html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setBorder(new EmptyBorder(8, 0, 0, 0));
        msgPanel.add(body);
        dialog.add(msgPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        JButton updateBtn = sized("Update");
        JButton skipBtn   = sized("Skip");
        JButton cancelBtn = sized("Cancel");
        btnPanel.add(updateBtn);
        btnPanel.add(skipBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dialog.dispose());
        skipBtn.addActionListener(e -> {
            UpdWinLCE.writeSkippedHash(latest.commitHash());
            dialog.dispose();
        });
        updateBtn.addActionListener(e -> {
            dialog.dispose();
            showUpdateProgress(parent, latest);
        });

        dialog.pack();
        dialog.setMinimumSize(new Dimension(340, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void showUpdateProgress(Component parent, NightlyInfo latest) {
        JDialog dialog = makeDialog(parent, "Updating \u2014 Nightly " + latest.shortHash());
        dialog.setContentPane(new UpdWinLCE(latest, dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Debug mode switch
    // -------------------------------------------------------------------------

    public static void showDebugSwitch(Component parent, boolean toDebug,
                                       Runnable onSuccess, Runnable onFailure) {
        String lceFolder = UpdWinDebug.readLceFolder();
        if (lceFolder == null) {
            JOptionPane.showMessageDialog(parent,
                    "LCE folder not found in vars.json.", "Error", JOptionPane.ERROR_MESSAGE);
            onFailure.run();
            return;
        }

        java.io.File repoRoot   = new java.io.File(lceFolder);
        boolean      needsBuild = toDebug && !new java.io.File(
                com.noelledotjpg.BootstrapContent.SetupPaths.buildDir(repoRoot),
                "Debug/MinecraftClient.exe").exists();

        String  title  = toDebug ? "Activating Debug Build" : "Activating Release Build";
        JDialog dialog = makeDialog(parent, title);
        dialog.setContentPane(needsBuild
                ? new UpdWinDebug.BuildPanel(repoRoot, toDebug, dialog, onSuccess, onFailure)
                : new UpdWinDebug.SwapPanel(repoRoot, toDebug, dialog, onSuccess, onFailure));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    static JDialog makeDialog(Component parent, String title) {
        JDialog d = new JDialog(
                parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent),
                title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setResizable(false);
        return d;
    }

    private static JButton sized(String label) {
        JButton b = new JButton(label);
        b.setPreferredSize(new Dimension(90, 26));
        return b;
    }
}