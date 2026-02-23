package com.immosnap.matching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.immosnap.BuildConfig
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

    /**
     * For each candidate, scrape og:image from the listing page,
     * then use Gemini vision to compare with the user's photo.
     * Returns candidates with thumbnailUrl populated and real confidence scores.
     */
    suspend fun rankCandidates(
        photo: Bitmap,
        candidates: List<ListingCandidate>
    ): List<MatchResult> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Step 1: Fetch og:image for each candidate
        val enriched = candidates.map { candidate ->
            val ogImage = try {
                scrapeOgImage(candidate.url)
            } catch (_: Exception) { null }
            candidate.copy(thumbnailUrl = ogImage) to ogImage
        }

        // Step 2: Download thumbnail bitmaps
        val withImages = enriched.mapNotNull { (candidate, ogImage) ->
            if (ogImage != null) {
                try {
                    val bitmap = downloadImage(ogImage)
                    candidate to bitmap
                } catch (_: Exception) {
                    candidate to null
                }
            } else {
                candidate to null
            }
        }

        // Step 3: If we have any images, compare with Gemini
        val candidatesWithBitmaps = withImages.filter { it.second != null }
        if (candidatesWithBitmaps.isEmpty()) {
            return@withContext withImages.map { (candidate, _) ->
                MatchResult(candidate, 0.5f, "No listing photo available")
            }
        }

        try {
            val response = model.generateContent(
                content {
                    text("USER'S PHOTO of the property (taken from the street):")
                    image(photo)
                    withImages.forEachIndexed { index, (candidate, bitmap) ->
                        text("Listing ${index + 1}: ${candidate.title} (${candidate.url})")
                        if (bitmap != null) {
                            image(bitmap)
                        } else {
                            text("[no image available]")
                        }
                    }
                    text(
                        """Compare the user's street photo with each listing photo.
                        |Determine which listings show the SAME BUILDING.
                        |Compare: facade shape, roof style, windows, door, colors, materials, architectural details.
                        |Note: the user's photo may show the building from a different angle or distance.
                        |
                        |Respond ONLY with a JSON array (no markdown):
                        |[{"index": 1, "confidence": 0.95, "reasoning": "Same red brick facade, matching bay window"}]
                        |Index is 1-based. Order by confidence descending.
                        |Use 0.0-1.0 scale. Be strict: only high confidence if clearly the same building.
                        """.trimMargin()
                    )
                }
            )

            val text = response.text ?: return@withContext withImages.map { (c, _) ->
                MatchResult(c, 0.5f, "Gemini returned no response")
            }

            val jsonText = text.replace("```json", "").replace("```", "").trim()
                .let { s -> s.substringAfter("[").substringBefore("]").let { "[$it]" } }
            val results = Json.parseToJsonElement(jsonText).jsonArray

            // Build a map of index -> (confidence, reasoning)
            val scoreMap = mutableMapOf<Int, Pair<Float, String>>()
            results.forEach { elem ->
                val obj = elem.jsonObject
                val idx = obj["index"]?.jsonPrimitive?.int?.minus(1) ?: return@forEach
                val conf = obj["confidence"]?.jsonPrimitive?.float ?: 0f
                val reason = obj["reasoning"]?.jsonPrimitive?.content ?: ""
                scoreMap[idx] = conf to reason
            }

            withImages.mapIndexed { index, (candidate, _) ->
                val (conf, reason) = scoreMap[index] ?: (0.1f to "Not evaluated")
                MatchResult(candidate, conf, reason)
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            withImages.map { (c, _) ->
                MatchResult(c, 0.5f, "Match error: ${e.message?.take(100)}")
            }
        }
    }

    private fun scrapeOgImage(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()
        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: return null
        response.close()

        // Look for og:image meta tag
        val ogPattern = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        ogPattern.find(html)?.groupValues?.get(1)?.let { return it }

        // Try reverse order (content before property)
        val ogPattern2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
        ogPattern2.find(html)?.groupValues?.get(1)?.let { return it }

        return null
    }

    private fun downloadImage(url: String): Bitmap {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val bytes = response.body!!.bytes()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
