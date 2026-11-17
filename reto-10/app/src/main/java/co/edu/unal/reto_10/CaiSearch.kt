package co.edu.unal.reto_10

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CaiResults(val dataset: CaiDataset, val filter: Locality?, val nearestMode: Boolean)
internal data class SearchState(
    val loading: Boolean = false,
    val results: CaiResults? = null,
    val error: String? = null
)
internal class CaiSearch(private val scope: CoroutineScope, private val source: CaiSource) {
    private val mutable = MutableStateFlow(SearchState())
    val state = mutable.asStateFlow()
    private var complete: CaiDataset? = null
    private var job: Job? = null
    private var revision = 0L
    private var pendingFilter: Locality? = null
    private var pendingNearest = false

    fun query(filter: Locality?, nearest: Boolean = false) {
        val effectiveFilter = if (nearest) null else filter
        if (mutable.value.loading && effectiveFilter == pendingFilter && nearest == pendingNearest) return
        pendingFilter = effectiveFilter
        pendingNearest = nearest
        val requestRevision = ++revision
        job?.cancel()
        mutable.value = SearchState(loading = true)
        job = scope.launch {
            try {
                val data = if (nearest) complete ?: source.load(null) else source.load(effectiveFilter)
                if (requestRevision != revision) return@launch
                if (effectiveFilter == null) complete = data
                mutable.value = SearchState(results = CaiResults(data, effectiveFilter, nearest))
            } catch (_: TimeoutCancellationException) {
                if (requestRevision == revision) mutable.value = SearchState(error = "El servicio tardó demasiado.")
            } catch (e: IOException) {
                if (requestRevision == revision) mutable.value = SearchState(error = when (e) {
                    is CaiFormatException -> "La respuesta del servicio está incompleta o tiene un formato inválido."
                    is CaiServiceException -> "El servicio oficial rechazó la consulta."
                    else -> "No se pudo conectar con el servicio oficial."
                })
            } catch (_: org.json.JSONException) {
                if (requestRevision == revision) mutable.value = SearchState(error = "El servicio devolvió un JSON inválido.")
            }
        }
    }

    fun retry() = query(pendingFilter, pendingNearest)
}
