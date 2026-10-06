# Validación de ViciontProtections 2.0.2

Compatibilidad objetivo: Spigot y Paper 1.21–26.2. El código se compila contra la API de Spigot 1.21 con bytecode Java 21 y no usa clases NMS. La API de diálogos de Spigot 1.21.6+ se carga solo cuando el servidor la incluye.

## Pruebas automatizadas

`mvn clean verify`: **17 pruebas**, sin fallos.

- Dimensiones exactas, bordes y límites inválidos.
- Jerarquía de creador/propietario/miembro y permisos explícitos.
- Instantáneas inmutables y detección de versiones con diálogos (incluido `26.2.build.129-stable`).
- CRUD SQLite, roles, banderas, prefijos SQL inválidos y migración 1.x idempotente.
- Copia SQLite → base vacía sin repetir la importación de 1.x.
- Paleta, degradados y prefijo `[Viciont Protections]`.
- Conversión de diálogos a la API de Spigot (formularios, opciones, confirmación y botones sin acción).

## Servidores reales

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

Los clientes automáticos no dibujan la interfaz: falta ver con un cliente real el aspecto de los diálogos y de las partículas. No se probó Oracle MySQL ni cada versión intermedia entre 1.21.1 y 26.2.
