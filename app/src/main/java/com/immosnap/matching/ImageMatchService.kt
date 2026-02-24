package com.immosnap.matching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.immosnap.BuildConfig
import com.immosnap.search.AddressInfo
import com.immosnap.search.ListingCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ImageMatchService {

    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun rankCandidates(
        photo: Bitmap,
        candidates: List<ListingCandidate>,
        address: AddressInfo? = null
    ): List<MatchResult> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Step 1: Try to scrape images from each listing page
        val enriched = candidates.map { candidate ->
            try {
                val images = scrapeListingImages(candidate.url)
                candidate.copy(imageUrls = images, thumbnailUrl = images.firstOrNull())
            } catch (_: Exception) {
                candidate
            }
        }

        // Step 2: Download available images
        val candidateImages = enriched.map { candidate ->
            val bitmaps = candidate.imageUrls.take(5).mapNotNull { url ->
                try { downloadImage(url) } catch (_: Exception) { null }
            }
            candidate to bitmaps
        }

        val hasAnyImages = candidateImages.any { it.second.isNotEmpty() }

        // Step 3: Use Gemini to rank candidates using both images AND text/address matching
        try {
            val response = model.generateContent(
                content {
                    text("USER'S PHOTO of a for-sale sign (may also show part of the building):")
                    image(photo)

                    if (address != null) {
                        text("KNOWN LOCATION: ${address.street ?: "unknown street"}, ${address.postalCode ?: ""} ${address.city ?: ""}")
                    }

                    text("---\nCANDIDATE LISTINGS:")
                    candidateImages.forEachIndexed { idx, (candidate, bitmaps) ->
                        text("LISTING ${idx + 1}: ${candidate.title}")
                        text("URL: ${candidate.url}")
                        if (candidate.snippet.isNotBlank()) text("Details: ${candidate.snippet}")
                        if (bitmaps.isNotEmpty()) {
                            bitmaps.forEachIndexed { imgIdx, bitmap ->
                                text("Photo ${imgIdx + 1}/${bitmaps.size}:")
                                image(bitmap)
                            }
                        } else {
                            text("[listing photos could not be loaded]")
                        }
                        text("---")
                    }

                    text(
                        """Rank these listings by how likely they are the SAME PROPERTY as the user's photo location.
                        |
                        |Use ALL available signals:
                        |1. ADDRESS MATCH: Does the listing address match the known GPS location? Street name match is very strong.
                        |2. IMAGE MATCH: If listing photos are available, does the building match the user's photo?
                        |3. LISTING DETAILS: Do price, size, type match what's visible?
                        |
                        |An exact street address match should give HIGH confidence (0.85+) even without photos.
                        |A matching building photo should give VERY HIGH confidence (0.9+).
                        |
                        |Respond ONLY with a JSON array (no markdown):
                        |[{"index": 1, "confidence": 0.95, "best_image": 2, "reasoning": "Address matches exactly: Schuurkouter 31"}]
                        |index = 1-based listing number. best_image = best matching photo (1-based, 0 if no photos).
                        |Order by confidence descending. Use 0.0-1.0 scale.
                        """.trimMargin()
                    )
                }
            )

            val text = response.text ?: return@withContext enriched.map {
                MatchResult(it, 0.5f, "Gemini returned no response")
            }

            val jsonText = text.replace("```json", "").replace("```", "").trim()
                .let { s -> s.substringAfter("[").substringBefore("]").let { "[$it]" } }
            val results = Json.parseToJsonElement(jsonText).jsonArray

            val scoreMap = mutableMapOf<Int, Triple<Float, Int, String>>()
            results.forEach { elem ->
                val obj = elem.jsonObject
                val idx = obj["index"]?.jsonPrimitive?.int?.minus(1) ?: return@forEach
                val conf = obj["confidence"]?.jsonPrimitive?.float ?: 0f
                val bestImg = obj["best_image"]?.jsonPrimitive?.int ?: 0
                val reason = obj["reasoning"]?.jsonPrimitive?.content ?: ""
                scoreMap[idx] = Triple(conf, bestImg, reason)
            }

            enriched.mapIndexed { index, candidate ->
                val (conf, bestImg, reason) = scoreMap[index] ?: Triple(0.1f, 0, "Not evaluated")
                val reordered = if (bestImg > 1 && bestImg <= candidate.imageUrls.size) {
                    val best = candidate.imageUrls[bestImg - 1]
                    listOf(best) + candidate.imageUrls.filterIndexed { i, _ -> i != bestImg - 1 }
                } else candidate.imageUrls
                MatchResult(
                    candidate.copy(imageUrls = reordered, thumbnailUrl = reordered.firstOrNull()),
                    conf, reason
                )
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            enriched.map {
                MatchResult(it, 0.5f, "Match error: ${e.message?.take(100)}")
            }
        }
    }

    private fun scrapeListingImages(url: String): List<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0")
            .build()
        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: return emptyList()
        response.close()

        if (html.contains("captcha-delivery") || html.contains("Please enable JS")) {
            return emptyList()
        }

        val images = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addImage(imgUrl: String) {
            val resolved = when {
                imgUrl.startsWith("//") -> "https:$imgUrl"
                imgUrl.startsWith("/") -> {
                    Regex("https?://[^/]+").find(url)?.value?.let { "$it$imgUrl" } ?: return
                }
                else -> imgUrl
            }
            if (resolved !in seen && !resolved.contains("logo") && !resolved.contains("icon")
                && !resolved.contains("sprite") && !resolved.contains("avatar")
                && !resolved.contains("favicon") && !resolved.contains("_round")) {
                seen.add(resolved)
                images.add(resolved)
            }
        }

        Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { addImage(it) }
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { addImage(it) }

        Regex(""""(https?://[^"]+\.(?:jpg|jpeg|png|webp))""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { match ->
                val src = match.groupValues[1]
                if (src.contains("photo") || src.contains("image") || src.contains("media")
                    || src.contains("property") || src.contains("classified") || src.contains("upload")
                    || src.contains("/pic")) {
                    addImage(src)
                }
            }

        return images.take(8)
    }

    private fun downloadImage(url: String): Bitmap {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val bytes = response.body!!.bytes()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
