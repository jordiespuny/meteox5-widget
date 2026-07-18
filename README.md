# Meteo X5 Widget

Widget para Android que muestra la pluviometría de la estación XEMA **X5**
(Servei Meteorològic de Catalunya, [meteo.cat](https://www.meteo.cat/observacions/xema/dades?codi=X5)).

## Estado actual

Este proyecto es un esqueleto funcional que **usa datos de ejemplo** (`MockRainfallRepository`).
Está pendiente conectarlo a la API real porque el alta en la API de Meteocat
(`api.meteocat@gencat.cat`) tarda unos 7 días en aprobarse.

Cuando llegue la API key:

1. Confirmar en `https://apidocs.meteocat.gencat.cat` el código exacto de la
   variable "Precipitació" (recurso `/variables/mesurades/metadades`) y el
   endpoint de última lectura para la estación `X5`.
2. Guardar la API key en `local.properties` (no se sube a git) y exponerla
   vía `BuildConfig`, nunca hardcodeada en el código fuente.
3. Crear una implementación `MeteocatRainfallRepository : RainfallRepository`
   que llame a la API real y sustituirla por `MockRainfallRepository` en
   `MeteoX5Widget` y `RainfallUpdateWorker`.

## Estructura

```
app/src/main/java/net/zoom3/meteox5widget/
├── MainActivity.kt              Activity mínima (host de la app del widget)
├── MeteoX5Widget.kt              AppWidgetProvider: pinta el RemoteViews del widget
├── data/
│   ├── RainfallData.kt           Modelo de datos
│   ├── RainfallRepository.kt     Interfaz de acceso a datos
│   └── MockRainfallRepository.kt Implementación de ejemplo (datos aleatorios)
└── work/
    └── RainfallUpdateWorker.kt   WorkManager: refresca el widget cada 30 min
```

## Abrir el proyecto

Requiere Android Studio (Koala o posterior) con JDK 17.

Este repositorio **no incluye el Gradle Wrapper** (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`)
porque se generó en un entorno sandbox sin acceso completo a `services.gradle.org`.
Al abrir el proyecto en Android Studio:

- Cuando pida sincronizar y no encuentre el wrapper, acepta usar el Gradle
  incluido en el IDE, o genera el wrapper tú mismo con `gradle wrapper --gradle-version 8.14.3`.
- Versiones usadas: Android Gradle Plugin 8.5.2, Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 24.

**Nota:** este esqueleto se ha creado en un entorno sin SDK de Android instalado,
así que no se ha podido compilar ni ejecutar aquí. Verifica el build y prueba
el widget en un emulador o dispositivo real desde Android Studio.

## Añadir el widget

Compila e instala la app, luego mantén pulsado en la pantalla de inicio →
Widgets → "Meteo X5 Widget".
