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

        val agencyName = text.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank()
                    && KEYWORDS.none { kw -> line.equals(kw, ignoreCase = true) }
                    && PHONE_REGEX.find(line) == null
                    && REF_REGEX.find(line) == null
            }

        return SignInfo(
            agencyName = agencyName,
            phoneNumber = phone,
            referenceNumber = ref,
            rawText = text
        )
    }
}
