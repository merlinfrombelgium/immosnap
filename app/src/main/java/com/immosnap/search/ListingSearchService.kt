package com.immosnap.search

import com.immosnap.BuildConfig
import com.immosnap.ocr.SignInfo
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ListingSearchService {

    private val client = OkHttpClient()

    suspend fun search(signInfo: SignInfo, address: AddressInfo): List<ListingCandidate> =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()

            address.street?.let { queryParts.add(it) }
            address.postalCode?.let { queryParts.add(it) }
            address.city?.let { queryParts.add(it) }

            signInfo.agencyName?.let { queryParts.add("\"$it\"") }
            signInfo.referenceNumber?.let { queryParts.add(it) }

            val siteFilter = "site:immoweb.be OR site:zimmo.be OR site:immovlan.be"
            val query = "${queryParts.joinToString(" ")} $siteFilter"

            val url = "https://www.googleapis.com/customsearch/v1" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&key=${BuildConfig.SEARCH_API_KEY}" +
                "&cx=${BuildConfig.SEARCH_ENGINE_ID}" +
                "&num=5"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val json = Json.parseToJsonElement(response.body!!.string()).jsonObject

            val items = json["items"]?.jsonArray ?: return@withContext emptyList()

            items.map { item ->
                val obj = item.jsonObject
                val link = obj["link"]!!.jsonPrimitive.content
                val source = when {
                    "immoweb.be" in link -> "immoweb"
                    "zimmo.be" in link -> "zimmo"
                    "immovlan.be" in link -> "immovlan"
                    else -> "other"
                }
                ListingCandidate(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    url = link,
                    snippet = obj["snippet"]?.jsonPrimitive?.content ?: "",
                    thumbnailUrl = obj["pagemap"]?.jsonObject
                        ?.get("cse_thumbnail")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("src")?.jsonPrimitive?.content,
                    source = source
                )
            }
        }
}
