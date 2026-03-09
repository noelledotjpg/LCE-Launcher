package com.noelledotjpg.BootstrapContent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class LCESetup {

    private final String username;
    private final File lceFolder;
    private final File vsFolder;
    private final File cmakeFolder;

    private final boolean testMode;

    private Consumer<String> outputConsumer;
    private Consumer<Integer> progressConsumer;
    private Process currentProcess;

    public LCESetup(String username, String lcePath, String vsPath, String cmakePath, Boolean testMode) {
        this.username = username;
        this.lceFolder = new File(lcePath);
        this.vsFolder = new File(vsPath);
        this.cmakeFolder = new File(cmakePath);
        this.testMode = testMode;
    }

    public void setOutputConsumer(Consumer<String> consumer) { this.outputConsumer = consumer; }
    public void setProgressConsumer(Consumer<Integer> consumer) { this.progressConsumer = consumer; }

    private void log(String msg) {
        if (outputConsumer != null) outputConsumer.accept(msg + "\n");
        else System.out.println(msg);
    }

    private void reportProgress(int percent) {
        if (progressConsumer != null) progressConsumer.accept(percent);
    }

    public void stopProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            log("Process was stopped by user.");
        }
    }

    public void runSetup() throws Exception {
        saveUsernameTxt();
        reportProgress(5);

        saveUsernameJson();
        saveVarsJson();
        reportProgress(10);

        ensureServersFile();
        reportProgress(15);

        if (!testMode) {
            runCMakeConfigure();
            reportProgress(40);

            // buildDebug(); // DEBUG BUILD SKIPPED
            // reportProgress(60);

            buildRelease(); // <-- wait for this to finish fully
            reportProgress(80);

            moveRuntimeArtifacts(); // Move AFTER buildRelease completes
            reportProgress(90);
        } else {
            log("Test mode: Skipping build steps.");
            reportProgress(80); // jump to near completion
        }

        createShortcuts();
        reportProgress(100);

        log("Setup complete.");
    }

    public void saveUsernameTxt() throws IOException {
        File usernameFile = new File(lceFolder, "username.txt");
        try (FileWriter writer = new FileWriter(usernameFile)) { writer.write(username); }
        log("Saved username.txt");
    }

    public void ensureServersFile() throws IOException {
        File servers = new File(lceFolder, "servers.txt");
        if (!servers.exists()) {
            try (FileWriter writer = new FileWriter(servers)) {
                writer.write("127.0.0.1\n25565\nLocalhost Server\n");
            }
            log("Created servers.txt");
        }
    }

    public void saveUsernameJson() throws IOException {
        File jsonFile = new File("src/main/resources/data/usernames.json");
        jsonFile.getParentFile().mkdirs();
        String jsonContent = "{\n  \"lastUsed\": \"" + username + "\",\n  \"usernames\": [\"" + username + "\"]\n}";
        try (FileWriter writer = new FileWriter(jsonFile)) { writer.write(jsonContent); }
        log("Saved usernames.json");
    }

    private void saveVarsJson() throws IOException {
        File varsFile = new File("src/main/resources/data/launcher/vars.json");
        varsFile.getParentFile().mkdirs();

        VarsData varsData = new VarsData();
        varsData.setSetupDone(true);
        varsData.setLceFolder(lceFolder.getAbsolutePath());
        varsData.setMinecraftExe(new File(lceFolder, "build/Release/MinecraftClient.exe").getAbsolutePath());

        try (FileWriter writer = new FileWriter(varsFile)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(varsData, writer);
        }

        log("Saved vars.json");
    }


    public void runCMakeConfigure() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "cmake", "-S", ".", "-B", "build",
                "-G", "Visual Studio 17 2022", "-A", "x64",
                "-DCMAKE_GENERATOR_INSTANCE=" + vsFolder.getAbsolutePath()
        );
        builder.directory(lceFolder);
        runProcess(builder, "CMake configure");
    }

    public void buildDebug() throws Exception {
        // ProcessBuilder builder = new ProcessBuilder("cmake", "--build", "build", "--config", "Debug", "--target", "MinecraftClient");
        // builder.directory(lceFolder);
        // runProcess(builder, "Build Debug");
        // log("DEBUG BUILD SKIPPED");
    }

    public void buildRelease() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "cmake", "--build", "build", "--config", "Release", "--target", "MinecraftClient"
        );
        builder.directory(lceFolder);
        runProcess(builder, "Build Release");
    }

    void moveRuntimeArtifacts() throws IOException {

        File buildDir = new File(lceFolder, "build");
        File releaseDir = new File(buildDir, "Release");

        if (!buildDir.exists())
            throw new IOException("Build folder missing");

        if (!releaseDir.exists())
            throw new IOException("Release folder missing");

        File[] items = buildDir.listFiles();
        if (items != null) {
            for (File item : items) {
                String name = item.getName();

                // Skip the Release folder itself
                if (item.equals(releaseDir)) continue;

                // Skip Visual Studio / CMake build system files and folders
                if (name.equals("CMakeFiles")
                        || name.equals("Debug")
                        || name.endsWith(".sln")
                        || name.endsWith(".vcxproj")
                        || name.endsWith(".vcxproj.filters")
                        || name.endsWith(".vcxproj.user")
                        || name.endsWith(".tlog")
                        || name.endsWith(".obj")
                        || name.endsWith(".lastbuildstate")
                        || name.endsWith(".dir")) {
                    continue;
                }

                Path source = item.toPath();
                Path dest = releaseDir.toPath().resolve(name);

                try {
                    Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                    log("Moved runtime: " + name);
                } catch (IOException e) {
                    throw new IOException("Failed moving " + name, e);
                }
            }
        }

        // lazy
        File usernameTxt = new File(lceFolder, "username.txt");
        File serversTxt = new File(lceFolder, "servers.txt");

        if (usernameTxt.exists()) {
            Files.copy(usernameTxt.toPath(), releaseDir.toPath().resolve(usernameTxt.getName()),
                    StandardCopyOption.REPLACE_EXISTING);
            log("Copied username.txt to Release folder");
        }

        if (serversTxt.exists()) {
            Files.copy(serversTxt.toPath(), releaseDir.toPath().resolve(serversTxt.getName()),
                    StandardCopyOption.REPLACE_EXISTING);
            log("Copied servers.txt to Release folder");
        }
    }

    void createShortcuts() throws IOException, InterruptedException {
        File buildDir = new File(lceFolder, "build");
        File releaseExe = new File(buildDir, "Release/MinecraftClient.exe");
        // File debugExe = new File(buildDir, "Debug/MinecraftClient.exe"); // DEBUG EXE SKIPPED

        if (!releaseExe.exists()) throw new IOException("MinecraftClient.exe not found in Release");

        File releaseShortcut = new File(lceFolder, "Minecraft LCE.lnk");
        // File debugShortcut = new File(lceFolder, "Minecraft LCE - Debug.lnk"); // DEBUG SHORTCUT SKIPPED

        createWindowsShortcut(releaseExe, releaseShortcut);
    }

    private void createWindowsShortcut(File target, File shortcut) throws IOException, InterruptedException {
        String ps = "$s=(New-Object -COM WScript.Shell).CreateShortcut('" +
                shortcut.getAbsolutePath().replace("\\", "\\\\") + "');" +
                "$s.TargetPath='" + target.getAbsolutePath().replace("\\", "\\\\") + "';" +
                "$s.WorkingDirectory='" + target.getParentFile().getAbsolutePath().replace("\\", "\\\\") + "';" +
                "$s.Save();";

        ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
        Process p = builder.start();
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("Failed to create shortcut: " + shortcut.getName());
        log("Created shortcut: " + shortcut.getName());
    }

    private void runProcess(ProcessBuilder builder, String stepName) throws Exception {
        log("Starting " + stepName + "...");

        builder.redirectErrorStream(true);
        currentProcess = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(currentProcess.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
            }
        }

        int exit = currentProcess.waitFor();

        // extra safety: ensure process really ended
        currentProcess.onExit().join();

        currentProcess = null;

        if (exit != 0)
            throw new RuntimeException(stepName + " failed with exit code: " + exit);

        log(stepName + " completed successfully.");

        // give MSBuild time to release file locks
        Thread.sleep(1500);
    }

}