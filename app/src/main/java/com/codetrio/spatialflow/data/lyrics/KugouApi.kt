package com.codetrio.spatialflow.data.lyrics

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface KugouApi {
    @GET
    fun search(
        @Url url: String,
        @Query("plat") plat: Int = 0,
        @Query("keyword") keyword: String,
        @Query("pagesize") pageSize: Int = 5,
        @Query("version") version: Int = 9108
    ): Call<JsonElement>

    @GET("yy/index.php")
    fun getSongData(
        @Query("r") r: String = "play/getdata",
        @Query("hash") hash: String,
        @Query("album_id") albumId: String?,
        @Query("mid") mid: String = "1"
    ): Call<JsonElement>
}

interface KugouLegacyApi {
    @GET("search")
    fun search(
        @Query("ver") ver: Int = 1,
        @Query("man") man: String = "yes",
        @Query("client") client: String = "pc",
        @Query("keyword") keyword: String,
        @Query("duration") durationMs: Long
    ): Call<JsonElement>

    @GET("download")
    fun download(
        @Query("ver") ver: Int = 1,
        @Query("client") client: String = "pc",
        @Query("id") id: String,
        @Query("accesskey") accesskey: String,
        @Query("fmt") fmt: String = "lrc",
        @Query("charset") charset: String = "utf8"
    ): Call<JsonElement>
}
