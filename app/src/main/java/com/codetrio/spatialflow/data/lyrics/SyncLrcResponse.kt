package com.codetrio.spatialflow.data.lyrics

import com.google.gson.annotations.SerializedName

data class SyncLrcResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("track") val track: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album") val album: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("instrumental") val instrumental: Boolean?,
    @SerializedName("karaoke") val karaoke: String?,
    @SerializedName("synced") val synced: String?,
    @SerializedName("plain") val plain: String?
)
