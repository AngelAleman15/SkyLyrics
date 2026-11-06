# SkyLyrics

Mod **solo cliente** para Minecraft **1.20.1**, Forge **47.x** y **Java 17**.
Muestra letras sincronizadas como texto flotante anclado al mundo, sin entidades
ni instalación en el servidor.

![LRC local dentro de Minecraft](docs/evidence/07-local-lrc-first.png)

**Estado: 0.2.0 experimental.** Renderizado y LRC local comprobados en un cliente
Minecraft real bajo Linux. LRCLIB comprobado mediante su API real. La integración
GSMTC está implementada, pero **todavía no se probó con Spotify en Windows**.
No se afirma compatibilidad verificada con shaders ni con modpacks concretos.

## Instalar y usar

1. Crear una instancia Forge 1.20.1 con Java 17. Compilado contra Forge 47.3.0.
2. Copiar `build/libs/skylyrics-1.20.1-0.2.0.jar` a su carpeta `mods/`.
3. En Windows 10 1809+ / Windows 11, abrir **Spotify de escritorio** y reproducir
   una canción. SkyLyrics consulta su sesión GSMTC al entrar al mundo.
4. Ejecutar `/skylyrics status` si no aparecen letras. **L** activa/desactiva la
   visualización; el atajo se puede cambiar en Controles.
5. `/skylyrics config` o el botón Config de la lista de mods abre los ajustes.

No reproduce ni descarga audio, no usa la API privada de Spotify, no solicita
contraseñas y no necesita Premium. Para letras online necesita Internet y que
LRCLIB tenga una versión sincronizada que coincida con los metadatos y duración.
Anuncios, podcasts y canciones sin duración/letras válidas pueden no mostrar texto.

El binario compilado también está incluido en `dist/`, con su SHA-256 en
`dist/SHA256SUMS`, para descargarlo directamente desde este repositorio.

## Anclaje absoluto

Cada aparición recibe un `WorldAnchor(x, y, z, escala, orientación)` inmutable.
El posicionador se invoca una sola vez desde el tick de creación. El renderizador
solo calcula `ancla - cámara`, que es una transformación de vista.

- Acercarse, alejarse, girar o pasar debajo cambia la perspectiva, nunca el ancla.
- Billboard rota los glifos alrededor del ancla sin desplazarla.
- Escala y coordenadas no se adaptan al movimiento posterior del jugador.
- Fade-in/fade-out modifican solo la opacidad. No hay movimiento vertical.
- No se recrea una frase para mantenerla visible. Ocultarla y reactivarla no la
  vuelve a generar. Si no se puede colocar al nacer, se omite ese intervalo.
- La caducidad se procesa aunque el texto esté fuera de pantalla o lejos.
- La pausa congela el intervalo musical. Las correcciones temporales conservan
  el mismo objeto, ID y ancla. Un seek a otro intervalo es un nuevo evento musical.
- Si se llena el límite, se omite la frase nueva sin expulsar las anteriores.
- Desconectarse/cambiar de mundo o dimensión libera las apariciones y servicios.

## Letras locales `.lrc`

Copiar archivos UTF-8 a `config/skylyrics/imports/` (crear la carpeta si hace falta).
El ejemplo original `examples/paisaje.lrc` permite probar sin Spotify ni red:

```text
/skylyrics lrc paisaje.lrc 36
```

El número es la duración en segundos. Este comando inicia un **reloj manual**;
no reproduce audio ni simula una detección de Spotify. Para acompañar música
externa hay que iniciarla manualmente y ajustar el desfase si corresponde.

Para asignar un archivo a la canción real detectada en Spotify:

```text
/skylyrics import "mi cancion.lrc"
```

Se guarda por título, artista, álbum y duración, y tiene prioridad sobre la caché
online. Los timestamps LRC gobiernan cada intervalo; una línea vacía con timestamp
marca silencio. Se admiten varios timestamps por línea, fracciones y `[offset:ms]`.
El fin de la última línea usa la duración conocida. No se inventan tiempos para
letras sin timestamps. Los tiempos por palabra se descartan para presentación de
línea: **esta versión no ofrece karaoke por palabra**.

## Comandos cliente

No requieren operador ni mod en el servidor.

| Comando | Función |
| --- | --- |
| `/skylyrics status` | Estado de medios/letras e IDs/coordenadas activas |
| `/skylyrics spotify` | Volver a Spotify o reintentar la detección/consulta |
| `/skylyrics lrc <archivo> <segundos>` | Reproducir un timeline LRC local manual |
| `/skylyrics import <archivo>` | Asociar LRC a la canción actual de Spotify |
| `/skylyrics config` | Abrir configuración |
| `/skylyrics demo` | Demo explícita de 36 s con frases originales |
| `/skylyrics anchor <texto>` | Frase de prueba de 30 s, en un punto fijo |
| `/skylyrics stop` | Detener y limpiar; Spotify se retoma con su comando |

Estados útiles: `WINDOWS_REQUIRED` (GSMTC no disponible en este sistema),
`NO_SESSION`, `BRIDGE_UNAVAILABLE`/`BRIDGE_EXITED`, `STALE_MEDIA`, `SYNCED`,
`INSTRUMENTAL`, `NO_SYNCED_LYRICS`, `NOT_FOUND`, `CONFIGURE_IDENTITY`, `RATE_LIMITED`.
Las letras locales funcionan también fuera de Windows. En error no aparece una
simulación automática. Los detalles del mecanismo Windows están en
[docs/INTEGRATION.md](docs/INTEGRATION.md).

## Configuración persistente

Archivo: `config/skylyrics-client.toml`. Para editar todos los campos, cerrar el
juego; la pantalla interna ofrece los ajustes principales.

| Campo | Valor inicial / significado |
| --- | --- |
| `enabled`, `spotify`, `billboard` | `true` |
| `lrclibIdentity` | `https://github.com/AngelAleman15/SkyLyrics` (URL real y configurable) |
| `syncOffsetMs` | 0; positivo adelanta las letras, rango ±10 s |
| `visibility` | `SKY`: prioriza altura al nacer, con profundidad |
| `style` | `CINEMATIC`; `AMBIENT` reduce tamaño |
| `minDistance`, `maxDistance` | 12–45 bloques horizontales |
| `minHeight`, `maxHeight` | 5–25 bloques sobre los ojos; se eleva sobre copas/superficie si hace falta |
| `textScale` | 0.14 bloques por píxel a 24 bloques, calculada solo al crear |
| `opacity`, `color` | 0.92, 16773596 (`#FFF1DC`) |
| `fadeInMs`, `fadeOutMs` | 550 / 900 ms; dentro del intervalo |
| `maxPhrases` | 4 |

`NATURAL` deja que los bloques oculten el texto; `ETHEREAL` usa texto sin prueba
visible de profundidad. `SKY` cambia únicamente la selección inicial del punto.
No se alteran cielo, shaders, iluminación del mundo ni generación de terreno.
No se incluye un efecto adicional de brillo.

## Compilar

Con JDK 17 en `JAVA_HOME`:

```sh
./gradlew build
./gradlew runClient
```

En Windows: `gradlew.bat build`. ForgeGradle **6.0.42**, Forge **47.3.0**,
Gradle Wrapper **8.8**, mappings oficiales **1.20.1**. La primera ejecución
necesita red para resolver dependencias. El JAR reobfuscado se genera en
`build/libs/skylyrics-1.20.1-0.2.0.jar`.

Las pruebas predeterminadas son locales. La prueba adicional contra LRCLIB es
opt-in: ejecutar `test --rerun` con `SKYLYRICS_LIVE_TESTS=true` en el entorno.
No ejecutar esa prueba sin red disponible. Ver [validación](docs/VALIDATION.md).

## Arquitectura y rendimiento

- `SpotifyMediaService`: un proceso Windows PowerShell y un lector en background.
- `LyricsProvider` / `LrclibProvider`: consulta exacta y control de 429/Retry-After.
- `LyricsSession` / `LyricsCache`: un worker para HTTP/disco, cancelación y revisión
  por solicitud para que resultados de canciones anteriores no aparezcan.
- `LrcParser`: líneas con tiempos reales; nunca temporiza texto plano.
- `MusicLyricsController` / `PresentationClock`: reproducción, pausas, seeks y plazos.
- `LyricsScene` / `WorldAnchor` / `WorldPhrase`: escena acotada y posiciones finales.
- `LyricsPlacementManager`: consulta inicial de chunks ya cargados y raycast acotado.
- `LyricsWorldRenderer`: evento `AFTER_PARTICLES`, fuente vanilla, buffer reutilizado.
- `AnimationController` / `SkyLyricsConfig`: alfa suave y configuración cliente.

No hay solicitudes HTTP ni lectura de disco en el renderizador. Los servicios se
cierran al desconectarse o salir del juego. No se han medido FPS con el modpack del
usuario. Investigación previa y fuentes: [docs/RESEARCH.md](docs/RESEARCH.md).

El código propio conserva todos los derechos reservados; las licencias de los
archivos del MDK y componentes externos se conservan en `licenses/`.
