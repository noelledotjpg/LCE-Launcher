package com.noelledotjpg.Data;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class LauncherUpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/noelledotjpg/LCE-Launcher/releases/latest";

    public record LauncherReleaseInfo(
            String tagName,
            String version,
            String msiDownloadUrl
    ) {}

    public static LauncherReleaseInfo fetchIfNewer() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new Exception("GitHub API returned " + resp.statusCode());

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();

        String tagName = json.get("tag_name").getAsString();
        String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;

        if (!isNewer(version, VarsData.VERSION)) return null;

        JsonArray assets = json.getAsJsonArray("assets");
        String msiUrl = null;
        for (JsonElement el : assets) {
            JsonObject asset = el.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.endsWith(".msi")) {
                msiUrl = asset.get("browser_download_url").getAsString();
                break;
            }
        }

        if (msiUrl == null)
            throw new Exception("No .msi asset found in release " + tagName);

        return new LauncherReleaseInfo(tagName, version, msiUrl);
    }

    private static boolean isNewer(String remoteVersion, String localVersion) {
        int[] remote = parseSemver(remoteVersion);
        int[] local  = parseSemver(localVersion);
        for (int i = 0; i < Math.max(remote.length, local.length); i++) {
            int r = i < remote.length ? remote[i] : 0;
            int l = i < local.length  ? local[i]  : 0;
            if (r > l) return true;
            if (r < l) return false;
        }
        return false;
    }

    private static int[] parseSemver(String version) {
        String[] parts = version.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { nums[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException ignored) { nums[i] = 0; }
        }
        return nums;
    }
}