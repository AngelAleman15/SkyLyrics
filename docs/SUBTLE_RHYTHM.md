# Subtle Rhythm Engine — 0.4

**Desactivado por defecto.** Activación explícita: `/skylyrics rhythm on` o el
botón “Ritmo (sistema)” de configuración. `/skylyrics rhythm off` detiene la captura
inmediatamente; la apariencia vuelve gradualmente a valores neutros.

## Investigación y elección

WASAPI Loopback captura la **mezcla de un dispositivo de salida**. No equivale a
capturar exclusivamente Spotify. La API de loopback por proceso requiere Windows
build 20348+ y `ActivateAudioInterfaceAsync`; no es la misma integración y no se
ofrece una opción `SPOTIFY_PROCESS` sin implementarla.

Se evaluó JNA/COM dentro del JVM. Requiere mantener interfaces WASAPI, estructuras,
v-tables y ciclos de vida nativos dentro del proceso de Minecraft. Se eligió un
auxiliar aislado, de código fuente incluido, usando **NAudio.Wasapi/Core 2.2.1**
(MIT), dirigido a **.NET Framework 4.7.2**, disponible en Windows 10 1809+.
Se fija la rama 2.x compatible con ese runtime; la API nueva de NAudio 3 no se
mezcla con la API 2.2.1 utilizada. No se requiere Premium, OAuth ni APIs de Spotify.

Fuentes inspeccionadas:

- https://learn.microsoft.com/en-us/windows/win32/coreaudio/loopback-recording
- https://learn.microsoft.com/en-us/samples/microsoft/windows-classic-samples/applicationloopbackaudio-sample/
- https://github.com/naudio/NAudio/blob/v2.2.1/NAudio.Wasapi/WasapiLoopbackCapture.cs
- https://github.com/naudio/NAudio/blob/v2.2.1/NAudio.Wasapi/WasapiCapture.cs

## Qué se captura

Solo salida SYSTEM del dispositivo Multimedia predeterminado, no el micrófono.
Puede incluir música, Minecraft, Discord y cualquier aplicación que suene en ese
mismo dispositivo. Si Spotify usa una salida distinta del dispositivo predeterminado,
puede no estar en esa mezcla. No se afirma captura exclusiva del proceso Spotify.

El auxiliar escucha cambios del dispositivo predeterminado mediante comprobación
acotada cada segundo; reinicia la sesión al cambiar su ID o terminar la captura.
Si no hay salida disponible, reintenta cada segundo, liberando objetos previos.
Entre reinicios por fallo se aplica una espera acotada para evitar un bucle de CPU.

Los bloques PCM se pliegan a mono y pasan por un **pipe local** al worker Java.
No se guardan ni transmiten a servidores. Ocho buffers reutilizables acotan la cola;
se limpian al entregarlos y cerrar. Java reutiliza y limpia sus buffers después de
analizarlos. Solo publica `RhythmState`, sin muestras de audio.

La captura solo comienza si está habilitada y hay una sesión real de Spotify
reproduciendo. La demo y el reloj LRC manual no simulan una sesión de Spotify.
Pausar/cerrar Spotify u ocultar todas las letras detiene el auxiliar. Salir del mundo/juego también lo cierra;
stdin recibe una orden de salida y hay un watchdog de 1.5 s si no termina.
Un cierre inesperado de Minecraft cierra stdin y permite al auxiliar salir.

## Análisis y límites visuales

`RhythmAnalyzer` procesa ventanas de 1024 muestras, sin FFT por frame:

- RMS de la señal y banda aproximada de 40–180 Hz mediante dos biquads Butterworth.
- Normalización relativa adaptativa con medias y varianza temporal.
- Detección de aumentos de energía y graves sobre un umbral adaptativo.
- Calentamiento de un segundo y periodo mínimo entre eventos de 180 ms.
- Ataque 80 ms, liberación 300 ms; no se utiliza un metrónomo ni pulsos sintéticos.
- Un evento de energía **no se etiqueta como bombo** ni estima BPM.

`SubtleRhythmController` realiza solo suavizado escalar al dibujar. Escala máxima
predeterminada: **1.02×**, alrededor del centro del texto. El brillo aparente es una
mezcla hacia blanco de hasta el 8% del margen disponible de cada canal: no aumenta
la opacidad, no supera 255 ni altera el pipeline de iluminación/shaders. El efecto
es deliberadamente tenue en un blanco ya claro; no es bloom real.

No escribe coordenadas, yaw/pitch/roll, intervalos ni IDs. El fade existente se
multiplica por la escala temporal; nunca se reinicia ni se alarga. Al pausar, el
reloj de letras se congela como antes y los efectos de audio se liberan hacia neutro.
Datos de captura o análisis con más de 500 ms de antigüedad no generan nuevos
pulsos. Se alinean épocas monotónicas distintas mediante el menor retardo IPC
observado; una cola atrasada se descarta en lugar de reproducir eventos viejos.

## Configuración

En `config/skylyrics-client.toml`:

| Campo | Predeterminado / rango |
| --- | --- |
| `enableSubtleRhythm` | false; consentimiento explícito |
| `audioCaptureMode` | SYSTEM; única fuente implementada |
| `rhythmSensitivity` | 1.0 / 0.1–3 |
| `maxScalePulse` | 0.02 / 0–0.02 |
| `maxBrightnessBoost` | 0.08 / 0–0.08 |
| `attackMs`, `releaseMs` | 80 / 300 |
| `minPulseIntervalMs` | 180, mínimo 100 |
| `enableBassReaction`, `enableTransientReaction` | true |

`/skylyrics rhythm` muestra estado, fuente, frecuencia, RMS, tiempo de análisis,
edad estimada del último bloque y edad en el último dibujo. Fuera de Windows
se informa `WINDOWS_REQUIRED` cuando se prueba el servicio; no se inicia un sustituto.

## Dependencias y reconstrucción

Fuentes en `native/SkyLyrics.AudioBridge/`. El proyecto se compila para net472
(producción Windows) y net8.0 (autoprueba de decodificación sin captura en Linux).
No hace falta instalar .NET 8 para usar el mod en Windows; sí se utiliza SDK 8
para reconstruir el auxiliar desde sus fuentes.

```sh
./tools/build-audio-helper.sh
./gradlew build
```

El script requiere SDK .NET 8 y Python 3 para empaquetar, además de Java 17/Gradle.
Los recursos precompilados ya están incluidos para que `gradlew build` normal no
necesite el SDK .NET. Se incluyen EXE, configuración y dos DLL de NAudio con sus
SHA-256 en `skylyrics/audio/windows/manifest.json`. Se extraen y verifican en el
worker, **solo tras la activación**, en `config/skylyrics/audio/windows/`.
La licencia MIT de NAudio acompaña al JAR y está en `licenses/NAudio-MIT.txt`.

## Verificación y mediciones

Compilación del auxiliar para ambos targets: sin errores. `--self-test` ejecutado
en Linux net8 verifica PCM16, PCM24, float32 y NaN, **sin capturar audio**.
Tests Java: RMS conocido, banda de graves, transitorios, silencio, señal constante,
adaptación a volumen, ataque/liberación, límites 102%/8%, ausencia de escritura en
anclas/plazos y cierre seguro en un sistema sin soporte.

El benchmark offline está en `docs/evidence/0.4/dsp-benchmark.txt`: mide 10.000
ventanas equivalentes a 213.33 s de audio, CPU, tiempo por ventana y asignaciones.
Solo mide DSP Java: no prueba FPS, captura WASAPI ni coste de interoperabilidad.

**Pendiente de prueba real en Windows:** Spotify, dispositivo físico, cambio de
salida, desconexión y latencia de extremo a extremo. Los timestamps usan relojes
monotónicos alineados entre procesos; la antigüedad mostrada es una
estimación desde la captura/entrega del buffer, no una medición acústica perfecta.
No se ha prometido impacto cero ni sincronización perfecta.
