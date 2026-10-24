# API de ViciontProtections 3.0.5

La API permite integrar tiendas, recompensas, generación de parcelas y otros sistemas sin ejecutar comandos ni escribir SQL. Los tipos públicos están en `com.viciont.viciontprotections.api`; `Protection` es un registro inmutable en `com.viciont.viciontprotections.models`.

## Dependencia

Compila contra el JAR de ViciontProtections con alcance `provided`. Si lo instalas en tu repositorio Maven local:

```bash
mvn install:install-file -Dfile=ViciontProtections-3.0.5.jar -DgroupId=com.viciont -DartifactId=ViciontProtections -Dversion=3.0.5 -Dpackaging=jar
```

```xml
<dependency>
    <groupId>com.viciont</groupId>
    <artifactId>ViciontProtections</artifactId>
    <version>3.0.5</version>
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

## Clanes

Desde la 3.0.0, Viciont Clans publica un segundo servicio, `ViciontClansApi`, en `com.viciont.viciontprotections.api.clan`. Con él otro plugin puede añadir un banco, cobrar un depósito al fundar o pagar recompensas de guerra sin tocar las tablas de clanes. Los tipos (`Clan`, `ClanMember`, `ClanRank`, `ClanTerritory`, `ClanWar`) son registros inmutables.

```java
ViciontClansApi clans = Bukkit.getServicesManager().load(ViciontClansApi.class);
```

### Consultas

| Método | Resultado |
| --- | --- |
| `getClans()` · `getClan(UUID)` · `getClanByName(String)` | Clanes del servidor |
| `getPlayerClan(UUID jugador)` | Clan que muestra el jugador |
| `getPlayerClans(UUID jugador)` | Todos sus clanes (más de uno solo si lo puso la administración) |
| `getClanAt(Location)` | Clan dueño del territorio en esa posición; hilo principal |
| `getRank(clan, jugador)` · `getRankName(ClanRank)` | Rango y su nombre visible en `clans.yml` |
| `hasPermission(clan, jugador, casilla)` | Casilla del rango del jugador, contando los ajustes del líder y los bloqueos del servidor |
| `getPermissions()` | Casillas del plugin y de las extensiones |
| `getWar(clan)` · `getWars(clan)` | Guerra activa e historial |

Los rangos, de menor a mayor, son `GUEST`, `APPRENTICE`, `OFFICER`, `CO_LEADER` y `LEADER`. `ClanRank#isAtLeast` sirve para comparar.

### Acciones

| Método | Efecto |
| --- | --- |
| `createClanBlock(ProtectionBlock, cantidad)` · `isClanBlock(ItemStack)` | Bloques de clan para tiendas; hilo principal |
| `foundClan(fundador, nombre, color, CreateProtectionRequest)` | Fundar sin requisitos de fundación |
| `disbandClan(clan, motivo)` | Disolver y guardar una copia recuperable durante `clanes.copia-dias` |
| `disbandClan(clan, motivo, true)` | Disolver por impago: no se guarda copia |
| `invite` · `addMember` · `removeMember` · `setRank` | Gestionar miembros |
| `addTerritory(clan, protección)` | Pasar una protección al clan |
| `broadcast(clan, mensaje)` | Aviso en el chat del clan con su formato |
| `setWarReward(guerra, texto)` | Anotar en la ficha de una guerra lo que se pagó |

Las acciones devuelven `CompletableFuture` y siguen las mismas reglas de hilos que las mutaciones de protecciones. Igual que la API de protecciones, no comprueban permisos Bukkit ni cobros: eso es cosa de tu integración.

### Extensiones

Registra las extensiones en `onEnable`. Se retiran solas cuando tu plugin se desactiva, o al llamar a `unregisterAll(plugin)`.

```java
// Botón del menú del clan para aprendices o superiores (sin casilla de permiso: null).
clans.registerButton(this, new ClanButton("banco", "{dato}Banco del clan",
        "Saldo, aportaciones y deudas", ClanRank.APPRENTICE, null, "/clanbanco"));

// Casilla en /clan permisos, activada por defecto para oficiales y sublíderes.
clans.registerPermission(this, new ClanPermission("banco-retirar", "Retirar del banco",
        "Sacar DinoCoins del banco", Set.of(ClanRank.OFFICER, ClanRank.CO_LEADER)));

// Línea en la ficha del clan.
clans.registerStatusLine(this, "saldo", (clan, viewer) -> "{texto}Banco: {dato}" + saldo(clan.id()));

// Página en /clan guia.
clans.registerGuideTopic(this, "banco", "Banco del clan", List.of("{texto}Cada clan tiene un banco..."));

// Depósito al fundar: se cobra antes de crear el clan y se devuelve si la fundación falla.
clans.registerFoundingRequirement(this, new FoundingRequirement() {
    public String describe(Player founder) { return "Depósito inicial: 10 DinoCoins"; }
    public CompletableFuture<Void> charge(Player founder, String clanName) { return cobrar(founder, 10); }
    public void refund(Player founder, String clanName) { devolver(founder, 10); }
});
```

Los requisitos de fundación se piden en orden de registro. Si uno falla, los anteriores reciben `refund` y el jugador conserva su bloque de clan. Recuperar una copia o cambiar el bloque de clan no vuelve a cobrar.

### Eventos de clanes

Están en `com.viciont.viciontprotections.api.event.clan` y llegan en el hilo principal, salvo `ClanChatEvent`.

| Evento | Momento |
| --- | --- |
| `ClanFoundEvent` | Antes de fundar; `cancel(mensaje)` lo impide y muestra el motivo |
| `ClanDisbandEvent` | Antes de disolver; `getCause()`: `LEADER`, `BLOCK_BROKEN`, `ADMIN`, `PLUGIN`, `HOME_LOST` o `UNPAID`. Solo se cancelan las dos primeras (`isForced()`) |
| `ClanMemberEvent` | Antes de `JOIN`, `LEAVE`, `KICK`, `RANK` o `LEADER`; cancelable |
| `ClanWarEvent` | `DECLARE` (cancelable con `cancel(mensaje)`), `START` y `END` con el final ya guardado en `getWar()` |
| `ClanChangedEvent` | Después de guardar cualquier cambio; `getPrevious()` y `getCurrent()` (`null` al fundar o disolver) |
| `ClanChatEvent` | Mensaje del chat del clan; puede llegar fuera del hilo principal, cancelable y editable |

Por ejemplo, para impedir que un clan con deudas declare una guerra y pagar al ganador de una rendición:

```java
@EventHandler
public void onWar(ClanWarEvent event) {
    ClanWar war = event.getWar();
    if (event.getType() == ClanWarEvent.Type.DECLARE && tieneDeuda(war.attacker())) {
        event.cancel("Tu clan tiene una deuda de guerra pendiente.");
    }
    if (event.getType() == ClanWarEvent.Type.END && war.ending() == ClanWar.Ending.SURRENDER) {
        int pagado = pagar(war.winner(), war.tribute());
        clans.setWarReward(war.id(), pagado + " DinoCoins");
    }
}
```

Para disolver un clan que no pagó su mantenimiento, usa `disbandClan(clan, "No pagó el mantenimiento", true)`: el evento llega con `UNPAID` y no se guarda ninguna copia.
