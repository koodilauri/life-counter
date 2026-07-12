package com.example.life_counter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The clean model the UI consumes — deliberately separate from the wire DTOs
 * below. The API returns ~40 fields per card; a search row only needs these six.
 */
data class Card(
    val name: String,
    val pitch: String,
    val cost: String,
    val typeText: String,
    val functionalText: String,
    val imageUrl: String?,
)

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
    val pitch: String = "",
    val cost: String = "",
    @SerialName("type_text") val typeText: String = "",
    @SerialName("functional_text_plain") val functionalText: String = "",
    val printings: List<PrintingDto> = emptyList(),
)

@Serializable
data class PrintingDto(
    @SerialName("image_url") val imageUrl: String? = null,
)
