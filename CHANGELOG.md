# Cambios

## 3.0.0: Viciont Clans

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
- Cambio de bloque de clan: el líder coloca uno más grande y confirma. El territorio principal se mueve sin disolver el clan y el bloque antiguo vuelve a su inventario.
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
- Panel `/clan admin` para gestionar cualquier clan y cerrar guerras. Permisos `viciontprotections.clan.*` y `viciontprotections.admin.clan.*`.
- Marcadores de PlaceholderAPI: `%viciont_clan%`, `_nombre`, `_rango`, `_color` y `_guerra`.

### API

- Servicio `ViciontClansApi` para consultar clanes, rangos, permisos y guerras, y para fundar, disolver (normal o por impago), invitar, añadir territorios, avisar al clan y anotar recompensas.
- Extensiones: botones del menú, líneas de estado, casillas de permiso, temas de guía y requisitos de fundación con cobro y reembolso, como un depósito.
- Eventos `ClanFoundEvent`, `ClanDisbandEvent`, `ClanMemberEvent`, `ClanWarEvent`, `ClanChangedEvent` y `ClanChatEvent`.

### Datos

- Siete tablas nuevas de clanes con el mismo prefijo. Antes de crearlas se guarda una copia, `protections-pre-clanes.db` o tablas `*_pre_clanes`.
- Los territorios siguen siendo protecciones con sus roles sincronizados, así que volver a 2.0.2 conserva las zonas y sus accesos.
- Al pasar de SQLite a MySQL se copian también los clanes.

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
