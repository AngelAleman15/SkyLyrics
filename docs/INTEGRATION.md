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

Se usa solo el endpoint documentado `GET https://lrclib.net/api/get` con título,
artista, álbum y duración. No se usa búsqueda aproximada. Se verifican también los
metadatos de la respuesta y una tolerancia de duración de ±2 s; un resultado dudoso
se rechaza. Esto puede omitir versiones válidas si sus metadatos difieren, pero
reduce el riesgo de mostrar letras de otra edición.

`User-Agent`: `SkyLyrics/0.3.0 (https://github.com/AngelAleman15/SkyLyrics)`.
El repositorio fue verificado como existente y público antes de configurar esta
URL. `lrclibIdentity` permite modificarla; vacío impide consultas online nuevas,
aunque conserva el acceso a archivos locales y caché. No hay una URL ficticia ni
un correo incorporado.

Un worker realiza HTTP/disco. Cada cambio cancela el trabajo anterior y etiqueta
los resultados por revisión y clave de canción. Una respuesta tardía puede quedar
en su caché correcta, pero nunca se aplica a la canción nueva. Se realizan consultas
solo al cambiar de clave o por un reintento explícito; no se consulta cada tick.
429 respeta `Retry-After`, con cooldown compartido entre sesiones del mismo cliente.

Caché en `config/skylyrics/cache/`, clave SHA-256 de título/artista/álbum/duración.
Resultados sincronizados duran 30 días; negativos e instrumentales, un día.
Un fallo de red no se guarda como ausencia definitiva de letras. El guardado usa
un archivo temporal y reemplazo. Letras locales asociadas en `config/skylyrics/local/`
tienen prioridad sobre la caché y no se envían a ningún servidor.

La API pública no acredita por sí misma derechos universales sobre cada letra.
El repositorio solo contiene frases de prueba originales; no se redistribuyen
letras comerciales. Fuente de contrato y requisitos: https://lrclib.net/docs.md.

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
