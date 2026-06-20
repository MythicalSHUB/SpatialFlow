import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.Locale

val videoId = "YALvuUpY_b0" // Apna Bana Le

val json = """
{
  "context": {
    "client": {
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20260531.05.00",
      "hl": "en",
      "gl": "US"
    }
  },
  "videoId": "$videoId"
}
"""

val url = URL("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
val connection = url.openConnection() as HttpURLConnection
connection.requestMethod = "POST"
connection.doOutput = true
connection.setRequestProperty("Content-Type", "application/json")
connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
connection.setRequestProperty("X-YouTube-Client-Name", "67")
connection.setRequestProperty("X-YouTube-Client-Version", "1.20260531.05.00")

connection.outputStream.use { os ->
    os.write(json.toByteArray(Charsets.UTF_8))
}

val responseCode = connection.responseCode
println("Response Code: $responseCode")

if (responseCode == 200) {
    val reader = BufferedReader(InputStreamReader(connection.inputStream))
    val responseStr = reader.readText()
    
    if (responseStr.contains("loudnessDb")) {
        println("FOUND LOUDNESS DB")
        val match = Regex("\"loudnessDb\":\\s*([-\\d.]+)").find(responseStr)
        println("Loudness: ${match?.groupValues?.get(1)}")
    } else {
        println("LOUDNESS DB NOT FOUND IN WEB_REMIX")
    }
} else {
    println(connection.errorStream?.reader()?.readText())
}
