package com.codetrio.spatialflow.data.lyrics

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface SimpMusicApi {
    @GET("api/lyrics")
    fun getLyrics(
        @Query("title") title: String?,
        @Query("artist") artist: String?,
        @Query("videoId") videoId: String?,
        @Query("limit") limit: Int? = 1
    ): Call<SimpMusicApiResponse>
}

data class SimpMusicApiResponse(
    val type: String?,
    val success: Boolean?,
    val data: List<SimpMusicLyricItem>?
)

data class SimpMusicLyricItem(
    val id: String?,
    val videoId: String?,
    val songTitle: String?,
    val artistName: String?,
    val plainLyric: String?,
    val syncedLyrics: String?
)
