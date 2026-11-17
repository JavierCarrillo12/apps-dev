package co.edu.unal.reto_10

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val APP_USER_AGENT = "Reto10CAI/1.0 (co.edu.unal.reto_10)"
internal data class Coordinates(val latitude: Double, val longitude: Double) {
    fun distanceTo(other: Coordinates): Double {
        val lat = Math.toRadians(latitude)
        val otherLat = Math.toRadians(other.latitude)
        val a = sin((otherLat - lat) / 2).let { it * it } +
            cos(lat) * cos(otherLat) * sin(Math.toRadians(other.longitude - longitude) / 2).let { it * it }
        return 12_742_017.6 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun valid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        (latitude != 0.0 || longitude != 0.0)
}

internal data class Locality(val code: String, val name: String)
internal data class Cai(
    val id: Long,
    val name: String,
    val address: String?,
    val phone: String?,
    val localityCode: String?,
    val hours: String?,
    val cutoff: Long?,
    val institutionalId: String?,
    val coordinates: Coordinates
)
internal data class CaiDataset(val places: List<Cai>, val discarded: Int, val received: Int)
internal data class RankedCai(val cai: Cai, val meters: Double)

internal fun closestTwo(places: List<Cai>, origin: Coordinates): List<RankedCai> =
    places.distinctBy { it.id }.map { RankedCai(it, origin.distanceTo(it.coordinates)) }
        .sortedWith(compareBy<RankedCai> { it.meters }.thenBy { it.cai.id }).take(2)
