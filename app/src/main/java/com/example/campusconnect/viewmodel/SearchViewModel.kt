package com.example.campusconnect.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.repository.SearchRepository
import com.example.campusconnect.repository.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val searchRepository: SearchRepository) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // true once the user has typed something meaningful, so an empty result list can be
    // told apart from "hasn't searched yet" (spec section 31: empty vs initial state)
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var searchJob: Job? = null

    fun performSearch(query: String, userId: Int) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _hasSearched.value = false
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        searchJob = viewModelScope.launch {
            delay(300) // debounce - avoid re-querying and spamming search history on every keystroke
            _hasSearched.value = true
            val results = searchRepository.searchEvents(query, userId)
            _searchResults.value = results
            _isSearching.value = false

            if (query.trim().length >= 3) {
                searchRepository.saveSearchHistory(userId, query.trim())
            }
        }
    }
}
