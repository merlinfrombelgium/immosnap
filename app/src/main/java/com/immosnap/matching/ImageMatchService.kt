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

class ImageMatchService {

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    private val httpClient = OkHttpClient()

    suspend fun rankCandidates(
        photo: Bitmap,
        candidates: List<ListingCandidate>
    ): List<MatchResult> {
        if (candidates.isEmpty()) return emptyList()

        val thumbnails = candidates.mapNotNull { candidate ->
            candidate.thumbnailUrl?.let { url ->
                try {
                    val bitmap = downloadImage(url)
                    candidate to bitmap
                } catch (e: Exception) {
                    null
                }
            }
        }

        if (thumbnails.isEmpty()) {
            return candidates.map { MatchResult(it, 0.5f, "No listing photo available for comparison") }
        }

        val response = model.generateContent(
            content {
                image(photo)
                thumbnails.forEachIndexed { index, (candidate, thumb) ->
                    text("Listing ${index + 1}: ${candidate.title} (${candidate.url})")
                    image(thumb)
                }
                text(
                    """You are comparing a user's photo of a house with listing photos.
                    |Which listing photo shows the same building as the user's photo?
                    |Compare facade, roof, windows, colors, and architectural style.
                    |
                    |Respond as JSON array: [{"index": 1, "confidence": 0.95, "reasoning": "..."}]
                    |Order by confidence descending. Index is 1-based matching the listing numbers above.
                    """.trimMargin()
                )
            }
        )

        val text = response.text ?: return candidates.map {
            MatchResult(it, 0.5f, "Gemini returned no response")
        }

        return try {
            val jsonText = text.substringAfter("[").substringBefore("]").let { "[$it]" }
            val results = Json.parseToJsonElement(jsonText).jsonArray
            results.mapNotNull { elem ->
                val obj = elem.jsonObject
                val index = obj["index"]!!.jsonPrimitive.int - 1
                val conf = obj["confidence"]!!.jsonPrimitive.float
                val reason = obj["reasoning"]?.jsonPrimitive?.content ?: ""
                if (index in thumbnails.indices) {
                    MatchResult(thumbnails[index].first, conf, reason)
                } else null
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            candidates.map { MatchResult(it, 0.5f, "Failed to parse Gemini response") }
        }
    }

    private suspend fun downloadImage(url: String): Bitmap = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val bytes = response.body!!.bytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
