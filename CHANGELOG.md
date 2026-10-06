# Cambios

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
