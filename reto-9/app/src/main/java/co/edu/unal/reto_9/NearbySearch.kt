package co.edu.unal.reto_9

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data object Failed : SearchState
    data class Results(val places: List<NearbyPlace>) : SearchState
}

// Calls and publication are confined to the caller's main-thread scope.
internal class NearbySearch(private val scope: CoroutineScope, private val source: PlaceSource) {
    private val mutableState = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = mutableState.asStateFlow()
    private var activeQuery: Job? = null
    private var revision = 0L

    fun clear() {
        revision++
        activeQuery?.cancel()
        mutableState.value = SearchState.Idle
    }

    fun search(origin: Coordinates, radiusKm: Int) {
        val queryRevision = ++revision
        activeQuery?.cancel()
        mutableState.value = SearchState.Loading
        activeQuery = scope.launch {
            val radiusMeters = radiusKm * 1_000
            val result = try {
                val places = source.find(origin, radiusMeters)
                SearchState.Results(nearbyPlaces(places, origin, radiusMeters))
            } catch (_: IOException) {
                SearchState.Failed
            }
            if (queryRevision == revision) mutableState.value = result
        }
    }
}
