package co.edu.unal.reto_10

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
internal fun CaiScreen(
    state: CaiUiState,
    search: SearchState,
    onSelect: (Locality?) -> Unit,
    onConsult: () -> Unit,
    onNearest: () -> Unit,
    onRetry: () -> Unit,
    onMetadataRetry: () -> Unit,
    onLocation: () -> Unit,
    onDial: (String) -> Unit,
    onLocationSettings: () -> Unit
) {
    val results = search.results
    val places = results?.dataset?.places.orEmpty()
    val nearest = remember(results, state.fix) {
        if (results?.nearestMode == true && state.fix != null) closestTwo(places, state.fix.coordinates) else emptyList()
    }
    var selectedId by remember(results) { mutableStateOf<Long?>(null) }
    val selected = places.firstOrNull { it.id == selectedId }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun open(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { }
    }

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("RETO 10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("CAI Bogotá", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onLocation, enabled = state.locationStatus != LocationStatus.LOCATING) {
                        Text("Mi ubicación")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Localidad · ${state.selected?.name ?: "Todas"}", maxLines = 2)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                            DropdownMenuItem(text = { Text("Todas") }, onClick = { onSelect(null); menu = false })
                            state.localities.forEach { locality ->
                                DropdownMenuItem(text = { Text(locality.name) }, onClick = { onSelect(locality); menu = false })
                            }
                        }
                    }
                    Button(onClick = onConsult, enabled = !search.loading) { Text("Consultar") }
                }
                if (state.metadataLoading) Text("Cargando localidades…", style = MaterialTheme.typography.labelSmall)
                if (state.metadataError) {
                    TextButton(onClick = onMetadataRetry) { Text("No se cargaron las localidades · Reintentar") }
                }
                FilledTonalButton(onClick = onNearest, enabled = !search.loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Los dos CAI más cercanos")
                }
                val message = when (state.locationStatus) {
                    LocationStatus.CHECKING, LocationStatus.LOCATING -> "Obteniendo ubicación…"
                    LocationStatus.DENIED -> "Sin permiso de ubicación · puedes explorar los CAI."
                    LocationStatus.UNAVAILABLE -> "Ubicación no disponible."
                    LocationStatus.READY -> if (state.fix?.access == LocationAccess.APPROXIMATE) "Ubicación aproximada" else null
                }
                if (message != null) Text(message, style = MaterialTheme.typography.labelSmall)
                if (state.locationHint) Text("Usa Mi ubicación para calcular la cercanía.", style = MaterialTheme.typography.labelMedium)
                if (state.locationStatus == LocationStatus.UNAVAILABLE) {
                    Row {
                        TextButton(onClick = onLocation) { Text("Reintentar ubicación") }
                        TextButton(onClick = onLocationSettings) { Text("Activar ubicación") }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                CaiMap(state.fix, places, nearest, results, onSelected = { selectedId = it?.id })
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clickable { open("https://www.openstreetmap.org/copyright") },
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text("© OpenStreetMap contributors", Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 270.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    if (search.loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Consultando CAI…")
                        }
                    }
                    if (search.error != null) {
                        Text(search.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRetry) { Text("Reintentar consulta de CAI") }
                    }
                    if (results != null) {
                        Text(
                            "${places.size} CAI · Filtro aplicado · ${results.filter?.name ?: "Todas"}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (state.selected != results.filter) Text("Selección pendiente · pulsa Consultar.", style = MaterialTheme.typography.labelSmall)
                        if (places.isEmpty()) Text(
                            if (results.dataset.received == 0) "La consulta no devolvió CAI." else "No hay CAI con datos válidos."
                        )
                        if (results.dataset.discarded > 0) Text(
                            "${results.dataset.discarded} registros descartados por nombre o coordenadas inválidos.",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (results.nearestMode) {
                            Text("Distancias en línea recta · conjunto completo de Bogotá", style = MaterialTheme.typography.labelSmall)
                            if (state.fix == null) Text("Usa Mi ubicación para recalcular.")
                            nearest.forEachIndexed { index, ranked ->
                                TextButton(onClick = { selectedId = ranked.cai.id }, contentPadding = PaddingValues(0.dp)) {
                                    Text("${index + 1}. ${ranked.cai.name} · ${ranked.meters.roundToInt()} m")
                                }
                            }
                        }
                        if (selected != null) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(selected.address ?: "Dirección no publicada")
                            Text(localityName(selected, state.localities))
                            Text("Teléfono · ${selected.phone ?: "No publicado"}")
                            val distance = state.fix?.coordinates?.distanceTo(selected.coordinates)
                            Text(distance?.let { "${it.roundToInt()} m en línea recta" } ?: "Distancia disponible con Mi ubicación")
                            selected.hours?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Row {
                                TextButton(onClick = { selected.phone?.let(onDial) }, enabled = selected.phone != null) { Text("Marcar teléfono") }
                                TextButton(onClick = { selectedId = null }) { Text("Cerrar ficha") }
                            }
                        }
                        val dates = places.mapNotNull { it.cutoff }.distinct().sorted()
                        val cutoff = when (dates.size) {
                            0 -> "Fecha de corte no publicada"
                            1 -> "Corte · ${cutoffDate(dates.first())}"
                            else -> "Cortes · ${cutoffDate(dates.first())} – ${cutoffDate(dates.last())}"
                        }
                        Text(
                            "Fuente · SDSCJ / datos.gov.co · $cutoff",
                            Modifier.clickable { open("https://www.datos.gov.co/d/gh9y-xbsi") },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun localityName(cai: Cai, localities: List<Locality>): String =
    localities.firstOrNull { it.code == cai.localityCode }?.name
        ?: cai.localityCode?.let { "Localidad $it" } ?: "Localidad no publicada"

private fun cutoffDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-CO")).apply {
        timeZone = TimeZone.getTimeZone("America/Bogota")
    }.format(Date(timestamp))
