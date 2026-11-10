# Reto 9 · Accediendo al GPS

App Android nativa para explorar hospitales, parques y atracciones turísticas cercanas. El punto azul señala la ubicación del dispositivo. Los marcadores de lugares muestran el nombre, la categoría y la distancia al tocarlos.

## Abrir y compilar

1. Abre la carpeta `Test` en Android Studio y sincroniza Gradle. La configuración existente usa el SDK Android 37 y Java 25 para el daemon de Gradle.
2. Selecciona el módulo `app` y ejecuta **Run** en un dispositivo o emulador con Android 7.0 (API 24) o posterior, ubicación activa e internet.
3. Ejecuta estas tareas desde la raíz del proyecto.

   ```powershell
   .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
   .\gradlew.bat :app:connectedDebugAndroidTest
   ```

   El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. La segunda orden requiere un dispositivo conectado y las dependencias de Unified Test Platform de Gradle.

   Si esas dependencias faltan al compilar sin conexión, ejecuta el mismo conjunto de pruebas instrumentadas con ADB.

   ```powershell
   .\gradlew.bat :app:assembleDebugAndroidTest
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb shell am instrument -w co.edu.unal.reto_9.test/androidx.test.runner.AndroidJUnitRunner
   ```

## Demostración en Bogotá

1. En **Extended controls → Location** del emulador, envía la ubicación del sector del Parque Metropolitano El Lago. También puedes usar `adb emu geo fix -74.08175 4.65333` con un solo emulador conectado. ADB recibe longitud antes que latitud.
2. Abre la app y concede el permiso de ubicación. Comprueba el punto azul y el círculo de **1 km** en una instalación nueva. Toca un marcador de color para ver el nombre.
3. Cambia a **2, 3 o 5 km** y comprueba que se ajustan el círculo y los resultados. Cierra la app, vuelve a abrirla y comprueba que se conserva la selección. Usa **Mi ubicación** para actualizar la posición y centrar el mapa otra vez.
4. En una instalación nueva, prueba **No permitir** y luego **Permitir ubicación**. Para verificar la recuperación de red, desactiva Wi‑Fi y datos, cambia el radio, vuelve a conectarte y pulsa **Reintentar búsqueda**.

## Tecnologías y datos

Kotlin, Jetpack Compose, Material 3, Android LocationManager, Preferences DataStore, MapLibre Native y OkHttp. El mapa muestra la atribución de OpenStreetMap, identifica la app al solicitar teselas y utiliza la caché del motor de mapas. Overpass recibe las coordenadas actuales y el radio en metros. Para áreas se usa el centro que devuelve la API y se aplica el mismo filtro de distancia que a los nodos. Las consultas canceladas se descartan y el radio se guarda localmente.

[Política de teselas de OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/) · [OpenStreetMap y su licencia](https://www.openstreetmap.org/copyright) · [Uso del servicio público de Overpass](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html)

## Verificación

Pasaron cuatro pruebas unitarias, que incluyen el límite del radio, la eliminación de duplicados y el descarte de una consulta tardía. En el Pixel 8a pasaron las dos pruebas instrumentadas, incluida la selección de un marcador con datos controlados. También se comprobó el mapa de Bogotá, la ubicación desactivada, el permiso denegado, la ubicación aproximada con un proveedor simulado, la persistencia de 5 km y la acción de reintento tras restaurar la red.

## Diagnóstico de Overpass

Las consultas registran en Logcat el radio, la duración total, el tiempo hasta las cabeceras cuando llegan, el estado HTTP y la excepción o el número de candidatos recibidos. El filtro por distancia y la eliminación de duplicados se aplican después.

```powershell
adb logcat -s Reto9Overpass:I
```

Mediciones en Bogotá (`4.65333, -74.08175`).

| Consulta | Radio | Resultado |
| --- | ---: | --- |
| Original, PC | 1 km | HTTP 200 en 21,7 s, 8 candidatos |
| Original, PC | 3 km | HTTP 200 en 56,6 s con `remark` de tiempo de ejecución agotado |
| Original, Pixel 8a | 1 y 3 km | `SocketTimeoutException` en 30,1 s, sin cabeceras HTTP |
| Rectángulo, PC, servidor `65.109.112.52` | 1 km | HTTP 200 en 27,9 s, 12 candidatos |
| Rectángulo, PC, servidor `65.109.112.52` | 3 km | HTTP 200 en 20,2 s, 147 candidatos |
| Rectángulo, PC, servidor `162.55.144.139` | 1 km | HTTP 200 en 35,6 s, 12 candidatos |
| Rectángulo, PC, servidor `162.55.144.139` | 3 km | HTTP 504 en 49,7 s en el primer intento. HTTP 200 en 32,0 s con 147 candidatos al repetir |
| Rectángulo, PC, Private.coffee | 3 km | Sin respuesta HTTP en 35 s |

El rectángulo envolvente acota el trabajo de Overpass. La app conserva el filtro circular por distancia. El servidor que resolvió Android entregó respuestas válidas después del límite anterior, por lo que la lectura pasó de 30 a 45 s y el plazo total de 35 a 55 s. La conexión conserva 10 s y la consulta solicita 20 s de ejecución al servidor. [Selección espacial en Overpass](https://dev.overpass-api.de/overpass-doc/en/full_data/index.html)

En el emulador con el APK final, la ruta normal entregó 147 candidatos a 3 km en 33,5 s y la app mostró 111 lugares. Al volver a 1 km, el primer intento agotó los 45 s de lectura. El reintento usó un túnel HTTPS temporal de pruebas hacia `65.109.112.52` y entregó 12 candidatos en 34,0 s. La app mostró 8 lugares. El marcador de **Parque Antiguo Hipodromo G.**, a 1.561 m, se abrió con 3 km y desapareció con 1 km. El túnel se retiró al terminar. [Parque en OpenStreetMap](https://www.openstreetmap.org/way/24313861)

Para repetir desde el PC, ejecuta este bloque en PowerShell.

```powershell
$boxes = @{
    1 = '(4.64433680,-74.09077295,4.66232320,-74.07272705)'
    3 = '(4.62635039,-74.10881884,4.68030961,-74.05468116)'
}
$queryFile = New-TemporaryFile
$responseFile = New-TemporaryFile
try {
    foreach ($km in 1, 3) {
        $query = '[out:json][timeout:20];(nwr{0}["amenity"="hospital"]["name"];nwr{0}["leisure"="park"]["name"];nwr{0}["tourism"="attraction"]["name"];);out center;' -f $boxes[$km]
        [IO.File]::WriteAllText($queryFile.FullName, $query)
        $metrics = curl.exe -sS --connect-timeout 10 --max-time 75 -A 'Reto9GPS/1.0 (co.edu.unal.reto_9)' --data-urlencode "data@$($queryFile.FullName)" -o $responseFile.FullName -w "HTTP=%{http_code} segundos=%{time_total} servidor=%{remote_ip}" https://overpass-api.de/api/interpreter
        $curlExit = $LASTEXITCODE
        "radio=${km}km $metrics curl_exit=$curlExit"
        if ($curlExit -eq 0 -and $metrics -match '^HTTP=200 ') {
            $response = Get-Content -Raw -LiteralPath $responseFile.FullName | ConvertFrom-Json
            if ($response.remark) { "remark=$($response.remark)" }
            else { "candidatos=$($response.elements.Count)" }
        }
    }
} finally {
    Remove-Item -LiteralPath $queryFile.FullName, $responseFile.FullName
}
```
