package com.immosnap.pipeline

import com.immosnap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Best-effort POST of pipeline debug info to a dev server, for diagnosing failed runs
 * without attaching a phone to a laptop. The endpoint is read from BuildConfig.DEBUG_SERVER_URL,
 * which is sourced from local.properties — leave it blank to disable reporting entirely.
 * Errors here never propagate up to the pipeline: this is telemetry, not a user-visible path.
 */
object DebugReporter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun send(info: DebugInfo) = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.DEBUG_SERVER_URL
        if (endpoint.isBlank()) return@withContext // disabled — do not waste a socket attempt
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
                .url(endpoint)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // Debug reporting is best-effort, never crash
        }
    }
}
