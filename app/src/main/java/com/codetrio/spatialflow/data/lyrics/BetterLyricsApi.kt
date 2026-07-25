package com.codetrio.spatialflow.data.lyrics

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface BetterLyricsApi {
    @GET("getLyrics")
    fun getLyrics(
        @Query("a") artist: String,
        @Query("s") song: String,
        @Query("al") album: String? = null,
        @Query("d") duration: Int? = null
    ): Call<BetterLyricsResponse>

    @GET("kugou/getLyrics")
    fun getKugouLyrics(
        @Query("a") artist: String,
        @Query("s") song: String,
        @Query("al") album: String? = null,
        @Query("d") duration: Int? = null
    ): Call<BetterLyricsResponse>
}

data class BetterLyricsResponse(
    val ttml: String?,
    val lyrics: String?
)
