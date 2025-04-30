<h1 align="center"> Viciont Protections </h1>

<p align="center">
<img src="https://github.com/CrissyjuanxD/imagenes_ropositorios/blob/main/VP_TITLE.png" />
</p>

Un basico y flexible sistema de protecciones para servidores de Minecraft que permite a los jugadores crear y gestionar áreas protegidas.

## Características

- Crea zonas de protección de diferentes tamaños personalizables
- Sistema de múltiples propietarios
- Gestión de miembros
- Nombres personalizados para las protecciones
- Límites visuales con bloques de lana amarilla temporales (1 minuto)
- Sistema de comercio con aldeano personalizado
- Almacenamiento en base de datos SQLite

## Comandos

### Comandos para Jugadores
| Comando                | Descripción                                   | Permiso                       |
|------------------------|-----------------------------------------------|-------------------------------|
| `/addnamepr <nombre>`  | Nombra tu protección                          | viciontprotections.user.name  |
| `/addmember <jugador>` | Añade un jugador a tu protección              | viciontprotections.user.add   |
| `/delmember <jugador>` | Elimina un jugador de tu protección           | viciontprotections.user.del   |
| `/addowner <jugador>`  | Añade un propietario a tu protección          | viciontprotections.user.owner |
| `/delowner <jugador>`  | Elimina un propietario de tu protección       | viciontprotections.user.owner |
| `/ownerlist`           | Lista todos los propietarios de tu protección | viciontprotections.user.owner |
| `/memberlist`          | Lista todos los miembros de tu protección     | viciontprotections.user.owner |
| `/prlist`              | Lista todas las protecciones                  | Ninguno                       |

El comando /addowner y /delowner solo pueden ser utilizados por el propietario principal de la protección.
Al ser propietaria normal de la protection, el jugador puede añadir o eliminar miembros de tu protección.

### Comandos de Administrador
| Comando                                       | Descripción                                                | Permiso                           |
|-----------------------------------------------|------------------------------------------------------------|-----------------------------------|
| `/givepr <tipo>`                              | Da un bloque de protección                                 | viciontprotections.admin.give     |
| `/newnamepr <nombre>`                         | Establece nombre para una protección sin nombre            | viciontprotections.admin.name     |
| `/modnamepr <protección> <nuevo_nombre>`      | Modifica el nombre de una protección                       | viciontprotections.admin.name     |
| `/modmember <protección> <add/del> <jugador>` | Modifica miembros de la protección (Añadir o eliminar)     | viciontprotections.admin.mod      |
| `/modowner <protección> <add/del> <jugador>`  | Modifica propietarios de la protección (Añadir o eliminar) | viciontprotections.admin.owner    |
| `/removepr <protección>`                      | Elimina una protección                                     | viciontprotections.admin.remove   |
| `/prvillager [x] [y] [z]`                     | Genera un comerciante de protecciones                      | viciontprotections.admin.villager |
| `/vpreload`                                   | Recarga la configuración del plugin                        | viciontprotections.admin.reload   |

## Tipos de Protección Base
- Pequeña (32x32)
- Mediana (64x64)
- Grande (128x128)

## Características de las Protecciones
- Límites visuales (bloques de lana amarilla durante 1 minuto)
- Sistema de múltiples propietarios
- Gestión de miembros
- Nombres personalizados
- Protección anti-solapamiento
- Protección de bloques
- Protección de interacciones

## Sistema de Comercio
El plugin incluye un aldeano comerciante personalizado que vende bloques de protección.
Los trades son configurables a tus gustos, puedes añadir 1 como 2 items por trade.

Ejemplos:

```yaml
  large:
    name: "Protección Premium 320x320"
    size: 320
    cost:
      - IRON_NUGGET:2
      - NETHERITE_BLOCK:2
```

```yaml
  small:
    name: "Protección Básica"
    size: 32
    cost:
      - DIAMOND:1
```

## Configuración
Todas las configuraciones se pueden personalizar en `config.yml`:
- Costos de las protecciones
- Mensajes
- Configuración del aldeano
- Configuración de la base de datos
- Tamaño de las protecciones
- Nombre del bloque de protección

_(**NOTA:** Cambiar de nombre un bloque de protección podría afectar a los bloques de protecciones que ya existen, por lo que se recomienda 
cambiar el nombre del bloque de protección al inicio de la creación del plugin o mantener el nombre por defecto)_

## Permisos
### Permisos de Usuario
```yaml
viciontprotections.user.*:
  description: Acceso a todos los comandos de usuario
  children:
    viciontprotections.user.name: true
    viciontprotections.user.add: true
    viciontprotections.user.del: true
    viciontprotections.user.owner: true
```

### Permisos de Administrador
```yaml
viciontprotections.admin.*:
  description: Acceso a todos los comandos de administrador
  children:
    viciontprotections.admin.give: true
    viciontprotections.admin.mod: true
    viciontprotections.admin.owner: true
    viciontprotections.admin.remove: true
    viciontprotections.admin.name: true
    viciontprotections.admin.villager: true
```

## Instalación
1. Descarga el archivo JAR del plugin
2. Colócalo en la carpeta `plugins` de tu servidor
3. Reinicia el servidor
4. Configura el plugin en `config.yml` si es necesario

## Versiones Compatibles
Compatible con las version de Minecraft:
- 1.21 - 1.21.1 - 1.21.2(Not Tested)

Versiones que tengo pensada que sean compatibles:
- 1.20.x
- 1.19.x

## Base de Datos
El plugin utiliza SQLite para el almacenamiento de datos, lo que facilita su configuración sin necesidad de configuración adicional.

## Soporte
Como es uno de mis primeros plugins en hacer, tampoco es tan profesional y soy relativamente nuevo en la programación, pero creo que quedo bastante decente.

Creo que este plugin puede llegar ser muy util en ciertos servidores que no se quieren comerse la cabeza con los permisos y que solo funcione 
para jugadores y operadores.

Si tienes alguna duda o sugerencia, no dudes en abrir un issue en el repositorio.


## Licencia
Este proyecto está licenciado bajo la Licencia MIT - consulta el archivo LICENSE para más detalles.