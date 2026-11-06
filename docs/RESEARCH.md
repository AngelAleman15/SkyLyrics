# Investigación técnica — 6 de noviembre de 2026

Esta es la investigación inicial del proyecto. La fuente, distribución y
animaciones añadidas en 0.3 están documentadas en [CINEMATIC.md](CINEMATIC.md).

Documento escrito antes de implementar. Objetivo: Forge 1.20.1, Java 17,
solo cliente. El requisito posterior del usuario impone anclas absolutas e
inmutables; reemplaza el movimiento vertical sugerido en la especificación.

## Renderizado

- MDK oficial seleccionado: Forge `1.20.1-47.3.0`, ForgeGradle 6, mappings
  oficiales 1.20.1. Gradle Wrapper 8.8 incluido en ese MDK.
- `RenderLevelStageEvent` entrega pose stack, cámara y partial tick. Se puede
  dibujar con `Font.drawInBatch`, `MultiBufferSource` y `Font.DisplayMode`
  sin registrar entidades ni enviar paquetes al servidor.
- El ancla se guarda en un valor inmutable. Cada frame transforma
  `ancla - cámara`; esa resta cambia la vista, nunca las coordenadas del ancla.
  Billboard cambia solo la rotación local. Escala y orientación fija se capturan
  al crear la frase. Solo se anima alfa.
- Modo Natural usa profundidad; Etéreo usa `SEE_THROUGH`. Cielo solo cambia
  la selección inicial. Raycast y heightmap son consultas cliente de chunks
  cargados; no se genera terreno para colocar letras.

Fuentes:
- https://docs.minecraftforge.net/en/1.20.x/gettingstarted/
- https://docs.minecraftforge.net/en/1.20.x/concepts/sides/
- https://lexxie.dev/forge/1.20.1/net/minecraftforge/client/event/RenderLevelStageEvent.html
- https://mappings.dev/1.20.1/net/minecraft/client/gui/Font.html
- https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-mdk.zip

## Spotify y Windows (fase posterior)

GSMTC es la opción preferida. Windows 10 1809+ ofrece
`GlobalSystemMediaTransportControlsSessionManager.RequestAsync()`,
`GetSessions()`, `SourceAppUserModelId`, `TryGetMediaPropertiesAsync()`,
`GetPlaybackInfo()` y `GetTimelineProperties()`. Se debe seleccionar la sesión
de Spotify, no asumir que la sesión global actual es Spotify.

`Position` corresponde a `LastUpdatedTime`, no necesariamente al instante de
la consulta. Extrapolar únicamente mientras se reproduce, con playback rate,
limitar a duración y reconciliar snapshots y seeks. Datos publicados por cada
aplicación pueden ser incompletos o tardíos: hace falta validación real con
Spotify para Windows y un ajuste manual de desfase.

Java 17 no incluye una proyección WinRT. Una implementación viable es un
auxiliar C# con Windows SDK, compilado y probado en Windows, comunicando
snapshots JSON por stdout con un único proceso, sin contraseñas ni OAuth.
Otra proyección disponible en Windows es Windows PowerShell 5.1 con
`System.Runtime.WindowsRuntime` y `System.WindowsRuntimeSystemExtensions.AsTask`.
Para evitar requerir un runtime adicional o un binario grande, se selecciona
ese auxiliar: un solo proceso hijo, script fuente incluido en el JAR,
snapshots JSON por stdout. No se modifican políticas del sistema.
No se afirma que la lectura GSMTC pueda probarse en Linux.
GSMTC no requiere Premium ni reproduce audio por su cuenta.

Spotify confirma que su API pública no ofrece letras. No se utilizarán
endpoints privados, cookies del navegador ni scraping.

Fuentes:
- https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessionmanager
- https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssession
- https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessiontimelineproperties
- https://community.spotify.com/forum/en/discussion/5355493/update-endpoint-for-lyrics
- https://developer.spotify.com/documentation/web-api/reference

## Letras (fase posterior)

LRCLIB documenta `GET https://lrclib.net/api/get` con `track_name`,
`artist_name`, `album_name` y `duration` en segundos. Usa duración con
tolerancia de ±2 segundos para evitar versiones equivocadas. La respuesta
incluye `instrumental`, `plainLyrics` y `syncedLyrics` (LRC con timestamps
de línea), que pueden ser nulos. No inferir timestamps ni tiempos por palabra.
La documentación actual menciona `lyricsfile` para tiempos por palabra:
solo habilitar karaoke tras validar datos explícitos y parser correspondiente.

Acceso anónimo disponible; exige identificar aplicación y contacto/proyecto
en User-Agent. Respetar 429/Retry-After, caché por metadatos+duración y
deduplicación. No usar un contacto o enlace ficticio para distribuir el mod.
Para consultas sin efectos de búsqueda externos existe `/api/get-cached`.

Que la API sea abierta no equivale a licencia universal de las letras. La
documentación consultada no establece una licencia general de reutilización
de todas las obras. Revisar términos/derechos vigentes antes de distribuir
letras; no incluir letras comerciales en el repositorio. La fase 1 solo usa
frases originales de prueba. `.lrc` local es una alternativa prevista.

Fuentes:
- https://lrclib.net/docs
- https://lrclib.net/docs.md (descargado y consultado en este entorno)

## Secuencia y condición para avanzar

La especificación exige: «No avanzar hasta comprobar que las frases se
renderizan correctamente». Se implementó y compiló primero fase 1. Luego
se ejecutó Minecraft con Xvfb y Mesa por software: texto visible, paralaje
al caminar, mismo ID/coordenadas al girar y caducidad fuera de pantalla.
La evidencia está en `docs/evidence`. Con ese requisito satisfecho se
continúa con las integraciones, distinguiendo compilación de verificación
real en Windows. No se sustituirá Spotify con una simulación silenciosa.
