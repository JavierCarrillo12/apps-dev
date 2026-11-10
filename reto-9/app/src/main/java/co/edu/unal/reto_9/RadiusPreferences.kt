package co.edu.unal.reto_9

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal val RADII_KM = listOf(1, 2, 3, 5)
internal const val DEFAULT_RADIUS_KM = 1

private val Context.radiusDataStore by preferencesDataStore(name = "gps_settings")
private val radiusKey = intPreferencesKey("radius_km")

internal class RadiusPreferences(context: Context) {
    private val store = context.applicationContext.radiusDataStore

    suspend fun read(): Int =
        store.data.first()[radiusKey]?.takeIf { it in RADII_KM } ?: DEFAULT_RADIUS_KM

    suspend fun save(radiusKm: Int) {
        store.edit { it[radiusKey] = radiusKm }
    }
}
