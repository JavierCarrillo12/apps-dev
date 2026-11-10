package co.edu.unal.reto_9

import kotlin.math.nextUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPlacesTest {
    private val origin = Coordinates(0.0, 0.0)

    @Test
    fun includesTheBoundaryAndExcludesPointsOutside() {
        val edge = Coordinates(0.0, Math.toDegrees(1_000 / EARTH_RADIUS_METERS))
        val inside = Place("node/1", "Dentro", PlaceCategory.PARK, Coordinates(0.0, edge.longitude * 0.9999))
        val boundary = Place("way/2", "Límite", PlaceCategory.PARK, edge)
        val outside = Place("relation/3", "Fuera", PlaceCategory.PARK, Coordinates(0.0, edge.longitude * 1.0001))

        val results = nearbyPlaces(listOf(outside, boundary, inside), origin, 1_000)

        assertEquals(1_000.0, origin.distanceTo(edge), 1e-8)
        assertEquals(listOf(inside, boundary), results.map { it.place })
        assertTrue(results.all { it.distanceMeters <= 1_000 })
        val justOutside = boundary.copy(coordinates = Coordinates(0.0, edge.longitude.nextUp().nextUp()))
        assertTrue(nearbyPlaces(listOf(justOutside), origin, 1_000).isEmpty())
    }

    @Test
    fun deduplicatesOsmIdsAndNearbyRepresentationsOfTheSamePlace() {
        val park = Place("node/1", "Parque Central", PlaceCategory.PARK, Coordinates(0.0, 0.001))
        val area = park.copy(osmId = "way/1", name = "PARQUE CENTRAL", coordinates = Coordinates(0.0, 0.0011))
        val separatePark = area.copy(osmId = "way/2", coordinates = Coordinates(0.0, 0.004))

        assertEquals(
            listOf(park, separatePark),
            nearbyPlaces(listOf(area, separatePark, park, park), origin, 1_000).map { it.place }
        )
    }
}
