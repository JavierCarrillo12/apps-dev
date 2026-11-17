package co.edu.unal.reto_10

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CaiServiceTest {
    @Test fun officialServiceLoadsCompleteDatasetAndSendsLocalityFilter(): Unit = runBlocking {
        val repository = CaiRepository()
        val localities = repository.localities()
        val teusaquillo = localities.single { it.code == "13" }
        val all = repository.load(null)
        val filtered = repository.load(teusaquillo)
        assertTrue(all.places.isNotEmpty())
        assertEquals(all.received, all.places.size + all.discarded)
        assertEquals(all.places.size, all.places.distinctBy { it.id }.size)
        assertTrue(filtered.places.isNotEmpty())
        assertTrue(filtered.places.all { it.localityCode == "13" })
        assertTrue(all.places.size > filtered.places.size)
        val publishedCodes = all.places.mapNotNull { it.localityCode }.filter { it.toIntOrNull() in 1..20 }.toSet()
        assertEquals(publishedCodes, localities.map { it.code }.toSet())
        assertTrue(localities.none { it.code in setOf("00", "66", "77", "99") })
        val nearest = closestTwo(all.places, Coordinates(4.65333, -74.08175))
        assertEquals(2, nearest.size)
        assertTrue(nearest.first().meters <= nearest.last().meters)
        android.util.Log.i("Reto10Verificacion",
            "total=${all.received} descartados=${all.discarded} teusaquillo=${filtered.received} cercanos=${nearest.map { it.cai.name to it.meters }}")
    }
}
