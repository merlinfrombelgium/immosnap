package com.immosnap.search

import com.immosnap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reverse geocodes a lat/lng pair to a Belgian address using the Google Maps Geocoding API.
 *
 * Returns a blank [AddressInfo] (all nulls) on any failure — network, bad status, malformed
 * JSON, missing fields — rather than throwing. The pipeline treats a missing address as a
 * weaker signal and falls back to the OCR data, which is much better than the whole snap
 * crashing because a single field wasn't where we expected it to be in the JSON.
 */
class GeocodingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun reverseGeocode(lat: Double, lng: Double): AddressInfo = withContext(Dispatchers.IO) {
        val url = "https://maps.googleapis.com/maps/api/geocode/json" +
            "?latlng=$lat,$lng&key=${BuildConfig.MAPS_API_KEY}&language=nl"

        val body = try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext BLANK
                response.body?.string() ?: return@withContext BLANK
            }
        } catch (_: Exception) {
            return@withContext BLANK
        }

        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return@withContext BLANK
        }

        val components = root["results"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("address_components")?.jsonArray
            ?: return@withContext BLANK

        var street: String? = null
        var postalCode: String? = null
        var city: String? = null

        for (comp in components) {
            val obj = comp.jsonObject
            val types = obj["types"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
            val name = obj["long_name"]?.jsonPrimitive?.contentOrNull ?: continue
            when {
                "route" in types -> street = name
                "postal_code" in types -> postalCode = name
                "locality" in types -> city = name
            }
        }
        AddressInfo(street, postalCode, city)
    }

    private companion object {
        val BLANK = AddressInfo(null, null, null)
    }
}
