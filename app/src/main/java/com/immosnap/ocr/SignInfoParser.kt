package com.immosnap.ocr

object SignInfoParser {

    private val PHONE_REGEX = Regex("""(\+32|0)\s*\d[\d\s]{7,12}\d""")
    private val REF_REGEX = Regex("""(?:ref|réf|reference)[.:# ]*\s*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE)
    private val KEYWORDS = setOf(
        "te koop", "a vendre", "for sale", "te huur", "a louer",
        "sold", "vendu", "verkocht", "open huis", "open deur",
        "nieuw", "nouveau", "new"
    )

    fun parse(text: String): SignInfo {
        val phone = PHONE_REGEX.find(text)?.value?.trim()
        val ref = REF_REGEX.find(text)?.groupValues?.get(1)

        // Collect consecutive non-keyword, non-phone lines as the agency name
        // e.g. "Immo\nLOT" → "Immo Lot"
        val candidateLines = text.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank()
                    && KEYWORDS.none { kw -> line.equals(kw, ignoreCase = true) }
                    && PHONE_REGEX.find(line) == null
                    && REF_REGEX.find(line) == null
                    && line.length <= 40  // skip very long lines (addresses etc)
            }

        // Take up to 2 consecutive short lines as the agency name
        val agencyName = when {
            candidateLines.isEmpty() -> null
            candidateLines.size >= 2 && candidateLines[1].length <= 20 ->
                "${candidateLines[0]} ${candidateLines[1]}".trim()
            else -> candidateLines[0]
        }

        return SignInfo(
            agencyName = agencyName,
            phoneNumber = phone,
            referenceNumber = ref,
            rawText = text
        )
    }
}
