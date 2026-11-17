package co.edu.unal.reto_10

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class CaiSearchTest {
    @Test fun aLateResponseCannotReplaceTheCurrentLocality() = runBlocking {
        val pending = mutableMapOf<String, Continuation<CaiDataset>>()
        val done = mutableListOf<String>()
        val search = CaiSearch(this, CaiSource { locality ->
            val key = locality?.code ?: "all"
            suspendCoroutine<CaiDataset> { pending[key] = it }.also { done += key }
        })
        search.query(null)
        yield()
        val locality = Locality("13", "TEUSAQUILLO")
        search.query(locality)
        yield()
        val data = CaiDataset(emptyList(), 0, 0)
        pending.getValue("13").resume(data)
        yield()
        pending.getValue("all").resume(data)
        yield()
        assertEquals(listOf("13", "all"), done)
        assertEquals(locality, search.state.value.results?.filter)
    }

    @Test fun nearestUsesCompleteDatasetAfterAFilteredQuery() = runBlocking {
        val requests = mutableListOf<String?>()
        val search = CaiSearch(this, CaiSource { locality ->
            requests += locality?.code
            CaiDataset(emptyList(), 0, 0)
        })
        search.query(null)
        yield()
        search.query(Locality("13", "TEUSAQUILLO"))
        yield()
        search.query(null, nearest = true)
        yield()
        assertEquals(listOf(null, "13"), requests)
        assertNull(search.state.value.results?.filter)
        assertTrue(search.state.value.results?.nearestMode == true)
    }
}
