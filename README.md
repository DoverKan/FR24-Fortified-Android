# FR24 Fortified

Aplicación Android para la monitorización en tiempo real de dispositivos FlightRadar24 (Feeder y Box).

Desarrollada por **SkyDronex** · **DoverKan (Nacho Cano)**  
🌐 [www.skydronex.com](https://www.skydronex.com)

---

## Características

### Nueva Vista de Mapa

Visualización geográfica de aeronaves con soporte para múltiples proveedores:

- **Mapbox (Opcional)** — Si se configura un token de Mapbox en los ajustes, la app utiliza el SDK oficial para una experiencia fluida y detallada.
- **OpenStreetMap (Gratuito)** — Opción por defecto si no hay token configurado. Utiliza `osmdroid` para ofrecer un mapa funcional sin coste adicional.
- **Lógica Inteligente** — Detección automática del token y cambio dinámico entre motores de mapas.
- **Pantalla completa** — Botón dedicado para expandir el mapa ocultando la barra superior, con controles flotantes de lluvia, ubicación y salida.
- **Centrado en mi ubicación** — Botón para centrar y seguir la posición GPS del dispositivo en tiempo real (requiere permiso de ubicación).
- **Capa de lluvia RainViewer** — Overlay de radar meteorológico en tiempo real, disponible tanto en Mapbox como en OpenStreetMap. Limitada al zoom máximo soportado por RainViewer (7) para evitar tiles inválidos.
- **Consistencia UI** — Barra superior con acceso rápido a configuración, acerca de y actualización, idéntica a la pantalla principal.

### Vista Feeder

- **Tarjeta Monitor feeder** — datos en tiempo real de `/monitor.json` (puerto 8754): estado del feed, servidor FR24, alias, ID, aviones tracked, mensajes procesados, modo, receptor, MLAT, versión del software. Se refresca cada 30 segundos.
- **Tarjeta Tráfico SBS** — estado de conexión al puerto 30003, aviones detectados (con y sin posición), mensajes por minuto. Solo visible cuando el puerto 30003 está activo (no es requisito).
- **Tabla de aviones activos** — ICAO, vuelo, altitud (ft), velocidad (kt), indicador de posición GPS. Ventana deslizante de 60 segundos.

### Vista Box

Monitorización completa del dispositivo FR24 Box mediante scraping de la interfaz web local:

- **Tarjeta Estado** — temperatura, radar code, aeronaves 1090 MHz, aviones detectados (con y sin posición), mensajes por minuto SBS
- **Tarjeta Sistema** *(colapsable)* — versión de firmware, fecha de actualización, uptime, uso de partición, dirección MAC
- **Tarjeta Red** *(colapsable)* — IP externa, IP interna, DNS público, DNS configurado
- **Tarjeta GPS** *(colapsable)* — estado, satélites usados, posición, niveles de señal

### Consola SBS

Terminal en tiempo real del stream SBS-1 BaseStation (puerto 30003):

- Inicio manual con botón ▶ Iniciar
- Pausa, reanudación y limpieza del buffer (500 líneas)
- Filtros por tipo de mensaje con código de colores:
  - 🔵 MSG,1 — Identificación (callsign)
  - 🩵 MSG,2 — Posición en superficie
  - 🟢 MSG,3 — Posición en vuelo
  - 🟡 MSG,4 — Velocidad y rumbo
  - 🟠 MSG,5 / MSG,6 — Altitud
  - ⚪ Otros
- Exportación del contenido visible mediante el sistema de compartición de Android

### Otras funcionalidades

- **Novedades al actualizar** — dialog automático con las novedades de la nueva versión, gestionado mediante `assets/changelog.txt`
- **Tema oscuro de aviación** — paleta consistente en toda la app (fondo `#090D18`, acento azul cielo, teal, emerald y ámbar)
- **Pantalla Acerca de** — logo de la app, enlace web funcional, sección de beta testers

---

## Tecnología

| Elemento | Versión |
|---|---|
| Lenguaje | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material3 |
| Compose BOM | 2026.04.01 |
| compileSdk | 37 |
| minSdk | 24 (Android 7.0) |
| targetSdk | 36 |
| AGP | 9.2.1 |

**Dependencias principales:**
- `androidx.compose.material3` — UI components
- `androidx.datastore.preferences` — persistencia de configuración y estado de novedades
- `jsoup` — parsing HTML de la interfaz web del Box
- `com.mapbox.extension:maps-compose` — Integración con Mapbox v11
- `org.osmdroid:osmdroid-android` — Motor de mapas OpenStreetMap
- `androidx.compose.material:material-icons-core` — iconografía

---

## Arquitectura

```
app/
├── assets/
│   └── changelog.txt                # Novedades por versión (editar para cada release)
└── java/com/skydronex/fr24fortified/
    ├── data/
    │   ├── AppConfig.kt             # Modelo de configuración
    │   ├── AppState.kt              # Estado global de la app
    │   ├── ConfigRepository.kt      # Persistencia DataStore (config + última versión vista)
    │   ├── ConnectionChecker.kt     # Comprobación de puertos TCP
    │   ├── Validation.kt            # Validación de IP y puertos
    │   ├── WhatsNewRepository.kt    # Lógica de novedades (lee changelog.txt)
    │   ├── box/
    │   │   ├── BoxData.kt           # Modelos de datos del Box
    │   │   └── BoxRepository.kt     # Fetching HTTP + parsing HTML/JSON
    │   ├── map/
    │   │   └── RainViewerRepository.kt # Cliente HTTP para el tile template de radar de RainViewer
    │   ├── monitor/
    │   │   ├── MonitorData.kt       # Modelo de /monitor.json
    │   │   └── MonitorRepository.kt # Cliente HTTP polling cada 30s
    │   └── sbs/
    │       ├── SbsAircraft.kt       # Modelo de aeronave SBS
    │       └── SbsRepository.kt     # Cliente TCP SBS-1, sliding window msg rate
    └── ui/
        ├── MainActivity.kt
        ├── about/AboutScreen.kt
        ├── console/SbsConsoleScreen.kt
        ├── home/
        │   ├── HomeScreen.kt
        │   ├── box/BoxHomeContent.kt
        │   └── feeder/FeederHomeContent.kt
        ├── setup/SetupScreen.kt
        ├── map/
        │   └── MapScreen.kt         # Pantalla de mapa condicional (Mapbox/OSM)
        ├── theme/
        │   ├── Color.kt             # Paleta oscura de aviación
        │   ├── Theme.kt             # Tema Material3 forzado dark
        │   └── Type.kt
        └── whatsnew/
            └── WhatsNewDialog.kt    # Dialog de novedades
```

**Flujo de datos:**
- `SbsRepository` se instancia en `HomeScreen` y se comparte entre `BoxHomeContent`, `FeederHomeContent` y `SbsConsoleScreen`
- `MonitorRepository` se instancia en `HomeScreen` y se pasa a `FeederHomeContent`; hace polling HTTP GET a `/monitor.json` cada 30 s
- `BoxRepository` realiza fetches HTTP paralelos (`async/await`) a `/index.php`, `/flights.js` y `/stats.json`
- La tasa de mensajes SBS se calcula con una `ArrayDeque<Long>` como ventana deslizante de 60 segundos
- `WhatsNewRepository` compara `BuildConfig.VERSION_CODE` con el último versionCode visto en DataStore

---

## Configuración

Al arrancar la app por primera vez se solicitan:

| Campo | Descripción |
|---|---|
| Tipo de dispositivo | Box o Feeder |
| Dirección IP | IP local del dispositivo FR24 |
| Token Mapbox | Token público de acceso (opcional) para habilitar mapas de Mapbox. |
| Puerto consola SBS | Puerto TCP del stream SBS-1 (por defecto 30003). Para Feeder no es obligatorio. |
| Puerto feeder | Puerto HTTP del servicio feeder (por defecto 8754). Solo para Feeder. |

La configuración se persiste con DataStore y puede modificarse desde el menú de tres puntos en la pantalla principal.

---

## Cómo añadir novedades en un nuevo release

1. Editar `app/src/main/assets/changelog.txt` añadiendo una nueva entrada al principio:
   ```
   [<versionCode>] <versionName>
   - Novedad 1
   - Novedad 2
   ```
2. Incrementar `versionCode` y `versionName` en `app/build.gradle.kts`.
3. Al instalar la nueva versión, la app mostrará automáticamente un dialog con las novedades.

---

## Protocolo SBS-1 (BaseStation)

La app consume el stream TCP en formato SBS-1 BaseStation del dispositivo FR24:

```
MSG,<tipo>,<sessionID>,<aircraftID>,<icao>,<flightID>,<date>,<time>,<date>,<time>,[campos...]
```

| Tipo | Contenido |
|---|---|
| MSG,1 | Callsign |
| MSG,2 | Posición en superficie + velocidad |
| MSG,3 | Posición en vuelo + altitud |
| MSG,4 | Velocidad, rumbo, tasa vertical |
| MSG,5 | Altitud de vigilancia |
| MSG,6 | Altitud con squawk |

---

## Créditos

| Rol | Nombre |
|---|---|
| Empresa | SkyDronex |
| Desarrollador | DoverKan · Nacho Cano |
| Beta testers | SOAR · Dalama |
| Web | [www.skydronex.com](https://www.skydronex.com) |

---

## Licencia

Proyecto privado de SkyDronex. Todos los derechos reservados.
