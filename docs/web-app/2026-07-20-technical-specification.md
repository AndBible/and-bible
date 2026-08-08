# Especificación Técnica: Plataforma Web de Estudio Bíblico

> Documento de definición técnica. Basado en los requerimientos iniciales del documento [2026-07-20-bible-software-requirements.md](2026-07-20-bible-software-requirements.md) (en adelante "BSD"). Cada sección de este documento expande y formaliza los requerimientos del BSD a nivel de implementación.

---

## 1. Objetivo del Producto

Desarrollar una aplicación web que sea el mejor software de estudio de la Biblia por sus funcionalidades, sin sacrificar practicidad, eficiencia y compatibilidad. La aplicación prioriza la experiencia de usuario y la facilidad de uso por encima de la complejidad académica. El producto debe ser funcional, estético y práctico para el usuario promedio.

---

## 2. Arquitectura General

### 2.1. Stack Tecnológico

| Capa | Tecnología |
| :--- | :--- |
| **Backend** | Java + Spring Boot (últimas versiones estables) |
| **Capa CRUD/MVC** | Pendiente: Integración de [White_CRUD](https://github.com/WhiteOrganization/White_CRUD) para la estructura genérica Facade de inyección de dependencias sobre Spring e Hibernate. La integración se realizará como tarea separada una vez el proyecto base esté inicializado. |
| **Base de Datos** | PostgreSQL (servidor centralizado). Abierto a ajustes sobre la marcha. |
| **Frontend** | Angular (última versión estable) + TypeScript |
| **Motor Bíblico** | Librería JSword (Java) reutilizada del proyecto AndBible + parser nativo e-Sword (Kotlin) reutilizado del módulo `esword` de AndBible |
| **PWA** | Tentativo. Se implementará como Progressive Web App si no genera conflicto con los demás requisitos. La aplicación móvil principal será la versión Android de AndBible. |

### 2.2. Modelo Cliente-Servidor

La aplicación seguirá un modelo Cliente-Servidor monolítico. No se utilizarán microservicios en esta primera versión. Si en el futuro se vieran necesarios, la arquitectura permitirá migrar sin reestructuración mayor.

### 2.3. Reutilización del Motor de AndBible

El proyecto reutiliza dos componentes centrales del repositorio [and-bible](https://github.com/AndBible/and-bible):

#### 2.3.1. Librería JSword
La librería JSword (fork de AndBible) está escrita en Java y se encarga de:
- Lectura e indexación de módulos en formato SWORD.
- Resolución de versificaciones (mapeo entre sistemas de numeración bíblica como KJVA, Synodal, etc.).
- Navegación por Libro → Capítulo → Versículo.
- Búsqueda de texto dentro de módulos instalados.

Esta librería se importará como dependencia directa en el proyecto Spring Boot.

#### 2.3.2. Parser e-Sword (.bblx)
AndBible ya cuenta con un parser completo para archivos e-Sword, ubicado en el paquete `net.bible.service.sword.esword`. Este parser se compone de:

- **ESwordBook.kt** (`app/src/main/java/net/bible/service/sword/esword/ESwordBook.kt`): Clase principal. Abre el archivo `.bblx` (que internamente es una base de datos SQLite), lee la tabla `Details` para extraer metadatos (nombre, abreviación, idioma, si tiene Strong) y la tabla `Bible` para obtener el texto de cada versículo mediante queries `SELECT Scripture FROM Bible WHERE Book = ? AND Chapter = ? AND Verse = ?`. Contiene la función `convertRtfToOsis()` que transforma el contenido RTF nativo de e-Sword a formato OSIS estándar, manejando negrita (`\b`/`\b0` → `<hi type="bold">`), cursiva (`\i`/`\i0` → `<hi type="italic">`), superíndice, saltos de línea (`\line`/`\par` → `<lb/>`), escapes Unicode y hexadecimales, y eliminación de grupos RTF irrelevantes (fonttbl, colortbl, stylesheet).
- **ESwordBookMap.kt** (`app/src/main/java/net/bible/service/sword/esword/ESwordBookMap.kt`): Mapeo bidireccional entre la numeración secuencial de e-Sword (1-66 canon protestante, 67-78 deuterocanónicos) y los enumerados `BibleBook` de JSword.
- **SqliteSwordDriver.kt** (`app/src/main/java/net/bible/service/sword/SqliteSwordDriver.kt`): Driver genérico que conecta módulos SQLite al sistema de libros de JSword.
- **ESwordBookTest.kt** (`app/src/test/java/net/bible/service/sword/esword/ESwordBookTest.kt`): Suite de 30+ pruebas unitarias que validan la conversión RTF→OSIS (texto plano, negrita, cursiva, superíndice, escapes Unicode, grupos anidados, datos reales de Gn 1:1 y Jn 3:16, etc.) y el mapeo de libros bíblicos.

**Nota sobre Kotlin:** Estos archivos del parser están escritos en Kotlin. Como Kotlin compila a bytecode JVM idéntico al de Java, estas clases pueden ser importadas y utilizadas desde un proyecto Java + Spring Boot sin ningún problema. La única dependencia adicional es incluir el runtime de Kotlin (`kotlin-stdlib`) en el classpath del proyecto. Esto ya es una práctica estándar y no genera ningún conflicto con Spring Boot.

**Dependencia de Android a eliminar:** La clase `ESwordBook.kt` actualmente utiliza `io.requery.android.database.sqlite.SQLiteDatabase` para abrir la base de datos SQLite. Para el entorno web (Spring Boot / JVM servidor), esta referencia deberá sustituirse por un driver JDBC SQLite estándar (por ejemplo, `org.xerial:sqlite-jdbc`). La API es funcionalmente idéntica (queries SQL, cursores), por lo que la migración es directa y no afecta la lógica del parser.

#### 2.3.3. Lógica de Renderizado (Frontend)
El submódulo `bibleview-js` (`app/bibleview-js/src`) de AndBible contiene la lógica de renderizado del texto bíblico escrita en TypeScript. Los siguientes componentes contienen lógica que será portada a Servicios de Angular:

- **Q.vue** (`app/bibleview-js/src/components/OSIS/Q.vue`): Renderizado de texto en rojo (palabras de Cristo). Detecta el atributo OSIS `who="jesus"` y aplica la clase CSS `redLetters` condicionalmente según la configuración `showRedLetters`.
- **W.vue** (`app/bibleview-js/src/components/OSIS/W.vue`): Renderizado de números Strong. Parsea el atributo `lemma` (ej. `strong:H8064`) y `morph` del estándar OSIS, formatea los enlaces clicables y soporta tres modos de visualización: `links` (números visibles al lado de la palabra), `hidden` (click en la palabra misma) y desactivado.
- **Note.vue** (`app/bibleview-js/src/components/OSIS/Note.vue`): Renderizado de notas al pie y referencias cruzadas. Distingue entre tipos `crossReference`, `explanation`, `translation`, `study`, `variant` y `alternative`. Las referencias cruzadas se muestran con un handle naranja en superíndice; las notas al pie con un handle púrpura. Al hacer clic, se abre un diálogo modal con el contenido expandido.
- **TransChange.vue** (`app/bibleview-js/src/components/OSIS/TransChange.vue`): Renderizado de palabras añadidas por el traductor (cursivas en gris).
- **Verse.vue** (`app/bibleview-js/src/components/OSIS/Verse.vue`): Componente de versículo individual. Maneja la numeración, el flujo de texto (continuo vs. verso por línea) y los eventos de selección/enfoque.

---

## 3. Módulos Funcionales

### 3.1. Registro e Inicio de Sesión

#### 3.1.1. Datos del Usuario
El sistema almacenará únicamente el email del usuario como identificador único. No se requerirán ni se almacenarán otros datos personales (nombre, dirección, teléfono, etc.). Un email se liga a exactamente una cuenta; un email no puede estar asociado a múltiples cuentas.

#### 3.1.2. SSO (Single Sign-On)
La autenticación se realizará mediante OAuth2 con Google como proveedor principal. Opcionalmente, se podrá agregar Meta (Facebook) u otros proveedores en el futuro. El flujo será:
1. El usuario hace clic en "Iniciar sesión con Google".
2. Se redirige al flujo OAuth2 de Google.
3. Google devuelve un token con el email verificado.
4. El backend valida el token y crea/recupera la sesión del usuario usando únicamente el email como identificador.
5. Se emite un JWT (JSON Web Token) para las sesiones subsecuentes.

No se implementarán mecanismos complejos de seguridad dado que la aplicación no maneja información financiera ni sensible. La seguridad será tan robusta como sea posible sin aumentar la complejidad del proyecto.

#### 3.1.3. Restaurar Contraseña
Para usuarios que se registren con email/contraseña (fuera de SSO), se implementará el flujo estándar:
1. El usuario solicita restauración ingresando su email.
2. El sistema envía un correo con un enlace temporal (token de un solo uso, expiración de 1 hora).
3. El usuario establece una nueva contraseña mediante el enlace.

#### 3.1.4. Términos y Condiciones
Al registrarse por primera vez, el usuario deberá aceptar los términos y condiciones. Estos términos incluirán las siguientes cláusulas obligatorias:

- **Deslinde de responsabilidad sobre licencias de Biblias:** Al subir una versión de la Biblia a su cuenta, el usuario declara tener la licencia correspondiente y ser responsable de su resguardo y presentación ante quien se la requiera, incluidos los operadores de la plataforma.
- **Restricción de menores:** La plataforma no está dirigida a menores de edad. Se incluirá la leyenda legal correspondiente según la jurisdicción aplicable (ej. COPPA para EE.UU., RGPD para Europa).
- **Uso de datos:** Se informará que el único dato almacenado es el email y que las notas del usuario son públicas por defecto (ver sección 3.5.4).

---

### 3.2. Versiones de la Biblia

#### 3.2.1. Formato Estándar: OSIS
Todas las Biblias dentro del sistema se manejan internamente en formato OSIS (Open Scripture Information Standard). Este es el formato estándar para almacenar textos bíblicos estructurados. El formato OSIS soporta:
- Estructura jerárquica: Libro (`<div type="book">`) → Capítulo (`<chapter>`) → Versículo (`<verse>`).
- Metadatos lingüísticos: atributo `xml:lang` para idioma.
- Números Strong: elemento `<w lemma="strong:H1234">`.
- Notas y referencias cruzadas: elemento `<note type="crossReference">`.
- Formateo tipográfico: `<hi type="italic">`, `<hi type="bold">`, `<q who="Jesus">`.

#### 3.2.2. Soporte de Archivos e-Sword
La aplicación aceptará archivos e-Sword con extensión `.bblx` (Biblias) y `.bbli` (Biblias internacionales). El flujo de procesamiento será:

1. **Carga:** El usuario selecciona un archivo `.bblx` desde su dispositivo mediante un control de subida de archivos en la interfaz.
2. **Parseo en memoria:** El backend recibe el archivo, lo abre como base de datos SQLite en memoria usando el parser reutilizado de AndBible (clase `ESwordBook`), lee la tabla `Details` para extraer metadatos y la tabla `Bible` para extraer versículos.
3. **Conversión RTF → OSIS:** Para archivos `.bblx`, el contenido de cada versículo está codificado en RTF. La función `convertRtfToOsis()` transforma este RTF a XML OSIS válido, preservando negrita, cursiva, superíndice, saltos de línea y caracteres Unicode.
4. **Registro temporal:** El módulo bíblico convertido se registra en la sesión del usuario para su uso durante la sesión activa. **En esta primera versión (V1), el servidor NO almacenará permanentemente los archivos subidos.** El usuario deberá volver a subir sus archivos en cada nueva sesión.

**Versión 2 (alcance futuro):** En una segunda versión, el servidor almacenará los archivos de manera permanente asociados a la cuenta del usuario. El límite de almacenamiento será por peso total (en megabytes), no por número de archivos. Por ejemplo, un usuario podría tener muchas biblias pequeñas o pocas biblias pesadas, siempre que no excedan el límite total asignado (límite exacto por definir, sugerencia: 50-100 MB por usuario). En esta versión futura, los usuarios también podrán seleccionar versiones que otros miembros de la comunidad hayan subido, siempre declarando que cuentan con las licencias correspondientes.

#### 3.2.3. ID de Biblia
Cada versión de la Biblia cargada en el sistema se identificará mediante un código único generado a partir de los metadatos del archivo:
- **Formato:** `{Fuente}-{NombreSinEspacios}` (ej. `ESword-RVR60`, `SWORD-KJV`).
- **Unicidad:** El identificador se sanitiza eliminando caracteres especiales (tal como hace la función `sanitizeModuleName` en `ESwordBook.kt`: reemplazando todo carácter no alfanumérico por `_`).
- **Impacto en compartir:** Dado que las biblias privadas del usuario pueden tener nombres arbitrarios, el identificador NO será universalmente compatible entre usuarios. Esto afecta la funcionalidad de compartir (ver sección 3.8.2).

#### 3.2.4. Detección de Strong
Al procesar un archivo bíblico (ya sea e-Sword u OSIS nativo), el sistema detectará automáticamente si la versión contiene números Strong. La detección se realizará en dos niveles:

1. **Nivel de metadatos:** Para archivos e-Sword, el parser lee la columna `Strong` (o `Strongs`) de la tabla `Details` de la base de datos SQLite. Si el valor es `true` (entero distinto de cero), se registra el flag `hasStrongs = true` en los metadatos del módulo. Para archivos SWORD/OSIS, se verifica la presencia de la directiva `GlobalOptionFilter = OSISStrongs` en la configuración del módulo.
2. **Nivel de contenido (validación):** Adicionalmente, el sistema podrá escanear una muestra de versículos (ej. los primeros 100) buscando elementos `<w lemma="strong:...">` en el XML OSIS resultante para confirmar la presencia real de números Strong, independientemente de lo que declaren los metadatos.

Tras la detección, el sistema registrará dos metadatos separados para la versión:
- **Identificador base:** El nombre de la versión sin el indicador de Strong (ej. `RVR60`).
- **Flag de Strong:** Un booleano que indica si esta versión específica contiene números Strong (ej. `hasStrongs: true`, representado visualmente como `RVR60+`).

El usuario recibirá una notificación/confirmación indicando: "Se ha detectado que esta versión contiene números Strong. ¿Desea registrarla como [RVR60+]?" para que pueda confirmar o corregir el nombre.

#### 3.2.5. Toggle de Strong
Si un usuario tiene cargada una versión sin Strong (ej. `RVR60`) y existe en el sistema una versión de la misma Biblia con Strong (ej. `RVR60+`), la interfaz mostrará un toggle/checkbox que permitirá cambiar entre ambas versiones instantáneamente sin necesidad de navegar a otro lugar ni cambiar de módulo manualmente. La detección de versiones pareadas se basará en la coincidencia del identificador base (ambas comparten `RVR60`).

#### 3.2.6. Licencias y Versiones de Dominio Público
La aplicación incluirá como versiones fijas (preinstaladas y disponibles para todos los usuarios sin necesidad de subir archivos) aquellas Biblias que sean de dominio público. Versiones candidatas a evaluar:
- **1602 Purificada (RV1602P):** Basada en la Reina-Valera original de 1602, revisada. Estado de dominio público por antigüedad.
- **Reina-Valera Gómez (RVG):** Publicada por Humberto Gómez. Verificar estado de licencia.
- **RV-SBT:** Conocida por tener problemas de licencias; NO incluir como versión fija.
- **King James Version (KJV):** Dominio público (1611). Candidata como Biblia por defecto del sistema.

**Versiones con licencia privada:** El usuario podrá cargar cualquier versión a la que tenga acceso, bajo la declaración de los Términos y Condiciones (sección 3.1.4). La plataforma no verificará, almacenará ni solicitará prueba de licencia. La responsabilidad recae completamente en el usuario.

---

### 3.3. Referencias Cruzadas

Cuando un versículo contenga referencias cruzadas en sus metadatos OSIS (elemento `<note type="crossReference">`), se mostrará un indicador visual junto al versículo. El comportamiento será el siguiente:

1. **Indicador visual:** Un carácter en superíndice de color naranja (reutilizando el estilo de `Note.vue` de AndBible: clase `.isCrossReference`, color `orange`). El carácter será una letra secuencial (`a`, `b`, `c`...) asignada por orden de aparición en el capítulo.
2. **Interacción:** Al hacer clic/tap en el indicador, se abrirá un diálogo modal (tooltip expandido) que mostrará la lista de referencias cruzadas con sus citas completas (ej. "Mt 4:4; Lc 4:4; Dt 8:3").
3. **Navegación:** Cada referencia dentro del diálogo será un enlace clicable que navegará al versículo referenciado en la misma versión de la Biblia que el usuario está leyendo.
4. **Configuración:** El usuario podrá ocultar/mostrar los indicadores de referencias cruzadas desde el menú de Configuraciones (ver sección 3.12).

---

### 3.4. Formato de Texto

#### 3.4.1. Cursivas
Las palabras añadidas por el traductor (marcadas en OSIS con `<transChange type="added">`) se renderizarán en estilo *cursiva* y en color gris atenuado para distinguirlas del texto original. Este es un estándar tipográfico bíblico ampliamente reconocido.

#### 3.4.2. Texto en Rojo (Palabras de Cristo)
Las palabras atribuidas a Jesucristo (marcadas en OSIS con `<q who="Jesus">`) se renderizarán en color rojo (`rgb(215, 13, 13)`, reutilizando el valor de `Q.vue` de AndBible). Esta funcionalidad será configurable mediante un toggle `showRedLetters` en las preferencias de visualización. Cuando esté desactivada, las palabras de Cristo se mostrarán con el mismo formato que el resto del texto.

#### 3.4.3. Resaltado

##### 3.4.3.1. Paleta de Colores
Los colores de resaltado disponibles serán una paleta curada, moderna y estéticamente compatible con los temas claro y oscuro. Se reutilizará como base la paleta de `BookmarkStyle` de AndBible: amarillo, rojo, verde, azul, naranja y púrpura (con valores ARGB específicos ya definidos en `BookmarkEntities.kt`). Los colores podrán ajustarse para el tema web, pero mantendrán la misma paleta base para garantizar compatibilidad visual futura con la app Android.

##### 3.4.3.2. Resaltado de Versículo Completo
El usuario podrá seleccionar uno o varios versículos consecutivos y aplicarles un color de resaltado. Este resaltado se almacenará como un Bookmark asociado al rango de versículos (identificado por `startOrdinal` y `endOrdinal` en el sistema de coordenadas de JSword). **El resaltado de versículo completo se extrapolará a todas las versiones de la Biblia que el usuario maneje**, ya que se almacena por coordenada de versículo (Libro + Capítulo + Versículo), no por texto.

##### 3.4.3.3. Resaltado de Texto Parcial
El usuario podrá resaltar una porción de texto dentro de un versículo o que abarque texto parcial de versículos consecutivos. Por ejemplo:
- Resaltar solo la segunda mitad de un versículo.
- Resaltar desde la mitad del versículo 3 hasta la mitad del versículo 4.

Este tipo de resaltado se almacenará mediante offsets de caracteres dentro del rango de versículos (`startOffset`, `endOffset`), vinculados a la versión específica de la Biblia. **A diferencia del resaltado de versículo completo, el resaltado parcial NO se extrapola a otras versiones**, ya que la posición de los caracteres varía entre traducciones.

---

### 3.5. Notas

#### 3.5.1. Tipos de Notas

##### 3.5.1.1. Notas de Versículo
El usuario podrá crear una nota asociada a un versículo específico (o al primer versículo del rango seleccionado/enfocado). La relación es Many-to-One: un versículo puede tener múltiples notas, pero cada nota de versículo apunta a un solo versículo.

##### 3.5.1.2. Notas de Tópico
El usuario podrá crear notas que se asocien a múltiples versículos bajo un tópico (título) común. La relación es Many-to-Many: un versículo puede estar ligado a múltiples tópicos, y un tópico puede contener múltiples versículos.

#### 3.5.2. Marca de Nota (Indicador Visual en Versículo)
Los versículos que tengan notas asociadas mostrarán un indicador visual diferenciado por tipo de nota:
- **Nota de versículo:** Un pequeño ícono de nota (📝 o similar) a la derecha del número del versículo, en un color discreto (ej. azul suave en tema oscuro).
- **Nota de tópico:** El mismo ícono pero con un indicador de agrupación (ej. un pequeño badge numérico indicando cuántos tópicos referencian este versículo) o un color diferente (ej. verde suave).
- Al hacer clic en el ícono, se abrirá el panel de notas con todos los controles de edición, visualización y la lista de notas asociadas.

#### 3.5.3. Formato de Notas
Las notas soportarán formato Markdown completo. La interfaz de edición será WYSIWYG (What You See Is What You Get) por defecto, mostrando el texto formateado mientras el usuario escribe. Adicionalmente:
- **Editor de código fuente:** Un botón discreto (similar al botón `<>` de GitHub) permitirá alternar entre la vista WYSIWYG y la vista de código fuente Markdown. Este botón estará deliberadamente poco visible (ej. como un ícono pequeño en la esquina inferior del editor) para no estorbar la experiencia del usuario que no conoce Markdown.
- **Hipervínculos:** Si el usuario selecciona un fragmento de texto en su nota y pega una URL (que normalmente sustituiría el texto), la aplicación detectará que el contenido del portapapeles es una URL y, en lugar de reemplazar el texto, lo convertirá automáticamente en un hipervínculo Markdown: `[texto seleccionado](URL pegada)`.

#### 3.5.4. Privacidad de Notas (Públicas por Defecto con Fricción para Privatizar)
Todas las notas del usuario serán **públicas por defecto**. No habrá ningún toggle visible de privacidad durante la creación de la nota.

Para hacer una nota privada, el usuario deberá:
1. Navegar a un botón/ícono de **configuración de la nota** (un engranaje ⚙️ o ícono de tres puntos) que no estará prominentemente ubicado — estará dentro de un submenú o menú contextual de la nota, de manera que no sea la primera opción visible.
2. Dentro de ese submenú, encontrar la opción "Hacer privada".
3. Al seleccionarla, se mostrará un cuadro de confirmación con un mensaje del estilo: *"Al compartir tus notas ayudas a otros creyentes en su estudio. ¿Estás seguro de que deseas hacer esta nota privada?"*
4. Solo tras confirmar, la nota se marcará como privada.

El objetivo de esta fricción intencionada (botón escondido + confirmación explícita) es incentivar que la mayoría de notas permanezcan públicas para alimentar el ecosistema de notas de la comunidad.

#### 3.5.5. Notas de Comunidad
Al seleccionar un versículo, el usuario podrá acceder a un panel de "Notas de la Comunidad" que mostrará todas las notas públicas de otros usuarios asociadas a ese versículo.

- **Ordenamiento:** Las notas se ordenarán por puntuación de votos (descendente), de modo que las notas más valoradas aparezcan primero en el feed.
- **Votación:** Cada nota de comunidad mostrará botones de voto (+1 / -1). Se incentivará al usuario a votar mediante prompts sutiles (ej. tras leer varias notas sin votar, un mensaje discreto: "¿Te fue útil alguna de estas notas? Tu voto ayuda a otros a encontrar las mejores.").
- **Carga progresiva (Lazy Loading):** Las notas se cargarán progresivamente conforme el usuario hace scroll (estilo feed de redes sociales), no todas de golpe, para optimizar rendimiento.
- **Unicidad de voto:** Un usuario solo puede emitir un voto por nota (positivo o negativo, modificable).

---

### 3.6. Comparar / Paralelo

La aplicación soportará dos modos de comparación:

1. **Comparar:** Muestra el mismo capítulo/versículo en múltiples versiones de la Biblia apiladas verticalmente (una debajo de la otra), permitiendo leer el mismo pasaje en diferentes traducciones de manera secuencial.
2. **Comparar en Paralelo:** Muestra las versiones en columnas lado a lado, sincronizadas por versículo. Al hacer scroll en una columna, las demás se desplazan al mismo versículo correspondiente.

El acceso a ambos modos será desde la barra de herramientas principal (siempre visible). El usuario podrá seleccionar cuáles de sus versiones cargadas desea comparar.

---

### 3.7. Búsqueda

Al presionar `Ctrl+F` (o el botón correspondiente en el menú), se abrirá un diálogo de búsqueda personalizado de la aplicación (no el buscador nativo del navegador). Este diálogo ofrecerá las siguientes opciones:

- **Alcance de búsqueda:** Radio buttons para seleccionar:
  - En el capítulo actual.
  - En el libro actual.
  - En toda la Biblia.
- **Tipo de coincidencia:** Checkbox para alternar entre:
  - **Palabras exactas (frase exacta):** Las palabras buscadas deben aparecer en ese orden exacto y de manera consecutiva.
  - **Palabras inexactas (cualquier orden):** Todas las palabras buscadas deben estar presentes pero pueden aparecer en cualquier orden, dentro de un rango de 1 o 2 versículos consecutivos.
- **Resultados:** Se mostrarán como una lista scrolleable con el texto del versículo resaltando las coincidencias. Cada resultado será clicable para navegar directamente al versículo.

---

### 3.8. Compartir

#### 3.8.1. Enfoque / Selección de Versículos
El usuario podrá seleccionar/enfocar uno o más versículos haciendo clic o tap sobre ellos. Al seleccionarlos:
- Los versículos seleccionados se resaltarán temporalmente.
- El resto de los versículos visibles en pantalla se atenuarán (reducción de opacidad) para crear un efecto de enfoque visual.
- Se mostrará una barra de acciones contextual (no intrusiva) con las opciones: Crear Nota, Compartir, Copiar, Crear Bookmark.

#### 3.8.2. Generar URL para Compartir
La aplicación generará una URL única basada en los versículos seleccionados. El formato de la URL incluirá:
- La referencia bíblica (libro, capítulo, versículos).
- El identificador de la versión de la Biblia utilizada.

**Comportamiento del receptor (quien abre la URL):**
- Si el receptor tiene cargada la misma versión de la Biblia que el remitente (ej. `RV-1602P`), se mostrará el versículo en esa versión.
- Si el receptor **NO** tiene esa versión, la aplicación mostrará el versículo en la **Biblia por defecto del sistema** (la Biblia por defecto está pendiente de definir, pero será la misma que utiliza AndBible como default, actualmente KJV para inglés).
- No se mostrará ningún mensaje de error visible al receptor; la transición a la Biblia por defecto será silenciosa y transparente.

#### 3.8.3. Copiar
El usuario podrá copiar el texto de los versículos seleccionados al portapapeles. No se impondrán restricciones de copia basadas en licencias, dado que la responsabilidad sobre el uso del texto recae en el usuario según los Términos y Condiciones.

#### 3.8.4. Formato de Copiado Configurable
El texto copiado seguirá un formato configurable por el usuario. Las opciones de personalización incluirán:

- **Elementos a incluir (toggles individuales):**
  - Texto del versículo: Siempre incluido.
  - Cita (referencia): Libro + #Capítulo + #Versículo (ej. "Juan 3:16"). Activado por defecto.
  - Versión de la Biblia: Activada por defecto. Con sub-opción para elegir entre nombre completo ("Reina-Valera 1602 Purificada") o abreviación ("RV1602P").
- **Orden de los elementos:** El usuario podrá arrastrar y soltar (drag & drop) para reordenar los componentes. Ejemplo de combinaciones posibles:
  - `"Porque de tal manera amó Dios al mundo... — Juan 3:16 (RV1602P)"`
  - `"RV1602P | Juan 3:16 | Porque de tal manera amó Dios al mundo..."`
  - `"Juan 3:16 — Porque de tal manera amó Dios al mundo..."`
- **Persistencia:** La configuración de formato se almacenará en las preferencias del usuario y se aplicará como formato predeterminado en todas las copias futuras. Al momento de copiar, se ofrecerá también un acceso rápido para modificar el formato antes de copiar.

---

### 3.9. Historial

Se almacenarán las últimas **5 citas** (capítulos o pasajes) que el usuario ha visitado, con la opción de hacer clic en cualquiera de ellas para volver instantáneamente.

- **Ubicación:** El historial debe ser un componente siempre visible cuando el usuario esté leyendo y navegando por la Biblia, ubicado en un área separada de la sección principal de lectura (ej. barra lateral, barra inferior, o breadcrumb). No debe sacrificar espacio de lectura ni estorbar al diseño.
- **Visibilidad condicional:** El historial NO necesita estar visible cuando el usuario esté en pantallas de configuración o ajustes.
- **Prioridad de acceso:** El acceso al historial debe ser rápido (1 clic máximo) y estar disponible en todo momento durante la lectura.

---

### 3.10. Bookmarks

Al seleccionar/enfocar un versículo (o al pasar el cursor sobre él), se mostrará un ícono de bookmark (🔖). Al hacer clic:
1. El versículo se agrega a la lista de bookmarks del usuario.
2. La lista de bookmarks es accesible desde un componente separado de la sección de lectura (ej. panel lateral o dropdown).
3. Cada bookmark muestra la referencia (ej. "Jn 3:16") y es clicable para navegar directamente.

**Caducidad:**
- Después de un número configurable de días sin acceder a un bookmark (ej. 7 días), el sistema lo marcará como "en desuso" (indicador visual: ícono atenuado o badge).
- Una vez al día (al iniciar sesión o al abrir la aplicación), si existen bookmarks en desuso, la aplicación preguntará al usuario: "Tienes X bookmarks que no has visitado recientemente. ¿Deseas limpiarlos?" con opciones de "Sí, eliminar marcados" / "No, conservar todos" / "Revisar individualmente".

---

### 3.11. Integración con Inteligencia Artificial (LLM)

La aplicación incluirá una sección para que el usuario conecte su propia cuenta de LLM (OpenAI/GPT, Google Gemini, u otros proveedores compatibles). La integración proporcionará:

- **Contexto automático:** La LLM recibirá como contexto el texto de los versículos que el usuario está leyendo actualmente, así como las notas asociadas que estén visibles.
- **Chat contextual:** El usuario podrá hacer preguntas a la LLM con el contexto bíblico precargado, sin necesidad de copiar y pegar el texto manualmente.

#### 3.11.1. IA en Notas (Baja Prioridad)
Cuando el usuario cree una "Nota de versículo", la aplicación enviará en tiempo real a la LLM:
- Los versículos seleccionados.
- El texto que el usuario está escribiendo en la nota.

La LLM analizará el contenido y, si identifica un tópico coherente, sugerirá al usuario un título para convertir la nota en una "Nota de tópico" en lugar de mantenerla como simple "Nota de versículo". La sugerencia aparecerá como un banner discreto debajo del campo de título de la nota.

---

### 3.12. Ubicación de Elementos en la Interfaz

> **Nota:** La disposición exacta de los elementos se definirá en los mockups de diseño (Figma) que se producirán como entregable separado antes del inicio del desarrollo del frontend. Los siguientes lineamientos establecen las reglas de visibilidad y agrupación, no la posición exacta en píxeles.

#### 3.12.1. Elementos Embebidos en la Lectura de Versículos
Al mostrar versículos bíblicos, los siguientes elementos serán visibles en todo momento (siempre que el versículo cuente con la información correspondiente):
- Ícono de notas (para consultar y editar notas asociadas).
- Ícono de referencias cruzadas (para abrir el tooltip de referencias).

#### 3.12.2. Elementos Mostrados al Enfocar/Seleccionar Versículos
Al seleccionar versículos, se mostrará una barra de acciones contextual no intrusiva con:
- Crear Nota.
- Compartir.
- Copiar (con acceso a la configuración de formato de copiado).
- Crear Bookmark.

#### 3.12.3. Elementos Siempre Visibles (Barra Principal)
- **Selector de Biblia:** Idioma, versión, libro, capítulo y versículo. Son los elementos focales de la aplicación y deben ser accesibles en todo momento.
- **Botón de Paralelo.**
- **Historial** (las últimas 5 citas visitadas).
- **Bookmarks** (si existen).

#### 3.12.4. Menú de Hamburguesa (Opciones Avanzadas)
Un menú colapsable con las opciones:
- **Configuraciones:** Tema de la aplicación (claro/oscuro), tamaño de fuente de la aplicación, ocultar/mostrar notas, ocultar/mostrar referencias cruzadas, términos y condiciones.
- **Búsqueda** (Ctrl+F).
- **Comparar.**
- **Fuente del texto bíblico y de Strong:** fontSize, style, weight, verse flow (justificado o normal), número de columnas.

---

### 3.13. Recursos Fuera de Alcance

Las siguientes funcionalidades NO se incluirán en esta versión del producto:
- Mapas bíblicos.
- Diccionarios fuera de Strong.
- Comentarios bíblicos.
- Línea del tiempo.
- Libros u otros recursos auxiliares.
