package com.noelledotjpg.Data;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    private AppPaths() {}

    private static final String BASE = "src/main/resources/data";

    public static final Path VARS_JSON        = Paths.get(BASE, "launcher/vars.json");
    public static final Path PREFERENCES_JSON = Paths.get(BASE, "launcher/preferences.json");
    public static final Path PROFILES_JSON    = Paths.get(BASE, "usernames.json");
    public static final Path PLAYTIME_JSON    = Paths.get(BASE, "playtime.json");
}
