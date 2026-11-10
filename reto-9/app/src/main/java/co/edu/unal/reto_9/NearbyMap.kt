package co.edu.unal.reto_9

import android.content.Context
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal fun configureMapTiles(context: Context) {
    MapLibre.getInstance(context)
    HttpRequestUtil.setOkHttpClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "tile.openstreetmap.org") {
                chain.proceed(request.newBuilder().header("User-Agent", APP_USER_AGENT).build())
            } else {
                chain.proceed(request)
            }
        }.build()
    )
}

@Composable
internal fun NearbyMap(
    fix: LocationFix?,
    radiusKm: Int?,
    places: List<NearbyPlace>,
    onPlaceSelected: (NearbyPlace?) -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestPlaces by rememberUpdatedState(places)
    val selectPlace by rememberUpdatedState(onPlaceSelected)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    AndroidView(
        factory = { context ->
            LifecycleMapView(context, lifecycle).apply {
                val hitPadding = 14 * resources.displayMetrics.density
                getMapAsync { readyMap ->
                    map = readyMap
                    readyMap.setStyle(Style.Builder().fromJson(MAP_STYLE)) { style = it }
                    readyMap.addOnMapClickListener { coordinate ->
                        val point = readyMap.projection.toScreenLocation(coordinate)
                        val area = RectF(
                            point.x - hitPadding, point.y - hitPadding,
                            point.x + hitPadding, point.y + hitPadding
                        )
                        val id = readyMap.queryRenderedFeatures(area, "places").firstOrNull()
                            ?.getStringProperty("osm_id")
                        selectPlace(latestPlaces.firstOrNull { it.place.osmId == id })
                        id != null
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.release() }
    )

    LaunchedEffect(map, fix?.id, radiusKm) {
        val position = fix?.coordinates ?: return@LaunchedEffect
        val latitudeScale = cos(Math.toRadians(position.latitude)).coerceAtLeast(0.1)
        val zoom = 13.6 - log2((radiusKm ?: DEFAULT_RADIUS_KM) * latitudeScale)
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(position.latitude, position.longitude), zoom))
    }

    LaunchedEffect(style, fix, radiusKm, places) {
        val currentStyle = style ?: return@LaunchedEffect
        val position = fix?.coordinates
        val user = position?.let { Feature.fromGeometry(it.point()) }
        val ring = if (position != null && radiusKm != null) {
            Feature.fromGeometry(Polygon.fromLngLats(listOf(radiusRing(position, radiusKm * 1_000))))
        } else null
        currentStyle.getSourceAs<GeoJsonSource>("radius")?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(ring))
        )
        currentStyle.getSourceAs<GeoJsonSource>("user")?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(user))
        )
        currentStyle.getSourceAs<GeoJsonSource>("places")?.setGeoJson(
            FeatureCollection.fromFeatures(places.map { nearby ->
                val place = nearby.place
                Feature.fromGeometry(place.coordinates.point()).apply {
                    addStringProperty("osm_id", place.osmId)
                    addStringProperty("color", place.category.color)
                }
            })
        )
    }
}

private class LifecycleMapView(context: Context, private val lifecycle: Lifecycle) : MapView(
    context,
    MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true)
        .logoEnabled(false)
        .attributionEnabled(false)
        .rotateGesturesEnabled(false)
        .tiltGesturesEnabled(false)
        .setPrefetchZoomDelta(0)
), DefaultLifecycleObserver {
    private var released = false

    init {
        onCreate(null)
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = onStart()
    override fun onResume(owner: LifecycleOwner) = onResume()
    override fun onPause(owner: LifecycleOwner) = onPause()
    override fun onStop(owner: LifecycleOwner) = onStop()
    override fun onDestroy(owner: LifecycleOwner) = release()

    fun release() {
        if (released) return
        released = true
        lifecycle.removeObserver(this)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onPause()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStop()
        onDestroy()
    }
}

private fun Coordinates.point(): Point = Point.fromLngLat(longitude, latitude)

private fun radiusRing(center: Coordinates, radiusMeters: Int): List<Point> {
    val angularDistance = radiusMeters / EARTH_RADIUS_METERS
    val latitude = Math.toRadians(center.latitude)
    val longitude = Math.toRadians(center.longitude)
    val ring = (0 until 72).map { index ->
        val bearing = Math.toRadians(index * 5.0)
        val lat = asin(sin(latitude) * cos(angularDistance) + cos(latitude) * sin(angularDistance) * cos(bearing))
        val lon = longitude + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitude),
            cos(angularDistance) - sin(latitude) * sin(lat)
        )
        Point.fromLngLat(Math.toDegrees(lon), Math.toDegrees(lat))
    }
    return ring + ring.first()
}

private const val MAP_STYLE = """
{
  "version": 8,
  "center": [-74.08, 4.65],
  "zoom": 3,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    },
    "radius": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
    "places": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
    "user": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}}
  },
  "layers": [
    {"id": "background", "type": "background", "paint": {"background-color": "#E8EFEF"}},
    {"id": "osm", "type": "raster", "source": "osm"},
    {"id": "range-fill", "type": "fill", "source": "radius",
     "paint": {"fill-color": "#1769BF", "fill-opacity": 0.12}},
    {"id": "range-edge", "type": "line", "source": "radius",
     "paint": {"line-color": "#1769BF", "line-width": 2}},
    {"id": "places", "type": "circle", "source": "places",
     "paint": {"circle-color": ["to-color", ["get", "color"]], "circle-radius": 7,
               "circle-stroke-width": 2, "circle-stroke-color": "#FFFFFF"}},
    {"id": "user-halo", "type": "circle", "source": "user",
     "paint": {"circle-color": "#0868D0", "circle-radius": 18, "circle-opacity": 0.25}},
    {"id": "user", "type": "circle", "source": "user",
     "paint": {"circle-color": "#0868D0", "circle-radius": 9,
               "circle-stroke-width": 3, "circle-stroke-color": "#FFFFFF"}}
  ]
}
"""
