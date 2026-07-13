package com.example.life_counter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A card's pitch value has an associated color; FaB uses three (plus "none"
 * for cards that don't pitch, like weapons/equipment). `fromApi` is a factory
 * on the companion object that maps the API's free-text color to our enum.
 */
enum class CardColor {
    RED,
    YELLOW,
    BLUE,
    NONE;

    companion object {
        fun fromApi(raw: String): CardColor = when (raw.trim().lowercase()) {
            "red" -> RED
            "yellow" -> YELLOW
            "blue" -> BLUE
            else -> NONE
        }
    }
}

/**
 * One printable version of a card at a given pitch/color. Many FaB cards come
 * in red (pitch 1) / yellow (2) / blue (3) versions that share a name and rules
 * text but differ in pitch, color, cost and card image.
 */
data class CardVariant(
    val color: CardColor,
    val pitch: String,
    val cost: String,
    // Attack power / defense; blank for cards that have none. Shown in the
    // rules-text view, where the card image (which normally carries them) isn't.
    val power: String = "",
    val defense: String = "",
    val functionalText: String,
    val imageUrl: String?,
    // Legality is per-pitch: e.g. Golden Tipple's red/yellow are CC-banned while
    // its blue is CC-legal, so each variant carries its own.
    val legality: Legality,
)

/** Which constructed formats a single card variant is legal — or banned — in. */
data class Legality(
    val legalFormats: List<String>,
    val bannedFormats: List<String>,
)

/**
 * A card-wide legality view aggregated across pitch variants. A `bannedFormats`
 * entry is either a bare format ("CC", banned at every pitch) or annotated with
 * the affected pitches ("CC (pitch 1, 2)") when only some pitches are banned.
 */
data class LegalitySummary(
    val legalFormats: List<String>,
    val bannedFormats: List<String>,
)

// The formats we surface, in display order.
private val LEGALITY_FORMATS = listOf("Blitz", "CC", "Commoner")

/**
 * The clean model the UI consumes: a single logical card grouping every pitch
 * variant the search returned under one name. Deliberately separate from the
 * wire DTOs below.
 */
data class Card(
    val name: String,
    val typeText: String,
    val variants: List<CardVariant>,   // ≥1, sorted by pitch
) {
    // Computed properties — derived from `variants`, so they can never fall out
    // of sync with it (no stored copy to keep updated).

    /** The version shown by default: lowest pitch / first returned. */
    val primary: CardVariant get() = variants.first()

    /** Distinct pip colors across variants, in pitch order. Drives the pips. */
    val colors: List<CardColor>
        get() = variants.map { it.color }.filter { it != CardColor.NONE }.distinct()

    /**
     * Fold the per-variant legality into one card-wide summary. For each format:
     * legal at every pitch → listed under legal; banned at every pitch → listed
     * bare under banned; banned at only some pitches → listed under banned,
     * annotated with those pitches so partial bans aren't lost or overstated.
     */
    fun legalitySummary(): LegalitySummary {
        val legal = mutableListOf<String>()
        val banned = mutableListOf<String>()
        for (format in LEGALITY_FORMATS) {
            val bannedAt = variants.filter { format in it.legality.bannedFormats }
            val legalAt = variants.filter { format in it.legality.legalFormats }
            when {
                bannedAt.isEmpty() -> if (legalAt.isNotEmpty()) legal += format
                bannedAt.size == variants.size -> banned += format
                else -> {
                    val pitches = bannedAt.mapNotNull { it.pitch.ifBlank { null } }
                    banned += if (pitches.isEmpty()) format
                    else "$format (pitch ${pitches.joinToString(", ")})"
                }
            }
        }
        return LegalitySummary(legalFormats = legal, bannedFormats = banned)
    }
}

// --- Wire format (GoAgain API: GET /v1/cards) -------------------------------
// @Serializable makes the compiler generate the JSON parser at build time (no
// reflection). Json { ignoreUnknownKeys = true } lets us omit fields we ignore;
// giving every property a default makes parsing resilient to missing fields.

@Serializable
data class CardSearchResponse(
    val data: List<CardDto> = emptyList(),
)

@Serializable
data class CardDto(
    val name: String = "",
    val color: String = "",
    val pitch: String = "",
    val cost: String = "",
    val power: String = "",
    val defense: String = "",
    @SerialName("type_text") val typeText: String = "",
    @SerialName("functional_text_plain") val functionalText: String = "",
    // Format legality flags. Legal in a format means the *_legal flag is set and
    // it's not banned/suspended there.
    @SerialName("blitz_legal") val blitzLegal: Boolean = false,
    @SerialName("cc_legal") val ccLegal: Boolean = false,
    @SerialName("commoner_legal") val commonerLegal: Boolean = false,
    @SerialName("blitz_banned") val blitzBanned: Boolean = false,
    @SerialName("cc_banned") val ccBanned: Boolean = false,
    @SerialName("commoner_banned") val commonerBanned: Boolean = false,
    @SerialName("blitz_suspended") val blitzSuspended: Boolean = false,
    @SerialName("cc_suspended") val ccSuspended: Boolean = false,
    @SerialName("commoner_suspended") val commonerSuspended: Boolean = false,
    val printings: List<PrintingDto> = emptyList(),
)

@Serializable
data class PrintingDto(
    @SerialName("image_url") val imageUrl: String? = null,
)
