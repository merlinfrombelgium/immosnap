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

    suspend fun rankCandidates(
        photo: Bitmap,
        candidates: List<ListingCandidate>
    ): List<MatchResult> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Step 1: Scrape images from each listing page
        val enriched = candidates.map { candidate ->
            try {
                val images = scrapeListingImages(candidate.url)
                candidate.copy(
                    imageUrls = images,
                    thumbnailUrl = images.firstOrNull()
                )
            } catch (_: Exception) {
                candidate
            }
        }

        // Step 2: For each candidate, download up to 5 images for gallery + matching
        val candidateImages = enriched.map { candidate ->
            val bitmaps = candidate.imageUrls.take(5).mapNotNull { url ->
                try { downloadImage(url) } catch (_: Exception) { null }
            }
            candidate to bitmaps
        }

        // Step 3: If any candidate has images, do Gemini comparison
        val hasAnyImages = candidateImages.any { it.second.isNotEmpty() }
        if (!hasAnyImages) {
            return@withContext enriched.map {
                MatchResult(it, 0.5f, "No listing photos available")
            }
        }

        try {
            val response = model.generateContent(
                content {
                    text("USER'S PHOTO (taken from the street, may show the building's facade):")
                    image(photo)
                    text("---")
                    candidateImages.forEachIndexed { idx, (candidate, bitmaps) ->
                        text("LISTING ${idx + 1}: ${candidate.title} (${candidate.url})")
                        if (bitmaps.isNotEmpty()) {
                            bitmaps.forEachIndexed { imgIdx, bitmap ->
                                text("Image ${imgIdx + 1}/${bitmaps.size}:")
                                image(bitmap)
                            }
                        } else {
                            text("[no images available]")
                        }
                        text("---")
                    }
                    text(
                        """Compare the user's street photo with ALL listing images above.
                        |Determine which listings show the SAME BUILDING as the user's photo.
                        |Look across ALL images for each listing - one might show the exterior/facade.
                        |Compare: facade shape, roof style, windows, door, colors, materials, architectural style.
                        |The user's photo angle may differ from listing photos.
                        |
                        |Respond ONLY with a JSON array (no markdown):
                        |[{"index": 1, "confidence": 0.95, "best_image": 2, "reasoning": "Image 2 shows matching red brick facade"}]
                        |index = 1-based listing number. best_image = which image (1-based) best matches.
                        |Order by confidence descending. Use 0.0-1.0 scale. Be strict.
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
                val bestImg = obj["best_image"]?.jsonPrimitive?.int ?: 1
                val reason = obj["reasoning"]?.jsonPrimitive?.content ?: ""
                scoreMap[idx] = Triple(conf, bestImg, reason)
            }

            enriched.mapIndexed { index, candidate ->
                val (conf, bestImg, reason) = scoreMap[index] ?: Triple(0.1f, 1, "Not evaluated")
                // Reorder images to put the best matching one first
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

    /**
     * Scrape listing images from a property page.
     * Tries og:image first, then looks for common image patterns.
     */
    private fun scrapeListingImages(url: String): List<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0")
            .build()
        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: return emptyList()
        response.close()

        val images = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addImage(imgUrl: String) {
            val resolved = when {
                imgUrl.startsWith("//") -> "https:$imgUrl"
                imgUrl.startsWith("/") -> {
                    val base = url.substringBefore("/", url).let {
                        Regex("https?://[^/]+").find(url)?.value ?: return
                    }
                    "$base$imgUrl"
                }
                else -> imgUrl
            }
            // Filter out tiny images, icons, logos
            if (resolved !in seen && !resolved.contains("logo") && !resolved.contains("icon")
                && !resolved.contains("sprite") && !resolved.contains("avatar")) {
                seen.add(resolved)
                images.add(resolved)
            }
        }

        // 1. og:image
        val ogPattern = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        ogPattern.find(html)?.groupValues?.get(1)?.let { addImage(it) }
        val ogPattern2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
        ogPattern2.find(html)?.groupValues?.get(1)?.let { addImage(it) }

        // 2. Look for large images in common listing patterns
        // Match img tags with src containing typical listing image URL patterns
        val imgPattern = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        imgPattern.findAll(html).forEach { match ->
            val src = match.groupValues[1]
            // Listing sites typically have images with dimensions or "photo" in URL
            if (src.contains("photo") || src.contains("image") || src.contains("/pic")
                || src.contains("property") || src.contains("classified")
                || src.contains("media") || src.contains("upload")) {
                addImage(src)
            }
        }

        // 3. JSON-LD or data attributes with image arrays
        val jsonImgPattern = Regex(""""(https?://[^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"""", RegexOption.IGNORE_CASE)
        jsonImgPattern.findAll(html).forEach { match ->
            val src = match.groupValues[1]
            if (src.contains("photo") || src.contains("image") || src.contains("media")
                || src.contains("property") || src.contains("classified") || src.contains("upload")) {
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
