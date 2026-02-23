package com.immosnap.ocr

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.immosnap.BuildConfig
import kotlinx.serialization.json.*

class OcrService {

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun extractText(bitmap: Bitmap): SignInfo {
        val response = model.generateContent(
            content {
                image(bitmap)
                text(
                    """Analyze this photo of a real estate 'for sale' or 'for rent' sign.
                    |Extract the following information and respond ONLY with JSON (no markdown):
                    |{
                    |  "agency_name": "full agency/company name",
                    |  "phone_number": "phone number if visible",
                    |  "reference_number": "listing reference number if visible",
                    |  "raw_text": "all text visible on the sign"
                    |}
                    |
                    |Rules:
                    |- Combine words that form the agency name even if they're on separate lines
                    |- Include the full company name (e.g. "Immo Lot", not just "Immo")
                    |- For phone numbers, keep the original format
                    |- Use null for any field you can't find
                    """.trimMargin()
                )
            }
        )

        val text = response.text ?: return SignInfo(null, null, null, "")

        return try {
            val jsonText = text
                .replace("```json", "").replace("```", "")
                .trim()
            val json = Json.parseToJsonElement(jsonText).jsonObject

            SignInfo(
                agencyName = json["agency_name"]?.jsonPrimitive?.contentOrNull,
                phoneNumber = json["phone_number"]?.jsonPrimitive?.contentOrNull,
                referenceNumber = json["reference_number"]?.jsonPrimitive?.contentOrNull,
                rawText = json["raw_text"]?.jsonPrimitive?.contentOrNull ?: text
            )
        } catch (e: Exception) {
            // Fallback: use the raw response as text
            SignInfo(null, null, null, text)
        }
    }
}
