# Meteo X5 Widget

Widget para Android que muestra el tiempo en tiempo real de la estación XEMA **X5 · PN dels Ports**
(Servei Meteorològic de Catalunya, [meteo.cat](https://www.meteo.cat/observacions/xema/dades?codi=X5)):
precipitación (del intervalo actual y acumulada de hoy), temperatura, humedad y viento.

## Estado actual

Usa `SocrataStationWeatherRepository`, que lee el dataset abierto **`nzvn-apee`**
("Dades meteorològiques de la XEMA", lecturas cada 30 min) del portal de
dades obertes de la Generalitat (Socrata) — no requiere la API key oficial
de Meteocat (cuyo alta tarda ~7 días en aprobarse), pero es una solución
provisional: no hay garantía de SLA ni de que el dataset se mantenga igual a
largo plazo.

Variables usadas (`codi_variable`):

| Código | Variable | Unidad |
|---|---|---|
| 35 | Precipitació (del intervalo de 30 min) | mm |
| 32 | Temperatura | °C |
| 33 | Humitat relativa | % |
| 30 + 31 | Velocitat i direcció del vent a 10 m | m/s, ° |

El acumulado de "Hoy" **no** usa el producto diario certificado de Meteocat
(que se publica con 1-2 días de retraso) — se calcula sumando nosotros mismos
las lecturas de la variable 35 desde la medianoche local, así que tiene la
misma frescura (~30-60 min) que el resto de datos.

Cuando llegue la API key oficial de Meteocat, migrar a
`https://apidocs.meteocat.gencat.cat` (mismos códigos de variable) creando
una implementación `MeteocatStationWeatherRepository : StationWeatherRepository`
y sustituyéndola en `MeteoX5Widget` y `WeatherUpdateWorker`. La API key debe
guardarse en `local.properties` (no se sube a git) y exponerse vía
`BuildConfig`, nunca hardcodeada en el código fuente.

## Aviso sonoro de inicio de lluvia

Cuando la precipitación del intervalo pasa de 0 mm a más de 0 mm (respecto a
la última lectura conocida), `WeatherUpdateWorker` dispara una notificación
con un sonido propio (`res/raw/rain_start.mp3`, grabación propia de ~15s).
Solo avisa en la transición 0 → lluvia, no en cada lectura con lluvia.

Si alguna vez se cambia `rain_start.mp3` por otro archivo, hay que subir
también el `CHANNEL_ID` en `RainAlertNotifier` (p. ej. de `_v2` a `_v3`):
Android fija el sonido de un canal de notificación en el momento en que se
crea y no permite cambiarlo después, así que reutilizar el mismo ID con un
sonido distinto no tendría efecto en los móviles donde el canal ya existe.

En Android 13+ hace falta conceder el permiso de notificaciones, que se pide
la primera vez que se abre la app (`MainActivity`).

## Nunca se pintan datos inventados

`onUpdate()` no pinta ningún dato mientras espera al primero real: encola un
`WorkManager` inmediato (`WeatherUpdateWorker.requestImmediateUpdate`) y hasta
que ese trabajo no termina, el widget se queda con el placeholder ("-- mm")
del layout. Antes se pintaba un dato de ejemplo aleatorio como primer
pantallazo, pero como el sistema puede volver a llamar a `onUpdate()` en
cualquier momento (algunos launchers lo hacen al desbloquear el móvil), eso
podía dejar visible un número inventado con pinta de dato real si la
actualización real tardaba. `schedulePeriodic` también pasó de
`ExistingPeriodicWorkPolicy.UPDATE` a `KEEP`, para no reiniciar el ciclo de
30 min cada vez que `onUpdate()` se repite.

## Botón de actualizar

Android retrasa el `WorkManager` periódico por ahorro de batería, así que a
veces el widget muestra datos de hace horas (sobre todo tras desbloquear el
móvil). Para no depender solo del ciclo automático hay un pequeño icono de
recargar en la esquina superior derecha: al tocarlo, `onReceive()` recibe la
acción `ACTION_REFRESH` y encola un `requestImmediateUpdate` que trae el dato
al momento. El `PendingIntent` del botón se adjunta en `updateWidgets`, así
que queda activo en cuanto llega el primer dato real.

## Estructura

```
app/src/main/java/net/zoom3/meteox5widget/
├── MainActivity.kt                       Activity mínima (host de la app + permiso de notificaciones)
├── MeteoX5Widget.kt                       AppWidgetProvider: pinta el RemoteViews del widget
├── data/
│   ├── StationWeatherData.kt              Modelo de datos
│   ├── StationWeatherRepository.kt        Interfaz de acceso a datos
│   ├── SocrataStationWeatherRepository.kt Implementación real (dades obertes, Socrata)
│   └── RainAlertState.kt                  Recuerda la última precipitación para detectar 0 -> >0
├── notification/
│   └── RainAlertNotifier.kt               Canal + notificación con sonido de "empieza a llover"
└── work/
    └── WeatherUpdateWorker.kt             WorkManager: refresca el widget cada 30 min
```

## Abrir el proyecto

Requiere Android Studio (Koala o posterior) con JDK 17.

Este repositorio **no incluye el Gradle Wrapper** (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`)
porque se generó en un entorno sandbox sin acceso completo a `services.gradle.org`.
Al abrir el proyecto en Android Studio:

- Cuando pida sincronizar y no encuentre el wrapper, acepta usar el Gradle
  incluido en el IDE, o genera el wrapper tú mismo con `gradle wrapper --gradle-version 8.14.3`.
- Versiones usadas: Android Gradle Plugin 8.5.2, Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 24.

## Añadir el widget

Compila e instala la app, luego mantén pulsado en la pantalla de inicio →
Widgets → "Meteo X5 Widget".
