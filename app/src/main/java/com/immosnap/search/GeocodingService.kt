package com.immosnap.search

import com.immosnap.BuildConfig
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeocodingService {

    private val client = OkHttpClient()

    suspend fun reverseGeocode(lat: Double, lng: Double): AddressInfo = withContext(Dispatchers.IO) {
        val url = "https://maps.googleapis.com/maps/api/geocode/json" +
            "?latlng=$lat,$lng&key=${BuildConfig.MAPS_API_KEY}&language=nl"

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = Json.parseToJsonElement(response.body!!.string()).jsonObject
        val components = json["results"]!!.jsonArray
            .firstOrNull()?.jsonObject
            ?.get("address_components")?.jsonArray ?: return@withContext AddressInfo(null, null, null)

        var street: String? = null
        var postalCode: String? = null
        var city: String? = null

        for (comp in components) {
            val types = comp.jsonObject["types"]!!.jsonArray.map { it.jsonPrimitive.content }
            val name = comp.jsonObject["long_name"]!!.jsonPrimitive.content
            when {
                "route" in types -> street = name
                "postal_code" in types -> postalCode = name
                "locality" in types -> city = name
            }
        }
        AddressInfo(street, postalCode, city)
    }
}
