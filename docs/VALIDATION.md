# Validación de ViciontProtections 2.0.0

La compatibilidad objetivo es Spigot/Paper 1.21–26.2. El código se compila contra Spigot 1.21 con bytecode Java 21 y no enlaza clases NMS ni API exclusivas de Paper.

## Pruebas automatizadas

`mvn clean verify`: **11 pruebas**, sin fallos ni errores.

- Dimensiones exactas, bordes y límites inválidos.
- Jerarquía de creador/propietario/miembro, permisos explícitos y restricciones de administración.
- Instantáneas inmutables y detección del cambio de versión para diálogos, incluido `26.2.build.129-stable`.
- CRUD SQLite, roles, banderas y registros de mundos ausentes.
- Migración 1.x idempotente, conservación de las tablas antiguas y de los límites originales.
- Rechazo de prefijos SQL inválidos.

## Servidores internos

| Servidor | Java | WorldEdit | WorldGuard |
| --- | --- | --- | --- |
| Paper 1.21, build 130 | 21 | 7.3.9 | 7.0.12 |
| Paper 1.21.6, build 48 | 21 | 7.3.16 | 7.0.14 |
| Paper 26.2, build 129 | 25 | 7.4.5 | 7.0.19 |

Un plugin auxiliar, excluido del JAR distribuido, comprueba en los servidores:

- Arranque y registro del servicio API.
- Creación de bloques personalizados y recuperación de sus dimensiones desde PDC.
- Creación y consulta de regiones, rechazo de solapamientos y denegación real de construcción mediante WorldGuard.
- Escrituras encadenadas de miembros, propietarios, nombres y banderas.
- Protección del creador frente a su eliminación como propietario añadido.
- Lectura de datos persistidos tras reiniciar.
- En 1.21.6 y 26.2: validación de guía, menús, información, listas, confirmación y formularios mediante el codec nativo de diálogos de Minecraft.

SQLite se comprueba en las tres versiones. El backend MySQL se comprueba con **MySQL Connector/J y MariaDB 11.4.13**, incluyendo creación de tablas, operaciones y recuperación al reiniciar en Paper 26.2. Esto no constituye una ejecución independiente sobre Oracle MySQL.

Los servidores están limitados a localhost. Las peticiones externas de claves públicas de Mojang fallan en este entorno de red; se distinguen de los errores del plugin y no validan la autenticación online.

## Alcance pendiente de validación manual

No se han conectado clientes reales: quedan por revisar el aspecto de los diálogos, los clics, las partículas y la colocación/rotura durante el juego con los otros plugins del servidor. Spigot puro y cada versión intermedia no se han arrancado individualmente. Los diálogos se validan como datos nativos, no mediante capturas de pantalla del cliente.

Para actualizar un servidor existente, conserva primero una copia de los datos y utiliza las versiones de WorldEdit/WorldGuard adecuadas. Las últimas versiones de esas dependencias pueden necesitar Java 25 aunque el servidor antiguo admita Java 21.
