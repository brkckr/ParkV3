package com.brkckr.parkv3.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object IsparkApiFactory {

    /**
     * The body is decoded into a JsonElement and interpreted field by field later. Strict
     * parsing is deliberate: lenient mode would accept an HTML error page as a JSON string.
     */
    val json: Json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String, client: OkHttpClient): IsparkApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(IsparkApi::class.java)
}
