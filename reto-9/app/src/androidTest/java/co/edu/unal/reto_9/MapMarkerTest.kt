package co.edu.unal.reto_9

import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.unal.reto_9.ui.theme.Reto9Theme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapMarkerTest {
    @Test
    fun tappingAPlaceShowsItsName() {
        val origin = Coordinates(4.65333, -74.08175)
        val park = Place("way/1", "Parque de prueba", PlaceCategory.PARK, origin.copy(latitude = 4.656))
        val fix = LocationFix(origin, LocationAccess.PRECISE, 1)
        val places = nearbyPlaces(listOf(park), origin, 1_000)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                configureMapTiles(activity.applicationContext)
                activity.setContent {
                    Reto9Theme {
                        GpsScreen(
                            state = GpsState(radiusKm = 1, locationStatus = LocationStatus.READY, fix = fix),
                            search = SearchState.Results(places),
                            onRadiusSelected = {},
                            onMyLocation = {},
                            onRetrySearch = {},
                            onLocationSettings = {}
                        )
                    }
                }
            }

            var view: MapView? = null
            var map: MapLibreMap? = null
            var markerPoint: PointF? = null
            await("El marcador no apareció en el mapa") {
                scenario.onActivity { activity ->
                    if (view == null) {
                        view = findMapView(activity.window.decorView)
                        view?.getMapAsync { map = it }
                    }
                    val readyMap = map ?: return@onActivity
                    val point = readyMap.projection.toScreenLocation(
                        LatLng(park.coordinates.latitude, park.coordinates.longitude)
                    )
                    if (readyMap.queryRenderedFeatures(point, "places").isNotEmpty()) {
                        markerPoint = point
                    }
                }
                markerPoint != null
            }

            await("La pantalla de prueba no quedó activa") {
                containsText(automation.rootInActiveWindow, "Lugares cercanos")
            }

            scenario.onActivity {
                val mapView = checkNotNull(view)
                val point = checkNotNull(markerPoint)
                val downTime = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        assertTrue(mapView.dispatchTouchEvent(event))
                    } finally {
                        event.recycle()
                    }
                }
            }

            await("El nombre del marcador no apareció al tocarlo") {
                containsText(automation.rootInActiveWindow, park.name)
            }
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    private fun containsText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.text?.contains(text) == true) return true
        return (0 until node.childCount).any { containsText(node.getChild(it), text) }
    }

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findMapView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
