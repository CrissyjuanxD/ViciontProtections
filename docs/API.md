# API de ViciontProtections 2.0

La API permite integrar tiendas, recompensas, generación de parcelas y otros sistemas sin ejecutar comandos ni escribir SQL. Los tipos públicos están en `com.viciont.viciontprotections.api`; `Protection` es un registro inmutable en `com.viciont.viciontprotections.models`.

## Dependencia

Compila contra el JAR de ViciontProtections con alcance `provided`. Si lo instalas en tu repositorio Maven local:

```bash
mvn install:install-file -Dfile=ViciontProtections-2.0.0.jar -DgroupId=com.viciont -DartifactId=ViciontProtections -Dversion=2.0.0 -Dpackaging=jar
```

```xml
<dependency>
    <groupId>com.viciont</groupId>
    <artifactId>ViciontProtections</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

No empaquetes una copia de esta API dentro de tu plugin. Declara en tu `plugin.yml`:

```yaml
depend: [ViciontProtections]
```

Si la integración es opcional, usa `softdepend` y carga tu clase de integración solo cuando ViciontProtections esté habilitado.

## Obtener el servicio

```java
ViciontProtectionsApi api = Bukkit.getServicesManager()
        .load(ViciontProtectionsApi.class);
if (api == null) {
    throw new IllegalStateException("ViciontProtections no está disponible");
}
```

No dependas de `ProtectionManager`, las clases de interfaz o la estructura interna de las tablas.

## Bloques para tiendas y recompensas

Ejecuta las operaciones con `ItemStack` en el hilo principal:

```java
ProtectionBlock spec = new ProtectionBlock(Material.AMETHYST_BLOCK, 32, 24, 0);
ItemStack item = api.createProtectionBlock(spec, 1);
Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
// Tu tienda decide cómo manejar los objetos que no caben antes de cobrar.
```

El orden es **material, ancho X, profundidad Z, altura Y**. Altura `0` significa toda la altura del mundo. El material debe ser un bloque colocable con objeto de inventario. La cantidad debe caber en una pila de ese material.

`api.readProtectionBlock(item)` devuelve `Optional<ProtectionBlock>` y también reconoce los bloques de 1.x. Los identificadores se guardan en PDC; cambiar solamente el nombre o el lore de un objeto normal no lo convierte en protector.

## Crear una región sin bloque

```java
Bounds bounds = new Bounds(100, -64, 100, 131, 319, 123);
CreateProtectionRequest request = CreateProtectionRequest.region(
        player.getUniqueId(), player.getWorld().getName(), bounds, "Tienda de Alex");

api.createProtection(request).whenComplete((protection, error) -> {
    if (error != null) {
        player.sendMessage("No se pudo crear la protección.");
        return;
    }
    player.sendMessage("Protección creada: " + protection.name());
});
```

Los seis límites son inclusivos. Para centrar un tamaño exacto:

```java
Bounds bounds = Bounds.centered(x, y, z, width, depth, height,
        world.getMinHeight(), world.getMaxHeight());
```

La creación comprueba mundo cargado, borde del mundo, límites verticales y solapamientos con regiones WorldGuard. Una cadena `name` vacía crea un nombre automático. Los nombres nuevos admiten letras, números, espacios, `.`, `_`, `#` y `-`, hasta 48 caracteres.

Para asociar una región a un bloque físico, proporciona `Anchor` al constructor de `CreateProtectionRequest`. La API **no coloca ese bloque**: tu integración debe colocarlo y coordinar su reversión si falla la creación. El ancla debe estar dentro de los límites. Al eliminar la protección se retira el bloque del ancla si conserva el material original.

## Consultas y mutaciones

| Método | Resultado |
| --- | --- |
| `getProtection(UUID)` | Instantánea por ID |
| `getProtectionAt(Location)` | Protección en una posición; hilo principal |
| `getProtections()` | Todas, ordenadas por nombre e ID |
| `getProtections(UUID jugador)` | Aquellas a las que tiene acceso por su rol |
| `canAccess(UUID protección, UUID jugador)` | Creador, propietario o miembro registrado |
| `renameProtection(id, nombre)` | Cambiar nombre |
| `addMember / removeMember(id, jugador)` | Gestionar miembros |
| `addOwner / removeOwner(id, jugador)` | Gestionar propietarios añadidos |
| `transferOwnership(id, jugador)` | Cambiar creador; el anterior queda como propietario |
| `setFlag(id, bandera, valor)` | Bandera de estado: `permitir`, `denegar`, `restablecer` |
| `deleteProtection(id)` | Eliminar región y ancla, sin devolver bloques |

Las mutaciones devuelven `CompletableFuture<Protection>`, excepto eliminar, que devuelve `CompletableFuture<Void>`. Los conjuntos y mapas de una instantánea no se pueden modificar: solicita los cambios por la API.

`canAccess` comprueba los roles registrados; no evalúa todas las banderas de WorldGuard ni su bypass administrativo. Para autorizar una acción concreta del mundo usa la consulta correspondiente de WorldGuard.

### Hilos, permisos y persistencia

- Las mutaciones se pueden solicitar desde cualquier hilo. El plugin programa la parte Bukkit/WorldGuard en el principal y la escritura SQL en un trabajador.
- No uses `join()` ni `get()` desde el hilo principal: bloquearías la operación que espera ejecutarse allí. Encadena `thenCompose` para varias escrituras sobre la misma protección.
- Las mutaciones normales completan el futuro en el hilo principal, después de persistir. Si el plugin está apagándose, el futuro puede fallar desde el hilo solicitante o SQL; no accedas a Bukkit desde ese callback sin comprobar el hilo y el ciclo de vida de tu plugin.
- La región propuesta se reserva en memoria y WorldGuard mientras se guarda. No interpretes su presencia en una consulta como confirmación SQL; espera el futuro o `ProtectionChangedEvent`.
- Otra escritura simultánea sobre el mismo ID se rechaza. Las escrituras fallidas intentan restaurar el estado anterior.
- La API es para plugins de confianza. **No comprueba los permisos Bukkit del jugador, cuotas de colocación ni si se ha pagado un bloque.** Tu integración debe hacerlo. Sí conserva las invariantes de roles, geometría y solapamientos.
- WorldGuard/WorldEdit y los mundos deben estar disponibles. Si guardas una región en un mundo que luego no se carga, su registro se conserva y se sincroniza cuando carga el mundo.

## Eventos

Todos están en `com.viciont.viciontprotections.api.event` y se emiten en el hilo principal.

| Evento | Momento |
| --- | --- |
| `ProtectionChangingEvent` | Antes del cambio, cancelable; `getPrevious()` y `getProposed()` |
| `ProtectionChangedEvent` | Después de persistir; `getPrevious()` y `getCurrent()` |
| `ProtectionCrossedEvent` | Un jugador entra o sale; `getPlayer()`, `getPrevious()` y `getCurrent()` |

En creación, `getPrevious()` es `null`; en eliminación, la instantánea nueva es `null`. El evento de cambio permite a otro plugin vetar una operación:

```java
@EventHandler(ignoreCancelled = true)
public void beforeProtectionChange(ProtectionChangingEvent event) {
    Protection proposed = event.getProposed();
    if (proposed != null && proposed.worldName().equals("arena")) {
        event.setCancelled(true);
    }
}
```

No esperes futuros ni provoques cambios recursivos sobre la misma protección dentro de este evento. Usa `ProtectionChangedEvent` para reaccionar a una escritura confirmada.
