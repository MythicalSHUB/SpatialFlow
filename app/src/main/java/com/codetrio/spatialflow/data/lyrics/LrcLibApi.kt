package com.codetrio.spatialflow.data.lyrics

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("/api/get")
    fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String?,
        @Query("duration") duration: Float? // in seconds
    ): Call<LrcLibResponse>

    @GET("/api/search")
    fun searchLyrics(
        @Query("q") query: String
    ): Call<List<LrcLibResponse>>
}
