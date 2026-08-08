## Descripción del software

La aplicación será muy robusta, así que tenemos que generar requerimientos bien definidos, lo más descriptivos posible y súper claros desde el inicio para no generar conflictos.

Permíteme describir la aplicación y sus requerimientos en un formato conversacional para que comprendas la idea en general y para que podamos aclarar todos los puntos y estar en la misma sintonía:

**La aplicación debe ser el mejor software de estudio de la Biblia (por sus funcionalidades sin sacrificar practicidad, eficiencia y compatibilidad).**

* **Uso de generadores**. Verdant es la mejor opción que estoy analizando, pero estoy abierto a escuchar otras sugerencias. La ventaja más fuerte que veo de Verdant es que podemos usarlo gratis durante un periodo de 3 días y, como tiene varios agentes, me imagino que podría hacer varias tareas simultáneas y coordinadas, a diferencia de otras herramientas.  
* **Aplicación Web**. Me estoy inclinando por desarrollar el software por completo como aplicación web. Sí, me interesa tenerlo en modo desktop y conectar ambos, pero me parece mejor iniciar con la versión web para utilizar primero los recursos que tenemos (me parece que Verdent solo ofrece un periodo de prueba de 3 días). La principal desventaja es que tendríamos que terminar en esos 3 días o nos meteríamos en problemas con la continuidad.  
* **Arquitectura**:  
  * Java \+ Spring Boot \+ Angular en sus últimas versiones.  
  * Aplicación Cliente-Servidor. Inicialmente, el software no usaría microservicios; si en un futuro se vieran necesarios, se podrían implementar sin problema.  
  * Usaremos una BD relacional (Postgres, abierto a ajustes sobre la marcha).  
  * La versión web debe ser compatible con la versión móvil, usando Progressive Web App.  
  * Formato OSIS. Utilizar el formato OSIS, el cual es estándar para almacenar biblias. El formato OSIS soporta también otro tipo de información fuera de biblias, pero este es el único que nos interesa por el momento.  
* **Look and Feel**: el diseño de la aplicación deberá ser moderno y estético, con un tema oscuro y opción para cambiar a otros temas al vuelo.  
  * **Mooks**. Me gustaría generar un diseño para la aplicación con todas las pantallas antes de implementar toda la funcionalidad de la aplicación. Es decir, tener toda la vista de las pantallas y su flujo antes de iniciar el desarrollo del back-end. ¿Es esto posible usando Verdent AI? ¿O será que me recomiendas otra herramienta para esto? En caso de que fuese otra herramienta, tendría que ser compatible con la herramienta de IA que usaremos para su implementación (en este caso, Verdent AI, a menos que también sugieras un cambio en ese rubro). El diseño debería ser fluido, los controles estándar y utilizar lo más posible los estándares de UX ya definidos apoyándonos en Angular y sus herramientas.  
    * Estrategia. Aquí hay 2 estrategias que podemos seguir,   
      * Generar mockups estilo Balsamiq. En donde solo generemos imágenes y pueda yo aprobar el orden y flujo de los elementos y posteriormente generar el código del front-end.  
      * Estilo Figma. U otros generadores donde a partir de las interfaces podemos generar el código del front-end.  
* **Modulos**:  
  * **Registro e inicio de sesión**. El usuario deberá poder tener un usuario. No es importante centrarnos en la seguridad por lo pronto, sino en la autorización y la identificación de los datos que se obtendrán del usuario.  
    * **Solo se almacena el email**. No necesitamos datos del usuario mas que identificarlo como único (una sesión); quizás requerimos su email solamente con este fin (el mail tendrá que ligarse a una sola cuenta).  
    * **SOO**. Debemos soportar SOO de Google, quizás otros como Meta serían convenientes también, pero no requerimos más que el mail del usuario.  
    * **Restaurar contraseña**. Funcionalidad regular de restauración de contraseña mediante e-mail.  
    * **Términos y condiciones**. Estoy pensando en usar biblias de uso público (sin necesidad de licencia o con licencias públicas) en el SW, pero permitir al usuario usar las suyas propias, ya sean personalizadas o aquellas con las que el usuario cuente con sus licencias. Nosotros no nos haremos cargo ni de almacenar sus licencias ni de requerírselas. En los términos y condiciones debemos incluir una declaración por parte del usuario que, al subir una versión a su cuenta, él declara tener su licencia y ser responsable no solo de su resguardo, sino de presentarla a quien se la requiriese, incluyéndonos a nosotros.  
      * **Menores**. Hay una leyenda que se debe incluir en los términos para excluir el uso por menores.  
  * **Versiones de la Biblia**.  
    * **Soporte de archivos e-sword**. Es posible recibir archivos que tiene e-sword .bblx tanto para biblias, pero no guardar nada en el servidor; de esta manera, el uso que le dé el usuario queda bajo su responsabilidad. Esta estrategia nos permitiría soportar formatos de recursos ya disponibles para e-sword (el SW más importante de estudio de la Biblia) sin tener que arriesgarnos a problemas legales.  
      * **E-sword \-\> OSIS Parser**. Al recibir el archivo, es posible transformarlo en un archivo OSIS para el uso de la aplicación y solo mediante esta transformación la aplicación podrá hacer uso de las biblias.  
      * **ID de Biblia**. Esto genera un tema que tenemos que decidir: si lo llegásemos a implementar así, tendríamos que definir alguna forma de nombrar las biblias para identificarlas y diferenciarlas unas de otras, quizás no podríamos hacerlas compatibles universalmente (entre usuarios), lo cual impactaría la funcionalidad de compartir versículos.  
      * **Detección de Strong**. Me gustaría que, si tenemos una versión de la Biblia con números de Strong, esta se identificara; por ejemplo, si el usuario sube la versión RVR60+ (el \+ indica Strong), el software (con confirmación de usuario) debe identificar la versión, independientemente de que contenga Strong; es decir, el SW debe identificar la versión: RVR60 y también identificar que tiene Strong (+).  
      * **Licencias**. Para esto es importante tu apoyo. ¿Qué versiones de la Biblia son de dominio público? Me gustaría poder tener como versiones fijas y, por lo tanto, poder citar específicamente las versiones 1602P, RV-SBT y RVG (Gómez), aunque entiendo que, por ejemplo, la RV-SBT no es pública y tiene problemas con licencias.  
        * **Versiones con licencia**. Para las versiones que no son públicas daremos oportunidad al usuario que consiga las licencias de estas Biblias y que pueda usarlas dentro de nuestro software sin necesidad que presente prueba (a nosotros) de que tiene esas licencias, es decir, volcaríamos la responsabilidad de presentar las licencias de las versiones que use dentro del software a quien se las llegase a requerir, incluso nosotros mismos podríamos solicitarle las licencias en caso de algun problema legal, pero de nuevo quien debe contar con las licencias y con su resguardo sería el usuario y no nosotros.  
        * **Compartir versiones con licencia**. Para estas versiones que no son públicas, en la práctica, la persona a la que se le comparte (quien abre la liga) no verá diferencia entre recibir una cita de una Biblia de uso público vs. una privada (personalizada) o con licencia que el usuario tiene. El  usuario debe poder compartir generando una url, pero quien abre esa url debe contar con la Biblia que el usuario está compartiendo, es decir, si la cita es "Jn 1.1 RV-1602P" el que abre esa url debe contar con la RV-1602P o de otra manera la aplicación abrirá la cita con la Biblia por defecto (la Biblia por defecto está pendiente por definir pero es la que utiliza and-biblie)  
        * **Almacenar en el servidor versiones con licencia**. Tocante a las versiones privadas o de licencia, es necesario definir si podemos almacenarlas; no creo que haya ningún problema con el hecho de que, una vez que el usuario suba su versión para ser usada en la aplicación, la aplicación la almacene en el servidor para que el usuario no tenga que estar subiendo cada vez que quiera usarla. Si esto es posible, tenemos que tener un límite máximo de versiones que puede usar el usuario.  
          * De igual manera,  si esto es posible, el usuario debería poder simplemente seleccionar otras versiones que la comunidad haya subido, SIEMPRE DECLARANDO QUE ÉL/ELLA TIENE LAS LICENCIAS CORRESPONDIENTES.  
    * Las biblias (sus archivos) deben soportar:  
      * Lenguaje  
      * Libros, sus capítulos y versículos.  
      * Dependiendo del tipo de Biblia, números de Strong.  
    * **Strong**. Si el usuario tiene la RVR60 sin strong, puede simplemente chequear un toggle o checkbox y el SW cambiará automáticamente a la versión con los números de strong en caso de que exista.  
  * **Referencias Cruzadas**. Los versículos deberán tener un icono de referencia cuando existan referencias cruzadas en el versículo, el cual, al hacer clic o tap en el mismo, se abrirá un tooltip con las referencias.  
  * **Notas**. El usuario podrá registrar notas.  
    * **Marca de nota**. Los versículos que tengan relacionadas notas deberán ser marcados de cierta manera (dejarémos que la LLM nos sugiera el control que podamos utilizar o tú mism@ puedes sugerirme el control a usar), por ejemplo podemos usar un símbolo de nota, de diferente color o forma dependiendo del tipo de nota asociada con el versículo a la derecha del mismo, pero quiero dejar esto abierto a sugerencias ya que no he visto muchas implementaciones de este tipo de cosas.  
    * **Notas de Usuario:**  
      * **Notas de tópico**. El usuario podrá ligar varios versículos a varios tópicos (relación ManyToMany).  
      * **Notas de versículo**. El usuario deberá poder registrar notas para un versículo (o el primer versículo seleccionado/enfocado).  
    * **Notas de usuario públicas por defecto**. Todas las notas del usuario serán públicas por defecto, pero el usuario puede elegir manualmente hacer sus notas individuales privadas. Para esto debe presentar algún tipo de inconveniente (como un segundo prompt o algo) para incentivar a que las notas sean públicas la mayoría del tiempo.  
      * **Notas de comunidad**. Al seleccionar algún versículo, el usuario deberá poder ver notas de la comunidad (otras notas públicas) y votarlas. Debemos también incentivar al usuario a votar (+1 ó \-1) a las notas de la comunidad que le hayan sido de utilidad. De esta manera podemos implementar nuestro sistema de sugerencia de notas.  
      * **Prioridad de notas de Comunidad**. El usuario podrá ver TODAS las notas públicas (cargadas con lazy loading según el usuario va scrolleándolas, estilo la carga de feed en las redes sociales), pero las notas estarán ordenadas de manera que a todos los usuarios se les sugieran las notas más votadas hasta mero arriba del feed.  
    * **Formato de notas**. Las notas deben usar/soportar formato de MarkDown.  
      * El usuario debe poder hacer clic en un botón (estilo GitHub «\>) para ver y editar el código fuente de su nota en formato MarkDown, pero esto debe estar muy escondido por defecto para que no estorbe la experiencia del usuario que no conoce el formato.  
    * **Hipervínculos en notas.** Las notas deben permitir hipervínculos en su texto; si el usuario selecciona un texto de su nota y pega un hipervínculo (lo que regularmente sustituiría el texto), la aplicación debería detectarlo como hipervínculo.  
  * **Formato de texto**  
    * **Cursivas**. Es común que en las traducciones bíblicas se utilicen palabras cursivas; éstas deberán aparecer en cursiva y en un color diferente (gris) para seguir el estándar.  
    * **Texto en rojo**. Algunas biblias tienen palabras resaltadas en rojo (e-sword tiene soporte para esto) y la aplicación debe soportarlo también.  
    * **Resaltado**. Lo más importante del subrayado es el tema; debe verse muy bien. Los colores del subrayado deben ser compatibles, agradables y modernos a la vista.  
      * **Resaltado de versículo**. El usuario debe poder subrayar/resaltar un versículo entero o varios versículos. Este tipo de subrayado/resaltado debe extrapolarse a todas las versiones de la Biblia que el usuario maneje.  
      * **Resaltado de texto**. El usuario debe poder subrayar el texto que sea parte de uno o varios versículos y que no abarque el versículo entero, por ejemplo, el usuario puede subrayar texto de la mitad de un versículo y solo se subrayará esa mitad; también podrá subrayar desde la mitad de un versículo hasta la mitad del siguiente (siempre subsecuentes) y se subrayará ese texto en específico, no los versículos enteros.  
  * **Compare / Parallel**. Deberá contar con las funciones normales de Comparar y Comparar en Paralelo que tienen muchos SW de este estilo.  
  * **Busqueda**. Ctrl+F deberá traer un diálogo de búsqueda personalizado de nuestra aplicación que permita buscar en el capítulo, en el libro, en toda la Biblia. Con un checkbox de palabras exactas (palabras en ese orden) o inexactas (en cualquier orden pero dentro de uno o 2 versículos).  
  * **Compartir**. Esta funcionalidad es muy importante.   
    * **Enfoque / Selección**. El usuario deberá poder seleccionar/enfocar versículos; en caso de que los seleccione haciendo clic o dando tap en la versión móvil, deberán ser temporalmente resaltados. Lo que sugiero es que el resto de los versículos en pantalla se atenúen.  
    * **Compartir**. El usuario deberá poder compartir los versículos con un enfoque previo indicado. La aplicación deberá generar una URL en base a los versículos enfocados / seleccionados  
    * **Copiar**. El usuario debe poder copiar el texto de los versículos. En este punto, parece que es relevante la licencia, ya que hay algunas versiones que no dejan copiar el texto a otros lados, pero asumiendo que el usuario tiene la licencia completa de uso absoluto sobre la versión no deberíamos impedirle copiarlos, especialmente si él/ella es el/la dueñ@.  
    * **Sharing Format**. El formato en el que se copie o se comparta el versículo debe ser estético, profesional y preferentemente configurable. Quizás podamos bajar la prioridad de la customización, pero el formato de copia es importante, específicamente.  
      * **Formato de copiado**. El usuario debe poder customizar el orden y formato de copiado. Por ejemplo, debe poder indicar si quiere la cita (libro+\#capítulo+\#versículo) con el versículo o solo el versículo o solo la cita; también debe poder especificar si quiere incluir la versión de la Biblia (por defecto debe incluirla), si quiere el nombre completo de la versión o su abreviación, por ejemplo, “Reina-Valera 1602 Purificada” vs. “RV1602P”. Debe también poder ajustar el orden, por ejemplo, primero la cita, luego el verso, luego la versión, o primero la versión, luego el verso, luego la cita, etc.  
  * **Historial**. Se mostrarán las últimas 5 citas (o capítulos) que el usuario ha visitado, con la opción de volver a ellas. La posición de este módulo es clave. Es importante que sea de fácil y rápido acceso. Debe haber una forma que siempre esté visible, si es posible (si no estorba al diseño de la lectura), para que el usuario en TODO momento pueda acceder a ella. No es necesario que esté visible cuando el usuario esté configurando la aplicación, pero sí cuando esté leyendo y navegando por la Biblia.   
  * **Bookmarks**. El usuario podrá, al seleccionar/enfocar algún versículo (o en hover), hacer clic en un icono de bookmark que aparecerá y esto lo agregará a una lista limitada de bookmarks a los que el usuario podrá acceder rápidamente haciendo click en ellos. Después de algunos días, el sistema marca los bookmarks como “en desuso”. Cada día, la aplicación validará si hay bookmarks en desuso y preguntará al usuario si quiere limpiar aquellos que están en desuso.  
  * **IA**. Incluimos una sección para que el usuario inicie sesión en su cuenta de LLM (GPT si es posible, o que pueda elegir la que desee). Que la LLM tenga contexto de lo que el usuario está viendo o leyendo, tanto de la Biblia como de las notas.  
    * **IA en notas**. Cuando el usuario intente crear *Notas de versículo*, la aplicación enviará en tiempo real los versículos seleccionados y lo que el usuario está (en tiempo real) escribiendo en la nota a la LLM para analizar si puede identificar un tópico y sugerirle al usuario un título para guardar las notas como *Notas de tópico,* en lugar de una simple *Nota de versículo*. (Esta funcionalidad sería de baja prioridad).  
  * **Ubicación temporal de los elementos**.  
    * **Embebidos en el uso de los versículos**:  
      * Al mostrar los versículos bíblicos se mostrarán los siguientes elementos, es decir, estas funcionalidades estarán en todo momento visibles al usuario siempre que los versículos cuenten con ellas:  
        * Símbolo para consultar y editar notas. Al dar clic en el símbolo, el sistema deberá mostrar las notas con todos sus controles.  
        * Símbolo para consultar referencias cruzadas. Al hacer click, la aplicación muestra el tooltip.  
        * Bookmarks. Si el usuario ya tiene bookmarks, aparecerán siempre que esté mostrando versículos en algún componente separado de la sección de lectura.  
        * Ubicación del historial. Siempre visible, de ser posible (sin sacrificar otros elementos) en algún componente separado de la sección de lectura.  
      * Al seleccionar los versículos (Enfocar) se mostrarán las opciones para hacer uso de estas funcionalidades de manera no intrusiva, para que no estorben la lectura y la experiencia limpia del usuario.  
        * Crear Nota.   
        * Compartir.  
        * Copiar. Al seleccionar el copiado se mostrará también la opción para modificar el formato de copiado predeterminado.  
        * Crear Bookmark  
    * **Siempre visibles**:  
      * **Versiones de la Biblia**. La selección del lenguaje de la Biblia, versión, capítulo y versículo debe ser siempre visible, pues son elementos focales de la aplicación.  
      * Parallelo  
      * **Menú de hamburguesa con opciones avanzadas**. Con los menús de:  
        * Configuraciones. Con: Tema de la aplicación (claro/oscuro,tamaño de fuente de la app, etc.) , ocultar notas, ocultar referencias, terms and conditions.  
        * Busqueda  
        * Comparar  
        * Fuente del texto de la Biblia y de Strong: fontSize, style, weight, verse flow (justificado o normal), \# de columnas, etc...  
  * **Recursos fuera de alcance**. No incluiremos mapas, ni soporte para otros diccionarios fuera de Strong, ni comentarios, ni línea del tiempo, ni libros u otros recursos por el estilo.

  