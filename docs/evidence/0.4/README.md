# Prueba gráfica 0.4

Forge 47.3.0, Java 17, Linux, Xvfb y Mesa por software.
Se cargó el JAR empaquetado mediante fg.deobf como dependencia del cliente de
desarrollo; no se registraron las fuentes como mod. SnakeYAML se descubrió
como dependencia Jar-in-Jar. No equivale a una instalación Windows.

- 01: estado inicial desactivado y plataforma sin soporte para GSMTC.
- 02–03: misma frase #1, mismas coordenadas y orientación después de caminar.
  La cámara mira fuera de su posición: es correcto que el texto no esté visible.
- 04–05: activación explícita, espera de Spotify y desactivación. No se extrajo
  ningún recurso en config/skylyrics/audio y no se inició captura en Linux.
- 06: LRC local → parser → estado SYNCED, metadatos y diagnóstico LINE/LOCAL_LRC.
  Exportación del informe comprobada en disco.

client-smoke.log contiene solo las respuestas de comandos de esta prueba.
Los tests y el benchmark usan muestras/frases sintéticas; native-self-test no
captura audio. No se midió latencia acústica ni FPS ni WASAPI real.
