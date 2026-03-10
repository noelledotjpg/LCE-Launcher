package com.noelledotjpg.BootstrapContent;

import java.io.File;

public class SetupPaths {

    public static final String REPO_URL = "https://github.com/smartcmd/MinecraftConsoles";

    public static File defaultRepoDir() {
        return new File(System.getenv("LOCALAPPDATA"), "LCELauncher/repo");
    }

    public static File buildDir(File repoRoot)    { return new File(repoRoot, "build"); }
    public static File releaseDir(File repoRoot)  { return new File(buildDir(repoRoot), "Release"); }
    public static File releaseExe(File repoRoot)  { return new File(releaseDir(repoRoot), "MinecraftClient.exe"); }
    public static File usernameTxt(File repoRoot) { return new File(repoRoot, "username.txt"); }
    public static File serversTxt(File repoRoot)  { return new File(repoRoot, "servers.txt"); }

    public static final String VARS_JSON      = "src/main/resources/data/launcher/vars.json";
    public static final String USERNAMES_JSON = "src/main/resources/data/usernames.json";
}