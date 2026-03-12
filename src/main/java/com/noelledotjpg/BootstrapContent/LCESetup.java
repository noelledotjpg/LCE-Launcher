package com.noelledotjpg.BootstrapContent;

import com.google.gson.GsonBuilder;
import com.noelledotjpg.Data.AppPaths;
import com.noelledotjpg.Data.VarsData;

import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

public class LCESetup {

    private final String  username;
    private final File    repoRoot;
    private final File    vsFolder;
    private final File    cmakeFolder;
    private final boolean testMode;

    private Consumer<String>  logger   = System.out::println;
    private Consumer<Integer> progress = p -> {};
    private ProcessRunner     runner;

    private static final int ESTIMATED_CPP_FILES = 150;

    public LCESetup(String username, String repoPath, String vsPath, String cmakePath, boolean testMode) {
        this.username    = username;
        this.repoRoot    = new File(repoPath);
        this.vsFolder    = new File(vsPath);
        this.cmakeFolder = new File(cmakePath);
        this.testMode    = testMode;
    }

    public void setOutputConsumer(Consumer<String> c)    { this.logger   = c; }
    public void setProgressConsumer(Consumer<Integer> c) { this.progress = c; }

    public void stopProcess() {
        if (runner != null) runner.stop();
    }

    public void runSetup() throws Exception {
        runner = new ProcessRunner(logger);

        ensureRepo();

        writeUsernameTxt();
        progress.accept(30);

        writeUsernameJson();
        writeVarsJson();
        progress.accept(35);

        if (testMode) {
            logger.accept("Test mode: skipping build steps.");
            progress.accept(90);
        } else {
            runCMake();
            buildRelease();
            BuildArtifactManager.moveReleaseToBuild(repoRoot, logger);
            writeServersDb();
            progress.accept(90);
        }

        createShortcut();
        progress.accept(100);
        logger.accept("Setup complete.");
    }

    private void ensureRepo() throws Exception {
        File cmakeLists = new File(repoRoot, "CMakeLists.txt");

        if (repoRoot.exists() && cmakeLists.exists()) {
            logger.accept("Repo folder already exists, skipping clone.");
            progress.accept(25);
            return;
        }

        if (repoRoot.exists()) {
            logger.accept("Repo folder exists but appears incomplete, deleting and re-cloning...");
            ProcessBuilder del = new ProcessBuilder(
                    "cmd", "/c", "rmdir", "/s", "/q", repoRoot.getAbsolutePath());
            int exitCode = del.start().waitFor();
            if (exitCode != 0 || repoRoot.exists())
                throw new IOException("Failed to delete incomplete repo folder: " + repoRoot.getAbsolutePath());
            logger.accept("Deleted incomplete repo folder.");
        }

        logger.accept("Cloning repository into " + repoRoot.getAbsolutePath() + "...");
        repoRoot.getParentFile().mkdirs();
        runner.run(new ProcessBuilder("git", "clone", SetupPaths.REPO_URL,
                repoRoot.getAbsolutePath()), "git clone");
        progress.accept(25);
    }

    private void runCMake() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cmake", "-S", ".", "-B", "build",
                "-G", "Visual Studio 17 2022", "-A", "x64",
                "-DCMAKE_GENERATOR_INSTANCE=" + vsFolder.getAbsolutePath()
        );
        pb.directory(repoRoot);

        runner.run(pb, "CMake configure", line -> {
            int bracket = line.indexOf('[');
            int percent = line.indexOf('%');
            if (bracket >= 0 && percent > bracket) {
                try {
                    int val = Integer.parseInt(line.substring(bracket + 1, percent).trim());
                    progress.accept(35 + (val * 15 / 100));
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void buildRelease() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cmake", "--build", "build", "--config", "Release", "--target", "MinecraftClient"
        );
        pb.directory(repoRoot);

        int[] compiled = {0};
        runner.run(pb, "Build Release", line -> {
            if (line.matches("\\d+>.*\\.(cpp|c|cc|cxx).*")) {
                compiled[0]++;
                int val = Math.min(compiled[0] * 100 / ESTIMATED_CPP_FILES, 99);
                progress.accept(50 + (val * 38 / 100));
            }
        });
    }

    private void writeServersDb() throws IOException {
        File releaseDir = SetupPaths.buildDir(repoRoot);
        File dbFile     = new File(releaseDir, "servers.db");

        if (dbFile.exists()) {
            logger.accept("servers.db already exists, skipping.");
            return;
        }

        String ip   = "127.0.0.1";
        String name = "Localhost Server";
        int    port = 25565;

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(dbFile))) {
            out.writeBytes("MCSV");
            writeIntLE(out, 1);
            writeIntLE(out, 1);

            byte[] ipBytes   = ip.getBytes();
            byte[] nameBytes = name.getBytes();
            writeShortLE(out, ipBytes.length);
            out.write(ipBytes);
            writeShortLE(out, port);
            writeShortLE(out, nameBytes.length);
            out.write(nameBytes);
        }
        logger.accept("Created servers.db");
    }

    private void createShortcut() throws IOException, InterruptedException {
        File exe      = BuildArtifactManager.activeExe(repoRoot);
        File shortcut = new File(repoRoot, "Minecraft LCE.lnk");
        if (!testMode && !exe.exists()) throw new IOException("MinecraftClient.exe not found after build.");
        if (exe.exists()) {
            ShortcutHelper.create(exe, shortcut);
            logger.accept("Created shortcut.");
        }
    }

    private void writeUsernameTxt() throws IOException {
        File f = SetupPaths.usernameTxt(repoRoot);
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) { w.write(username); }
        logger.accept("Saved username.txt");
    }

    private void writeUsernameJson() throws IOException {
        File f = AppPaths.PROFILES_JSON.toFile();
        f.getParentFile().mkdirs();
        String json = "{\n  \"lastUsed\": \"" + username + "\",\n  \"usernames\": [\"" + username + "\"]\n}";
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        logger.accept("Saved usernames.json");
    }

    private void writeVarsJson() throws IOException {
        File f = AppPaths.VARS_JSON.toFile();
        f.getParentFile().mkdirs();
        VarsData vars = new VarsData();
        vars.setSetupDone(false);
        vars.setLceFolder(repoRoot.getAbsolutePath());
        vars.setMinecraftExe(BuildArtifactManager.activeExe(repoRoot).getAbsolutePath());
        vars.setInstalledCommitHash(getRepoHeadHash());
        try (FileWriter w = new FileWriter(f)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(vars, w);
        }
        logger.accept("Saved vars.json");
    }

    private String getRepoHeadHash() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(repoRoot)
                    .start();
            String hash = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return hash;
        } catch (Exception e) {
            logger.accept("Warning: could not read repo HEAD hash: " + e.getMessage());
            return "";
        }
    }

    private static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }
}