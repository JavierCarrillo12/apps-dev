package co.edu.unal.reto_10

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val CAI_LAYER = "https://oaiee.scj.gov.co/agc/rest/services/Tematicos_NR/EquipamientoPMSDSCJ/MapServer/22"
internal fun interface CaiSource {
    suspend fun load(locality: Locality?): CaiDataset
}
internal class CaiServiceException(message: String) : IOException(message)
internal class CaiFormatException(message: String) : IOException(message)

internal class CaiRepository : CaiSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun localities(): List<Locality> = withContext(Dispatchers.IO) {
        val domain = parseLocalities(
            get(CAI_LAYER.toHttpUrl().newBuilder().addQueryParameter("f", "json").build(), "metadatos")
        )
        val url = queryUrl("1=1")
            .addQueryParameter("outFields", "CAIIULOCAL")
            .addQueryParameter("returnDistinctValues", "true")
            .addQueryParameter("returnGeometry", "false")
            .build()
        val response = get(url, "localidades con CAI")
        if (response.optBoolean("exceededTransferLimit", false)) {
            throw CaiFormatException("Listado de localidades incompleto")
        }
        val features = response.getJSONArray("features")
        val availableCodes = (0 until features.length()).mapNotNull { index ->
            features.getJSONObject(index).getJSONObject("attributes").text("CAIIULOCAL")
        }.toSet()
        domain.filter { it.code.toInt() in 1..20 && it.code in availableCodes }
    }

    override suspend fun load(locality: Locality?): CaiDataset = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val where = localityWhere(locality)
        try {
            val dataset = withTimeout(120_000) {
                val expected = get(queryUrl(where).addQueryParameter("returnCountOnly", "true").build(), where)
                    .let { checked(it).getInt("count") }
                collectCaiPages(expected) { lastId ->
                    val pageWhere = if (lastId < 0) where else "($where) AND OBJECTID > $lastId"
                    val url = queryUrl(pageWhere)
                        .addQueryParameter("outFields", "*")
                        .addQueryParameter("outSR", "4326")
                        .addQueryParameter("returnGeometry", "true")
                        .addQueryParameter("orderByFields", "OBJECTID ASC")
                        .addQueryParameter("resultRecordCount", "200")
                        .build()
                    parsePage(get(url, where))
                }
            }
            Log.i("Reto10CAI", "filtro=$where elapsed_ms=${SystemClock.elapsedRealtime() - start} operacion=completa registros=${dataset.received} validos=${dataset.places.size} descartados=${dataset.discarded}")
            dataset
        } catch (e: Exception) {
            Log.w("Reto10CAI", "filtro=$where elapsed_ms=${SystemClock.elapsedRealtime() - start} operacion=completa error=${e.javaClass.simpleName}")
            throw e
        }
    }

    private fun queryUrl(where: String): HttpUrl.Builder =
        "$CAI_LAYER/query".toHttpUrl().newBuilder()
            .addQueryParameter("where", where).addQueryParameter("f", "json")

    private suspend fun get(url: HttpUrl, filter: String): JSONObject {
        val start = SystemClock.elapsedRealtime()
        var http: Int? = null
        try {
            val body = suspendCancellableCoroutine { continuation ->
                val call = client.newCall(Request.Builder().url(url).header("User-Agent", APP_USER_AGENT).build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val text = response.use {
                                http = it.code
                                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                                it.body?.string() ?: throw CaiFormatException("Respuesta sin contenido")
                            }
                            continuation.resume(text)
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    }
                })
            }
            val json = checked(JSONObject(body))
            val count = json.optJSONArray("features")?.length() ?: json.optInt("count", -1)
            Log.i("Reto10CAI", "filtro=$filter elapsed_ms=${SystemClock.elapsedRealtime() - start} http=$http registros=$count")
            return json
        } catch (e: Exception) {
            Log.w("Reto10CAI", "filtro=$filter elapsed_ms=${SystemClock.elapsedRealtime() - start} http=${http ?: "-"} error=${e.javaClass.simpleName}")
            if (e is JSONException) throw CaiFormatException("JSON mal formado")
            throw e
        }
    }
}

internal fun localityWhere(locality: Locality?): String {
    if (locality == null) return "1=1"
    require(locality.code.matches(Regex("[0-9]{1,2}"))) { "Código de localidad inválido" }
    return "CAIIULOCAL='${locality.code}'"
}

internal fun checked(json: JSONObject): JSONObject {
    if (json.has("error")) throw CaiServiceException("El servicio oficial rechazó la consulta")
    return json
}

internal fun parseLocalities(json: JSONObject): List<Locality> {
    val fields = checked(json).getJSONArray("fields")
    val field = (0 until fields.length()).map { fields.getJSONObject(it) }
        .firstOrNull { it.optString("name") == "CAIIULOCAL" }
        ?: throw CaiFormatException("No existe el dominio de localidades")
    val codes = field.getJSONObject("domain").getJSONArray("codedValues")
    val result = (0 until codes.length()).map { index ->
        val entry = codes.getJSONObject(index)
        val code = entry.get("code").toString().trim()
        val name = entry.optString("name").trim()
        if (!code.matches(Regex("[0-9]{1,2}")) || name.isBlank()) {
            throw CaiFormatException("Dominio de localidades inválido")
        }
        Locality(code, name)
    }
    if (result.isEmpty() || result.distinctBy { it.code }.size != result.size) {
        throw CaiFormatException("Dominio de localidades vacío o duplicado")
    }
    return result.sortedBy { it.code.toInt() }
}

internal data class CaiPage(val places: List<Cai>, val ids: List<Long>, val discarded: Int, val exceeded: Boolean)

internal fun parsePage(json: JSONObject): CaiPage {
    val features: JSONArray = checked(json).getJSONArray("features")
    val spatialReference = json.optJSONObject("spatialReference")
    if (spatialReference != null && spatialReference.optInt("wkid") != 4326 &&
        spatialReference.optInt("latestWkid") != 4326) {
        throw CaiFormatException("Coordenadas en una referencia diferente a WGS84")
    }
    val ids = mutableListOf<Long>()
    val places = mutableListOf<Cai>()
    var discarded = 0
    for (i in 0 until features.length()) {
        val feature = features.getJSONObject(i)
        val attributes = feature.getJSONObject("attributes")
        val idValue = attributes.get("OBJECTID")
        if (idValue !is Number || idValue.toDouble() != idValue.toLong().toDouble() || idValue.toLong() < 0) {
            throw CaiFormatException("OBJECTID inválido")
        }
        val id = idValue.toLong()
        ids.add(id)
        val geometry = feature.optJSONObject("geometry")
        val coordinates = geometry?.let { Coordinates(it.optDouble("y"), it.optDouble("x")) }
        val name = attributes.text("CAINOMBRE")
        if (name == null || coordinates?.valid() != true) {
            discarded++
            continue
        }
        places.add(Cai(
            id, name, attributes.text("CAIDIR_SIT"), attributes.text("CAITELEFON"),
            attributes.text("CAIIULOCAL"), attributes.text("CAIHORARIO"),
            attributes.optLong("CAIFECHA_C").takeIf { it > 0 },
            attributes.text("CAIIDENTIF"), coordinates
        ))
    }
    return CaiPage(places, ids, discarded, json.optBoolean("exceededTransferLimit", false))
}

private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

internal suspend fun collectCaiPages(expected: Int, fetch: suspend (Long) -> CaiPage): CaiDataset {
    if (expected < 0) throw CaiFormatException("Conteo negativo")
    val places = mutableListOf<Cai>()
    var received = 0
    var discarded = 0
    var lastId = -1L
    do {
        val page = fetch(lastId)
        if (page.ids.any { it <= lastId } || page.ids.zipWithNext().any { it.first >= it.second }) {
            throw CaiFormatException("Orden de paginación no válido")
        }
        received += page.ids.size
        discarded += page.discarded
        places.addAll(page.places)
        if (received > expected) throw CaiFormatException("El conjunto cambió durante la consulta")
        if (page.ids.isNotEmpty()) lastId = page.ids.last()
        if (page.exceeded && page.ids.isEmpty()) throw CaiFormatException("Página incompleta sin avance")
    } while (page.exceeded || received < expected && page.ids.isNotEmpty())
    if (received != expected) throw CaiFormatException("Respuesta incompleta: $received de $expected registros")
    return CaiDataset(places, discarded, received)
}
