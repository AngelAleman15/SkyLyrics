# Presentación cinematográfica — SkyLyrics 0.3

## Tipografía incluida

Se eligió **Cormorant Garamond**, de Christian Thalmann / The Cormorant Project
Authors. Su licencia **SIL Open Font License 1.1** permite empaquetarla con el mod.
Se conserva el archivo original sin modificar; no se necesitan fuentes de Windows.
La alternativa VANILLA sigue usando `minecraft:default`.

- Fuente: https://github.com/CatharsisFonts/Cormorant/tree/master/fonts/ttf
- Commit consultado: `016784836d2ad47bc4c62b7f504a0a5875d85aeb`.
- Archivo original: `CormorantGaramond-Regular.ttf` (variante estática Regular).
- SHA-256: `aba4d0a0205a57abc489db3c1c1531b4f4e59fbe5c596912be5a3b821a06efc0`.
- Copyright 2015 the Cormorant Project Authors (github.com/CatharsisFonts/Cormorant).
- Licencia completa en `licenses/CormorantGaramond-OFL.txt` y dentro del JAR,
  `META-INF/licenses/CormorantGaramond-OFL.txt`.

Se verificó el proveedor real `TrueTypeGlyphProviderDefinition` de Minecraft
1.20.1: admite `file`, `size`, `oversample` y `shift`; antepone `font/` al recurso.
`assets/skylyrics/font/editorial.json` usa TTF de tamaño 16 con oversample 4 y
referencia a `minecraft:default` para caracteres que la serif no contenga.
No se reemplazan las fuentes de menús, chat ni otros mods.

La presentación usa blanco cálido, sombra marrón al 18% de la opacidad del texto,
sin sombra vanilla gruesa ni fondo. Los layouts se envuelven a 180 píxeles, hasta
tres líneas; texto más largo lleva puntos suspensivos. Se almacenan como máximo
32 layouts y se invalidan al cambiar fuente o recargar recursos.

## Distribución espacial

`placementMode=CINEMATIC` selecciona primero una zona, usando pesos normalizados:
izquierda 40, derecha 40, elevado 10 y frontal 10. `sideBias=1` conserva exactamente
esas proporciones; es un multiplicador de los pesos laterales, entre 0 y 4.
Los cuatro pesos se configuran independientemente. Si todos quedan en cero, se
usa una distribución lateral simétrica segura.

`SIDES` elige solo izquierda/derecha; `RANDOM` permite azimut uniforme de 360°.
Las zonas laterales están aproximadamente a 32–64° del rumbo de creación
(40–78° en SIDES). Es correcto que una frase quede parcialmente fuera de vista.

Se evalúan hasta 16 candidatos **dentro de la zona elegida**. La visibilidad no
puede convertir todas las apariciones en frontales. Una distancia y altura
objetivo aleatorias por frase evitan seleccionar siempre el candidato más lejano
o más bajo. `heightVariation` controla la fracción utilizada del rango vertical.

Los límites completos de texto se calculan al crear: como máximo 12 bloques de
ancho y 3.5 de alto. Se rechazan candidatos que solapen esferas de tamaño real o
conos angulares de otras frases desde los ojos iniciales. Esa prevención se hace
solo al nacer: caminar después puede alinear textos que antes estaban separados.
No se mueven para corregirlo. Solo se consultan chunks ya cargados.

## Orientación y animación

`WorldAnchor` conserva posición, escala base, yaw, pitch, roll y límites. La
orientación fija mira hacia la posición de creación del observador, con una
variación pequeña; el roll predeterminado es de hasta ±5°. Todo queda almacenado
al crear la frase. Con `enableBillboarding=false` no se vuelve a consultar la
orientación de la cámara; con billboard se conserva el roll inicial.

Fade-in y fade-out usan el reloj musical existente. La entrada escala suavemente
del 95% al 100%, alrededor del centro del texto. Una pausa congela ambos efectos.
**No hay traslación vertical**: el ancla y el centro del texto permanecen fijos,
respetando el requisito fundamental de coordenadas absolutas del usuario.

Al cambiar de canción, todas las apariciones anteriores se retiran capturando su
opacidad actual y aplicando una transición de 350 ms de reloj real. Aunque el
nuevo tema esté pausado, ese retiro termina. Una corrección tardía no lo reinicia.
La limpieza del tick usa ambos relojes y no depende de que el texto se renderice.

## Actualización de configuración

Antes de registrar la configuración de Forge se migran, conservando sus valores:

| 0.2 | 0.3 |
| --- | --- |
| `billboard` | `enableBillboarding` |
| `opacity` | `textOpacity` |
| `fadeInMs` | `fadeInDuration` |
| `fadeOutMs` | `fadeOutDuration` |
| `maxPhrases` | `maxVisibleLyrics` |

Si existe la clave nueva tiene prioridad. La migración es idempotente. Los valores
nuevos siguen validados por ForgeConfigSpec; los rangos de distancia se ordenan
antes de usar y se mantiene un mínimo efectivo de seis bloques al crear.
