package com.ghaith.ironhud.ai

/**
 * Parses the model's line format (NAME:/TYPE:/CONFIDENCE:/BRIEF:/- fact) and tolerates the usual
 * deviations: markdown bold, different bullet characters, wrapped lines, a missing NAME line.
 * Safe to call on a half-received stream; the last line may simply be incomplete.
 */
object BriefParser {
    private enum class Field { NONE, NAME, TYPE, CONFIDENCE, BRIEF, FACT }

    private val KEY = Regex("""^(name|object|type|category|confidence|brief|summary|description)\s*[:：]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("""^(?:[-•*·▪►]|\d+[.)])\s+(.*)$""")
    private const val MAX_FACTS = 5

    fun parse(raw: String): Brief {
        var name = ""
        var type = ""
        var confidence = ""
        val summary = StringBuilder()
        val facts = mutableListOf<String>()
        var current = Field.NONE
        var firstLoose: String? = null

        for (rawLine in raw.replace("\r", "").lines()) {
            val line = rawLine.replace("**", "").replace("__", "").trim().trimStart('#').trim()
            if (line.isEmpty()) continue
            val key = KEY.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            when {
                key != null -> {
                    val value = key.groupValues[2].trim()
                    when (key.groupValues[1].lowercase()) {
                        "name", "object" -> { name = value; current = Field.NAME }
                        "type", "category" -> { type = value; current = Field.TYPE }
                        "confidence" -> { confidence = value.lowercase(); current = Field.CONFIDENCE }
                        else -> {
                            if (summary.isNotEmpty()) summary.append(' ')
                            summary.append(value)
                            current = Field.BRIEF
                        }
                    }
                }
                bullet != null -> {
                    if (facts.size < MAX_FACTS) facts += bullet.groupValues[1].trim()
                    current = Field.FACT
                }
                current == Field.BRIEF -> summary.append(' ').append(line)
                current == Field.FACT && facts.isNotEmpty() -> facts[facts.lastIndex] = facts.last() + " " + line
                else -> if (firstLoose == null) firstLoose = line
            }
        }
        if (name.isBlank() && firstLoose != null) name = firstLoose!!.take(40)
        return Brief(
            name = name.trim().trimEnd('.'),
            type = type.trim(),
            confidence = confidence.trim(),
            summary = summary.toString().trim(),
            facts = facts.filter { it.isNotBlank() },
        )
    }
}
