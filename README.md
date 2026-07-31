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
misma frescura (~30-60 min) que el resto de datos. De la misma consulta se
saca la lluvia de las últimas 24 h, que alimenta el índice de incendio.

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

## Nombre del viento

Junto a la dirección cardinal (NE, SO…) se muestra el nombre tradicional del
viento en Catalunya según la rosa dels vents: Tramuntana (N), Gregal (NE),
Llevant (E), Xaloc (SE), Migjorn (S), Garbí (SO), Ponent (O), Mestral (NO).
El nombre corresponde a la dirección de la que sopla el viento (la que da la
XEMA en la variable 31), igual que en la web de meteo.cat.

## Peligro meteorológico de incendio

En la esquina superior izquierda hay un cartel ("INCENDIO" + nivel) que cambia
de color según `FireRisk`: verde bajo, ámbar moderado, naranja alto, rojo
extremo. Se muestra el **nivel**, no un número: un "24/100" invitaba a leerse
como "24 % de probabilidad de incendio", que es justo lo que este índice no
es.

Usa el **índice de Angström modificado con corrección por lluvia**, adaptado a
pinar mediterráneo:

```text
I0 = HR/20 + (27 - T)/10     inflamabilidad atmosférica (menor = más seco)
W  = 1 + (V/20)^1.5          el viento acelera secado y propagación
B  = 1.0 / 1.3 / 1.8 / 2.5   según lluvia 24 h <2 / <10 / <20 / >=20 mm
R  = W / (I0 * B) * D * S
```

Clasificación: `R < 0.5` bajo · `0.5-1.2` moderado · `1.2-2.5` alto · `> 2.5`
extremo.

### D — sequedad de fondo (medida)

El instantáneo no distingue entre "hoy no ha llovido" y "no llueve desde hace
un mes", y en pinar mediterráneo eso cambia mucho las cosas: es el papel que
en el FWI cumplen DMC y DC. `D` sale de la lluvia real de 7 y 30 días
(`1.35` si el mes lleva <5 mm, hasta `0.85` si pasa de 50 mm, con alivio extra
si la lluvia fue reciente). Como los 7 días están contenidos en los 30, el
resultado se acota a `[0.85, 1.35]` para no aplicar el mismo alivio dos veces.
Sin datos de sequía, `D = 1.0` y el índice sigue funcionando.

Estas ventanas se leen del dataset **diario** (`7bvh-jvq2`, variable 1300): son
~30 registros en vez de ~1.400 lecturas semihorarias, y su retraso de 1-2 días
es irrelevante en una suma de semanas.

### S — factor estacional (calendario)

Lo que la estación aporta **más allá** de la sequía es el combustible vivo: en
verano el sotobosque entra en letargo y los pinos emiten más terpenos, y eso no
se deduce de la lluvia. Va deliberadamente suave — `0.85` en invierno a `1.15`
en julio-agosto — y no con los `1.4-1.5` que se usan cuando el calendario es el
*único* sustituto de la sequía: aquí la sequedad ya se mide aparte, y aplicar
ambos a plena escala contaría dos veces lo mismo.

Es un índice de **peligro meteorológico**, no una probabilidad de incendio:
indica cómo de favorables son las condiciones si aparece una ignición.

**Por qué no el FWI canadiense**, que es más sólido: necesita arrastrar los
códigos FFMC/DMC/DC del día anterior, lecturas al mediodía solar y semanas de
spin-up antes de que el Drought Code sea representativo. Un widget recién
instalado daría valores sin sentido durante mucho tiempo.

La lluvia que atenúa el índice es la de las **últimas 24 h** (`rain24hMm`), no
la acumulada desde medianoche: a las 00:30 esta última sería casi siempre 0
aunque hubiera llovido 30 mm la tarde anterior. Ambas ventanas se calculan de
una sola consulta.

Cuando X5 está caída, el índice se calcula con los datos de la estación de
respaldo, como el resto de valores. Solo se muestra si hay temperatura y
humedad y el dato es fresco; si no, se oculta.

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

## Botón de actualizar y caché

Android retrasa el `WorkManager` periódico por ahorro de batería, así que a
veces el widget muestra datos de hace horas (sobre todo tras desbloquear el
móvil). Para no depender solo del ciclo automático hay un pequeño icono de
recargar en la esquina superior derecha: al tocarlo, `onReceive()` recibe la
acción `ACTION_REFRESH`, muestra "Actualizando…" como feedback inmediato y
encola un `requestImmediateUpdate` que trae el dato al momento.

La última lectura pintada se guarda en `WeatherCache` (SharedPreferences).
`onUpdate()` repinta desde esa caché —con el botón ya funcional— cada vez que
el sistema lo llama (desbloqueo, reinicio, reinstalación), sin esperar a que
el worker termine. Esto arregla dos cosas: el widget ya no parpadea al
placeholder al desbloquear, y el botón queda operativo aunque el widget se
haya quedado con un dibujo antiguo (p. ej. tras actualizar la app, cuando el
`PendingIntent` del botón todavía no se había adjuntado).

## Indicador de dato viejo

La XEMA publica cada 30 min. Si la última lectura tiene más de 90 min
(`STALE_THRESHOLD_MS`) —normalmente porque la estación ha caído o porque el
portal abierto va muy rezagado— el widget lo señala: atenúa los valores
(color `widget_text_muted`) y cambia la línea de estado a
"⚠️ Sin datos nuevos desde HH:mm" en ámbar. Así se distingue de un vistazo un
dato desfasado de la fuente de un fallo de la app. El umbral deja margen de
sobra sobre la latencia normal (~30-60 min), así que en funcionamiento normal
no salta.

## Estación de respaldo

Si X5 lleva más de 90 min sin datos frescos (caída), el repositorio recurre
automáticamente a una estación cercana, **C9 · Mas de Barberans** (a ~11 km,
mismo flanco sureste del macizo dels Ports). Se eligió sobre Horta de Sant
Joan (D8, ~17 km) por cercanía y misma orientación de ladera, mejor para que
coincidan los episodios de lluvia; su contrapartida es que está bastante más
abajo (241 m vs 1056 m de X5), así que la temperatura se lee más cálida.

El respaldo es **explícito**: el widget muestra "C9 · Mas de Barberans
(respaldo)" y la línea de estado "⚠️ X5 sin datos · HH:mm" en ámbar, para que
nunca se confundan sus datos con los de X5. En cuanto X5 vuelve a publicar,
el widget regresa solo a la estación principal. La alerta de "empieza a
llover" no se dispara al cambiar de estación (`RainAlertState` recuerda de qué
estación era la última lectura y solo compara dentro de la misma).

## Estructura

```
app/src/main/java/net/zoom3/meteox5widget/
├── MainActivity.kt                       Activity mínima (host de la app + permiso de notificaciones)
├── MeteoX5Widget.kt                       AppWidgetProvider: pinta el RemoteViews del widget
├── FireRisk.kt                            Índice Angström modificado (peligro de incendio)
├── data/
│   ├── StationWeatherData.kt              Modelo de datos
│   ├── StationWeatherRepository.kt        Interfaz de acceso a datos
│   ├── SocrataStationWeatherRepository.kt Implementación real (X5 + respaldo C9, Socrata)
│   ├── RainAlertState.kt                  Recuerda última precipitación y estación (detectar 0 -> >0)
│   └── WeatherCache.kt                    Cachea la última lectura para repintar sin esperar red
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
