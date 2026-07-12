package com.example.life_counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the search overlay can be showing at a given moment. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Results(val cards: List<Card>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/**
 * Search-as-you-type, built as a single declarative Flow pipeline.
 *
 * Contrast this with GameViewModel.scheduleCommit(), which debounces history
 * imperatively — cancel the old Job, launch a new delayed one. Here the same
 * "wait for a typing pause" behaviour is one operator (`debounce`), and
 * `flatMapLatest` adds something the manual approach can't easily do: when a
 * newer keystroke arrives it *cancels the in-flight network request* for the
 * stale query, so results never flicker or arrive out of order.
 *
 * `@JvmOverloads` generates a real no-arg constructor so `viewModel()` works in
 * the app, while tests still inject a fake CardRepository.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CardSearchViewModel @JvmOverloads constructor(
    private val repo: CardRepository = GoAgainCardRepository(),
) : ViewModel() {

    // The typed text is itself a Flow — the input end of the pipeline.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(text: String) {
        _query.value = text
    }

    val uiState: StateFlow<SearchUiState> = _query
        .map { it.trim() }
        .distinctUntilChanged()          // ignore no-op edits (e.g. trailing space)
        .debounce(DEBOUNCE_MS)           // wait for a typing pause before searching
        .flatMapLatest { q ->            // a new query cancels the previous search
            when {
                q.length < MIN_QUERY_LENGTH -> flowOf<SearchUiState>(SearchUiState.Idle)
                else -> flow<SearchUiState> {
                    emit(SearchUiState.Loading)
                    val result = runCatching { repo.search(q) }
                    emit(
                        result.fold(
                            onSuccess = { cards ->
                                if (cards.isEmpty()) SearchUiState.Empty
                                else SearchUiState.Results(cards)
                            },
                            onFailure = { SearchUiState.Error(it.message ?: "Search failed") },
                        ),
                    )
                }
            }
        }
        // Turn the cold pipeline into a hot StateFlow the UI can read. The
        // upstream (and thus the network) only runs while the overlay collects
        // it, and keeps running 5s after it stops to survive config changes.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    companion object {
        const val DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }
}
