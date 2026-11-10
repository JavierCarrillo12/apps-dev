package co.edu.unal.reto_9

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class NearbySearchTest {
    @Test
    fun lateResponseCannotReplaceTheCurrentRadiusResults() = runBlocking {
        val pending = mutableMapOf<Int, Continuation<List<Place>>>()
        val completed = mutableListOf<Int>()
        val origin = Coordinates(4.65, -74.08)
        val search = NearbySearch(this, PlaceSource { _, radius ->
            // Simulate a source that completes even after its request was cancelled.
            suspendCoroutine<List<Place>> { pending[radius] = it }.also { completed += radius }
        })
        val oldPlace = Place("node/1", "Consulta anterior", PlaceCategory.PARK, origin)
        val currentPlace = Place("node/2", "Consulta vigente", PlaceCategory.HOSPITAL, origin)

        search.search(origin, 1)
        yield()
        search.search(origin, 5)
        yield()

        pending.getValue(5_000).resume(listOf(currentPlace))
        yield()
        val current = SearchState.Results(listOf(NearbyPlace(currentPlace, 0.0)))
        assertEquals(current, search.state.value)

        pending.getValue(1_000).resume(listOf(oldPlace))
        yield()
        assertEquals(listOf(5_000, 1_000), completed)
        assertEquals(current, search.state.value)
    }
}
