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
 * Returns an [AddressInfo] whose address fields are null on any failure — network, bad
 * status, malformed JSON, missing fields — rather than throwing. The pipeline treats a
 * missing address as a weaker signal and falls back to OCR-only search, which is much
 * better than the whole snap crashing because a single field wasn't where we expected.
 *
 * When the failure is a "real" outage (HTTP error, parse error, network exception), the
 * reason is captured into [AddressInfo.error] so the result screen's debug card can show
 * the user what happened rather than silently masking a quota / auth / key misconfiguration.
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
                if (!response.isSuccessful) {
                    return@withContext errorAddress("HTTP ${response.code} from Maps Geocoding API")
                }
                response.body?.string()
                    ?: return@withContext errorAddress("Empty body from Maps Geocoding API")
            }
        } catch (e: Exception) {
            return@withContext errorAddress("Maps request failed: ${e.message ?: e.javaClass.simpleName}")
        }

        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return@withContext errorAddress("Maps response was not JSON: ${e.message ?: e.javaClass.simpleName}")
        }

        // Google Maps returns a top-level status field. Anything other than OK is a real problem
        // the user should see (REQUEST_DENIED, OVER_QUERY_LIMIT, INVALID_REQUEST, etc.).
        val status = root["status"]?.jsonPrimitive?.contentOrNull
        if (status != null && status != "OK" && status != "ZERO_RESULTS") {
            val apiMessage = root["error_message"]?.jsonPrimitive?.contentOrNull
            return@withContext errorAddress("Maps status=$status${apiMessage?.let { ": $it" } ?: ""}")
        }

        val components = root["results"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("address_components")?.jsonArray
            ?: return@withContext AddressInfo(null, null, null) // ZERO_RESULTS: not an error

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

    private fun errorAddress(message: String): AddressInfo =
        AddressInfo(null, null, null, error = message)
}
