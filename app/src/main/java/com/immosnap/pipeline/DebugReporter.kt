package com.immosnap.pipeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object DebugReporter {

    // Change this to your dev machine's IP
    private const val DEBUG_SERVER = "http://192.168.1.150:8899/debug"

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun send(info: DebugInfo) = withContext(Dispatchers.IO) {
        try {
            val json = buildJsonObject {
                put("locationSource", info.locationSource)
                put("lat", info.lat)
                put("lng", info.lng)
                put("address", info.address)
                put("agency", info.agencyName)
                put("refNumber", info.refNumber)
                put("searchQuery", info.searchQuery)
                put("searchError", info.searchError)
                put("ocrText", info.ocrText)
                put("searchResults", JsonArray(info.searchResults.map { JsonPrimitive(it) }))
            }.toString()

            val request = Request.Builder()
                .url(DEBUG_SERVER)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // Debug reporting is best-effort, never crash
        }
    }
}
