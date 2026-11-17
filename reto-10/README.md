# Reto 10 · CAI Bogotá

App Android en Kotlin, Jetpack Compose y Material 3. Permite consultar CAI por localidad, explorar sus fichas, preparar una llamada en el marcador del sistema y destacar los dos CAI más cercanos a la posición del dispositivo.

## Abrir, compilar y ejecutar

Abre `C:\Users\LENOVO\AndroidStudioProjects\Reto10CAI` en Android Studio y sincroniza Gradle. Conserva las versiones de la copia original, con SDK 37, mínimo API 24, Gradle 9.6.0, AGP 9.5.1 y Java 25 para el daemon. Ejecuta el módulo `app` con internet. El acceso a la ubicación se solicita en primer plano. Puedes consultar y explorar los CAI con el permiso denegado.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w co.edu.unal.reto_10.test/androidx.test.runner.AndroidJUnitRunner
```

Si tienes más de un dispositivo, añade `-s SERIAL` a ADB. El APK está en `app/build/outputs/apk/debug/app-debug.apk`. El identificador `co.edu.unal.reto_10` permite instalarlo junto a Reto 9.

## Servicio oficial y parámetros

El catálogo de datos.gov.co registra **Comando de Atención Inmediata. Bogotá D.C.**, identificador `gh9y-xbsi`, como un recurso federado de la Secretaría Distrital de Seguridad, Convivencia y Justicia. Los metadatos del catálogo remiten al servicio ArcGIS utilizado por la app.

- [Ficha en datos.gov.co](https://www.datos.gov.co/d/gh9y-xbsi)
- [Metadatos del catálogo](https://www.datos.gov.co/api/views/gh9y-xbsi.json)
- [Capa y dominio de localidades](https://oaiee.scj.gov.co/agc/rest/services/Tematicos_NR/EquipamientoPMSDSCJ/MapServer/22?f=pjson)
- [Endpoint de consulta](https://oaiee.scj.gov.co/agc/rest/services/Tematicos_NR/EquipamientoPMSDSCJ/MapServer/22/query)

La app construye las URL con `HttpUrl.Builder.addQueryParameter` de OkHttp. Todas usa `where=1=1`. El menú cruza el dominio con los códigos publicados mediante returnDistinctValues=true y conserva las localidades con códigos del 01 al 20 que tienen CAI. Una localidad utiliza un código validado del dominio, por ejemplo `where=CAIIULOCAL='13'`. El selector cambia la selección pendiente y **Consultar** envía el filtro al servidor. Los resultados muestran el filtro aplicado.

Cada carga obtiene primero `returnCountOnly=true`. Las páginas usan `outFields=*`, `outSR=4326`, `returnGeometry=true`, `f=json`, `orderByFields=OBJECTID ASC` y `resultRecordCount=200`. Para avanzar, añade `AND OBJECTID > último_id` al filtro. Sigue `exceededTransferLimit`, comprueba el avance y el orden y contrasta el total recibido con el conteo inicial. Un fallo de página o una discrepancia invalida toda la carga.

Procesa las respuestas con `org.json.JSONObject` y `org.json.JSONArray` del SDK Android. Comprueba HTTP y el objeto JSON `error`. Una lista `features=[]` es un vacío válido. Un JSON sin `features` es un error de formato. Los registros sin nombre o sin coordenadas WGS84 válidas se descartan y se informa su cantidad. El conteo completo incluye esos descartes.

| Campo | Uso |
| --- | --- |
| OBJECTID | Clave única y desempate |
| CAINOMBRE | Nombre de la ficha y el marcador |
| CAIDIR_SIT | Dirección o indicación de ausencia |
| CAITELEFON | Texto, conserva ceros y prepara ACTION_DIAL |
| CAIIULOCAL | Relación con el dominio de localidades |
| CAIHORARIO | Horario opcional |
| CAIFECHA_C | Milisegundos Unix, muestra la fecha de corte en Bogotá |
| CAIIDENTIF | Identificador institucional, puede repetirse |
| geometry.x / geometry.y | Longitud / latitud solicitadas en EPSG 4326 |
| CAILONGITU / CAILATITUD | Coordenadas adicionales de la publicación, la app utiliza la geometría WGS84 |

Los registros CAI Laches y CAI Mirador comparten el identificador institucional `E03C06020`. Se conservan porque sus OBJECTID son distintos.

## Cercanía y estados

**Los dos CAI más cercanos** utiliza el conjunto completo validado de Bogotá almacenado en memoria. Si falta, lo carga antes de seleccionar. Restablece el selector y el filtro aplicado a Todas. Calcula la distancia en línea recta con Haversine, ordena por distancia y OBJECTID, y toma hasta dos registros distintos. Presenta los puestos 1 y 2, destaca sus marcadores y encuadra estos puntos y la posición del usuario. Si cambia la ubicación, recalcula la selección.

Las consultas posteriores limpian el destacado. Durante carga o error se retiran los resultados anteriores. **Reintentar consulta de CAI** repite la operación fallida, **Reintentar** en localidades vuelve a obtener el dominio y **Mi ubicación** actualiza la posición. Las respuestas tardías se descartan mediante una revisión de consulta y cancelación de OkHttp. Las peticiones GET permiten la recuperación acotada de OkHttp ante conexiones reutilizadas cerradas por el servidor. Las recomposiciones no lanzan peticiones.

La red y el procesamiento JSON se ejecutan fuera del hilo principal. Los límites son 10 s de conexión, 30 s de lectura, 40 s por petición y 120 s para completar una colección paginada. Permiten margen sobre las mediciones del servicio público, manteniendo un límite finito. Logcat registra filtro, duración, HTTP, registros, descartes y tipo de error. La posición del usuario permanece fuera de los registros y de las consultas de CAI.

```powershell
adb logcat -s Reto10CAI:I Reto10Verificacion:I
```

El mapa usa MapLibre y teselas de OpenStreetMap con atribución visible, caché del motor y User-Agent propio. La fuente de CAI, su corte y el enlace al catálogo aparecen en la pantalla. El catálogo declara licencia CC BY-SA 4.0 para el conjunto. [Licencia del conjunto](https://creativecommons.org/licenses/by-sa/4.0/) · [Política de teselas](https://operations.osmfoundation.org/policies/tiles/)

## Guion de demostración

1. Abre Reto 10 y deniega la ubicación. Comprueba la consulta general y explora los marcadores.
2. Selecciona TEUSAQUILLO. Comprueba que el filtro aplicado sigue siendo Todas hasta pulsar Consultar. Ejecuta la consulta y abre un CAI.
3. Consulta nombre, dirección, localidad, teléfono, horario y fecha. Pulsa Marcar teléfono y verifica el número preparado en la aplicación del sistema.
4. Concede ubicación y simula Bogotá con `adb emu geo fix -74.08175 4.65333`. Pulsa Mi ubicación.
5. Activa Los dos CAI más cercanos desde la consulta filtrada. Comprueba Todas, el conjunto completo, los dos destacados, sus distancias y el encuadre.
6. Desactiva internet, consulta, comprueba el error, restaura la red y pulsa Reintentar consulta de CAI.
7. Comprueba que Reto 9 y Reto 10 aparecen instalados simultáneamente.

## Verificaciones

La consulta realizada desde PC devolvió 158 registros en 0,142 s. Teusaquillo devolvió 6 registros en 0,097 s. Los metadatos de la capa respondieron en 0,242 s. Estas cantidades son observaciones de la verificación y la app calcula el total en cada carga.

Las pruebas unitarias cubren selección de dos CAI, conjuntos vacíos o con un único resultado, desempates, duplicados por OBJECTID, identificadores institucionales compartidos, respuestas tardías, independencia del filtro y paginación incompleta. Las pruebas Android ejecutan las clases reales org.json para verificar el parser y consultan el servicio oficial para contrastar la colección completa y Teusaquillo.

La compilación final y las seis pruebas unitarias terminaron correctamente. En Pixel 8a se verificaron la carga general sin permiso de ubicación, el cambio pendiente del selector, la consulta de seis CAI de Teusaquillo y la apertura del marcador del mapa. La ficha de CAI Federmán mostró KR 45 58A 10, el horario publicado y el teléfono 3168045986. ACTION_DIAL abrió la aplicación del sistema con ese número preparado.

La cercanía se activó desde el filtro Teusaquillo y restableció Todas con los 158 CAI. Desde la ubicación fija de prueba mostró CAI Federmán a 462 m y CAI 7 de Agosto a 756 m, con dos marcadores destacados y el punto azul encuadrados. Al desactivar Wi‑Fi y datos se mostró el error de conexión. Al restaurarlos y pulsar Reintentar consulta de CAI se recuperaron los 158 resultados en 1,286 s. La carga inicial completa en el emulador tardó 4,765 s y la consulta filtrada exitosa 0,296 s. Hubo un fallo de conexión en el primer intento filtrado que se recuperó con el botón de reintento.

Se comprobó que los paquetes co.edu.unal.reto_9 y co.edu.unal.reto_10 estaban instalados simultáneamente. La validación visual se realizó en el emulador Pixel 8a. Queda pendiente repetirla en un teléfono físico.

La pasada final de pruebas instrumentadas devolvió OK (4 tests). Incluyó tres pruebas con org.json real y una prueba del endpoint oficial. En esa pasada, el conjunto completo respondió en 0,233 s y Teusaquillo en 0,138 s, con 158 y 6 registros válidos respectivamente y cero descartes.
