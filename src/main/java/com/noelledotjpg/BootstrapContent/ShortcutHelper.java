package com.noelledotjpg.BootstrapContent;

import java.io.File;
import java.io.IOException;

public class ShortcutHelper {

    public static void create(File target, File shortcut) throws IOException, InterruptedException {
        String ps = "$s=(New-Object -COM WScript.Shell).CreateShortcut('"
                + esc(shortcut) + "');"
                + "$s.TargetPath='" + esc(target) + "';"
                + "$s.WorkingDirectory='" + esc(target.getParentFile()) + "';"
                + "$s.Save();";

        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
        int exit = pb.start().waitFor();
        if (exit != 0) throw new IOException("Failed to create shortcut: " + shortcut.getName());
    }

    private static String esc(File f) {
        return f.getAbsolutePath().replace("\\", "\\\\");
    }
}