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
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val quickClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun detectSource(url: String, agencyDomain: String?): String = when {
        agencyDomain != null && agencyDomain.substringBefore(".") in url -> "agency"
        "immoweb" in url -> "immoweb"
        "zimmo" in url -> "zimmo"
        "immovlan" in url -> "immovlan"
        "immolot" in url -> "immolot"
        "spotto" in url -> "spotto"
        else -> "other"
    }

    data class SearchResult(
        val candidates: List<ListingCandidate>,
        val query: String,
        val rawResults: List<String>,
        val error: String? = null
    )

    suspend fun search(signInfo: SignInfo, address: AddressInfo): SearchResult =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()
            address.street?.let { queryParts.add(it) }
            address.postalCode?.let { queryParts.add(it) }
            address.city?.let { queryParts.add(it) }
            signInfo.agencyName?.let { queryParts.add(it) }
            signInfo.referenceNumber?.let { queryParts.add(it) }
            if (address.postalCode == null && address.city == null) {
                signInfo.phoneNumber?.let { queryParts.add(it.replace(" ", "")) }
            }

            val searchTerms = queryParts.joinToString(" ")
            // Derive agency website domain from name (e.g. "Immo LOT" -> "immolot.be")
            val agencyDomain = signInfo.agencyName?.let {
                it.replace(" ", "").lowercase() + ".be"
            }
            val phoneHint = signInfo.phoneNumber?.let { " (phone: $it)" } ?: ""
            val agencySite = agencyDomain?.let { "\n|IMPORTANT: First search the agency's own website at $it for their current listings." } ?: ""
            val streetHint = address.street?.let { " on or near $it" } ?: ""
            val prompt = """Find property listings for sale by "${signInfo.agencyName ?: "unknown"}"$phoneHint$streetHint in ${address.city ?: "Belgium"} ${address.postalCode ?: ""}.
                |$agencySite
                |Search these sites in order of priority:
                |1. ${agencyDomain ?: "the agency's own website"} (agency's own site - HIGHEST PRIORITY)
                |2. immoweb.be
                |3. zimmo.be
                |4. immovlan.be
                |
                |I need DIRECT LISTING PAGES with specific property IDs, not search result pages.
                |Good: immolot.be/te-koop/7543047/huis-in-Dendermonde/
                |Good: immoweb.be/en/classified/house/for-sale/dendermonde/9200/12345678
                |Bad: immoweb.be/en/search/house/for-sale/dendermonde/9200
                |
                |Return ONLY a JSON array (no markdown): [{"title": "description", "url": "direct listing URL", "snippet": "address and price"}]
                |Return [] if nothing found.
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

                // Try to parse JSON URLs from Gemini's text response, validate they exist
                try {
                    val jsonText = textResponse
                        .replace("```json", "").replace("```", "").trim()
                    if (jsonText.startsWith("[")) {
                        val textResults = Json.parseToJsonElement(jsonText).jsonArray
                        textResults.forEach { elem ->
                            val obj = elem.jsonObject
                            val url = obj["url"]?.jsonPrimitive?.content ?: return@forEach
                            val title = obj["title"]?.jsonPrimitive?.content ?: ""
                            val snippet = obj["snippet"]?.jsonPrimitive?.content ?: ""
                            val source = detectSource(url, agencyDomain)
                            if (source != "other") {
                                // Validate URL actually exists (Gemini often hallucinates URLs)
                                val valid = try {
                                    val headReq = Request.Builder().url(url).head().build()
                                    val headResp = quickClient.newCall(headReq).execute()
                                    val code = headResp.code
                                    headResp.close()
                                    code in 200..399
                                } catch (_: Exception) { false }
                                if (valid) {
                                    candidates.add(ListingCandidate(title = title, url = url, snippet = snippet, source = source))
                                    rawResults.add("Text JSON (verified): $title | $url")
                                } else {
                                    rawResults.add("Text JSON (INVALID): $title | $url")
                                }
                            }
                        }
                    }
                } catch (_: Exception) { /* text wasn't valid JSON, that's ok */ }

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
                        val redirectResp = quickClient.newCall(redirectReq).execute()
                        val resolved = redirectResp.request.url.toString()
                        redirectResp.close()
                        resolved
                    } catch (_: Exception) {
                        redirectUri
                    }

                    rawResults.add("$title | $realUrl")

                    val source = detectSource(realUrl, agencyDomain)

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
