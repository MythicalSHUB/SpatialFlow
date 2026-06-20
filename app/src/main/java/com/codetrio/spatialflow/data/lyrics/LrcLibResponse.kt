package com.codetrio.spatialflow.data.lyrics

import com.google.gson.annotations.SerializedName

/**
 * Data model for the response from lrclib.net.
 */
data class LrcLibResponse(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String?, // Title

    @SerializedName("artistName")
    val artistName: String?,

    @SerializedName("albumName")
    val albumName: String?,

    @SerializedName("duration")
    val duration: Float,

    @SerializedName("instrumental")
    val instrumental: Boolean,

    @SerializedName("plainLyrics")
    val plainLyrics: String?,

    @SerializedName("syncedLyrics")
    val syncedLyrics: String? // LRC format content
)
