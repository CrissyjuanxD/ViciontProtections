<div align="center">

# ۞ ViciontProtections

<img src="https://github.com/CrissyjuanxD/imagenes_ropositorios/blob/main/VP_TITLE.png" alt="Viciont Protections" />

**Tu terreno, tu nombre, tus reglas.**

Protecciones con WorldGuard, clanes con territorio y guerras, bloques personalizables y una interfaz en tonos morados y rosas.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21–26.2-C8A2E8)
![Java](https://img.shields.io/badge/Java-21%2B-EAB5D5)
![Versión](https://img.shields.io/badge/Versión-3.0.4-B995E8)
![Licencia](https://img.shields.io/badge/Licencia-MIT-E8B9D9)

[Guía](#-guía-para-jugadores) · [Clanes](#-viciont-clans) · [Comandos](#-comandos) · [Permisos](#-permisos-y-roles) · [API](docs/API.md)

</div>

## ۞ Qué incluye

- Protecciones con nombre, creador, propietarios añadidos y miembros.
- Seguridad de WorldGuard: construcción, contenedores, interacciones y prevención de solapamientos.
- Bloques protectores de cualquier material colocable y dimensiones configurables, también rectangulares y con altura limitada.
- Menú con diálogos desde **1.21.6** (Spigot y Paper), con guía integrada; menús de chat pulsables en versiones anteriores.
- Prefijo `[Viciont Protections]` en degradado, paleta morada y rosa configurable y colores propios para administración, WorldGuard y WorldEdit.
- Aviso de entrada por chat y action bar, y límites personales con partículas en degradado morado y rosa pastel.
- **Viciont Clans**: clanes con bloque de clan, territorio, cinco rangos con permisos ajustables, chat privado, nombre sobre el nick, guerras de rol, copias de 5 días y API para bancos y otros plugins.
- SQLite incluido o MySQL con creación automática de tablas.
- API pública con servicio Bukkit, eventos y operaciones asíncronas de persistencia.

## ۞ Instalación

1. Utiliza Spigot/Paper **1.21–26.2** y la versión de Java que requiere tu servidor. El plugin se compila para Java 21; Minecraft 26.2 requiere Java 25.
2. Instala **WorldEdit y WorldGuard para tu versión exacta de Minecraft**. No están incluidos en este JAR.
3. Coloca `ViciontProtections-3.0.4.jar` en `plugins/` y reinicia el servidor.
4. Configura `plugins/ViciontProtections/config.yml`, `messages.yml` y `clans.yml`. Los permisos son opcionales: sin plugin de permisos cualquier jugador puede usar las funciones de usuario y de clanes (consulta [Permisos y roles](#-permisos-y-roles)).

PlaceholderAPI es opcional: si está instalado, se registran los marcadores de clan.

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
- Todas las páginas acaban igual: sus botones, **◀ Volver** a la página anterior y, abajo del todo, **✖ Salir**. ESC hace lo mismo que «Salir» y cierra el menú desde cualquier página.
- `/prclan` es el panel de inicio, en una columna y por secciones: **Mis protecciones** (solo las personales), **Mis clanes** y las guías. Si estás dentro de una protección tuya o de un territorio de tu clan, abre su menú normal, y su «Volver» lleva al panel. `/prclan inicio` abre siempre el panel, y el «Volver» de las portadas de `/pr` y `/clan` lleva ahí.
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

## ۞ Viciont Clans

Desde la 3.0.0 el plugin incluye clanes: grupos de jugadores con nombre, color, territorio protegido, rangos, chat privado y guerras de rol. Sus mensajes llevan el prefijo **`[Viciont Clans]`** con una paleta propia de amarillo pastel, dorado y naranja con toques morados. `/clan guia` explica todo el sistema por temas, también a quien no tiene clan, y enlaza con la guía de protecciones.

### Fundar un clan

1. La administración, una tienda o un plugin entrega un **bloque de clan** (`/pr dar <jugador> <bloque> <ancho> … clan`). Lleva el nombre «۞ Bloque de clan» y su tamaño en la descripción.
2. Un jugador sin clan lo coloca en terreno libre. Se abre el diálogo para elegir **nombre** (3–24 caracteres) y **color**, con los requisitos de los plugins conectados, como un depósito inicial.
3. La zona del bloque pasa a ser el **territorio principal** y el fundador es el **líder**. Si no confirma en 5 minutos, el bloque vuelve a su inventario.

**El clan engloba su zona**: no cuenta como una protección tuya, no sale en «Mis protecciones» ni en `/pr lista`, y con `/pr` dentro de ella se abre el menú del clan, donde están sus territorios, límites, banderas y el cambio de bloque. La administración la sigue viendo como protección.

Solo el líder puede romper el bloque de clan, y romperlo **disuelve el clan** tras confirmarlo. Las protecciones aportadas vuelven a sus dueños con sus roles anteriores.

**Cambiar a un bloque más grande.** El líder coloca el nuevo bloque de clan (por ejemplo, de 300 × 300 en lugar de 150 × 150) en el mismo mundo, por ejemplo al lado del actual, y elige el centro del territorio:

- **En el mismo sitio**: el bloque nuevo ocupa el lugar del antiguo y la zona crece a su alrededor. El bloque que acaba de colocar se retira.
- **Donde está el bloque nuevo**: la zona se centra en él, y lo que quede fuera deja de estar protegido.

En los dos casos el bloque antiguo vuelve a su inventario y el clan conserva miembros, rangos, permisos, territorios y banco. La nueva zona puede incluir la antigua, pero no otras regiones; si choca con alguna, el plugin lo avisa y deja elegir la otra opción o cancelar.

**Copias de seguridad.** Al disolver un clan (rompiendo el bloque, por su líder o por la administración) se guarda una copia durante **5 días** (`clanes.copia-dias`). Cuando su antiguo líder coloca un bloque de clan, el plugin le pregunta si quiere **fundar uno nuevo o recuperar el anterior**, con nombre, color, rangos, permisos, banderas y los miembros que no se hayan unido a otro clan. Las zonas aportadas no vuelven, porque ya regresaron a sus dueños, pero se pueden aportar de nuevo. La recuperación no cobra el depósito. **Los clanes disueltos por no pagar el mantenimiento no guardan copia.**

### Rangos, permisos y miembros

| Rango | Por defecto |
| --- | --- |
| Líder | Todo: permisos, ajustes, banderas, guerras, transferir y disolver |
| Sublíder | Lo del oficial, y además asciende, degrada y acepta territorios |
| Oficial | Lo del aprendiz, y además abre cofres, invita y expulsa |
| Aprendiz | Construye, usa animales, ve los límites y aporta territorios |
| Invitado | Entra, usa puertas y botones y habla en el chat del clan |

Nadie actúa sobre alguien de su mismo rango o superior. El líder cambia cada casilla por rango en `/clan permisos`; el servidor puede fijar casillas en `clans.yml`, y los plugins conectados añaden las suyas (por ejemplo, aportar o retirar del banco). Un clan admite **30 miembros** por defecto (`clanes.miembros-maximos`), contando al líder. Cada jugador pertenece a un solo clan; la administración puede ponerlo en varios y él elige cuál mostrar con `/clan activo`.

### Territorios

- Dentro de una protección propia, `/clan territorio aportar` la pasa al clan. Líder y sublíderes la aportan al momento; los demás rangos envían una solicitud que caduca en 7 días.
- En los territorios mandan los rangos del clan: construir, cofres, puertas y animales según sus casillas. Quien no es del clan pierde el acceso, y el menú lo avisa antes de confirmar.
- Mientras una zona sea del clan, `/pr` no cambia sus miembros, propietarios, creador ni la elimina.
- `/clan territorio quitar` devuelve una zona a su dueño con sus roles anteriores. `/clan limites` muestra el borde de todos los territorios.
- El líder ajusta las banderas de WorldGuard de todos los territorios a la vez con `/clan bandera`, entre las permitidas en `clans.yml`.
- Al entrar y salir de un territorio se avisa por chat y action bar.

### Chat y nombre sobre el nick

- `/cc <mensaje>` habla solo con el clan. `/cc` sin texto activa el **modo clan**, en el que todo lo que escribes va al clan.
- En el chat público aparece `[Clan]` con su color delante del nombre. Si prefieres colocarlo tú, desactívalo y usa `%viciont_clan%` de PlaceholderAPI.
- Cada mensaje del clan se registra en la consola (`[ChatClan]`) y en `logs/chat-clanes-AAAA-MM-DD.log`. Con `admin.clan.spy`, `/clan espiar` muestra todos los chats de clan.
- El **nombre del clan sale encima del nick** de cada jugador, con su color y ⚔ durante una guerra. Es una entidad de texto que sigue al jugador, sin usar `BELOW_NAME` ni el marcador lateral, así que convive con otros plugins que ya los usan. Su altura se ajusta si hay algo debajo del nombre. El propio jugador no la ve, y se oculta al estar invisible, en espectador, muerto, con algo encima (como una burbuja de chat) o con la etiqueta de nombre oculta por un equipo.

### Guerras

1. El líder declara la guerra con un motivo: `/clan guerra declarar <clan> <motivo>`.
2. Pasadas **24 horas** de preaviso, hay PvP entre los dos clanes en cualquier lugar, también donde WorldGuard lo deniega. Romper, construir o abrir cofres del enemigo sigue prohibido.
3. Termina cuando un líder **se rinde** (con un tributo y condiciones) o ambos firman la **paz**, que admite contraofertas. Sin final en 14 días, acaba sin vencedor.

Solo luchan los miembros con 24 horas en el clan, nunca los invitados. Un clan solo tiene una guerra a la vez, los clanes con menos de 3 días no reciben declaraciones, y dos clanes esperan 7 días para volver a enfrentarse. Las bajas quedan en la crónica de la guerra. Todos los plazos están en `clans.yml`.

### Banco y plugins conectados

El banco, el mantenimiento diario, el depósito para fundar y las recompensas de guerra **no están en este plugin**: los añade otro plugin mediante la [API de clanes](docs/API.md#clanes). Por ejemplo, QuasoPlugin implementa un banco de DinoCoins con su botón en el menú, sus casillas de permiso, sus líneas de estado y su tema en la guía. Sin plugins conectados, fundar es gratis y no hay mantenimiento.

### Comandos de clanes

`/clan` (alias `/clanes`, `/clans`) abre el menú de tu clan, o la guía y tus copias si no tienes ninguno; su primer botón es la **Guía de clanes**. `/prclan` abre el menú del sitio donde estás o, fuera de todo, el panel con tus protecciones, tus clanes y las dos guías.

| Comando | Función |
| --- | --- |
| `/prclan` · `/prclan inicio` | Panel de inicio: el menú de donde estás, tus protecciones, tus clanes y las guías |
| `/clan guia [tema]` · `/clan ayuda` | Guía por temas y lista de comandos |
| `/clan lista [página]` · `/clan info [clan]` | Clanes del servidor y su ficha |
| `/clan fundar recuperar <clan>` · `/clan fundar nuevo` | Tras colocar un bloque de clan con una copia guardada |
| `/clan aceptar <clan>` · `/clan rechazar <clan>` | Responder a una invitación |
| `/clan miembros` · `/clan invitar <jugador> [rango]` · `/clan expulsar <jugador>` | Gestionar miembros |
| `/clan rango <jugador> <rango>` · `/clan ascender` · `/clan degradar` | Cambiar rangos |
| `/clan transferir <jugador>` | Pasar el liderazgo; el antiguo líder queda como sublíder |
| `/clan territorio aportar\|quitar` · `/clan territorios` · `/clan solicitudes` | Territorios y solicitudes |
| `/clan limites` | Mostrar u ocultar el borde de los territorios |
| `/clan permisos` · `/clan ajustes` | Casillas por rango, nombre, color y banderas |
| `/clan nombre <nombre>` · `/clan color <color>` · `/clan bandera <bandera> <valor>` | Ajustes del líder |
| `/clan bloque [mantener\|confirmar\|cancelar]` | Cambiar el bloque de clan por el que acabas de colocar, con el centro en el mismo sitio (`mantener`) o en el bloque nuevo (`confirmar`) |
| `/clan guerra [declarar\|rendirse\|paz]` | Estado y acciones de guerra |
| `/clan chat` · `/clan modo` · `/cc [mensaje]` | Chat del clan |
| `/clan activo <clan>` | Elegir el clan que muestras, si estás en varios |
| `/clan salir` · `/clan disolver` | Salir del clan o disolverlo (con confirmación) |
| `/clan espiar` | Ver todos los chats de clan (administración) |
| `/clan admin <clan> [info\|añadir\|quitar\|rango\|nombre\|color\|disolver]` | Gestionar cualquier clan (administración) |
| `/clan admin guerra terminar <clan>` | Cerrar una guerra (administración) |

### Permisos de clanes

| Permiso | Por defecto | Función |
| --- | --- | --- |
| `viciontprotections.clan.*` | Todos | `clan.use` y `clan.create` |
| `viciontprotections.clan.use` | Todos | Usar `/clan` y `/cc` según el rango en cada clan |
| `viciontprotections.clan.create` | Todos | Fundar un clan colocando un bloque de clan |
| `viciontprotections.admin.clan.*` | OP | Las tres de administración de clanes |
| `viciontprotections.admin.clan.spy` | OP | `/clan espiar` |
| `viciontprotections.admin.clan.manage` | OP | `/clan admin`: miembros, rangos, varios clanes, nombre, color y disolver |
| `viciontprotections.admin.clan.war` | OP | `/clan admin guerra terminar` |

Los permisos de clan de los jugadores siguen la opción `permissions.require-user-permissions`, igual que los de usuario: con `false` (por defecto) son de todos, y con `true` solo de OP y hay que concederlos, por ejemplo con `/lp group default permission set viciontprotections.clan.* true`. Los nodos de administración están bajo `admin.clan.*` para que `clan.*` no los conceda; siempre son de OP y `viciontprotections.admin.*` los incluye.

### clans.yml y PlaceholderAPI

`plugins/ViciontProtections/clans.yml` controla la paleta y los prefijos, los nombres de los rangos, los colores elegibles, los permisos por defecto y bloqueados, las banderas permitidas, los formatos del chat y de la etiqueta, los avisos de territorio, los plazos de guerra, el límite de miembros, los días de copia y los textos de la guía. `/pr recargar` lo recarga. Con `clanes.activos: false` se desactivan los clanes.

Con PlaceholderAPI instalado: `%viciont_clan%` (etiqueta con color), `%viciont_clan_nombre%`, `%viciont_clan_rango%`, `%viciont_clan_color%` y `%viciont_clan_guerra%` (clan enemigo).

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
| `/pr dar <jugador> <bloque> <ancho> [profundidad] [altura\|todo] [cantidad] [clan]` | Entregar bloques protectores, o de clan con `clan` al final |
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
/pr dar Alex BEACON 150 150 todo 1 clan
/pr seleccion Alex Mercado central
/pr bandera pvp denegar
```

Por defecto el límite de dimensiones de los bloques es 4096, configurable con `protections.max-block-size`. El límite de creaciones por jugador es 20; `admin.manage` lo omite. Una región nueva no puede atravesar otra región de WorldGuard ni el borde del mundo.

`/pr bandera` sin argumentos lista las banderas admitidas. Se excluyen `build` y `passthrough` para conservar el control por miembros. Los mensajes de integración de ViciontProtections tienen su propia paleta; **`/rg` y `//set` conservan los mensajes de sus respectivos plugins**. Gestiona las banderas de estas protecciones con `/pr bandera`: la sincronización reconstruye las regiones a partir de los datos del plugin.

Se mantienen los comandos antiguos: `/givepr`, `/addnamepr`, `/newnamepr`, `/addmember`, `/delmember`, `/addowner`, `/delowner`, `/ownerlist`, `/memberlist`, `/prlist`, `/modnamepr`, `/modmember`, `/modowner`, `/removepr` y `/vpreload`. `/ownerlist` y `/memberlist` equivalen a `/pr propietarios` y `/pr miembros`. `/givepr small|medium|large` sigue funcionando. Los comandos `mod*` reciben primero el nombre sin espacios o ID de la protección; miembros/propietarios usan después `añadir|quitar <jugador>`. `/removepr <ID> confirmar` elimina después de confirmarlo.

El sistema de intercambios de aldeanos y `/prvillager` se han retirado.

## ۞ Permisos y roles

**Por defecto, los permisos de usuario y de clanes son de todos**: cualquier jugador puede colocar bloques protectores, gestionar sus protecciones y usar los clanes sin plugin de permisos. Los de administración (`viciontprotections.admin.*`) son solo de OP. La guía y la ayuda son públicas.

Se controla en `config.yml`:

```yaml
permissions:
  require-user-permissions: false
```

Con `true`, los permisos de usuario y de clanes pasan a ser solo de OP y hay que concederlos con un plugin de permisos; por ejemplo, con LuckPerms:

```text
/lp group default permission set viciontprotections.user.* true
/lp group default permission set viciontprotections.clan.* true
```

El cambio se aplica al arrancar y con `/pr recargar`. Con cualquiera de los dos valores, un plugin de permisos puede seguir dando o quitando permisos concretos.

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

`admin.manage` no concede automáticamente `dar`, `seleccion`, `recargar` o acceso a listas. Para administración completa usa `admin.*` junto con `user.*`, o conserva OP. Los permisos de clanes están en [Permisos de clanes](#permisos-de-clanes).

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

SQLite funciona sin configuración adicional. Para MySQL, crea una base de datos y un usuario con permisos sobre ella; el plugin crea sus tablas `vp_meta`, `vp_protections` y `vp_roles`, y las de clanes (`vp_clans`, `vp_clan_members`, `vp_clan_permissions`, `vp_clan_territories`, `vp_clan_requests`, `vp_clan_wars` y `vp_clan_backups`).

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

### Desde 2.0.x

Reemplaza el JAR y reinicia. Antes de crear las tablas de clanes, el plugin guarda una copia de la base: `protections-pre-clanes.db` con SQLite, o tablas `*_pre_clanes` con MySQL. Los territorios de los clanes siguen siendo protecciones normales, con el líder como creador, los sublíderes como propietarios y el resto como miembros, así que volver a un JAR 2.0.x conserva las zonas y sus accesos.

### Desde 1.x

1. Detén el servidor y guarda una copia de las carpetas de ViciontProtections y WorldGuard.
2. Instala WorldEdit/WorldGuard compatibles y reemplaza el JAR del plugin.
3. Arranca y revisa la consola. El importador conserva las tablas antiguas; con SQLite también crea `protections-pre-2.0.db` una sola vez. Las secciones `messages` y `villager` de tu `config.yml` antiguo se retiran (los mensajes ahora están en `messages.yml`) y se guarda una copia en `config-1.x-anterior.yml`.
4. Comprueba tus regiones. Los jugadores conservan el acceso a las funciones de usuario como en 1.x, salvo que actives `permissions.require-user-permissions`.

La migración conserva creador, propietarios, miembros y **los límites inclusivos originales**: una protección antigua configurada como 32 abarcaba 33 bloques y seguirá abarcándolos. Los bloques ya entregados con los tres CustomModelData antiguos siguen siendo reconocidos. Las protecciones sin nombre reciben uno automáticamente.

Si configuras MySQL al actualizar desde 1.x y conservas el SQLite antiguo en la carpeta del plugin, se importa a las nuevas tablas. La migración es idempotente. Si faltan dueños principales o hay datos inválidos, el inicio se detiene para que puedas corregirlos; no se borra la información antigua.

**Para pasar de SQLite a MySQL** basta con cambiar `database.type` y reiniciar: si la base MySQL está vacía, el plugin copia las protecciones del archivo SQLite (que se conserva como copia) y anota la importación para no repetirla. Los clanes se copian con ellas; si falta en MySQL alguna zona de un clan, no se copian y la consola lo avisa. Cada servidor debe usar su propia base o prefijo: no hay sincronización en vivo entre varios servidores. Conserva los nombres de los mundos al mover sus datos.

## ۞ Para desarrolladores

Consulta [la documentación de la API](docs/API.md): crear regiones sin bloques, entregar objetos para tiendas, consultar acceso, gestionar miembros/propietarios, banderas y eventos cancelables. La [API de clanes](docs/API.md#clanes) añade consultas y acciones sobre clanes, botones, casillas de permiso, líneas de estado, temas de guía, requisitos de fundación y eventos.

Compila con JDK 21 o posterior y Maven:

```bash
mvn clean verify
```

El JAR de `target/ViciontProtections-3.0.4.jar` incluye SQLite, el conector MySQL y el pool de conexiones. No incluye WorldEdit, WorldGuard, PlaceholderAPI ni la API del servidor. También se genera un JAR de fuentes. Los repositorios de Spigot, EngineHub y PlaceholderAPI deben ser accesibles durante la compilación.

## ۞ Créditos y licencia

Creado por **CrissyjuanxD**. Integración mediante las API públicas de [WorldGuard](https://enginehub.org/worldguard) y [WorldEdit](https://enginehub.org/worldedit). [ProtectionStones](https://github.com/espidev/ProtectionStones) se consultó como referencia de integración y permisos; esta implementación no incorpora su código.

Distribuido bajo la [licencia MIT](LICENSE).
