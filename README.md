[🇬🇧 English](README.en.md)

<div align="center">
  <br/>
  <img src="docs/icon.png" width="180" alt="Icono de Mokona" />

# Mokona

**モコナ · Un visor de Pixiv sin anuncios, con las obras en su proporción real.**

<br/>

![Android 8.0+](https://img.shields.io/badge/android-8.0%2B-3ddc84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-2.2-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/jetpack%20compose-material%20you-4285f4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Licencia Apache 2.0](https://img.shields.io/badge/licencia-Apache%202.0-1b150d?style=for-the-badge)

<br/>

[![Descargar APK](https://img.shields.io/github/v/release/Chidaruma696/Mokona?label=%F0%9F%93%B2%20DESCARGAR%20APK&style=for-the-badge&color=2b2140)](https://github.com/Chidaruma696/Mokona/releases/latest)

<br/>

*Sin anuncios · sin rastreo · Material You · negro AMOLED · R-18 apagado por defecto*

</div>

---

> [!IMPORTANT]
> **Mokona no aloja ni distribuye ninguna imagen.** Lee la misma API que la app oficial de Pixiv, con tu propia cuenta, y muestra lo que Pixiv te muestra. Cada obra pertenece a su artista. Si te gusta lo que ves, síguelo, márcalo y apóyalo en Pixiv.

<br/>

## 📲 Descargar

1. Entra en la [última versión](https://github.com/Chidaruma696/Mokona/releases/latest) y baja el archivo `Mokona-x.y.z.apk`.
2. Ábrelo en el móvil. Android te pedirá permiso para instalar apps de esta fuente; acéptalo una vez.
3. Inicia sesión con tu cuenta de Pixiv. Mokona abre la página de inicio de sesión de Pixiv; tu contraseña nunca pasa por la app.

Mokona no está en Play Store ni va a estarlo; se distribuye solo desde aquí. Y mientras Android siga siendo abierto, con eso basta ([por qué importa](#-keep-android-open)).

<br/>

## 🗺️ Qué es

Mokona es una app Android para ver Pixiv como debería verse. La app oficial recorta todas las miniaturas a un cuadrado, así que una ilustración vertical y una horizontal se ven igual hasta que las abres. Mokona usa el ancho y el alto reales que la API devuelve y arma una **cuadrícula escalonada**: lo vertical sale vertical, lo horizontal sale horizontal, y nada se recorta.

| 🖼️ Ver | 🔍 Encontrar | 🎨 Vivir |
| --- | --- | --- |
| Inicio con las recomendaciones de tu cuenta | Ranking diario, semanal, mensual, por público, originales y novatos | Material You: la paleta sale de tu fondo de pantalla (Android 12+) |
| Cuadrícula escalonada con la proporción real de cada obra, de 1 a 4 columnas | Búsqueda por etiqueta con las etiquetas en tendencia a un toque | Modo claro, oscuro o del sistema, y **negro AMOLED** para pantallas OLED |
| Detalle con todas las páginas, etiquetas traducidas, obras relacionadas y descarga del original | Marcadores desde la app, sincronizados con tu cuenta | Contenido R-18 **desactivado por defecto**, activable en Ajustes › Contenido |

<br/>

## 🔞 Contenido adulto

Pixiv marca cada obra como para todos, R-18 o R-18G. Mokona arranca con ese contenido **oculto en todas partes**: en Inicio, en el ranking (los modos R-18 ni siquiera aparecen), en la búsqueda y en las obras relacionadas. El interruptor está en Ajustes › Contenido y también en la pantalla de primer arranque. Lo que Pixiv te enseñe con el interruptor encendido depende, además, de la configuración de tu propia cuenta.

<br/>

## 🎨 Material You y AMOLED

Mokona usa Material 3 tal cual: colores dinámicos tomados del fondo de pantalla en Android 12 en adelante, y una paleta propia (negro Mokona con la joya roja) en versiones anteriores o si prefieres apagar los colores dinámicos. El modo **negro AMOLED** sustituye todas las superficies del tema oscuro por negro puro, para que las pantallas OLED apaguen esos píxeles y la ilustración sea lo único encendido.

<br/>

## 🔐 Cuenta y privacidad

- La API de Pixiv solo responde a usuarios con sesión, así que hace falta una cuenta.
- El inicio de sesión es el de la propia app de Pixiv (OAuth con PKCE): Mokona abre la página oficial en un WebView y solo recibe el código de vuelta. **Nunca ve tu contraseña.**
- Los tokens se guardan en el almacenamiento privado de la app y se renuevan solos.
- Mokona no tiene anuncios, no incluye analíticas ni rastreadores, y no habla con nadie más que con Pixiv.

<br/>

## 🛠️ Compilar

Necesitas el SDK de Android (API 36) y un JDK 17 o superior.

```
git clone https://github.com/Chidaruma696/Mokona.git
cd Mokona
./gradlew :app:assembleDebug
```

Para la versión firmada, crea `local.properties` con `keystore.file`, `keystore.password`, `keystore.alias` y `keystore.keyPassword`, y ejecuta `./gradlew :app:assembleRelease`.

<br/>

## 🧩 Código

- `app/src/main/kotlin/com/mokona/app/data`: modelos de la API, cliente OkHttp con las cabeceras de la app oficial e inicio de sesión PKCE.
- `app/src/main/kotlin/com/mokona/app/ui`: tema Material You y AMOLED, preferencias, ViewModels.
- `app/src/main/kotlin/com/mokona/app/ui/screens`: Inicio, Ranking, Búsqueda, Detalle, Ajustes, Inicio de sesión y primer arranque.

Todo Kotlin y Jetpack Compose, sin librería de navegación: una pila de pantallas en memoria y el botón atrás.

<br/>

## 📜 Créditos y licencia

Mokona se distribuye bajo la [licencia Apache 2.0](LICENSE). Las librerías de terceros y sus licencias están en [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- **Pixiv** y sus servicios pertenecen a pixiv Inc. Mokona es un visor independiente sin afiliación ni respaldo. Cada obra pertenece a su artista.
- La documentación de la API de la app de Pixiv viene de [pixivpy](https://github.com/upbit/pixivpy).
- **Mokona** es un personaje de **CLAMP** (*Magic Knight Rayearth*, *xxxHolic*, *Tsubasa*). El nombre es un homenaje; no existe afiliación con CLAMP ni con sus editoras.

<br/>

## 📢 Keep Android Open

> **Tu teléfono está a punto de dejar de ser tuyo.** [keepandroidopen.org/es](https://keepandroidopen.org/es/)

Google anunció en 2025 una **verificación obligatoria de desarrolladores**, en vigor a partir de 2027: quien publique una app para Android tendrá que registrarse en un sistema central de Google, pagar una cuota y entregar su documento de identidad. Las apps de quien no se registre **quedarán bloqueadas en todos los dispositivos certificados del mundo**, estén o no en Play Store, incluidas las de F-Droid. Instalar por tu cuenta pasará a ser un proceso de nueve pasos con 24 horas de espera, controlado por Google Play Services y revocable en cualquier momento.

Que quede claro: **si eso se aplica, Mokona deja de existir.** Y cualquier app libre que no pase por la caja de Google. Este proyecto solo es posible porque Android todavía es abierto.

Por eso la app muestra un aviso en Inicio (se puede ocultar) y un enlace permanente en Ajustes › Acerca de. La campaña la impulsan 71 organizaciones de 23 países (F-Droid, EFF, FSF, Nextcloud, Proton, KDE, Tor Project, LineageOS, GNOME, Brave…). Lo que pide es sencillo:

- 📲 **Instala F-Droid** en cada dispositivo Android que tengas.
- ✍️ **Firma la petición** y comparte la página.
- 🏛️ **Escribe a tu regulador** de competencia o protección al consumidor.
- 🧑‍💻 Si desarrollas: **no te registres**, y convence a otros de no hacerlo.

<br/>

<div align="center">

モコナ · もこな

</div>
