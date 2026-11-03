# Tabla de revisión del inglés de la interfaz (Etapa 8)

La tabla completa está en **`ui-es-en.csv`** (UTF-8 con BOM, separador coma; se abre en Excel o Google Sheets). Una fila por texto (cadenas, cada cantidad de un plural y cada elemento de un arreglo):
columnas `archivo`, `clave`, `espanol`, `ingles`, `revisar_longitud`, `termino_glosario`. Está ordenada por archivo para que cada integrante revise un módulo.

## Guía de revisión (5 líneas)
1. Lee la columna `ingles` junto a `espanol`: ¿dice lo mismo, con el mismo tono (cercano, claro, de app educativa) y en inglés americano?
2. `termino_glosario` lista los términos de `modulo-5-glosario.md` detectados en la fila: el inglés debe usar exactamente ese término (una traducción por término en toda la app).
3. `revisar_longitud` marca los textos cuyo inglés mide más de 1.3 veces el español: míralos en el emulador (botones, chips, pestañas, arena) con fuente grande.
4. No se traducen los comandos de lab, los nombres propios (NightWatch Protocol, CiberMynd), los códigos ni los emojis; los placeholders (`%1$s`, `%2$d`) deben seguir en el texto.
5. Anota los cambios en una copia de la tabla (o como comentario en el PR) con la clave; el cambio se hace solo en `app/src/main/res/values-en/<archivo>`, nunca en `values/`.

## Reparto sugerido por módulo
| Módulo | Archivos | Filas |
|---|---|---|
| Comunes, insignias, errores de autenticación, notificaciones | `strings.xml` | 81 |
| Autenticación (módulos 1 y 2) | `strings_auth.xml` | 29 |
| Quiz | `strings_quiz.xml` | 13 |
| Perfil y Ajustes | `strings_perfil.xml` | 171 |
| Aprendizaje (módulo 3) | `strings_aprendizaje.xml` | 151 |
| Social: errores, reportar/bloquear y perfil social | `strings_social.xml` | 95 |
| Social: pantallas, batallas y arena | `strings_social_pantallas.xml` | 253 |
| **Total** | | **793** |

Las 793 filas son: 770 cadenas traducibles (incluye el aviso nuevo de Idioma; las 2 cadenas de nombres de idioma son `translatable="false"` y no se listan) + los 8 plurales abiertos en
16 filas (`one` y `other`) + el arreglo de iniciales de la semana en 7 filas.

## Totales de la revisión automática
24 filas marcadas `revisar_longitud`; 263 filas con al menos un término del glosario detectado. La detección es por coincidencia de texto y puede tener falsos positivos o dejar términos fuera.
