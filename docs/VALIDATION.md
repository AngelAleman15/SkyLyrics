# Validación — 6 de noviembre de 2026

## Compilación y pruebas

JDK Temurin 17.0.21, Gradle Wrapper 8.8, ForgeGradle 6.0.42,
Minecraft 1.20.1 / Forge 47.3.0, mappings oficiales.

`./gradlew build`: exitoso. Genera un JAR reobfuscado para Java 17.
15 pruebas locales y una prueba adicional opt-in contra LRCLIB: todas pasaron.
La prueba de red usa los metadatos de ejemplo publicados en la documentación de
LRCLIB; verifica letras sincronizadas y lectura idéntica desde la caché en disco.
No guarda esas letras en el repositorio.

Cobertura relevante:

- identidad de la aparición y ancla inmutable durante toda su existencia;
- consumo único de intervalos, sin recreación por ocultación/reactivación;
- fade y caducidad sin llamadas al renderizador ni dependencia de distancia;
- fades en intervalos cortos, límite sin expulsar frases y limpieza;
- pausas del reloj, correcciones/seeks dentro del mismo intervalo sin nuevo ancla;
- playback rate, límites de extrapolación y pausa de snapshots;
- LRC: varias marcas, fracciones, offset, líneas vacías, simultáneas y texto plano;
- prioridad de LRC local, claves distintas por edición y ausencia de identidad;
- LRCLIB real: API → parser → caché → lectura equivalente.

## Pruebas ejecutadas dentro de Minecraft

Cliente de desarrollo Forge 47.3.0 con Xvfb 1280×720 y Mesa por software, Linux.
Mundo individual creativo plano, fuente vanilla, sin shaders ni mods adicionales.

| Recorrido | Resultado / evidencia |
| --- | --- |
| Crear frase, caminar de costado | Visible con paralaje; mismo ID/coordenadas (`01-origin`, `02-walked`) |
| Girar 180° y volver | Sale de la vista y vuelve al mismo lugar (`03-turned-away`, `04-returned-view`) |
| Esperar fuera de la vista | Caduca; estado muestra cero frases (`05-expired`) |
| Detección fuera de Windows | Estado explícito `WINDOWS_REQUIRED`, sin demo automática (`06-platform-status`) |
| LRC local → parser → reloj → render | Frases sucesivas con IDs/anclas diferentes, estado `SYNCED` (`07`, `08`) |
| Pantalla de configuración | Abre dentro del juego, guarda opciones (`09-config-screen`) |
| Natural y Etéreo frente a una pared | Natural oculta la frase, Etéreo la muestra completa; mismo ID/coordenadas (`10-natural-wall`, `11-ethereal-wall`) |

Las capturas y el extracto del log de anclaje están en `docs/evidence/`.
Las primeras capturas corresponden al núcleo de fase 1, antes de integrar Spotify.

## No ejecutado / límites de esta entrega

- **Spotify/GSMTC en Windows**: implementado, sin sistema Windows disponible aquí.
  Falta probar sesiones reales, pausa/reanudación, cierre y cambios rápidos.
- Servidor multijugador sin el mod; no se ha afirmado una prueba de conexión real.
  No hay paquetes, registros de contenido ni dependencia requerida del servidor.
- Shaders y modpack SPMovesets Ascension - Extreme Edition del usuario.
- Medición comparativa de FPS; ejecutar en GPU por software no valida rendimiento
  con la GPU o modpack del usuario.
- Karaoke por palabra: no incluido. Los datos LRC se presentan a nivel de línea.

El cliente de desarrollo emitió avisos de autenticación de Mojang y de dispositivo
de audio ausente en el entorno headless. No impidieron entrar al mundo y verificar
el renderizado. No se probó reproducción de audio en este entorno.

## Protocolo de aceptación en la instancia del usuario

1. Instalar el JAR en Forge 1.20.1/Java 17; no agregarlo al servidor.
2. Probar `/skylyrics anchor Ancla fija`, consultar `/skylyrics status`, caminar,
   acercarse, pasar debajo, girar y alejarse: conservar ID/coordenadas.
3. Probar Natural detrás de una pared y Etéreo a través de ella.
4. Esperar el final fuera de pantalla; volver sin que se regenere.
5. Spotify Windows: verificar canción/artista/duración/progreso, pausa y seeks;
   ajustar `syncOffsetMs` si hay desfase del reproductor.
6. Probar instrumental, canción sin letras, cambios rápidos y Spotify cerrado.
7. Repetir en multijugador y con los shaders/mods reales, comparando FPS.
