package co.edu.unal.reto_9

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun GpsScreen(
    state: GpsState,
    search: SearchState,
    onRadiusSelected: (Int) -> Unit,
    onMyLocation: () -> Unit,
    onRetrySearch: () -> Unit,
    onLocationSettings: () -> Unit
) {
    val places = (search as? SearchState.Results)?.places.orEmpty()
    var selected by remember(state.fix?.id, state.radiusKm, places) { mutableStateOf<NearbyPlace?>(null) }

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "RETO 9 · ACCEDIENDO AL GPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Lugares cercanos",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onMyLocation,
                        enabled = state.locationStatus != LocationStatus.LOCATING
                    ) { Text("Mi ubicación") }
                }
                Text("Radio de búsqueda", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RADII_KM.forEach { radius ->
                        FilterChip(
                            selected = state.radiusKm == radius,
                            onClick = { onRadiusSelected(radius) },
                            enabled = state.radiusKm != null,
                            label = { Text("$radius km") }
                        )
                    }
                }
                if (state.radiusSaveFailed) {
                    TextButton(onClick = { state.radiusKm?.let(onRadiusSelected) }) {
                        Text("No se guardó el radio · Reintentar")
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                NearbyMap(state.fix, state.radiusKm, places, onPlaceSelected = { selected = it })
                MapAttribution(Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val place = selected
                    if (place != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(place.place.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${place.place.category.label} · ${place.distanceMeters.roundToInt()} m",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            TextButton(onClick = { selected = null }) { Text("Cerrar") }
                        }
                    } else {
                        ScreenStatus(state, search, onMyLocation, onRetrySearch, onLocationSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenStatus(
    state: GpsState,
    search: SearchState,
    onMyLocation: () -> Unit,
    onRetrySearch: () -> Unit,
    onLocationSettings: () -> Unit
) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state.locationStatus) {
            LocationStatus.CHECKING, LocationStatus.LOCATING -> StatusLoading("Obteniendo tu ubicación…")
            LocationStatus.DENIED -> {
                Text("Permiso de ubicación denegado", style = MaterialTheme.typography.titleSmall)
                Text("Concede acceso para ver lugares cerca de ti.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onMyLocation) { Text("Permitir ubicación") }
            }
            LocationStatus.UNAVAILABLE -> {
                Text("Ubicación no disponible", style = MaterialTheme.typography.titleSmall)
                Text("Activa la ubicación del dispositivo e inténtalo de nuevo.", style = MaterialTheme.typography.bodyMedium)
                FlowRow {
                    TextButton(onClick = onMyLocation) { Text("Reintentar") }
                    TextButton(onClick = onLocationSettings) { Text("Ajustes de ubicación") }
                }
            }
            LocationStatus.READY -> {
                if (state.fix?.access == LocationAccess.APPROXIMATE) {
                    Text("Usando ubicación aproximada", style = MaterialTheme.typography.labelMedium)
                }
                when (search) {
                    SearchState.Idle, SearchState.Loading -> StatusLoading("Buscando lugares cercanos…")
                    SearchState.Failed -> {
                        Text("No pudimos consultar los lugares", style = MaterialTheme.typography.titleSmall)
                        Text("Revisa tu conexión o vuelve a intentar.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRetrySearch) { Text("Reintentar búsqueda") }
                    }
                    is SearchState.Results -> {
                        val count = search.places.size
                        val label = when (count) {
                            0 -> "No se encontraron lugares en este radio"
                            1 -> "1 lugar dentro de ${state.radiusKm} km"
                            else -> "$count lugares dentro de ${state.radiusKm} km"
                        }
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        if (count > 0) {
                            FlowRow(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PlaceCategory.entries.forEach { category ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(Modifier.size(8.dp).background(mapColor(category.color), CircleShape))
                                        Text(category.label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLoading(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        "© OpenStreetMap contributors",
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Color.White)
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
                } catch (_: ActivityNotFoundException) {
                    // The attribution text remains visible on devices without a browser.
                }
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF253246),
        textDecoration = TextDecoration.Underline
    )
}

private fun mapColor(hex: String) = Color(android.graphics.Color.parseColor(hex))
