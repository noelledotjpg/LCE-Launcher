package com.noelledotjpg.Data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class PlaytimeTracker {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, String> playtimeMap;
    private Instant startTime;
    private String  currentProfile;

    public PlaytimeTracker() {
        loadPlaytime();
    }

    public void registerProfile(String profile) {
        playtimeMap.putIfAbsent(profile, "0h 0m");
        savePlaytime();
    }

    public void unregisterProfile(String profile) {
        playtimeMap.remove(profile);
        savePlaytime();
    }

    public void renameProfile(String oldName, String newName) {
        if (playtimeMap.containsKey(oldName)) {
            playtimeMap.put(newName, playtimeMap.remove(oldName));
            savePlaytime();
        }
    }

    public void syncProfiles(Iterable<String> profiles) {
        Map<String, String> newMap = new HashMap<>();
        for (String profile : profiles)
            newMap.put(profile, playtimeMap.getOrDefault(profile, "0h 0m"));
        playtimeMap = newMap;
        savePlaytime();
    }

    public void startSession(String profile) {
        currentProfile = profile;
        startTime      = Instant.now();
    }

    public void endSession() {
        if (currentProfile == null || startTime == null) return;

        Duration elapsed = Duration.between(startTime, Instant.now());
        long hours   = elapsed.toHours();
        long minutes = elapsed.toMinutes() % 60;

        String previous   = playtimeMap.getOrDefault(currentProfile, "0h 0m");
        String accumulated = accumulateTime(previous, hours + "h " + minutes + "m");

        playtimeMap.put(currentProfile, accumulated);
        savePlaytime();

        startTime      = null;
        currentProfile = null;
    }

    public String getPlaytime(String profile) {
        return playtimeMap.getOrDefault(profile, "0h 0m");
    }

    private void loadPlaytime() {
        try {
            if (Files.exists(AppPaths.PLAYTIME_JSON)) {
                try (Reader reader = Files.newBufferedReader(AppPaths.PLAYTIME_JSON)) {
                    playtimeMap = gson.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                    if (playtimeMap == null) playtimeMap = new HashMap<>();
                }
            } else {
                playtimeMap = new HashMap<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            playtimeMap = new HashMap<>();
        }
    }

    private void savePlaytime() {
        try {
            Files.createDirectories(AppPaths.PLAYTIME_JSON.getParent());
            try (Writer writer = Files.newBufferedWriter(AppPaths.PLAYTIME_JSON)) {
                gson.toJson(playtimeMap, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String accumulateTime(String previous, String added) {
        int prevH = Integer.parseInt(previous.split("h")[0].trim());
        int prevM = Integer.parseInt(previous.split("h")[1].replace("m", "").trim());
        int addH  = Integer.parseInt(added.split("h")[0].trim());
        int addM  = Integer.parseInt(added.split("h")[1].replace("m", "").trim());

        int totalMinutes = prevM + addM;
        int totalHours   = prevH + addH + totalMinutes / 60;
        totalMinutes     = totalMinutes % 60;

        return totalHours + "h " + totalMinutes + "m";
    }
}
