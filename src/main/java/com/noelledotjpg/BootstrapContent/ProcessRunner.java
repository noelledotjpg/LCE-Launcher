package com.noelledotjpg.BootstrapContent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class ProcessRunner {

    private Process current;
    private final Consumer<String> logger;

    public ProcessRunner(Consumer<String> logger) {
        this.logger = logger;
    }

    public void run(ProcessBuilder pb, String stepName, Consumer<String> onLine) throws Exception {
        logger.accept("Starting " + stepName + "...");
        pb.redirectErrorStream(true);
        current = pb.start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(current.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                logger.accept(line);
                if (onLine != null) onLine.accept(line);
            }
        }

        int exit = current.waitFor();
        current.onExit().join();
        current = null;

        if (exit != 0) throw new RuntimeException(stepName + " failed with exit code: " + exit);
        logger.accept(stepName + " completed.");
        Thread.sleep(1500);
    }

    public void run(ProcessBuilder pb, String stepName) throws Exception {
        run(pb, stepName, null);
    }

    public void stop() {
        if (current != null && current.isAlive()) {
            current.descendants().forEach(ph -> {
                ph.destroyForcibly();
                logger.accept("Killed: " + ph.info().command().orElse("unknown process"));
            });
            current.destroyForcibly();
            try { current.waitFor(); } catch (InterruptedException ignored) {}
            current = null;
            logger.accept("All processes stopped.");
        }
    }
}