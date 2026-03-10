package com.noelledotjpg.BootstrapContent;

import com.google.gson.GsonBuilder;
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
        ensureServersFile();
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
            moveArtifacts();
            progress.accept(90);
        }

        createShortcut();
        progress.accept(100);
        logger.accept("Setup complete.");
    }

    private void ensureRepo() throws Exception {
        if (repoRoot.exists()) {
            logger.accept("Repo folder already exists, skipping clone.");
            progress.accept(25);
            return;
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
                    // cmake configure mapped to 35–50%
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
                // build mapped to 50–88%
                progress.accept(50 + (val * 38 / 100));
            }
        });
    }

    private void moveArtifacts() throws IOException {
        File buildDir   = SetupPaths.buildDir(repoRoot);
        File releaseDir = SetupPaths.releaseDir(repoRoot);

        if (!releaseDir.exists()) throw new IOException("Release folder missing after build.");

        File[] items = buildDir.listFiles();
        if (items != null) {
            for (File item : items) {
                if (item.equals(releaseDir) || isBuildSystemFile(item.getName())) continue;
                Files.move(item.toPath(), releaseDir.toPath().resolve(item.getName()),
                        StandardCopyOption.REPLACE_EXISTING);
                logger.accept("Moved: " + item.getName());
            }
        }

        copyToRelease(SetupPaths.usernameTxt(repoRoot), releaseDir);
        copyToRelease(SetupPaths.serversTxt(repoRoot),  releaseDir);
    }

    private void copyToRelease(File file, File releaseDir) throws IOException {
        if (file.exists()) {
            Files.copy(file.toPath(), releaseDir.toPath().resolve(file.getName()),
                    StandardCopyOption.REPLACE_EXISTING);
            logger.accept("Copied: " + file.getName());
        }
    }

    private boolean isBuildSystemFile(String name) {
        return name.equals("CMakeFiles") || name.equals("Debug")
                || name.endsWith(".sln")             || name.endsWith(".vcxproj")
                || name.endsWith(".vcxproj.filters") || name.endsWith(".vcxproj.user")
                || name.endsWith(".tlog")            || name.endsWith(".obj")
                || name.endsWith(".lastbuildstate")  || name.endsWith(".dir");
    }

    private void createShortcut() throws IOException, InterruptedException {
        File exe      = SetupPaths.releaseExe(repoRoot);
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

    private void ensureServersFile() throws IOException {
        File f = SetupPaths.serversTxt(repoRoot);
        f.getParentFile().mkdirs();
        if (!f.exists()) {
            try (FileWriter w = new FileWriter(f)) { w.write("127.0.0.1\n25565\nLocalhost Server\n"); }
            logger.accept("Created servers.txt");
        }
    }

    private void writeUsernameJson() throws IOException {
        File f = new File(SetupPaths.USERNAMES_JSON);
        f.getParentFile().mkdirs();
        String json = "{\n  \"lastUsed\": \"" + username + "\",\n  \"usernames\": [\"" + username + "\"]\n}";
        try (FileWriter w = new FileWriter(f)) { w.write(json); }
        logger.accept("Saved usernames.json");
    }

    private void writeVarsJson() throws IOException {
        File f = new File(SetupPaths.VARS_JSON);
        f.getParentFile().mkdirs();
        VarsData vars = new VarsData();
        vars.setSetupDone(false);
        vars.setLceFolder(repoRoot.getAbsolutePath());
        vars.setMinecraftExe(SetupPaths.releaseExe(repoRoot).getAbsolutePath());
        try (FileWriter w = new FileWriter(f)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(vars, w);
        }
        logger.accept("Saved vars.json");
    }
}