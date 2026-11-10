package co.edu.unal.reto_9

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val EARTH_RADIUS_METERS = 6_371_008.8

internal data class Coordinates(val latitude: Double, val longitude: Double) {
    fun distanceTo(other: Coordinates): Double {
        val lat = Math.toRadians(latitude)
        val otherLat = Math.toRadians(other.latitude)
        val halfLongitudeDelta = Math.toRadians(other.longitude - longitude) / 2
        val a = sin((otherLat - lat) / 2).let { it * it } +
            cos(lat) * cos(otherLat) * sin(halfLongitudeDelta).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

internal enum class PlaceCategory(val label: String, val color: String) {
    HOSPITAL("Hospital", "#A72343"),
    PARK("Parque", "#087054"),
    ATTRACTION("Atracción turística", "#805219")
}

internal data class Place(
    val osmId: String,
    val name: String,
    val category: PlaceCategory,
    val coordinates: Coordinates
)

internal data class NearbyPlace(val place: Place, val distanceMeters: Double)

internal fun nearbyPlaces(
    places: List<Place>,
    origin: Coordinates,
    radiusMeters: Int
): List<NearbyPlace> {
    val nearby = places.asSequence()
        .map { NearbyPlace(it, origin.distanceTo(it.coordinates)) }
        .filter { it.distanceMeters <= radiusMeters }
        .sortedBy { it.distanceMeters }
        .distinctBy { it.place.osmId }

    val byName = mutableMapOf<Pair<PlaceCategory, String>, MutableList<NearbyPlace>>()
    return nearby.filter { candidate ->
        val place = candidate.place
        val group = byName.getOrPut(place.category to place.name.lowercase()) { mutableListOf() }
        val duplicate = group.any { it.place.coordinates.distanceTo(place.coordinates) <= 50 }
        if (!duplicate) group.add(candidate)
        !duplicate
    }.toList()
}
