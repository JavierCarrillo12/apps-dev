package co.edu.unal.reto_9

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unal.reto_9.ui.theme.Reto9Theme

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[GpsViewModel::class.java] }
    private val permissionHistory by lazy { getPreferences(MODE_PRIVATE) }
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        model.setAccess(locationAccess())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureMapTiles(applicationContext)
        setContent {
            Reto9Theme {
                GpsScreen(
                    state = model.state.collectAsStateWithLifecycle().value,
                    search = model.search.collectAsStateWithLifecycle().value,
                    onRadiusSelected = model::selectRadius,
                    onMyLocation = ::myLocation,
                    onRetrySearch = model::searchAgain,
                    onLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                )
            }
        }
        if (locationAccess() == LocationAccess.NONE && !permissionWasRequested()) requestLocation()
    }

    override fun onResume() {
        super.onResume()
        model.setAccess(locationAccess())
    }

    private fun myLocation() {
        val access = locationAccess()
        when {
            access != LocationAccess.NONE -> model.setAccess(access, refresh = true)
            !permissionWasRequested() || ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> requestLocation()
            else -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    private fun permissionWasRequested(): Boolean = permissionHistory.getBoolean("location_requested", false)

    private fun requestLocation() {
        permissionHistory.edit().putBoolean("location_requested", true).apply()
        permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}
