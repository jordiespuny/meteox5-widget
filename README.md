# Meteo X5 Widget

Widget para Android que muestra el tiempo en tiempo real de la estación XEMA **X5 · PN dels Ports**
(Servei Meteorològic de Catalunya, [meteo.cat](https://www.meteo.cat/observacions/xema/dades?codi=X5)):
precipitación (del intervalo actual y acumulada del último día cerrado), temperatura, humedad y viento.

## Estado actual

Usa `SocrataStationWeatherRepository`, que lee dos datasets abiertos del
portal de dades obertes de la Generalitat (Socrata) — no requieren la API
key oficial de Meteocat (cuyo alta tarda ~7 días en aprobarse), pero son una
solución provisional: no hay garantía de SLA ni de que se mantengan igual a
largo plazo.

- **`nzvn-apee`** — lecturas cada 30 min. Variables usadas (`codi_variable`):

  | Código | Variable | Unidad |
  |---|---|---|
  | 35 | Precipitació (del intervalo de 30 min, no acumulado diario) | mm |
  | 32 | Temperatura | °C |
  | 33 | Humitat relativa | % |
  | 30 + 31 | Velocitat i direcció del vent a 10 m | m/s, ° |

- **`7bvh-jvq2`** — agregados diarios. Variable `1300` = "Precipitació
  acumulada diària". Ojo: este dataset va con **1-2 días de retraso** (el día
  no se cierra hasta medianoche), así que el widget lo etiqueta con la fecha
  real a la que corresponde el dato en vez de asumir "ayer".

Cuando llegue la API key oficial de Meteocat, migrar a
`https://apidocs.meteocat.gencat.cat` (mismos códigos de variable) creando
una implementación `MeteocatStationWeatherRepository : StationWeatherRepository`
y sustituyéndola en `MeteoX5Widget` y `WeatherUpdateWorker`. La API key debe
guardarse en `local.properties` (no se sube a git) y exponerse vía
`BuildConfig`, nunca hardcodeada en el código fuente.

## Estructura

```
app/src/main/java/net/zoom3/meteox5widget/
├── MainActivity.kt                       Activity mínima (host de la app del widget)
├── MeteoX5Widget.kt                       AppWidgetProvider: pinta el RemoteViews del widget
├── data/
│   ├── StationWeatherData.kt              Modelo de datos
│   ├── StationWeatherRepository.kt        Interfaz de acceso a datos
│   ├── SocrataStationWeatherRepository.kt Implementación real (dades obertes, Socrata)
│   └── MockStationWeatherRepository.kt    Implementación de ejemplo (datos aleatorios)
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
