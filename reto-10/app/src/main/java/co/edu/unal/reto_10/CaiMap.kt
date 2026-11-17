package co.edu.unal.reto_10

import android.content.Context
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal fun configureMapTiles(context: Context) {
    MapLibre.getInstance(context)
    HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", APP_USER_AGENT).build())
    }.build())
}

@Composable
internal fun CaiMap(
    fix: LocationFix?,
    places: List<Cai>,
    nearest: List<RankedCai>,
    results: CaiResults?,
    onSelected: (Cai?) -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestPlaces by rememberUpdatedState(places)
    val select by rememberUpdatedState(onSelected)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var view by remember { mutableStateOf<MapView?>(null) }
    AndroidView(
        factory = { context ->
            LifecycleMapView(context, lifecycle).apply {
                view = this
                val tolerance = 12 * resources.displayMetrics.density
                getMapAsync { ready ->
                    map = ready
                    ready.setStyle(Style.Builder().fromJson(MAP_STYLE)) { style = it }
                    ready.addOnMapClickListener { coordinates ->
                        val point = ready.projection.toScreenLocation(coordinates)
                        val area = RectF(point.x - tolerance, point.y - tolerance, point.x + tolerance, point.y + tolerance)
                        val features = ready.queryRenderedFeatures(area, "places")
                        val selected = features.mapNotNull { feature ->
                            val id = feature.getStringProperty("object_id")?.toLongOrNull()
                            latestPlaces.firstOrNull { it.id == id }
                        }.minByOrNull { cai ->
                            val projected = ready.projection.toScreenLocation(cai.coordinates.latLng())
                            (projected.x - point.x) * (projected.x - point.x) +
                                (projected.y - point.y) * (projected.y - point.y)
                        }
                        select(selected)
                        selected != null
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.release() }
    )

    LaunchedEffect(style, places, fix, nearest) {
        val current = style ?: return@LaunchedEffect
        val highlights = nearest.map { it.cai.id }.toSet()
        current.getSourceAs<GeoJsonSource>("places")?.setGeoJson(
            FeatureCollection.fromFeatures(places.map { cai ->
                Feature.fromGeometry(cai.coordinates.point()).apply {
                    addStringProperty("object_id", cai.id.toString())
                    addStringProperty("color", if (cai.id in highlights) "#CB6510" else "#087054")
                    addNumberProperty("radius", if (cai.id in highlights) 11 else 7)
                }
            })
        )
        current.getSourceAs<GeoJsonSource>("user")?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(fix?.coordinates?.let { Feature.fromGeometry(it.point()) }))
        )
    }

    LaunchedEffect(map, fix?.id, results, nearest) {
        val ready = map ?: return@LaunchedEffect
        val points = when {
            results?.nearestMode == true && fix != null -> nearest.map { it.cai.coordinates } + fix.coordinates
            fix != null && results?.filter == null -> listOf(fix.coordinates)
            else -> places.map { it.coordinates }
        }
        val mapView = view ?: return@LaunchedEffect
        mapView.post {
            if (points.distinct().size >= 2) {
                val bounds = LatLngBounds.Builder().includes(points.map { it.latLng() }).build()
                val margin = (32 * mapView.resources.displayMetrics.density).toInt()
                ready.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, margin))
            } else {
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(points.firstOrNull()?.latLng() ?: LatLng(4.65, -74.08), 12.5))
            }
        }
    }
}

private class LifecycleMapView(context: Context, private val lifecycle: Lifecycle) : MapView(
    context, MapLibreMapOptions.createFromAttributes(context).textureMode(true).logoEnabled(false)
        .attributionEnabled(false).rotateGesturesEnabled(false).tiltGesturesEnabled(false).setPrefetchZoomDelta(0)
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
private fun Coordinates.point() = Point.fromLngLat(longitude, latitude)
private fun Coordinates.latLng() = LatLng(latitude, longitude)

private const val MAP_STYLE = """
{
  "version": 8, "center": [-74.08, 4.65], "zoom": 11,
  "sources": {
    "osm": {"type": "raster", "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
            "tileSize": 256, "maxzoom": 19, "attribution": "© OpenStreetMap contributors"},
    "places": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
    "user": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}}
  },
  "layers": [
    {"id": "background", "type": "background", "paint": {"background-color": "#E8EFEF"}},
    {"id": "osm", "type": "raster", "source": "osm"},
    {"id": "places", "type": "circle", "source": "places",
     "paint": {"circle-color": ["to-color", ["get", "color"]], "circle-radius": ["get", "radius"],
               "circle-stroke-width": 2, "circle-stroke-color": "#FFFFFF"}},
    {"id": "user-halo", "type": "circle", "source": "user",
     "paint": {"circle-color": "#0868D0", "circle-radius": 18, "circle-opacity": 0.25}},
    {"id": "user", "type": "circle", "source": "user",
     "paint": {"circle-color": "#0868D0", "circle-radius": 9,
               "circle-stroke-width": 3, "circle-stroke-color": "#FFFFFF"}}
  ]
}
"""
