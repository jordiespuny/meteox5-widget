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
con un sonido propio (`res/raw/rain_start.wav`, tres "gotas" sintetizadas).
Solo avisa en la transición 0 → lluvia, no en cada lectura con lluvia.

En Android 13+ hace falta conceder el permiso de notificaciones, que se pide
la primera vez que se abre la app (`MainActivity`).

## Estructura

```
app/src/main/java/net/zoom3/meteox5widget/
├── MainActivity.kt                       Activity mínima (host de la app + permiso de notificaciones)
├── MeteoX5Widget.kt                       AppWidgetProvider: pinta el RemoteViews del widget
├── data/
│   ├── StationWeatherData.kt              Modelo de datos
│   ├── StationWeatherRepository.kt        Interfaz de acceso a datos
│   ├── SocrataStationWeatherRepository.kt Implementación real (dades obertes, Socrata)
│   ├── MockStationWeatherRepository.kt    Implementación de ejemplo (datos aleatorios)
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
