# Cambios

## 3.0.6

- Colores HEX libres para los clanes: `/clan color #B75FFF`, administración y fundación aceptan cualquier
  `#RRGGBB` válido, sin registrarlo antes en la paleta. Se conservan permisos, colores preestablecidos y
  persistencia SQL, incluidas las copias de clanes disueltos.
- Campo de texto HEX en los diálogos de fundación y cambio de color, con acciones separadas para aplicar
  el selector o el código escrito. Al abrir un clan con color propio se conserva su color actual.
- Nuevo tema **Color del clan** en `/clan guia`, disponible también al actualizar un `clans.yml` existente
  sin sobrescribir los textos personalizados.
- Integración acompañante **QuasoPlugin #174**: mantenimiento diario por miembro, cobrado del banco común;
  con cuota 2 y 10 miembros se pagan 20 DinoCoins. Se actualizan el menú, los avisos y el tema del banco
  de `/clan guia`. Para activar el cobro por miembro hay que instalar también el JAR de QuasoPlugin enlazado
  en la release; ViciontProtections no cobra monedas por su cuenta.
- No se modifica la API pública ni el esquema SQL.

## 3.0.5

### Novedades

- El panel de `/prclan` va en dos columnas, por parejas:
  - «۞ Mis protecciones (N)» abre la lista; al lado, tu última protección (la última que creaste o, si no, la última a la que te añadieron) o «Ninguna».
  - «⚑ Mis clanes (N)» abre tus clanes; al lado, tu clan o «Ninguno».
  - «Guía de protecciones» y «Guía de clanes».
- `/clan mios`: tus clanes, y cada uno entra directo a su menú. La lista de clanes del servidor, con sus fichas, sigue en el menú del clan.

## 3.0.4

### Correcciones

- `/prclan` dentro de una protección tuya o de un territorio de tu clan salía como lista en una columna. Ahora abre el menú normal de ese sitio, y su «◀ Volver» lleva al panel. La lista por secciones queda solo para cuando estás fuera, o con `/prclan inicio`.

## 3.0.3

### El clan engloba su zona

- La zona que crea un clan al fundarse, y las que se le aportan, ya no aparecen como protecciones aparte. No salen en «Mis protecciones» ni en `/pr lista`, que avisa de que se gestionan desde el menú del clan.
- Con `/pr` dentro de una zona de tu clan se abre el menú del clan. La administración sigue viendo la protección.
- `/prclan` va en una sola columna y por secciones: **Mis protecciones**, con las personales, y **Mis clanes**, con cada clan una vez y tu rango. Debajo van las guías. En el menú del sitio donde estás no se repiten ese sitio ni su guía.

## 3.0.2

### Menús más claros

- Todas las páginas acaban igual: sus botones, **◀ Volver** a la página anterior y, abajo del todo, **✖ Salir**. Se quitan los botones que hacían de «volver» con otros nombres, como «Rangos» en los permisos, «Temas de la guía» o «Menú del clan» en la guía, y «Cancelar» en las confirmaciones.
- `/prclan` ya no copia la guía de protecciones. Si estás en una protección tuya o en un territorio de tu clan, abre su menú y añade al final tus otras protecciones, tus clanes y las guías. Si no, muestra el panel de inicio con todo eso. `/prclan inicio` abre siempre el panel.
- El «Volver» del menú de protección, del menú del clan, de las guías y de las listas lleva al panel de inicio.
- El menú de protección empieza con el botón **Guía de protecciones**, igual que el del clan con su guía.
- Las páginas de las listas y de la guía usan «« Anterior» y «Siguiente »», para no confundirlas con «Volver».

## 3.0.1

### Novedades

- `/prclan`: una página con las guías de protecciones y de clanes a la vez. Incluye tus protecciones, la protección donde estás, tu clan o tus invitaciones, y un botón para cada tema de la guía de clanes.
- El menú del clan y la portada sin clan empiezan con el botón **Guía de clanes**.

### Correcciones

- ESC ya no vuelve atrás página tras página: ahora cierra el menú desde cualquier página. Todos los menús tienen abajo un botón **✖ Salir**, y los botones que llevan a otra página («Volver», «Guía…», «Cancelar» en las confirmaciones) pasan a la lista de botones.

## 3.0.0

Viciont Clans llega a ViciontProtections. Incluye también las correcciones de 2.0.3, 2.0.4 y 2.0.5.

### Clanes

- Clanes con nombre, color y territorio. Se fundan colocando un **bloque de clan** (`/pr dar … clan`), que abre el diálogo para elegir nombre y color.
- Cinco rangos: líder, sublíder, oficial, aprendiz e invitado. Tienen permisos por defecto en `clans.yml`, casillas que el líder ajusta en `/clan permisos` y casillas que el servidor puede bloquear.
- Invitaciones con caducidad, expulsiones, ascensos, transferencia del liderazgo, salida y disolución con confirmación.
- Límite de 30 miembros por clan (`clanes.miembros-maximos`).
- Territorios:
  - Las protecciones propias se aportan al clan; líder y sublíderes al momento, el resto por solicitud.
  - Las zonas aportadas se devuelven con sus roles anteriores.
  - Dentro de los territorios, los rangos controlan construir, cofres, puertas y animales.
  - `/pr` no cambia el acceso de una zona del clan.
  - Banderas comunes, bordes con partículas y avisos al entrar y salir.
- Cambio de bloque de clan: el líder coloca uno más grande y elige si el centro sigue en el mismo sitio (el bloque nuevo ocupa el lugar del antiguo) o pasa al bloque nuevo. El clan no se disuelve y el bloque antiguo vuelve a su inventario.
- Copias de los clanes disueltos durante 5 días (`clanes.copia-dias`). Al colocar un bloque de clan, su antiguo líder elige entre fundar uno nuevo o recuperar el anterior. Los clanes disueltos por no pagar el mantenimiento no guardan copia.
- Chat del clan:
  - `/cc` y modo clan.
  - Etiqueta del clan en el chat público.
  - Avisos de plugins conectados.
  - `/clan espiar` para la administración.
  - Registro en consola y en `logs/chat-clanes-AAAA-MM-DD.log`.
- Nombre del clan encima del nick con una entidad de texto, sin usar `BELOW_NAME` ni marcadores. Se ajusta su altura, se oculta al propio jugador y respeta la invisibilidad y el vanish.
- Guerras de rol:
  - Se declaran con un motivo y empiezan tras un preaviso de 24 horas.
  - Durante la guerra hay PvP entre los dos clanes también donde WorldGuard lo deniega.
  - Terminan por rendición con tributo, por paz con contraofertas o al caducar.
  - Llevan una crónica de bajas, un tiempo de espera entre guerras y protección para clanes nuevos.
- Menús de clan en diálogos (1.21.6+) y en el chat con botones, con la paleta amarillo pastel, dorado y naranja con toques morados y el prefijo `[Viciont Clans]`.
- Guía de clanes por temas en `/clan guia`, ampliable por plugins y enlazada con la de protecciones.
- Panel `/clan admin` para gestionar cualquier clan y cerrar guerras. Permisos `viciontprotections.clan.*`, que siguen `permissions.require-user-permissions` como los de usuario (de todos por defecto), y `viciontprotections.admin.clan.*`, solo de OP.
- Marcadores de PlaceholderAPI: `%viciont_clan%`, `_nombre`, `_rango`, `_color` y `_guerra`.

### API

- Servicio `ViciontClansApi` para consultar clanes, rangos, permisos y guerras, y para fundar, disolver (normal o por impago), invitar, añadir territorios, avisar al clan y anotar recompensas.
- Extensiones: botones del menú, líneas de estado, casillas de permiso, temas de guía y requisitos de fundación con cobro y reembolso, como un depósito.
- Eventos `ClanFoundEvent`, `ClanDisbandEvent`, `ClanMemberEvent`, `ClanWarEvent`, `ClanChangedEvent` y `ClanChatEvent`.

### Datos

- Siete tablas nuevas de clanes con el mismo prefijo. Antes de crearlas se guarda una copia, `protections-pre-clanes.db` o tablas `*_pre_clanes`.
- Los territorios siguen siendo protecciones con sus roles sincronizados, así que volver a 2.0.x conserva las zonas y sus accesos.
- Al pasar de SQLite a MySQL se copian también los clanes.

## 2.0.5

### Correcciones

- Eliminar una protección desde el menú (o con `/pr eliminar confirmar`) seguía sin devolver el bloque protector en 2.0.4: el bloque ya se había quitado del suelo cuando se comprobaba. Ahora se comprueba antes de eliminar y el bloque vuelve al inventario.
- El bloqueo de crafteos solo mira los huecos de entrada: comprar un bloque protector a un aldeano o sacar lo ya fundido de un horno ya no se bloquea por error. También se tapan el yunque, la mesa de cartografía y los aldeanos, y el horno no gasta combustible intentando fundir un bloque protector.

## 2.0.4

### Correcciones

- Al eliminar una protección desde el menú (o con `/pr eliminar confirmar`) el bloque protector se quedaba puesto como bloque normal y no se devolvía. Ahora se quita del suelo y vuelve al inventario de quien la elimina (en creativo solo se quita; desde la consola cae en su sitio). Solo se devuelve si el bloque seguía puesto, así no se puede duplicar.
- Los bloques protectores ya no sirven para craftear ni fundir: mesa de crafteo, crafter, herrería, cortapiedras, piedra de afilar, telar y hornos los rechazan. Antes, por ejemplo, un bloque protector de netherite daba 9 lingotes de netherite.

## 2.0.3

### Novedades

- Opción `permissions.require-user-permissions` en `config.yml`, desactivada por defecto: así cualquier jugador puede colocar bloques protectores y gestionar sus protecciones sin plugin de permisos, como en 1.x. Con `true`, los permisos `viciontprotections.user.*` vuelven a ser solo de OP. Se aplica al arrancar y con `/pr recargar`; los de administración siguen siendo de OP.

### Correcciones

- La guía de `/proteccion` mostraba dos botones «Cerrar» a quien no tenía protecciones ni el permiso de listar.

## 2.0.2

### Correcciones

- Casi todos los comandos (`/pr`, `/pr guia`, `/pr lista`, `/pr info`, `/pr eliminar`, `/prlist`, `/ownerlist`...) respondían «No se pudo completar la operación. Consulta la consola». Los diálogos se abrían con `/dialog show <UUID>`, que Minecraft rechaza; ahora se usan la API de diálogos de Spigot o un emisor silencioso en Paper.
- Los errores inesperados se registran en la consola con su traza (antes no se registraba nada).
- Ya no aparece `[Server: Displayed dialog to ...]` a los OP cada vez que alguien abre un menú.
- La lista vacía ya no rompe su diálogo y todos los diálogos tienen al menos un botón.
- WorldGuard con las regiones desactivadas en un mundo ya no provoca errores al moverse ni impide arrancar.
- Al actualizar desde 1.x se retiran las secciones `messages` y `villager` del `config.yml` (con copia en `config-1.x-anterior.yml`).

### Novedades

- Prefijo `[Viciont Protections]` en degradado y paleta morada/rosa configurable en `messages.yml` (hex, `&`, `<gradient>` y colores como `{dato}`), con etiquetas propias para Admin, WorldGuard y WorldEdit. `messages.yml` se actualiza solo y guarda una copia del anterior.
- Menús rediseñados según el rol: el creador gestiona miembros, propietarios, nombre, límites y eliminación; los propietarios añadidos solo miembros y límites; la administración además banderas, transferencia y panel.
- Los diálogos vuelven a abrirse con el resultado de cada acción, añaden jugadores con un clic y quitan miembros o propietarios desde su lista.
- Diálogo «Nombra tu protección» al colocar un bloque protector, y nombres automáticos como «Protección de Alex».
- Panel `/pr admin` con formularios para entregar bloques, proteger selecciones de WorldEdit, ver todas las protecciones y recargar.
- Nuevos comandos `/pr miembros`, `/pr propietarios` y `/pr lista todas`; `/pr seleccion` acepta el nombre como opcional.
- Listas, fichas y menús de chat con botones y descripciones al pasar el ratón para versiones sin diálogos.
- Bordes con degradado animado morado y rosa pastel, dos alturas y columnas en las esquinas.
- Al pasar de SQLite a MySQL, las protecciones se copian automáticamente a la base vacía. Las tablas MySQL/MariaDB se crean en `utf8mb4`.


## 2.0.0

- Seguridad regional delegada a WorldGuard y creación desde selecciones de WorldEdit.
- Jerarquía separada de creador, propietarios añadidos y miembros; permisos por defecto para OP.
- Bloques de materiales y dimensiones variables, nombres personalizados y listas paginadas.
- Comandos unificados con alias antiguos y autocompletado.
- Diálogos vanilla desde 1.21.6, guía integrada y enlaces configurables para Spigot/Modrinth.
- Mensajes españoles en morados y rosas, action bar al entrar y límites personales con partículas.
- Almacenamiento transaccional SQLite/MySQL, caché de consultas e importación de datos 1.x.
- API pública mediante servicios Bukkit y eventos cancelables.
- Compatibilidad objetivo 1.21–26.2 con bytecode Java 21.
- Retirada de intercambios de aldeanos.
