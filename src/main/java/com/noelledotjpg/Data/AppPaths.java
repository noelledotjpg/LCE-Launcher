package com.noelledotjpg.Data;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    private AppPaths() {}

    private static final Path BASE = Paths.get(
            System.getenv("LOCALAPPDATA"), "LCELauncher", "data"
    );

    public static final Path VARS_JSON        = BASE.resolve("launcher/vars.json");
    public static final Path PREFERENCES_JSON = BASE.resolve("launcher/preferences.json");
    public static final Path PROFILES_JSON    = BASE.resolve("usernames.json");
    public static final Path PLAYTIME_JSON    = BASE.resolve("playtime.json");
}