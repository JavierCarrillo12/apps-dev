package co.edu.unal.reto_9

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

internal const val APP_USER_AGENT = "Reto9GPS/1.0 (co.edu.unal.reto_9)"

internal fun interface PlaceSource {
    suspend fun find(origin: Coordinates, radiusMeters: Int): List<Place>
}

internal class OverpassPlaces : PlaceSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Volatile private var retryAfterMillis = 0L

    override suspend fun find(origin: Coordinates, radiusMeters: Int): List<Place> {
        val startedAt = SystemClock.elapsedRealtime()
        if (startedAt < retryAfterMillis) {
            val error = IOException("Overpass solicitó una pausa entre consultas")
            logQuery(radiusMeters, startedAt, "cooldown", error = error)
            throw error
        }
        val bounds = overpassBoundingBox(origin, radiusMeters)
        val query = """
            [out:json][timeout:20];
            (
              nwr$bounds["amenity"="hospital"]["name"];
              nwr$bounds["leisure"="park"]["name"];
              nwr$bounds["tourism"="attraction"]["name"];
            );
            out center;
        """.trimIndent()
        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", APP_USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val outcome = if (continuation.isCancelled) "cancelled" else "failed"
                    logQuery(radiusMeters, startedAt, outcome, error = e)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val status = response.code
                    val headersMillis = SystemClock.elapsedRealtime() - startedAt
                    val places = try {
                        response.use {
                            if (!it.isSuccessful) {
                                if (status == 429 || status == 406) {
                                    retryAfterMillis = SystemClock.elapsedRealtime() + 30_000
                                }
                                throw IOException("Overpass HTTP $status")
                            }
                            parse(it.body?.string() ?: throw IOException("Respuesta vacía"))
                        }
                    } catch (e: IOException) {
                        val outcome = if (continuation.isCancelled) "cancelled" else "failed"
                        logQuery(radiusMeters, startedAt, outcome, status, headersMillis, error = e)
                        continuation.resumeWithException(e)
                        return
                    } catch (e: JSONException) {
                        logQuery(radiusMeters, startedAt, "failed", status, headersMillis, error = e)
                        continuation.resumeWithException(IOException("Respuesta no válida de Overpass", e))
                        return
                    }

                    logQuery(radiusMeters, startedAt, "ok", status, headersMillis, places.size)
                    continuation.resume(places)
                }
            })
        }
    }

    private fun logQuery(
        radiusMeters: Int,
        startedAt: Long,
        outcome: String,
        status: Int? = null,
        headersMillis: Long? = null,
        count: Int? = null,
        error: Exception? = null
    ) {
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
        val message = buildString {
            append("radio_m=$radiusMeters elapsed_ms=$elapsedMillis outcome=$outcome http=${status ?: "-"}")
            headersMillis?.let { append(" headers_ms=$it") }
            count?.let { append(" lugares_recibidos=$it") }
            error?.let {
                append(" exception=${it.javaClass.simpleName}")
                it.cause?.let { cause -> append(" cause=${cause.javaClass.simpleName}") }
            }
        }
        if (outcome == "ok" || outcome == "cancelled") Log.i("Reto9Overpass", message)
        else Log.w("Reto9Overpass", message)
    }


    private fun parse(body: String): List<Place> {
        val data = JSONObject(body)
        if (data.optString("remark").isNotBlank()) {
            throw OverpassQueryException()
        }
        val elements = data.getJSONArray("elements")
        return buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: continue
                val name = tags.optString("name").trim()
                if (name.isEmpty()) continue
                val category = when {
                    tags.optString("amenity") == "hospital" -> PlaceCategory.HOSPITAL
                    tags.optString("leisure") == "park" -> PlaceCategory.PARK
                    tags.optString("tourism") == "attraction" -> PlaceCategory.ATTRACTION
                    else -> continue
                }
                val type = element.optString("type")
                val point = if (type == "node") element else element.optJSONObject("center")
                val latitude = point?.optDouble("lat") ?: continue
                val longitude = point.optDouble("lon")
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
                add(Place("$type/${element.getLong("id")}", name, category, Coordinates(latitude, longitude)))
            }
        }
    }
}

private class OverpassQueryException : IOException("Overpass devolvió un resultado incompleto")

private fun overpassBoundingBox(origin: Coordinates, radiusMeters: Int): String {
    val angle = radiusMeters / EARTH_RADIUS_METERS
    val latitudeDelta = Math.toDegrees(angle)
    val south = (origin.latitude - latitudeDelta).coerceAtLeast(-90.0)
    val north = (origin.latitude + latitudeDelta).coerceAtMost(90.0)
    val includesPole = south == -90.0 || north == 90.0
    val longitudeDelta = if (includesPole) 180.0 else {
        Math.toDegrees(asin((sin(angle) / cos(Math.toRadians(origin.latitude))).coerceAtMost(1.0)))
    }
    val west = if (includesPole) -180.0 else wrapLongitude(origin.longitude - longitudeDelta)
    val east = if (includesPole) 180.0 else wrapLongitude(origin.longitude + longitudeDelta)
    return String.format(Locale.ROOT, "(%.8f,%.8f,%.8f,%.8f)", south, west, north, east)
}

private fun wrapLongitude(longitude: Double): Double = (longitude + 540.0) % 360.0 - 180.0
