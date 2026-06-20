package com.codetrio.spatialflow.data.innertube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based Downloader for NewPipe Extractor.
 * Handles all HTTP communication for local YouTube extraction.
 * No proxies, no external dependencies — just direct requests.
 */
class NewPipeDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, dataToSend?.toRequestBody())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")

        // Add all headers from NewPipe (override as needed)
        headers.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        // Collect response headers
        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name)
        }

        val responseBody = response.body.string()
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            latestUrl
        )
    }

    companion object {
        private var instance: NewPipeDownloader? = null

        @JvmStatic
        fun getInstance(): NewPipeDownloader {
            if (instance == null) {
                instance = NewPipeDownloader()
            }
            return instance!!
        }
    }
}
