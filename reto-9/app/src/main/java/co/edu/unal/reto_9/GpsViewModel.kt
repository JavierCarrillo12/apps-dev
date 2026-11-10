package co.edu.unal.reto_9

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class LocationFix(val coordinates: Coordinates, val access: LocationAccess, val id: Long)
internal enum class LocationStatus { CHECKING, DENIED, LOCATING, UNAVAILABLE, READY }

internal data class GpsState(
    val radiusKm: Int? = null,
    val radiusSaveFailed: Boolean = false,
    val locationStatus: LocationStatus = LocationStatus.CHECKING,
    val fix: LocationFix? = null
)

internal class GpsViewModel(application: Application) : AndroidViewModel(application) {
    private val location = DeviceLocation(application)
    private val preferences = RadiusPreferences(application)
    private val nearby = NearbySearch(viewModelScope, OverpassPlaces())
    private val mutableState = MutableStateFlow(GpsState())
    val state = mutableState.asStateFlow()
    val search = nearby.state

    private var locationJob: Job? = null
    private var access: LocationAccess? = null
    private var fixId = 0L

    init {
        viewModelScope.launch {
            val savedRadius = try {
                preferences.read()
            } catch (_: IOException) {
                DEFAULT_RADIUS_KM
            }
            mutableState.update { it.copy(radiusKm = savedRadius) }
            searchAgain()
        }
    }

    fun setAccess(newAccess: LocationAccess, refresh: Boolean = false) {
        if (access == newAccess && !refresh) return
        access = newAccess
        locationJob?.cancel()
        nearby.clear()

        if (newAccess == LocationAccess.NONE) {
            mutableState.update { it.copy(locationStatus = LocationStatus.DENIED, fix = null) }
            return
        }

        mutableState.update {
            it.copy(locationStatus = LocationStatus.LOCATING, fix = it.fix?.takeIf { fix -> fix.access == newAccess })
        }
        locationJob = viewModelScope.launch {
            val coordinates = location.find(newAccess)
            if (coordinates == null) {
                mutableState.update { it.copy(locationStatus = LocationStatus.UNAVAILABLE, fix = null) }
                return@launch
            }
            val fix = LocationFix(coordinates, newAccess, ++fixId)
            mutableState.update { it.copy(locationStatus = LocationStatus.READY, fix = fix) }
            searchAgain()
        }
    }

    fun selectRadius(radiusKm: Int) {
        val previous = state.value
        if (radiusKm !in RADII_KM || previous.radiusKm == null) return
        if (previous.radiusKm == radiusKm && !previous.radiusSaveFailed) return

        mutableState.update { it.copy(radiusKm = radiusKm, radiusSaveFailed = false) }
        if (previous.radiusKm != radiusKm) searchAgain()
        viewModelScope.launch {
            try {
                preferences.save(radiusKm)
            } catch (_: IOException) {
                mutableState.update {
                    if (it.radiusKm == radiusKm) it.copy(radiusSaveFailed = true) else it
                }
            }
        }
    }

    fun searchAgain() {
        val current = state.value
        val radius = current.radiusKm ?: return
        val fix = current.fix ?: return
        if (current.locationStatus == LocationStatus.READY) nearby.search(fix.coordinates, radius)
    }
}
