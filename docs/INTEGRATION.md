# Integración con Spotify y letras

## Windows GSMTC

`SpotifyMediaService` inicia un único `powershell.exe` de Windows (ruta bajo
`SystemRoot/System32/WindowsPowerShell/v1.0/`). El script fuente está incluido en
`src/main/resources/skylyrics/spotify-gsmtc.ps1`. Se transmite con
`-NoLogo -NoProfile -NonInteractive -EncodedCommand` (UTF-16LE/base64), sin crear
archivos ejecutables, modificar políticas del sistema ni solicitar elevación.
El contenido codificado es exactamente el script incluido, no datos del usuario.

Usa la proyección WinRT de Windows PowerShell 5.1:

- Solicita `GlobalSystemMediaTransportControlsSessionManager`.
- Filtra `GetSessions()` por `SourceAppUserModelId` que contenga Spotify.
- Prefiere una sesión reproduciendo frente a una pausada.
- Consulta metadatos, estado y timeline cada 500 ms desde el proceso auxiliar.
- `Position` se extrapola desde `LastUpdatedTime` únicamente durante reproducción,
  aplicando playback rate y limitando a la duración informada.
- Entrega snapshots JSON por stdout; Java compensa hasta 2 s de latencia de
  transporte y utiliza `System.nanoTime()` para extrapolar después.
- Sin snapshots frescos por 2 s deja de extrapolar y termina la aparición actual.
- Cada espera WinRT tiene un timeout de 5 s. En errores publica un estado explícito.

No usa OAuth, credenciales de Spotify ni control de audio. No accede a sesiones de
navegador para extraer cookies. Windows 10 1809+ es el mínimo de la API; la
publicación de metadatos y timeline depende también del reproductor de escritorio.
Si el auxiliar no está disponible, `/skylyrics status` lo informa y se puede usar
LRC local. No hay fallback silencioso a un timeline simulado.

**Pendiente de validación real en Windows.** Este entorno de desarrollo es Linux;
la compilación del mod y la inspección del script no equivalen a verificar WinRT
con Spotify. Deben probarse reproducción, pausa, reanudación, cierre y seeks en el
reproductor real antes de considerar esta integración estable.

## LRCLIB

Se consulta `/api/get` con los metadatos originales y después `/api/search` con
variantes acotadas del título. La comparación conserva controles de artista,
colaboradores, versión, álbum y duración. Los candidatos dudosos se rechazan;
los errores de conexión y las letras sin tiempos tienen estados propios.
Los detalles, límites, diagnóstico y formatos están en
[LYRICS_RECOVERY.md](LYRICS_RECOVERY.md).

`User-Agent`: `SkyLyrics/0.4.0 (https://github.com/AngelAleman15/SkyLyrics)`.
`lrclibIdentity` permite modificar la identificación; vacío impide consultas online
nuevas, conservando archivos locales y caché. Se utiliza la URL real del proyecto.

HTTP y disco se ejecutan en un worker. Los resultados llevan revisión y clave de
canción para descartar respuestas tardías. No se consulta cada tick. La caché v2
guarda sincronizados durante 30 días y negativos durante 6 horas; los fallos de
red no se guardan como ausencia de letras. Los LRC asociados localmente tienen
prioridad. Hay reintentos acotados y cooldown persistente que respeta Retry-After.

Solo se utilizan endpoints públicos documentados: https://lrclib.net/docs.md.
El repositorio contiene frases de prueba originales, sin redistribuir letras
comerciales. El formato Lyricsfile conserva tiempos por palabra cuando existen;
el renderizado sigue mostrando frases por línea.

## Sincronización y anclas

`PlaybackSnapshot` describe reproducción; `MusicLyricsController` selecciona el
intervalo por búsqueda binaria. `PresentationClock` congela el intervalo mientras
Spotify está pausado. Las correcciones dentro del mismo intervalo ajustan solo
el plazo de la misma aparición, conservando su objeto, ID y `WorldAnchor` final.

Al pasar a otro intervalo termina el anterior y nace una aparición nueva, elegida
una vez alrededor del jugador. Un seek a otro intervalo permite esa nueva
aparición musical; caminar, girar o perder visibilidad no lo permite. Un intervalo
consumido con la visualización desactivada no se vuelve a crear al activarla.

`syncOffsetMs` positivo adelanta la visualización respecto a la posición reportada.
El offset `[offset:ms]` del propio LRC se aplica restándolo de sus timestamps.
No se inventan tiempos por palabra: la versión actual presenta líneas completas,
incluso si un LRC mejorado contiene marcadores por palabra.
