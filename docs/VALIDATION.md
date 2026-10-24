# Validación de ViciontProtections 3.0.5

Compatibilidad objetivo: Spigot y Paper 1.21–26.2. El código se compila contra la API de Spigot 1.21 con bytecode Java 21 y no usa clases NMS. La API de diálogos de Spigot 1.21.6+ se carga solo cuando el servidor la incluye.

## Pruebas automatizadas

`mvn clean verify`: **29 pruebas**, sin fallos.

- Dimensiones exactas, bordes y límites inválidos.
- Jerarquía de creador/propietario/miembro y permisos explícitos.
- Instantáneas inmutables y detección de versiones con diálogos (incluido `26.2.build.129-stable`).
- CRUD SQLite, roles, banderas, prefijos SQL inválidos y migración 1.x idempotente.
- Copia SQLite → base vacía sin repetir la importación de 1.x.
- Paleta, degradados y prefijo `[Viciont Protections]`.
- Conversión de diálogos a la API de Spigot (formularios, opciones, confirmación y botones sin acción).
- Correcciones de 2.0.3–2.0.5: permisos de usuario opcionales, un solo botón en los diálogos sin acciones y devolución del bloque al eliminar.
- ESC cierra siempre: los botones de navegación pasan a la lista, el inferior solo cierra y las confirmaciones se convierten igual (también a la API de Spigot).
- «◀ Volver» queda siempre el último botón, justo encima de «✖ Salir», y no llega al cliente como campo extra.
- Clanes:
  - Rangos en español con y sin acentos, y un único líder por clan.
  - Permisos por defecto, cambios del líder y bloqueos del servidor.
  - Lectura de `clans.yml`.
  - Copia `protections-pre-clanes.db` una sola vez.
  - Ida y vuelta de clanes, solicitudes y guerras.
  - Copias de clanes disueltos y su caducidad.
  - Copia de clanes de SQLite a una base vacía.

## Clanes en servidores reales (3.0.0)

Los clanes se recorrieron con varios clientes automáticos (mineflayer) a la vez. Se comprobaron los diálogos recibidos, el chat, la action bar, las entidades de texto y el daño entre jugadores.

| Servidor | Java | Clanes | Notas |
| --- | --- | --- | --- |
| Paper 1.21.8 | 21 | Diálogos | LuckPerms sin OP, plugin de prueba de la API |
| Paper 1.21.1 | 21 | Chat con botones | Sin diálogos |
| Spigot 1.21.8 (BuildTools) | 21 | Diálogos de Spigot | |
| Paper 26.2 | 25 | Diálogos | QuasoPlugin con el banco de clanes, MariaDB |

Comprobado:

- Fundación:
  - Entregar bloques de clan, colocarlos y fundar con nombre y color.
  - Nombres repetidos o inválidos.
  - El bloque vuelve al inventario al cancelar o caducar.
  - Solo el líder rompe el bloque, y la disolución pide confirmación y devuelve el bloque.
- Miembros:
  - Invitaciones, aceptar y rechazar, caducidad y expulsiones.
  - Ascensos y degradaciones respetando el rango.
  - Transferencia del liderazgo, salida y disolución.
  - Límite de miembros.
- Permisos por rango dentro de los territorios (construir, cofres, puertas y animales), con cambios del líder en `/clan permisos` y casillas bloqueadas por el servidor.
- Territorios:
  - Aportar al momento o por solicitud, aceptar, rechazar y devolver con los roles anteriores.
  - Bloqueo de `/pr` sobre las zonas del clan.
  - Banderas comunes, límites con partículas y avisos al entrar y salir.
- Chat:
  - `/cc` y modo clan.
  - Etiqueta en el chat público.
  - Espía, registro en consola y archivo diario.
- Nombre del clan sobre el nick, con la variante ⚔ en guerra. No se ve sobre uno mismo y se oculta en espectador, invisible o con pasajeros. La altura se ajusta con `BELOW_NAME`.
- Guerras:
  - Declarar con motivo y preaviso.
  - PvP con la bandera `pvp` de WorldGuard denegada.
  - Bajas en la crónica.
  - Paz con contraoferta, rendición con tributo y cierre por la administración.
  - Espera entre guerras, protección de clanes nuevos y veto desde otro plugin.
- Cambio de bloque de clan de 15 × 15 a 25 × 25, con el centro en el bloque nuevo y en el mismo sitio. El territorio se mueve o crece, el bloque antiguo vuelve al inventario y se conservan miembros, rangos y banco. Si la zona choca con otra región, el cambio sigue pendiente.
- Copias de clanes disueltos:
  - Al colocar un bloque de clan, el líder elige entre fundar uno nuevo o recuperar el anterior.
  - La recuperación conserva miembros, rangos, permisos y saldo del banco, y no cobra depósito.
  - Los clanes disueltos por impago no guardan copia.
- Varios clanes por administración y `/clan activo`.
- Panel `/clan admin`.
- Permisos `clan.*` sin acceso a la administración.
- Uso desde consola.
- Datos:
  - MariaDB 10.11: tablas de clanes y copia `*_pre_clanes`.
  - Persistencia tras reiniciar.
  - Importación desde SQLite, y aviso sin importar cuando faltan zonas en MySQL.
- API de clanes con un plugin de prueba: botones, líneas de estado, casillas, temas de guía, requisitos de fundación con reembolso y eventos.
- Con las correcciones de 2.0.3–2.0.5:
  - Un jugador sin permisos de LuckPerms funda un clan con `permissions.require-user-permissions: false`.
  - Un bloque de clan no sirve para craftear.
  - `/pr eliminar confirmar` devuelve el bloque protector.
  - Los menús de clan sin botones muestran uno solo.
- Paneles y menús (3.0.1–3.0.3):
  - `/prclan` por secciones, con una protección personal y un clan: la zona del clan no sale como protección.
  - `/pr lista` sin zonas de clan.
  - `/pr` dentro de la zona del clan abre el menú del clan.
  - `/prclan` dentro de la protección personal y de la zona del clan abre su menú normal; fuera, la lista.
  - «◀ Volver» y «✖ Salir» al final de cada página.
  - Panel por parejas con y sin clan, y `/clan mios` con y sin clanes.
- QuasoPlugin (Paper 26.2):
  - Depósito al fundar, con un intento sin fondos y otro con fondos.
  - Aportar y retirar según las casillas.
  - Recompensa de rendición y deuda de guerra con veto a nuevas declaraciones.
  - Cobro y aviso del mantenimiento, y disolución por impago sin copia.

## Protecciones en servidores reales (2.0.2)

Cada servidor se arrancó con WorldEdit y WorldGuard y se recorrieron los comandos con clientes automáticos (mineflayer) conectados como jugadores, comprobando los mensajes de chat, la action bar, el contenido de cada diálogo recibido y los paquetes de partículas.

| Servidor | Java | WorldEdit | WorldGuard | Diálogos |
| --- | --- | --- | --- | --- |
| Paper 1.21.1 | 21 | 7.3.9 | 7.0.12 | No (chat) |
| Paper 1.21.8 | 21 | 7.3.19 | 7.0.14 | Paper |
| Spigot 1.21.8 (BuildTools) | 21 | 7.3.19 | 7.0.14 | Spigot |
| Paper 26.2 | 25 | 7.4.5 | 7.0.19 | Paper (cliente 26.1 vía ViaVersion/ViaBackwards) |

Comprobado:

- Guía, menú, información, listas (propias y `todas`), confirmación de borrado, panel de administración y todos los formularios.
- Menús por rol con jugadores sin OP y permisos de LuckPerms: creador, propietario añadido (solo miembros y límites) y miembro.
- Añadir/quitar miembros y propietarios, nombres válidos e inválidos, límites, banderas, transferir, entregar bloques, selección de WorldEdit, colocar y romper bloques protectores.
- WorldGuard deniega construir a un desconocido y lo permite tras añadirlo como miembro.
- Sin avisos `[Server: Displayed dialog ...]` a los OP ni registros de diálogos en consola.
- Comandos antiguos (`/prlist`, `/ownerlist`, `/memberlist`, `/addmember`, `/delowner`...), autocompletado y uso desde consola.
- Actualización real desde 1.1.1: protecciones creadas con el JAR 1.1.1, importadas al arrancar 2.0.2, bloques antiguos `small`/`medium` reconocidos y limpieza del `config.yml` antiguo.
- MariaDB 10.11 con el conector MySQL incluido: creación de tablas `utf8mb4`, copia automática desde SQLite (también con una base `latin1`), cambios y persistencia tras reiniciar.
- API: servicio, bloques de tienda, regiones sin bloque, mutaciones encadenadas, veto con `ProtectionChangingEvent` y eventos de cambio y entrada.

## Pendiente de revisión manual

Los clientes automáticos no dibujan la interfaz: falta ver con un cliente real el aspecto de los diálogos, las partículas y el nombre del clan sobre el nick. No se probó Oracle MySQL, PlaceholderAPI con los marcadores de clan ni cada versión intermedia entre 1.21.1 y 26.2. Las guerras se probaron con plazos acortados en `clans.yml`.
