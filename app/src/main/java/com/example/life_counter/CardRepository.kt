package com.example.life_counter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The seam the ViewModel depends on. Being an interface (not a concrete class)
 * lets tests swap in a fake that returns canned results without any network.
 */
interface CardRepository {
    suspend fun search(query: String): List<Card>
}

/**
 * Real implementation, backed by the GoAgain Flesh and Blood card API.
 *
 * The HttpClient is created once and reused — a fresh client per request would
 * leak its connection pool. `search` is a `suspend fun`: it looks synchronous
 * but yields the thread while awaiting the network (Ktor's CIO engine already
 * runs the socket I/O off the main thread), much like `await fetch()` in JS.
 */
class GoAgainCardRepository(
    private val client: HttpClient = defaultClient(),
) : CardRepository {

    override suspend fun search(query: String): List<Card> {
        val response: CardSearchResponse = client.get(SEARCH_URL) {
            parameter("name", query)
            // The API returns one row per pitch/color, and grouping collapses
            // several of those into one card — so ask for more rows than the
            // number of cards we want to show.
            parameter("limit", 40)
        }.body()

        // Group rows sharing a name into a single Card carrying all its pitch
        // variants. groupBy keeps first-seen order for both keys and values.
        return response.data
            .groupBy { it.name }
            .map { (name, dtos) -> dtos.toCard(name) }
    }

    companion object {
        private const val SEARCH_URL = "https://api.goagain.dev/v1/cards"

        private fun defaultClient() = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

// Extension functions on the DTO types keep the mapping close to the data it
// maps, without cluttering the repository class body.

private fun List<CardDto>.toCard(name: String): Card {
    val variants = this
        // pitch is a string like "1"/"2"/"3" (or "" for non-pitching cards);
        // sort numerically, empties last, so red→yellow→blue is stable.
        .sortedBy { it.pitch.toIntOrNull() ?: Int.MAX_VALUE }
        .map { dto ->
            CardVariant(
                color = CardColor.fromApi(dto.color),
                pitch = dto.pitch,
                cost = dto.cost,
                power = dto.power,
                defense = dto.defense,
                functionalText = dto.functionalText,
                imageUrl = dto.printings.firstOrNull()?.imageUrl,
                // Per-pitch: bans can differ between the red/yellow/blue versions.
                legality = dto.toLegality(),
            )
        }
    // typeText is the same across pitches — read it off any row.
    val any = this.first()
    return Card(name = name, typeText = any.typeText, variants = variants)
}

private fun CardDto.toLegality(): Legality {
    val legal = buildList {
        if (blitzLegal && !blitzBanned && !blitzSuspended) add("Blitz")
        if (ccLegal && !ccBanned && !ccSuspended) add("CC")
        if (commonerLegal && !commonerBanned && !commonerSuspended) add("Commoner")
    }
    val banned = buildList {
        if (blitzBanned || blitzSuspended) add("Blitz")
        if (ccBanned || ccSuspended) add("CC")
        if (commonerBanned || commonerSuspended) add("Commoner")
    }
    return Legality(legalFormats = legal, bannedFormats = banned)
}
