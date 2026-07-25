package com.codetrio.spatialflow.update

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient(private val owner: String, private val repo: String) {

    companion object {
        private const val TAG = "GitHubReleaseClient"
    }

    val latestRelease: ReleaseInfo?
        get() = fetchLatestRelease()

    fun fetchLatestRelease(): ReleaseInfo? {
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SpatialFlow-UpdateChecker")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String? = null
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val tagName = json.getString("tag_name")
                val body = json.optString("body", "")
                val assets = json.getJSONArray("assets")

                val assetNames = mutableMapOf<String, String>()
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    assetNames[asset.getString("name")] = asset.getString("browser_download_url")
                }

                var apkUrl: String? = null
                val supportedAbis = android.os.Build.SUPPORTED_ABIS
                
                for (abi in supportedAbis) {
                    val targetName = "app-$abi-release.apk"
                    if (assetNames.containsKey(targetName)) {
                        apkUrl = assetNames[targetName]
                        break
                    }
                }
                
                if (apkUrl == null) {
                    apkUrl = assetNames["app-universal-release.apk"] ?: assetNames["app-release.apk"]
                }
                
                val checksumUrl = assetNames["checksum.txt"]

                if (apkUrl != null) {
                    return ReleaseInfo(tagName, body, apkUrl, checksumUrl)
                }
            } else {
                Log.e(TAG, "GitHub API returned code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch latest release", e)
        }
        return null
    }

    fun getAllReleases(): List<ReleaseInfo> {
        val releases = ArrayList<ReleaseInfo>()
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SpatialFlow-UpdateChecker")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String? = null
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonArray = JSONArray(response.toString())
                for (j in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(j)
                    val tagName = json.getString("tag_name")
                    val body = json.optString("body", "")
                    val assets = json.optJSONArray("assets")

                    var apkUrl: String? = null
                    var checksumUrl: String? = null

                    if (assets != null) {
                        val assetNames = mutableMapOf<String, String>()
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            assetNames[asset.getString("name")] = asset.getString("browser_download_url")
                        }

                        val supportedAbis = android.os.Build.SUPPORTED_ABIS
                        for (abi in supportedAbis) {
                            val targetName = "app-$abi-release.apk"
                            if (assetNames.containsKey(targetName)) {
                                apkUrl = assetNames[targetName]
                                break
                            }
                        }
                        
                        if (apkUrl == null) {
                            apkUrl = assetNames["app-universal-release.apk"] ?: assetNames["app-release.apk"]
                        }
                        
                        checksumUrl = assetNames["checksum.txt"]
                    }
                    releases.add(ReleaseInfo(tagName, body, apkUrl, checksumUrl))
                }
            } else {
                Log.e(TAG, "GitHub API returned code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch all releases", e)
        }
        return releases
    }

    class ReleaseInfo(
        @JvmField val tagName: String,
        @JvmField val changelog: String?,
        @JvmField val apkUrl: String?,
        @JvmField val checksumUrl: String?
    )
}
