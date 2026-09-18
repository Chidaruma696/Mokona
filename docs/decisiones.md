# Decisiones

## No forkear Materixiv (sept 2026)
Es GPL-3 y mis apps van en Apache-2.0, así que no se puede llevar código. Además lleva sin tocarse desde 2022, Views con XML y un login que Pixiv seguramente ya rompió. Mejor de cero con Compose, usando de ahí solo ideas.

## Login en el navegador del teléfono
La contraseña nunca pasa por la app. Pixiv devuelve un código por `pixiv://` y con eso basta. Pixiv puede banear cuentas que usan clientes de terceros; el riesgo existe y no lo escondo.

## R-18 apagado por defecto
Se activa en Ajustes. Y el fondo de pantalla automático nunca usa obras para adultos, aunque el filtro esté encendido.

## Navegación a mano
Un `when` sobre una pila en vez de Navigation Compose. Me costó el bug de que al volver de una obra la cuadrícula saltaba al principio; se arregló guardando el estado de cada pantalla con `SaveableStateHolder`. Sigo prefiriéndolo a meter una librería.

## Traducción en línea por defecto (18 sept 2026)
Primero lo hice al revés: obligaba a bajar el diccionario de ML Kit antes de traducir. Mal. Ahora el reconocimiento de texto va en el teléfono y la traducción sale a Google Traductor sin bajar nada; los diccionarios son una opción para quien quiera traducir sin internet.

## Notas estilo Danbooru, no texto encima
El texto traducido sobre el dibujo parecía "pasado por Google". Un recuadro fino numerado sobre cada línea y la traducción aparte, con el original debajo, se lee mejor y no tapa nada. El otro modo quedó como opción.

## El idioma lo detecta la app
Quien lee no tiene por qué saber si la página es japonés, chino o coreano. Se reconoce con el motor japonés, se mira la escritura que salió y se relee con el motor correcto si hace falta.

## Un APK por arquitectura
Los modelos de reconocimiento van dentro del APK y pesan unos 25 MB por arquitectura. Un APK universal se iba a 110 MB; separado son 32 y 23. El aviso de versión nueva elige el que le toca al teléfono.

## Sin servidor propio
Todo corre en el teléfono o va directo a Pixiv, GitHub o Google. No quiero mantener un backend para una app de visor.
