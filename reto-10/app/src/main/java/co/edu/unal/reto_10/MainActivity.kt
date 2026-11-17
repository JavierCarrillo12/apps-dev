package co.edu.unal.reto_10

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unal.reto_10.ui.theme.Reto10Theme

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[CaiViewModel::class.java] }
    private val history by lazy { getPreferences(MODE_PRIVATE) }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.setAccess(locationAccess())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureMapTiles(applicationContext)
        setContent {
            Reto10Theme {
                CaiScreen(
                    model.state.collectAsStateWithLifecycle().value,
                    model.search.collectAsStateWithLifecycle().value,
                    model::select, model::consult, model::nearest, model::retry,
                    model::loadLocalities, ::myLocation, ::dial,
                    { launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                )
            }
        }
        if (locationAccess() == LocationAccess.NONE && !history.getBoolean("requested", false)) requestLocation()
    }

    override fun onResume() {
        super.onResume()
        model.setAccess(locationAccess())
    }

    private fun myLocation() {
        if (locationAccess() != LocationAccess.NONE) {
            model.setAccess(locationAccess(), refresh = true)
        } else if (!history.getBoolean("requested", false) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestLocation()
        } else {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun requestLocation() {
        history.edit().putBoolean("requested", true).apply()
        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun dial(phone: String) {
        launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No hay una aplicación compatible para esta acción.", Toast.LENGTH_LONG).show()
        }
    }
}
