package com.noelledotjpg.BootstrapContent;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Manages moving build artifacts between:
 *   build/          — the "active" folder (exe launched from here)
 *   build/Release/  — Release config files
 *   build/Debug/    — Debug config files
 *
 * Only MinecraftClient.exe and MinecraftWorld.lib are ever moved.
 * All other files (CMake internals, assets, servers.db, etc.) stay put.
 */
public class BuildArtifactManager {

    /** Artifacts present in both Release and Debug. */
    private static final String[] COMMON_ARTIFACTS = {
            "MinecraftClient.exe",
            "MinecraftWorld.lib"
    };

    /** Additional artifacts only present in Debug builds. */
    private static final String[] DEBUG_ONLY_ARTIFACTS = {
            "MinecraftClient.pdb",
            "MinecraftWorld.pdb"
    };

    // -------------------------------------------------------------------------
    // Called after a Release build completes — moves artifacts from Release/ to build/
    // -------------------------------------------------------------------------

    public static void moveReleaseToBuild(File repoRoot, Consumer<String> log) throws IOException {
        File buildDir   = SetupPaths.buildDir(repoRoot);
        File releaseDir = SetupPaths.releaseDir(repoRoot);

        if (!releaseDir.exists())
            throw new IOException("Release folder missing after build: " + releaseDir);

        log.accept("Moving Release artifacts to build/ ...");
        moveArtifacts(releaseDir, buildDir, COMMON_ARTIFACTS, log);
        log.accept("build/ contents:");
        logDir(buildDir, log);
    }

    // -------------------------------------------------------------------------
    // Called when toggling the debug mode checkbox
    // -------------------------------------------------------------------------

    /**
     * Swaps MinecraftClient.exe + MinecraftWorld.lib between build/ and the
     * appropriate config folder (Release/ or Debug/), using a temp file suffix
     * so nothing is ever overwritten mid-move.
     *
     * @param repoRoot  repo root folder
     * @param toDebug   true = activate Debug, false = activate Release
     * @param log       log consumer
     */
    public static void swapActiveConfig(File repoRoot, boolean toDebug, Consumer<String> log)
            throws IOException {
        File buildDir   = SetupPaths.buildDir(repoRoot);
        File releaseDir = SetupPaths.releaseDir(repoRoot);
        File debugDir   = new File(buildDir, "Debug");

        File incomingDir = toDebug ? debugDir   : releaseDir;
        File outgoingDir = toDebug ? releaseDir : debugDir;

        if (!incomingDir.exists())
            throw new IOException((toDebug ? "Debug" : "Release") + " folder not found: " + incomingDir);

        outgoingDir.mkdirs();

        log.accept("Swapping artifacts (build/ ↔ " + incomingDir.getName() + "/) ...");

        // When going to Debug, we also move .pdb files; when returning to Release, we move them back
        String[] artifactsToSwap = toDebug
                ? concat(COMMON_ARTIFACTS, DEBUG_ONLY_ARTIFACTS)
                : concat(COMMON_ARTIFACTS, DEBUG_ONLY_ARTIFACTS); // move all — pdb files just won't exist in Release

        for (String name : artifactsToSwap) {
            File active   = new File(buildDir,    name);
            File incoming = new File(incomingDir, name);
            File outgoing = new File(outgoingDir, name);
            File temp     = new File(buildDir,    name + ".swap_tmp");

            // Step 1 — move active → temp (frees the name in build/)
            if (active.exists()) {
                Files.move(active.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.accept("  Staged: " + name);
            }

            // Step 2 — move incoming → build/
            if (incoming.exists()) {
                Files.move(incoming.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.accept("  Activated: " + name + " from " + incomingDir.getName() + "/");
            } else {
                log.accept("  Warning: " + name + " not found in " + incomingDir.getName() + "/");
            }

            // Step 3 — move temp → outgoing dir
            if (temp.exists()) {
                Files.move(temp.toPath(), outgoing.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.accept("  Stored: " + name + " → " + outgoingDir.getName() + "/");
            }
        }

        log.accept("Swap complete. build/ contents:");
        logDir(buildDir, log);
    }

    // -------------------------------------------------------------------------
    // Builds the Debug config if Debug/MinecraftClient.exe is missing
    // -------------------------------------------------------------------------

    public static void ensureDebugBuild(File repoRoot, Consumer<String> log,
                                        Consumer<String> cmdLog) throws Exception {
        File debugExe = new File(SetupPaths.buildDir(repoRoot), "Debug/MinecraftClient.exe");
        if (debugExe.exists()) {
            log.accept("Debug build already exists, skipping.");
            return;
        }

        log.accept("Debug build not found. Building Debug config...");
        ProcessBuilder pb = new ProcessBuilder(
                "cmake", "--build", "build", "--config", "Debug", "--target", "MinecraftClient"
        );
        pb.directory(repoRoot);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) cmdLog.accept(line);
        }

        int exit = proc.waitFor();
        if (exit != 0) throw new IOException("Debug build failed with exit code " + exit);
        log.accept("Debug build complete.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Moves only the specified artifact files from src to dest. */
    private static void moveArtifacts(File src, File dest, String[] names, Consumer<String> log)
            throws IOException {
        dest.mkdirs();
        for (String name : names) {
            File f = new File(src, name);
            if (f.exists()) {
                Files.move(f.toPath(), dest.toPath().resolve(name),
                        StandardCopyOption.REPLACE_EXISTING);
                log.accept("  Moved: " + name);
            } else {
                log.accept("  Not found (skipping): " + name);
            }
        }
    }

    private static String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static void logDir(File dir, Consumer<String> log) {
        File[] items = dir.listFiles();
        if (items == null || items.length == 0) { log.accept("  (empty)"); return; }
        for (File f : items)
            log.accept("  " + f.getName() + (f.isDirectory() ? "/" : ""));
    }

    /** Returns the active exe path — always in build/ */
    public static File activeExe(File repoRoot) {
        return new File(SetupPaths.buildDir(repoRoot), "MinecraftClient.exe");
    }
}