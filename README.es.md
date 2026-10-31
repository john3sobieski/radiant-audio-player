# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

Un reproductor de música nativo y sencillo para Android, para tus **archivos de audio locales**. Reproduce música únicamente de la carpeta que elijas, así que los tonos de llamada, los mensajes de voz y otros sonidos de tu teléfono nunca acaban en tu biblioteca.

Sin anuncios, sin cuentas, sin necesidad de acceso a internet. Todo permanece en tu dispositivo.

> **Idiomas:** inglés, alemán, ruso, ucraniano, chino, japonés, francés, checo, español y polaco. Elige uno en **Ajustes → Idioma de la aplicación**, o déjalo en *Sistema* para seguir el idioma del teléfono (se usa inglés si el idioma del teléfono no es compatible).

## Características

- **Tu música, tu carpeta.** Elige una carpeta una sola vez; Radiant la analiza junto con sus subcarpetas y vuelve a analizarla cada vez que abres la aplicación. Usa el Storage Access Framework de Android, por lo que no puede ver nada fuera de esa carpeta.
- **Biblioteca:** Listas de reproducción, Álbumes (agrupados automáticamente según las etiquetas de los archivos) y Canciones, con búsqueda y ordenación en cada sección.
- **Listas de reproducción:** crear, renombrar, eliminar, añadir y quitar canciones. *Canciones que me gustan* siempre está fijada arriba.
- **Colas:** una cuarta sección con exactamente cuatro colas fijas (no se pueden crear ni eliminar). Añade canciones desde el botón del reproductor o el menú «⋮», quítalas y reordénalas con una pulsación larga y arrastrando, o con «subir / bajar».
- **Arrastrar para reordenar** listas de reproducción, álbumes y canciones con una pulsación larga.
- **Reproducción en segundo plano** con notificación multimedia, controles en la pantalla de bloqueo y compatibilidad con los botones de los auriculares.
- **Reproductor:** reproducir/pausar, siguiente/anterior, buscar, aleatorio, repetir (desactivado / todo / una), carátula y una insignia de calidad con el formato del archivo y la tasa de bits.
- **Letras:** un botón debajo de la «i» en el reproductor abre la letra. Radiant busca letras en las etiquetas del archivo (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) y en archivos `.lrc` / `.txt` con el mismo nombre junto a la canción, y vuelve a comprobarlo cada vez que abres la aplicación. Las letras sincronizadas (`.lrc`) se desplazan con la canción, resaltan la línea actual y atenúan las líneas ya cantadas; toca una línea para saltar a ella. La letra sin sincronizar se muestra en una vista compacta.
- **Velocidad de reproducción** desde 3× más lenta hasta 3× más rápida en pasos de 0,1, conservando el tono.
- **Ajustes de sonido:** mono/estéreo, normalización de volumen, refuerzo de graves y agudos, filtro subsónico y un ecualizador de 12 bandas con 10 preajustes. El ecualizador puede aplicar una misma curva a todas las canciones o recordar una curva distinta para cada una.
- **Idioma de la interfaz:** *Ajustes → Idioma de la aplicación* abre una ventana con *Sistema* arriba y los diez idiomas debajo, en orden alfabético. El cambio se aplica al instante, sin interrumpir la reproducción.
- **Temas:** claro, oscuro con negro puro (ideal para pantallas OLED) o el predeterminado del sistema, con colores dinámicos en Android 12+.
- **Ahorro de energía** (Ajustes): *Desactivado*, *Medio* (casi sin animaciones, actualizaciones de progreso menos frecuentes) o *Intensivo* (además tema negro forzado, volumen limitado al 40 %, solo mono, ecualizador/graves/agudos/subsónico/normalización omitidos, salida solo por cable o altavoz del teléfono sin Bluetooth, análisis manual de la carpeta, caché de carátulas más pequeña).
- **Detalles de la canción:** etiquetas, formato, tasa de bits, frecuencia de muestreo, canales, tamaño del archivo y más.
- **Formatos:** MP3, FLAC, OGG/Opus, M4A/AAC, WAV y cualquier otro que Android pueda decodificar.

## Requisitos

- Android 8.0 (API 26) o posterior

## Instalación

La última versión estable siempre está disponible en la página de **[Releases](../../releases/latest)** como archivo `.apk`.

1. Descarga el `.apk` de la última versión en tu teléfono.
2. Ábrelo y permite la instalación desde orígenes desconocidos si Android lo solicita.
3. Abre Radiant.

Las versiones nuevas se instalan sobre las anteriores sin desinstalar, por lo que se conservan tus listas de reproducción y ajustes.

## Primeros pasos

1. Abre la aplicación y toca **Elegir carpeta**.
2. Selecciona la carpeta con tu música. La biblioteca se llena automáticamente.
3. Explora **Listas de reproducción**, **Álbumes** y **Canciones** y toca una canción para reproducirla.
4. Para crear una lista de reproducción, toca **⋮** junto a cualquier canción y elige **Añadir a lista de reproducción**.

## Compilar desde el código fuente

Necesitas JDK 17 y Android Studio o el SDK de Android con Gradle 8.9+.

- **Android Studio:** abre el proyecto, cambia la variante de compilación a **release** (las compilaciones debug son notablemente más lentas) y elige *Build → Build APK(s)*.
- **Línea de comandos:** ejecuta `gradle assembleRelease`.
- **GitHub Actions:** haz un fork del repositorio y ejecuta el flujo de trabajo **Build APK**; el APK se adjunta a la ejecución como artefacto.

El APK se genera en `app/build/outputs/apk/release/app-release.apk`.

> **Firma.** Las versiones oficiales se firman con una clave privada que no forma parte de este repositorio. Una compilación que hagas tú mismo se firma con la clave de depuración estándar de Android, por lo que se instala sin problemas pero no se actualizará sobre una versión oficial (desinstala una antes de instalar la otra). Si quieres tus propias compilaciones firmadas en GitHub Actions, añade los secretos del repositorio `KEYSTORE_BASE64` (tu archivo `.jks` codificado en base64) y `KEYSTORE_PASSWORD`; el alias de la clave es `radiant` por defecto.

## Desarrollado con

Kotlin, Jetpack Compose (Material 3) y AndroidX Media3 (ExoPlayer).

## Contribuir

Los issues y los pull requests son bienvenidos. Para cambios más grandes, abre primero un issue para comentar qué te gustaría cambiar. Al enviar un pull request aceptas que tu contribución se proporcione bajo la misma licencia que el proyecto.

## Licencia

Radiant se distribuye bajo la [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Eres libre de usarlo, estudiarlo, modificarlo y compartirlo para cualquier fin **no comercial**, incluidos el uso personal, los proyectos de aficionados, la educación y las organizaciones sin ánimo de lucro. **No se permite el uso comercial** sin un acuerdo aparte, por lo que no puedes vender la aplicación ni publicarla con ánimo de lucro. Si quieres usar Radiant con fines comerciales, ponte en contacto abriendo un issue en este repositorio.

Conserva el archivo `LICENSE.md` y su línea `Required Notice` en cualquier copia que compartas.
