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
            parameter("limit", 20)
        }.body()

        return response.data.map { dto ->
            Card(
                name = dto.name,
                pitch = dto.pitch,
                cost = dto.cost,
                typeText = dto.typeText,
                functionalText = dto.functionalText,
                imageUrl = dto.printings.firstOrNull()?.imageUrl,
            )
        }
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
