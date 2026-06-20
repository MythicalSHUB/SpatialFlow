package com.codetrio.spatialflow.data.lyrics

import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface PaxsenixApi {
    @GET("spotify/search")
    fun searchSpotify(@Query("q") query: String): Call<JsonElement>

    @GET("spotify/lyrics")
    fun getSpotifyLyrics(@Query("id") id: String): Call<ResponseBody>

    @GET("musixmatch/lyrics")
    fun getMusixmatchLyrics(
        @Query("q") query: String,
        @Query("t") trackTitle: String? = null,
        @Query("a") artistName: String? = null,
        @Query("duration") duration: String? = null,
        @Query("type") type: String = "word"
    ): Call<ResponseBody>

    @GET("youtube/search")
    fun searchYouTube(@Query("q") query: String): Call<JsonElement>

    @GET("youtube/lyrics")
    fun getYouTubeLyrics(@Query("id") id: String): Call<ResponseBody>
    
    // Apple Music AMP
    @GET("https://amp-api.music.apple.com/v1/catalog/{storefront}/search")
    fun searchAppleMusic(
        @retrofit2.http.Path("storefront") storefront: String,
        @retrofit2.http.Header("Authorization") auth: String,
        @retrofit2.http.Header("Origin") origin: String,
        @retrofit2.http.Header("Referer") referer: String,
        @retrofit2.http.Header("User-Agent") userAgent: String,
        @Query("term") term: String,
        @Query("types") types: String = "songs",
        @Query("limit") limit: Int = 10
    ): Call<JsonElement>

    @GET("apple-music/lyrics")
    fun getAppleMusicLyrics(
        @Query("id") id: String,
        @Query("ttml") ttml: Boolean = false
    ): Call<ResponseBody>
}
