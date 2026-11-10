package co.edu.unal.reto_9

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal enum class LocationAccess { NONE, APPROXIMATE, PRECISE }

internal fun Context.locationAccess(): LocationAccess = when {
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED -> LocationAccess.PRECISE
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED -> LocationAccess.APPROXIMATE
    else -> LocationAccess.NONE
}

internal class DeviceLocation(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    suspend fun find(access: LocationAccess): Coordinates? {
        if (access == LocationAccess.NONE || !LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = buildList {
            if (access == LocationAccess.PRECISE) add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        for (provider in providers) {
            if (provider !in manager.allProviders || !manager.isProviderEnabled(provider)) continue
            val location = withTimeoutOrNull(8_000) { currentLocation(provider) } ?: continue
            return Coordinates(location.latitude, location.longitude)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun currentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = androidx.core.os.CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, cancellation, ContextCompat.getMainExecutor(context)
                ) { continuation.resume(it) }
            } catch (_: IllegalArgumentException) {
                continuation.resume(null)
            } catch (_: SecurityException) {
                continuation.resume(null)
            }
        }
}
