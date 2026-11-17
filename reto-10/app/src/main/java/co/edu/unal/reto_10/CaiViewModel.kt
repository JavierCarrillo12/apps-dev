package co.edu.unal.reto_10

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

internal data class LocationFix(val coordinates: Coordinates, val access: LocationAccess, val id: Long)
internal enum class LocationStatus { CHECKING, DENIED, LOCATING, UNAVAILABLE, READY }
internal data class CaiUiState(
    val localities: List<Locality> = emptyList(),
    val metadataLoading: Boolean = false,
    val metadataError: Boolean = false,
    val selected: Locality? = null,
    val locationStatus: LocationStatus = LocationStatus.CHECKING,
    val fix: LocationFix? = null,
    val locationHint: Boolean = false
)
internal class CaiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CaiRepository()
    private val searcher = CaiSearch(viewModelScope, repository)
    val search = searcher.state
    private val mutable = MutableStateFlow(CaiUiState())
    val state = mutable.asStateFlow()
    private val device = DeviceLocation(application)
    private var locationJob: Job? = null
    private var access: LocationAccess? = null
    private var fixId = 0L

    init {
        loadLocalities()
        searcher.query(null)
    }

    fun loadLocalities() {
        if (state.value.metadataLoading) return
        mutable.update { it.copy(metadataLoading = true, metadataError = false) }
        viewModelScope.launch {
            try {
                val localities = repository.localities()
                mutable.update { it.copy(localities = localities, metadataLoading = false) }
            } catch (_: IOException) {
                mutable.update { it.copy(metadataError = true, metadataLoading = false) }
            } catch (_: JSONException) {
                mutable.update { it.copy(metadataError = true, metadataLoading = false) }
            }
        }
    }

    fun select(locality: Locality?) {
        if (locality == null || locality in state.value.localities) mutable.update { it.copy(selected = locality) }
    }

    fun consult() = searcher.query(state.value.selected)
    fun retry() = searcher.retry()

    fun nearest() {
        if (state.value.fix == null || state.value.locationStatus != LocationStatus.READY) {
            mutable.update { it.copy(locationHint = true) }
            return
        }
        mutable.update { it.copy(selected = null, locationHint = false) }
        searcher.query(null, nearest = true)
    }

    fun setAccess(newAccess: LocationAccess, refresh: Boolean = false) {
        if (access == newAccess && !refresh) return
        access = newAccess
        locationJob?.cancel()
        if (newAccess == LocationAccess.NONE) {
            mutable.update { it.copy(locationStatus = LocationStatus.DENIED, fix = null) }
            return
        }
        mutable.update { it.copy(locationStatus = LocationStatus.LOCATING) }
        locationJob = viewModelScope.launch {
            val coordinates = device.find(newAccess)
            mutable.update {
                it.copy(
                    locationStatus = if (coordinates == null) LocationStatus.UNAVAILABLE else LocationStatus.READY,
                    fix = coordinates?.let { point -> LocationFix(point, newAccess, ++fixId) },
                    locationHint = false
                )
            }
        }
    }
}
