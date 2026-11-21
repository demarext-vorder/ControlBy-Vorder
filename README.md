# Control by VORDER

App Android (Kotlin + Jetpack Compose) para controlar TVs, luces y relés de la misma red Wi-Fi.

| Tipo | Marcas / protocolos |
|------|---------------------|
| TV | LG webOS, Samsung (Tizen), Google TV / Android TV |
| Luces | WiZ, Yeelight, Philips Hue (puente), Tasmota |
| Relés / enchufes | Shelly (Gen1 y Gen2+), Tasmota, Sonoff modo DIY |

## Compilar el APK sin Android Studio
1. Subí esta carpeta a un repositorio de GitHub (rama `main`).
2. Entrá a **Actions → Build APK → Run workflow** (o se ejecuta solo con cada push).
3. Al terminar, descargá el artefacto **Control-by-VORDER-apk** (zip con el `.apk`) e instalalo.

## Notas
- Las TVs LG/Samsung piden aceptar la conexión en pantalla la primera vez.
- Google TV: se vincula con un código de 6 caracteres que muestra la TV.
- Para encender una TV apagada se usa Wake-on-LAN: cargá su MAC en "Editar".
- Yeelight: activar "Control LAN". WiZ: activar "Comunicación local". Sonoff: modo DIY.
- Icono: `art/icon.svg` (fuente) y vectores adaptativos en `app/src/main/res/drawable`.
