package com.immosnap.search

import com.immosnap.BuildConfig
import com.immosnap.ocr.SignInfo
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ListingSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class SearchResult(
        val candidates: List<ListingCandidate>,
        val query: String,
        val rawResults: List<String>,
        val error: String? = null
    )

    suspend fun search(signInfo: SignInfo, address: AddressInfo): SearchResult =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()
            address.postalCode?.let { queryParts.add(it) }
            address.city?.let { queryParts.add(it) }
            signInfo.agencyName?.let { queryParts.add(it) }
            signInfo.referenceNumber?.let { queryParts.add(it) }
            if (address.postalCode == null && address.city == null) {
                signInfo.phoneNumber?.let { queryParts.add(it.replace(" ", "")) }
            }

            val searchTerms = queryParts.joinToString(" ")
            // Also use phone for search if available
            val phoneHint = signInfo.phoneNumber?.let { " (phone: $it)" } ?: ""
            val prompt = """Search for specific individual property listings for sale by the real estate agency "${signInfo.agencyName ?: "unknown"}"$phoneHint in ${address.city ?: "Belgium"} ${address.postalCode ?: ""}.
                |
                |I need SPECIFIC PROPERTY LISTING PAGES (with classified/listing IDs in the URL), NOT general search result pages.
                |For example: immoweb.be/en/classified/house/for-sale/dendermonde/9200/12345678
                |NOT: immoweb.be/en/search/house/for-sale/dendermonde/9200
                |
                |Search on immoweb.be, zimmo.be, immovlan.be, and the agency website if available.
                |
                |Return a JSON array (no markdown fences): [{"title": "property description", "url": "direct listing URL", "snippet": "address and price if known"}]
                |Return [] if no specific listings found.
            """.trimMargin()

            val requestBody = buildJsonObject {
                put("contents", JsonArray(listOf(
                    buildJsonObject {
                        put("parts", JsonArray(listOf(
                            buildJsonObject { put("text", prompt) }
                        )))
                    }
                )))
                put("tools", JsonArray(listOf(
                    buildJsonObject {
                        put("google_search", buildJsonObject {})
                    }
                )))
            }.toString()

            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body!!.string()
                val json = Json.parseToJsonElement(body).jsonObject

                val error = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                if (error != null) {
                    return@withContext SearchResult(emptyList(), searchTerms, emptyList(), "Gemini error: $error")
                }

                val candidates = mutableListOf<ListingCandidate>()
                val rawResults = mutableListOf<String>()

                // Get text response from Gemini
                val textResponse = json["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                    ?.joinToString("\n") ?: ""

                rawResults.add("Gemini: ${textResponse.take(800)}")

                // Extract grounding chunks and resolve redirect URLs
                val groundingChunks = json["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("groundingMetadata")?.jsonObject
                    ?.get("groundingChunks")?.jsonArray

                groundingChunks?.forEach { chunk ->
                    val web = chunk.jsonObject["web"]?.jsonObject ?: return@forEach
                    val title = web["title"]?.jsonPrimitive?.content ?: ""
                    val redirectUri = web["uri"]?.jsonPrimitive?.content ?: ""

                    // Resolve the redirect URL to get the real listing URL
                    val realUrl = try {
                        val redirectReq = Request.Builder().url(redirectUri).build()
                        val redirectResp = client.newCall(redirectReq).execute()
                        val resolved = redirectResp.request.url.toString()
                        redirectResp.close()
                        resolved
                    } catch (_: Exception) {
                        redirectUri
                    }

                    rawResults.add("$title | $realUrl")

                    val source = when {
                        "immoweb" in realUrl -> "immoweb"
                        "zimmo" in realUrl -> "zimmo"
                        "immovlan" in realUrl -> "immovlan"
                        "immolot" in realUrl -> "immolot"
                        "spotto" in realUrl -> "spotto"
                        else -> "other"
                    }

                    // Determine a better title from the URL or Gemini text
                    val betterTitle = when {
                        realUrl.contains("/classified/") || realUrl.contains("/te-koop/") ->
                            "$title - ${realUrl.substringAfterLast("/").replace("-", " ").take(60)}"
                        else -> title
                    }

                    if (source != "other") {
                        candidates.add(
                            ListingCandidate(
                                title = betterTitle,
                                url = realUrl,
                                snippet = "",
                                thumbnailUrl = null,
                                source = source
                            )
                        )
                    }
                }

                // Deduplicate by URL
                val uniqueCandidates = candidates.distinctBy { it.url }

                SearchResult(uniqueCandidates, searchTerms, rawResults)
            } catch (e: Exception) {
                SearchResult(emptyList(), searchTerms, emptyList(), "Search error: ${e.message}")
            }
        }
}
