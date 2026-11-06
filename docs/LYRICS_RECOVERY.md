# Recuperación de letras — 0.4

## Auditoría

La versión 0.3 hacía una única consulta `/api/get` y exigía igualdad de título,
artista y álbum. Esto descartaba remasters, cambios de edición y colaboraciones
expresadas de otra forma. Guardaba negativos por un día y no exponía los metadatos,
el error HTTP ni las razones de rechazo en el juego. Esos negativos antiguos se
invalidan automáticamente; las letras sincronizadas y LRC locales se conservan.

## Búsqueda escalonada

1. `/api/get` con **título, artista, álbum y duración originales**, sin reemplazar
   la identidad de la canción por una variante normalizada.
2. Si falta una coincidencia o solo hay texto sin tiempos, `/api/search` estructurado
   con título/artista originales.
3. Búsqueda estructurada con título base y artista principal.
4. Búsqueda `q` con título base y artista principal, si todavía hace falta.

Se deduplican consultas e IDs. Como máximo se hacen tres búsquedas de 20 registros,
sin paginación inventada. Se detiene la búsqueda cuando hay un candidato sincronizado
seguro; una coincidencia exacta sincronizada requiere solo una petición.

La normalización trata mayúsculas, acentos y puntuación, elimina sufijos conocidos
como Remaster y versiones entre paréntesis, pero conserva subtítulos significativos
como “Sweet Dreams (Are Made of This)”. Las variantes solo sirven para recuperar
candidatos: siempre se comparan con la canción **original**.

Los controles de artista incluyen colaboradores explícitos y separadores habituales.
Un cover con otro artista se rechaza. Live, acústico, remix/mix, karaoke, instrumental,
demo, clean/explicit y ediciones extendidas no se intercambian automáticamente.
Si dos sufijos de interpretación especifican actuaciones distintas, se rechazan
incluso cuando ambos sean Live. Un remaster puede corresponder a la misma grabación.

La tolerancia de duración es ±2 segundos entre álbumes distintos; con el mismo
álbum se permiten 3–5 segundos según duración. El álbum exacto, título original,
artista y cercanía temporal mejoran la puntuación. Ediciones de álbum distintos
con puntuaciones equivalentes se devuelven como `AMBIGUOUS_MATCH`, no se elige
arbitrariamente la primera. El sistema sigue siendo conservador: ante dudas se
puede asociar un LRC a la canción detectada.

## Formatos y tiempos

Se prioriza `lyricsfile` con palabras explícitamente temporizadas; después
`syncedLyrics` LRC; también se admiten líneas de Lyricsfile cuando no hay LRC.
Se preservan `WordCue` absolutos, incluido un `endMs=null` si el proveedor no lo
especifica. El renderizador actual presenta las frases por línea: la recepción de
palabras temporizadas **no afirma un karaoke por palabra ya implementado**.

Se usa SnakeYAML 2.3 con `SafeConstructor`, sin tipos Java arbitrarios, sin aliases,
límite de profundidad y tamaño. La versión de Lyricsfile debe ser `"1.0"`.
El significado de `metadata.offset_ms` sigue abierto en la especificación: valores
no nulos distintos de cero se rechazan con diagnóstico, y se intenta LRC.

Los inicios vienen del proveedor. Si falta el fin de una línea se utiliza el
siguiente inicio o la duración conocida como intervalo de presentación, sin escribir
un tiempo inventado en el archivo. No se inventan inicios ni tiempos por palabra.
Si solo hay `plainLyrics`, el resultado es `PLAIN_ONLY`, sin generar frases con
un reloj supuesto. Los instrumentales tienen su propio estado.

## Caché y reintentos

La caché v2 conserva la lista de líneas, tiempos por palabra y diagnóstico por la
clave original título/artista/álbum/duración. TTL positivo: 30 días; negativos:
6 horas. La escritura usa un temporal y reemplazo. Los LRC asociados en
`config/skylyrics/local/` siempre tienen prioridad.

Las peticiones son secuenciales, separadas al menos 250 ms. Cada operación tiene
un máximo de tres intentos en fallos de red o 5xx, con backoff de 500/1000 ms.
429/503 respetan `Retry-After` tanto en segundos como fecha HTTP: el plazo se
guarda en disco y no se elude al recrear el servicio. Un fallo de red nunca se
cachea como ausencia de letras.

El coordinador permite dos reintentos automáticos adicionales con esperas de
20/40 segundos o el Retry-After mayor. Cambiar de canción cancela trabajo pendiente
y descarta respuestas tardías. `/skylyrics retry` fuerza una nueva búsqueda sin
borrar LRC locales ni saltarse el cooldown de LRCLIB.

## Diagnóstico

- `/skylyrics diagnostics`: canción detectada, título/artista/álbum/duración originales,
  posición, pausa, fuente, sincronización, caché, consulta enviada, HTTP y rechazos.
- `/skylyrics diagnostics on|off`: habilita/deshabilita el registro local de metadatos
  y decisiones; está desactivado por defecto. Nunca imprime el texto de las letras.
- `/skylyrics diagnostics export`: informe completo en
  `config/skylyrics/diagnostics/last-lookup.json`, escrito fuera del hilo principal.
- `/skylyrics retry`: reintento explícito.
- `/skylyrics import "archivo.lrc"`: asocia el archivo de `imports/` a la canción
  actual exacta de Spotify, conservando esa asociación al reiniciar.

Estados distinguibles: `INVALID_METADATA`, `NETWORK_ERROR`, `HTTP_ERROR`,
`MALFORMED_RESPONSE`, `RATE_LIMITED`, `NO_SAFE_MATCH`, `AMBIGUOUS_MATCH`, `PLAIN_ONLY`,
`NO_SYNCED_LYRICS`, `LOCAL_FILE_ERROR`, `NOT_FOUND`, `INSTRUMENTAL`, `SYNCED`.
`NOT_FOUND` corresponde a búsquedas sin candidatos, no a fallos de conexión.
El chat muestra hasta diez candidatos; el archivo exportado conserva todos.

## Fuentes verificadas

- https://lrclib.net/docs.md — `/api/get`, `/api/search`, cabeceras y errores.
- https://github.com/tranxuanthang/lyricsfile/blob/main/SPECIFICATION.md — formato 1.0.
- https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/ — empaquetado.

SnakeYAML 2.3 se incluye fijado mediante Forge Jar-in-Jar. Su licencia Apache 2.0
se incluye en `licenses/` y en el JAR. No se utilizan APIs privadas de Spotify ni
scraping. Los tests usan frases originales y transportes simulados explícitos;
la prueba opt-in contra LRCLIB usa los metadatos del ejemplo de su documentación.
