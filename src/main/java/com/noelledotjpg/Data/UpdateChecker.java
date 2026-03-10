package com.noelledotjpg.Data;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/smartcmd/MinecraftConsoles/releases/tags/nightly";

    public record NightlyInfo(String commitHash, String shortHash) {}

    public static NightlyInfo fetchLatest() throws Exception {
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

        String hash = json.get("target_commitish").getAsString();
        return new NightlyInfo(hash, hash.substring(0, 7));
    }
}