package com.codetrio.spatialflow.update;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubReleaseClient {

    private static final String TAG = "GitHubReleaseClient";
    private final String owner;
    private final String repo;

    public GitHubReleaseClient(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    public ReleaseInfo getLatestRelease() {
        try {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SpatialFlow-UpdateChecker");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                String tagName = json.getString("tag_name");
                String body = json.optString("body", "");
                JSONArray assets = json.getJSONArray("assets");

                String apkUrl = null;
                String checksumUrl = null;

                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.getString("name");
                    if (name.equals("app-release.apk")) {
                        apkUrl = asset.getString("browser_download_url");
                    } else if (name.equals("checksum.txt")) {
                        checksumUrl = asset.getString("browser_download_url");
                    }
                }

                if (apkUrl != null) {
                    return new ReleaseInfo(tagName, body, apkUrl, checksumUrl);
                }
            } else {
                Log.e(TAG, "GitHub API returned code: " + responseCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch latest release", e);
        }
        return null;
    }

    public static class ReleaseInfo {
        public final String tagName;
        public final String changelog;
        public final String apkUrl;
        public final String checksumUrl;

        public ReleaseInfo(String tagName, String changelog, String apkUrl, String checksumUrl) {
            this.tagName = tagName;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
        }
    }
}
