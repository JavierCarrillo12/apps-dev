package co.edu.unal.reto_10

import org.junit.Assert.*
import org.junit.Test

class CaiTest {
    private val origin = Coordinates(4.65, -74.08)
    private fun cai(id: Long, latitude: Double = 4.65) =
        Cai(id, "CAI $id", null, null, null, null, null, "compartido", Coordinates(latitude, -74.08))

    @Test fun nearestHandlesEmptySingleDuplicatesAndTies() {
        assertTrue(closestTwo(emptyList(), origin).isEmpty())
        assertEquals(listOf(1L), closestTwo(listOf(cai(1), cai(1)), origin).map { it.cai.id })
        assertEquals(listOf(2L, 3L), closestTwo(listOf(cai(5, 4.66), cai(3), cai(2)), origin).map { it.cai.id })
    }

    @Test fun distinctObjectIdsWithSameInstitutionalIdSurvive() {
        assertEquals(listOf(1L, 2L), closestTwo(listOf(cai(2), cai(1)), origin).map { it.cai.id })
    }
}
