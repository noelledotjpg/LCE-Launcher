package com.noelledotjpg.MainContent;

import com.noelledotjpg.Data.AppPaths;
import com.noelledotjpg.Data.PreferencesData;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.io.Reader;
import java.nio.file.Files;

public class ThemeManager {

    public static final String SYSTEM      = "System";
    public static final String FLAT_LIGHT  = "FlatLight";
    public static final String FLAT_DARK   = "FlatDark";

    public static final String[] ALL_THEMES = {
            SYSTEM, FLAT_LIGHT, FLAT_DARK
    };

    public static PreferencesData loadPreferences() {
        try {
            if (Files.exists(AppPaths.PREFERENCES_JSON)) {
                try (Reader r = Files.newBufferedReader(AppPaths.PREFERENCES_JSON)) {
                    PreferencesData data = new GsonBuilder().setPrettyPrinting().create()
                            .fromJson(r, PreferencesData.class);
                    if (data != null) return data;
                }
            }
        } catch (Exception ignored) {}
        return new PreferencesData();
    }

    public static void apply(PreferencesData prefs) {
        try {
            if (isFlatLaf(prefs.getTheme())) {
                com.formdev.flatlaf.FlatLaf.setUseNativeWindowDecorations(
                        prefs.isUseSystemTitleBar()
                );
            }

            UIManager.setLookAndFeel(lafClass(prefs.getTheme()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void applyFromDisk() {
        apply(loadPreferences());
    }

    public static boolean isFlatLaf(String theme) {
        return FLAT_LIGHT.equals(theme) || FLAT_DARK.equals(theme);
    }

    private static String lafClass(String theme) {
        return switch (theme) {
            case FLAT_LIGHT  -> "com.formdev.flatlaf.FlatLightLaf";
            case FLAT_DARK   -> "com.formdev.flatlaf.FlatDarkLaf";
            // add more later
            default          -> UIManager.getSystemLookAndFeelClassName();
        };
    }
}