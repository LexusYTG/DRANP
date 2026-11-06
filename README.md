# Legales: si eres el dueño de los derechos de autor de este juego y no quieres que este publico este port, porfavor, abre un issue y retirare el port, no quiero problemas legales y esto solo lo estoy haciendo por cariño al juego 

# DRANP: Diamond Rush Android Native Port

Port nativo a Android de **Diamond Rush** (Gameloft, 2006, Java ME / J2ME).
No es un emulador: el juego corre como una app Android normal, con una capa
propia que reemplaza la API `javax.microedition.*`.

## Por qué existe

Diamond Rush nunca tuvo versión para Android y Gameloft no parece tener interés en hacerla,
así que el juego quedó abandonado. Este proyecto lo mantiene jugable
en celulares actuales, con controles táctiles y sin depender de un emulador.

## Estado

Funciona casi todo: intro, menús, niveles, sonido y guardado. Probado a mano,
sin tests automáticos. Pendiente: pulir detalles y probar en más dispositivos.

## Cómo se logró

El código fuente salió de descompilar y desofuscar el juego original. Como el
juego solo habla con `javax.microedition.*`, en vez de tocar la lógica se
escribió esa API sobre Android:

- `lcdui` (Canvas, GameCanvas, Graphics, Image, Font, Display): el juego dibuja
  en un bitmap propio; `GameView` lo muestra en pantalla con escala sin filtro
  (pixel art nítido) y triple buffer para evitar parpadeo.
- `lcdui.game` (Sprite, TiledLayer, LayerManager): con colisión por píxel.
- `rms.RecordStore`: guardado con formato propio, escritura atómica y recuperación
  de archivos corruptos.
- `media`: sonido y música con `MediaPlayer`, con pausa y reanudación.
- `midlet.MIDlet`: ciclo de vida real (`startApp`/`pauseApp`/`destroyApp`).
- `game.Res`: reemplaza `getResourceAsStream`, que en Android devuelve null
  porque los datos están en assets. Era la causa del cuelgue de la intro.

Los controles táctiles son un joystick analógico fijo, botones OK, L, R y
`*` (reiniciar nivel cuando quedás encerrado), con latch mínimo de 80 ms porque
el juego lee las teclas una vez por frame y un toque corto se perdía.
También funcionan teclado y gamepad.

## Controles

| Acción | Táctil | Teclado / gamepad |
|---|---|---|
| Mover | Joystick | Flechas o WASD |
| OK | Botón OK | Enter, A |
| Softkey izquierda | L | L1 |
| Softkey derecha | R | R1, B, Back |
| Reiniciar nivel | `*` | `*` |

## Compilar

Abrir en Android Studio o correr `./gradlew assembleDebug` (con tu SDK de
Android configurado en `local.properties`).

## Estructura

- `app/src/main/java/javax/microedition/`: la capa J2ME reimplementada.
- `app/src/main/java/com/diamondrush/port/`: la Activity, la vista y el juego.
- `app/src/main/assets/`: los datos del juego.

## Aviso

Proyecto sin fines de lucro, hecho para preservación. Diamond Rush, su código
y sus datos pertenecen a Gameloft. Este repositorio no está afiliado a ellos.
