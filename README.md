<div align="center">

# ۞ ViciontProtections

**Tu terreno, tu nombre, tus reglas.**

Protecciones con WorldGuard, bloques personalizables y una interfaz en tonos morados y rosas.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21–26.2-C8A2E8)
![Java](https://img.shields.io/badge/Java-21%2B-EAB5D5)
![Versión](https://img.shields.io/badge/Versión-2.0.2-B995E8)
![Licencia](https://img.shields.io/badge/Licencia-MIT-E8B9D9)

[Guía](#-guía-para-jugadores) · [Comandos](#-comandos) · [Permisos](#-permisos-y-roles) · [API](docs/API.md)

</div>

## ۞ Qué incluye

- Protecciones con nombre, creador, propietarios añadidos y miembros.
- Seguridad de WorldGuard: construcción, contenedores, interacciones y prevención de solapamientos.
- Bloques protectores de cualquier material colocable y dimensiones configurables, también rectangulares y con altura limitada.
- Menú con diálogos desde **1.21.6** (Spigot y Paper), con guía integrada; menús de chat pulsables en versiones anteriores.
- Prefijo `[Viciont Protections]` en degradado, paleta morada y rosa configurable y colores propios para administración, WorldGuard y WorldEdit.
- Aviso de entrada por chat y action bar, y límites personales con partículas en degradado morado y rosa pastel.
- SQLite incluido o MySQL con creación automática de tablas.
- API pública con servicio Bukkit, eventos y operaciones asíncronas de persistencia.

## ۞ Instalación

1. Utiliza Spigot/Paper **1.21–26.2** y la versión de Java que requiere tu servidor. El plugin se compila para Java 21; Minecraft 26.2 requiere Java 25.
2. Instala **WorldEdit y WorldGuard para tu versión exacta de Minecraft**. No están incluidos en este JAR.
3. Coloca `ViciontProtections-2.0.2.jar` en `plugins/` y reinicia el servidor.
4. Configura permisos, `plugins/ViciontProtections/config.yml` y `messages.yml`.

Los diálogos usan la API nativa de Spigot 1.21.6+ y, en Paper, el comando vanilla `/dialog` con un emisor silencioso (sin avisos a los OP ni registros en consola). Consulta [VALIDATION.md](docs/VALIDATION.md) para ver qué versiones se han probado y cómo.

No uses `/reload` ni recargadores de plugins. `/pr recargar` recarga mensajes y ajustes; cambiar la conexión SQL necesita reiniciar.

## ۞ Guía para jugadores

### Tu primera protección

1. Consigue un bloque protector mediante la administración, una tienda o un plugin integrado.
2. Colócalo en una zona libre. Se crea una protección con nombre automático.
3. Dentro de ella, usa `/pr nombre Mi casa` para personalizarla.
4. Usa `/pr miembro añadir Alex` para dar acceso a un amigo.
5. Consulta `/pr lista` y `/pr limites` para localizar tus protecciones y ver su borde.

Las nuevas dimensiones son **exactas**: un bloque de 32 × 32 protege 32 bloques en cada eje horizontal. `todo` cubre toda la altura del mundo. El bloque protector queda centrado; con dimensiones pares, el lado negativo tiene un bloque más que el positivo.

Solo el creador o un administrador puede romper el bloque protector. Al romperlo en supervivencia se elimina la protección y se devuelve el bloque. **Eliminarla por comando o API no devuelve un objeto.** Las protecciones creadas desde una selección de WorldEdit no necesitan un bloque físico.

### Menú y guía

- `/proteccion`, `/protections` y `/pr` abren el menú en servidor y cliente **1.21.6 o posterior**, tanto en Spigot como en Paper.
- Fuera de una protección se abre la guía. Dentro, el botón **Guía de protecciones** queda al final del diálogo.
- Al colocar un bloque protector se abre directamente el diálogo **Nombra tu protección**.
- Cada acción del menú vuelve a abrir el diálogo con el resultado (por ejemplo «✔ Alex ahora es miembro»), sin tener que mirar el chat.
- Los botones dependen de tu rol y se vuelven a comprobar al pulsarlos:

| Rol | Botones del menú |
| --- | --- |
| Creador | Añadir/quitar miembro, añadir/quitar propietario, cambiar nombre, límites, información, eliminar |
| Propietario añadido | Añadir/quitar miembro y límites |
| Miembro | Datos de la protección y guía |
| Administración | Todo lo anterior, banderas de WorldGuard, transferir creador y panel de administración |

- El panel de administración (`/pr admin`) incluye formularios para entregar bloques de cualquier material y tamaño, proteger una selección de WorldEdit, ver todas las protecciones y recargar.
- `/pr guia` está disponible para todos. En versiones anteriores a 1.21.6 los menús, listas e información se muestran en el chat con botones pulsables y descripciones al pasar el ratón.
- Con ViaVersion instalado se comprueba también la versión del cliente. Puedes desactivar los diálogos con `dialogs.enabled: false`.

La visualización de límites es personal y temporal: no muestra partículas a todos los jugadores y se desactiva al desconectarte o perder acceso.

## ۞ Comandos

Alias principales: `/proteccion`, `/pr`, `/protections`, `/vp` y `/proteccionv`.

| Comando | Función |
| --- | --- |
| `/pr` | Menú o guía según tu ubicación y versión |
| `/pr guia` | Guía y enlaces de las páginas del plugin |
| `/pr ayuda` | Resumen de comandos |
| `/pr lista [página]` | Lista paginada de tus protecciones |
| `/pr lista todas [página]` | Todas las protecciones del servidor (administración) |
| `/pr info` | Nombre, ID, dimensiones, creador, propietarios y miembros |
| `/pr miembros` · `/pr propietarios` | Ver los jugadores con un botón para quitar a cada uno |
| `/pr nombre <nombre>` | Cambiar el nombre, hasta 48 caracteres |
| `/pr miembro <añadir\|quitar> <jugador\|UUID>` | Gestionar miembros |
| `/pr propietario <añadir\|quitar> <jugador\|UUID>` | Gestionar propietarios añadidos |
| `/pr limites` | Mostrar u ocultar tus partículas de borde |
| `/pr eliminar` | Pedir confirmación para eliminar la protección |
| `/pr admin` | Panel de administración (diálogo) |
| `/pr dar <jugador> <bloque> <ancho> [profundidad] [altura\|todo] [cantidad]` | Entregar bloques protectores |
| `/pr seleccion <creador> [nombre]` | Proteger tu selección cúbica de WorldEdit |
| `/pr bandera <bandera> <permitir\|denegar\|restablecer>` | Configurar una bandera de estado de WorldGuard |
| `/pr transferir <jugador\|UUID>` | Cambiar el creador; el anterior queda como propietario añadido |
| `/pr recargar` | Recargar ajustes y mensajes |

Para gestionar una protección a distancia, antepón `@ID`: `/pr @a1b2c3d4 miembro añadir Alex`. Puedes usar el UUID completo o el ID corto mostrado en la lista. No concede permisos adicionales.

Los destinatarios por nombre deben estar conectados o haber entrado al servidor; también puedes utilizar su UUID. `/pr dar` requiere que el destinatario esté conectado.

### Ejemplos de administración

```text
/pr dar Alex DIAMOND_BLOCK 32
/pr dar Alex AMETHYST_BLOCK 45 21 todo 2
/pr dar Alex GOLD_BLOCK 20 12 15 1
/pr seleccion Alex Mercado central
/pr bandera pvp denegar
```

Por defecto el límite de dimensiones de los bloques es 4096, configurable con `protections.max-block-size`. El límite de creaciones por jugador es 20; `admin.manage` lo omite. Una región nueva no puede atravesar otra región de WorldGuard ni el borde del mundo.

`/pr bandera` sin argumentos lista las banderas admitidas. Se excluyen `build` y `passthrough` para conservar el control por miembros. Los mensajes de integración de ViciontProtections tienen su propia paleta; **`/rg` y `//set` conservan los mensajes de sus respectivos plugins**. Gestiona las banderas de estas protecciones con `/pr bandera`: la sincronización reconstruye las regiones a partir de los datos del plugin.

Se mantienen los comandos antiguos: `/givepr`, `/addnamepr`, `/newnamepr`, `/addmember`, `/delmember`, `/addowner`, `/delowner`, `/ownerlist`, `/memberlist`, `/prlist`, `/modnamepr`, `/modmember`, `/modowner`, `/removepr` y `/vpreload`. `/ownerlist` y `/memberlist` equivalen a `/pr propietarios` y `/pr miembros`. `/givepr small|medium|large` sigue funcionando. Los comandos `mod*` reciben primero el nombre sin espacios o ID de la protección; miembros/propietarios usan después `añadir|quitar <jugador>`. `/removepr <ID> confirmar` elimina después de confirmarlo.

El sistema de intercambios de aldeanos y `/prvillager` se han retirado.

## ۞ Permisos y roles

**Todos los permisos de gestión tienen `default: op`.** La guía y la ayuda son públicas. Para permitir el uso normal a jugadores, concede el grupo de permisos de usuario; por ejemplo, con LuckPerms:

```text
/lp group default permission set viciontprotections.user.* true
```

Los permisos Bukkit habilitan la función; el rol dentro de la protección determina sobre qué protección puedes usarla.

| Acción | Creador | Propietario añadido | Miembro |
| --- | :---: | :---: | :---: |
| Construir y usar contenedores | ✓ | ✓ | ✓ |
| Consultar información | ✓ | ✓ | ✓ |
| Añadir/quitar miembros | ✓ | ✓ | — |
| Mostrar/ocultar límites | ✓ | ✓ | — |
| Añadir/quitar propietarios | ✓ | — | — |
| Cambiar nombre o eliminar | ✓ | — | — |
| Cambiar creador o banderas | Solo administración | Solo administración | — |

| Permiso | Función |
| --- | --- |
| `viciontprotections.user.*` | Todas las funciones de usuario, respetando el rol |
| `viciontprotections.user.create` | Colocar un bloque protector |
| `viciontprotections.user.list` | Consultar listas |
| `viciontprotections.user.info` | Consultar información |
| `viciontprotections.user.members` | Gestionar miembros |
| `viciontprotections.user.owners` | Gestionar propietarios añadidos |
| `viciontprotections.user.name` | Cambiar nombres |
| `viciontprotections.user.delete` | Eliminar protecciones propias |
| `viciontprotections.user.borders` | Activar/desactivar límites |
| `viciontprotections.admin.*` | Todas las funciones administrativas |
| `viciontprotections.admin.manage` | Gestionar cualquier protección y omitir el límite por jugador |
| `viciontprotections.admin.give` | Entregar bloques |
| `viciontprotections.admin.selection` | Crear desde WorldEdit |
| `viciontprotections.admin.flags` | Editar banderas |
| `viciontprotections.admin.transfer` | Cambiar creador |
| `viciontprotections.admin.list` | Ver todas las protecciones en la lista |
| `viciontprotections.admin.reload` | Recargar configuración |

`admin.manage` no concede automáticamente `dar`, `seleccion`, `recargar` o acceso a listas. Para administración completa usa `admin.*` junto con `user.*`, o conserva OP.

## ۞ Mensajes y enlaces

Todos los mensajes están en español, con el símbolo **۞** y el prefijo **`[Viciont Protections]`** en degradado morado → rosa. La administración, WorldGuard y WorldEdit tienen su propia etiqueta y color (`Admin »`, `WorldGuard »`, `WorldEdit »`).

En `messages.yml` puedes cambiar los prefijos, la paleta, los avisos de entrada/salida (chat y action bar), el aviso de creación, el mensaje de WorldGuard al denegar una acción y la guía. Formatos admitidos:

| Formato | Ejemplo |
| --- | --- |
| Hex | `&#F5B3DA` |
| Códigos clásicos | `&l`, `&o` |
| Degradado | `<gradient:#C29BFF:#F5A8D6>Viciont Protections</gradient>` |
| Paleta | `{titulo}` `{texto}` `{suave}` `{dato}` `{acento}` `{error}` `{admin}` `{worldguard}` `{worldedit}` |

Los colores de la sección `colores:` se aplican también a menús, listas y diálogos. Un mensaje vacío (`''`) lo desactiva. Cuando una actualización trae mensajes nuevos, el plugin guarda tu archivo como `messages-vN-anterior.yml` y crea uno actualizado.

En `config.yml`, completa los enlaces cuando publiques las páginas de guía:

```yaml
guide:
  spigot: ''
  modrinth: ''
```

Usa direcciones `https://`. Un campo vacío oculta ese enlace y mantiene visible la guía integrada. Después ejecuta `/pr recargar`.

## ۞ Almacenamiento y actualización

SQLite funciona sin configuración adicional. Para MySQL, crea una base de datos y un usuario con permisos sobre ella; el plugin crea sus tablas `vp_meta`, `vp_protections` y `vp_roles`.

```yaml
database:
  type: mysql
  filename: protections.db
  table-prefix: vp_
  mysql:
    host: localhost
    port: 3306
    database: viciontprotections
    username: viciont
    password: 'TU_CONTRASEÑA'
    ssl-mode: PREFERRED
```

Las tablas de MySQL/MariaDB se crean en `utf8mb4`, así que los nombres con acentos u otros alfabetos funcionan aunque la base use otra codificación. Las escrituras son transaccionales y se ejecutan fuera del hilo principal. Las consultas durante el movimiento utilizan la caché y el índice de WorldGuard. Si una escritura falla, el plugin intenta restaurar el estado anterior y avisa al usuario.

### Desde 1.x

1. Detén el servidor y guarda una copia de las carpetas de ViciontProtections y WorldGuard.
2. Instala WorldEdit/WorldGuard compatibles y reemplaza el JAR del plugin.
3. Arranca y revisa la consola. El importador conserva las tablas antiguas; con SQLite también crea `protections-pre-2.0.db` una sola vez. Las secciones `messages` y `villager` de tu `config.yml` antiguo se retiran (los mensajes ahora están en `messages.yml`) y se guarda una copia en `config-1.x-anterior.yml`.
4. Comprueba tus regiones y concede los nuevos permisos de usuario a tus grupos.

La migración conserva creador, propietarios, miembros y **los límites inclusivos originales**: una protección antigua configurada como 32 abarcaba 33 bloques y seguirá abarcándolos. Los bloques ya entregados con los tres CustomModelData antiguos siguen siendo reconocidos. Las protecciones sin nombre reciben uno automáticamente.

Si configuras MySQL al actualizar desde 1.x y conservas el SQLite antiguo en la carpeta del plugin, se importa a las nuevas tablas. La migración es idempotente. Si faltan dueños principales o hay datos inválidos, el inicio se detiene para que puedas corregirlos; no se borra la información antigua.

**Para pasar de SQLite a MySQL** basta con cambiar `database.type` y reiniciar: si la base MySQL está vacía, el plugin copia las protecciones del archivo SQLite (que se conserva como copia) y anota la importación para no repetirla. Cada servidor debe usar su propia base o prefijo: no hay sincronización en vivo entre varios servidores. Conserva los nombres de los mundos al mover sus datos.

## ۞ Para desarrolladores

Consulta [la documentación de la API](docs/API.md): crear regiones sin bloques, entregar objetos para tiendas, consultar acceso, gestionar miembros/propietarios, banderas y eventos cancelables.

Compila con JDK 21 o posterior y Maven:

```bash
mvn clean verify
```

El JAR de `target/ViciontProtections-2.0.2.jar` incluye SQLite, el conector MySQL y el pool de conexiones. No incluye WorldEdit, WorldGuard ni la API del servidor. También se genera un JAR de fuentes. Los repositorios de Spigot y EngineHub deben ser accesibles durante la compilación.

## ۞ Créditos y licencia

Creado por **CrissyjuanxD**. Integración mediante las API públicas de [WorldGuard](https://enginehub.org/worldguard) y [WorldEdit](https://enginehub.org/worldedit). [ProtectionStones](https://github.com/espidev/ProtectionStones) se consultó como referencia de integración y permisos; esta implementación no incorpora su código.

Distribuido bajo la [licencia MIT](LICENSE).
