package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.api.event.ProtectionChangedEvent;
import com.viciont.viciontprotections.api.event.clan.*;
import com.viciont.viciontprotections.database.ClanStore;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.ClanBackup;
import com.viciont.viciontprotections.models.ClanRequest;
import com.viciont.viciontprotections.models.Protection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Núcleo de los clanes. Todo cambio se valida y aplica en el hilo principal y se guarda en la base
 * fuera de él; si la escritura falla, el clan vuelve a su estado anterior. Los rangos se reflejan
 * en las protecciones de cada territorio (creador = líder, propietarios = sublíderes, miembros = el
 * resto), así WorldGuard deja pasar a todo el clan y las tablas de la 2.x siguen siendo válidas.
 */
public final class ClanManager implements ViciontClansApi, Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final ClanStore store;
  private final ProtectionManager protections;
  private final ClanSettings settings;
  private final ClanMessages messages;
  private final ClanExtensions extensions = new ClanExtensions();
  private final Map<UUID, Clan> clans = new ConcurrentHashMap<>();
  private final Map<UUID, ClanRequest> requests = new ConcurrentHashMap<>();
  private final Map<UUID, ClanBackup> backups = new ConcurrentHashMap<>();
  private volatile Map<UUID, List<UUID>> byPlayer = Map.of();
  private volatile Map<UUID, UUID> byTerritory = Map.of();
  private final Set<UUID> pending = new HashSet<>();
  private final Set<String> reservedNames = new HashSet<>();
  private final Set<UUID> dirty = new LinkedHashSet<>();
  private final BukkitTask task;
  private ClanWars wars;
  private ClanChat chat;

  public ClanManager(
      JavaPlugin plugin,
      ClanStore store,
      ProtectionManager protections,
      ClanSettings settings,
      ClanMessages messages)
      throws SQLException {
    this.plugin = plugin;
    this.store = store;
    this.protections = protections;
    this.settings = settings;
    this.messages = messages;
    for (Clan clan : store.loadClans()) clans.put(clan.id(), clan);
    long now = System.currentTimeMillis();
    for (ClanBackup backup : store.loadBackups()) {
      if (backup.expired(now, settings.backupMillis()) || clans.containsKey(backup.clan().id()))
        store.deleteBackup(backup.clan().id());
      else backups.put(backup.clan().id(), backup);
    }
    for (ClanRequest request : store.loadRequests()) {
      if (request.expired(now) || !clans.containsKey(request.clan())) store.deleteRequest(request.id());
      else requests.put(request.id(), request);
    }
    reindex();
    dirty.addAll(clans.keySet());
    protections.guard().setDenyOverride(this::denyMessage);
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 10);
  }

  public void connect(ClanWars wars, ClanChat chat) {
    this.wars = wars;
    this.chat = chat;
  }

  public ClanSettings settings() {
    return settings;
  }

  public ClanMessages messages() {
    return messages;
  }

  public ProtectionManager protections() {
    return protections;
  }

  public ClanWars wars() {
    return wars;
  }

  public ClanChat chat() {
    return chat;
  }

  @Override
  public void close() {
    task.cancel();
    protections.guard().setDenyOverride(null);
  }

  // ================================================================== consultas

  @Override
  public List<Clan> getClans() {
    return clans.values().stream()
        .sorted(Comparator.comparing(Clan::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Override
  public Optional<Clan> getClan(UUID clan) {
    return Optional.ofNullable(clans.get(clan));
  }

  @Override
  public Optional<Clan> getClanByName(String name) {
    if (name == null) return Optional.empty();
    String key = normalized(name);
    return clans.values().stream().filter(c -> normalized(c.name()).equals(key)).findFirst();
  }

  @Override
  public Optional<Clan> getPlayerClan(UUID player) {
    List<Clan> list = getPlayerClans(player);
    if (list.size() <= 1) return list.stream().findFirst();
    return list.stream()
        .filter(c -> c.members().get(player).active())
        .findFirst()
        .or(() -> list.stream().findFirst());
  }

  @Override
  public List<Clan> getPlayerClans(UUID player) {
    return byPlayer.getOrDefault(player, List.of()).stream()
        .map(clans::get)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingLong(c -> c.members().get(player).joinedAt()))
        .toList();
  }

  @Override
  public Optional<Clan> getClanAt(Location location) {
    return protections.getProtectionAt(location).flatMap(p -> territoryClan(p.id()));
  }

  public Optional<Clan> territoryClan(UUID protection) {
    UUID clan = byTerritory.get(protection);
    return clan == null ? Optional.empty() : getClan(clan);
  }

  public boolean isTerritory(UUID protection) {
    return byTerritory.containsKey(protection);
  }

  @Override
  public Optional<ClanRank> getRank(UUID clan, UUID player) {
    return getClan(clan).flatMap(c -> c.rank(player));
  }

  @Override
  public boolean hasPermission(UUID clan, UUID player, String permission) {
    Clan value = clans.get(clan);
    return value != null && allows(value, player, permission);
  }

  public boolean allows(Clan clan, UUID player, String permission) {
    return clan.rank(player).map(rank -> allows(clan, rank, permission)).orElse(false);
  }

  public boolean allows(Clan clan, ClanRank rank, String permission) {
    if (rank == ClanRank.LEADER) return true;
    var definition = permission(permission);
    if (definition.isEmpty()) return false;
    return resolve(
        rank,
        settings.defaults(permission, definition.get().defaults()),
        settings.locked(permission),
        clan.permissions().getOrDefault(rank, Map.of()).get(permission));
  }

  /**
   * Regla de una casilla: el líder siempre la tiene; si el servidor la fijó, vale la de clans.yml;
   * si no, el cambio del líder o, sin cambio, la de clans.yml.
   */
  static boolean resolve(ClanRank rank, Set<ClanRank> defaults, boolean locked, Boolean changed) {
    if (rank == ClanRank.LEADER) return true;
    if (locked || changed == null) return defaults.contains(rank);
    return changed;
  }

  public Optional<ClanPermission> permission(String key) {
    for (ClanPermission permission : ClanSettings.BUILT_IN)
      if (permission.key().equals(key)) return Optional.of(permission);
    return extensions.permission(key);
  }

  @Override
  public List<ClanPermission> getPermissions() {
    List<ClanPermission> result = new ArrayList<>();
    for (ClanPermission permission : ClanSettings.BUILT_IN)
      result.add(
          new ClanPermission(
              permission.key(),
              permission.name(),
              permission.description(),
              settings.defaults(permission.key(), Set.of())));
    result.addAll(extensions.permissions());
    return result;
  }

  @Override
  public Optional<ClanWar> getWar(UUID clan) {
    return wars.active(clan);
  }

  @Override
  public List<ClanWar> getWars(UUID clan) {
    return wars.history(clan);
  }

  @Override
  public String getRankName(ClanRank rank) {
    return settings.rankName(rank);
  }

  ClanExtensions extensions() {
    return extensions;
  }

  /** Temas que los plugins conectados añaden a la guía. */
  public List<ClanSettings.Topic> extensionTopics() {
    return extensions.topics();
  }

  /** Líneas de estado de las extensiones para el menú del clan. */
  public List<String> statusLines(Clan clan, Player player) {
    List<String> lines = new ArrayList<>();
    for (var entry : extensions.lines())
      try {
        String line = entry.value().apply(clan, player);
        if (line != null && !line.isBlank()) lines.add(line);
      } catch (RuntimeException failure) {
        plugin.getLogger().warning("La línea de estado de " + entry.plugin().getName() + " falló: " + failure);
      }
    return lines;
  }

  /** Botones de extensiones que el rango del jugador puede ver. */
  public List<ClanButton> buttons(Clan clan, Player player) {
    ClanRank rank = clan.rank(player.getUniqueId()).orElse(null);
    if (rank == null) return List.of();
    return extensions.buttons().stream()
        .filter(b -> rank.isAtLeast(b.minimum()))
        .filter(b -> b.permission() == null || allows(clan, rank, b.permission()))
        .toList();
  }

  public List<ClanRequest> requests(Predicate<ClanRequest> filter) {
    long now = System.currentTimeMillis();
    return requests.values().stream()
        .filter(r -> !r.expired(now))
        .filter(filter)
        .sorted(Comparator.comparingLong(ClanRequest::createdAt))
        .toList();
  }

  public List<ClanRequest> invitations(UUID player) {
    return requests(r -> r.type() == ClanRequest.Type.INVITE && r.player().equals(player));
  }

  public List<ClanRequest> territoryRequests(UUID clan) {
    return requests(r -> r.type() == ClanRequest.Type.TERRITORY && r.clan().equals(clan));
  }

  /** Busca un clan por @ID, #ID corto o nombre. */
  public Clan resolve(String selector) {
    String value = selector.trim();
    if (value.startsWith("@") || value.startsWith("#")) {
      String id = value.substring(1);
      for (Clan clan : clans.values())
        if (clan.id().toString().equalsIgnoreCase(id) || clan.id().toString().startsWith(id.toLowerCase(Locale.ROOT)) && id.length() >= 8)
          return clan;
    }
    return getClanByName(value)
        .orElseThrow(() -> new ProtectionException("No existe ningún clan llamado " + value + "."));
  }

  /**
   * Clan cuyo nombre ocupa las primeras palabras de {@code args} desde {@code start}. Devuelve el
   * clan y el índice de la primera palabra que sobra, o null.
   */
  public Map.Entry<Clan, Integer> resolvePrefix(String[] args, int start) {
    if (start < args.length && (args[start].startsWith("@") || args[start].startsWith("#"))) {
      Clan clan = resolve(args[start]);
      return Map.entry(clan, start + 1);
    }
    for (int end = args.length; end > start; end--) {
      String name = String.join(" ", Arrays.copyOfRange(args, start, end));
      var clan = getClanByName(name);
      if (clan.isPresent()) return Map.entry(clan.get(), end);
    }
    return null;
  }

  static String normalized(String value) {
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll(" +", " ")
        .toLowerCase(Locale.ROOT);
  }

  /** Nombre válido y libre (salvo para el propio clan). */
  public String validName(String name, UUID self) {
    String value =
        Normalizer.normalize(Objects.requireNonNullElse(name, ""), Normalizer.Form.NFKC)
            .trim()
            .replaceAll(" +", " ");
    int min = settings.nameMin(), max = settings.nameMax();
    if (!value.matches("[\\p{L}\\p{N} ._-]{" + min + "," + max + "}"))
      throw new ProtectionException(
          "El nombre del clan debe tener entre "
              + min
              + " y "
              + max
              + " letras, números, espacios, puntos o guiones.");
    String key = normalized(value);
    for (Clan clan : clans.values())
      if (!clan.id().equals(self) && normalized(clan.name()).equals(key))
        throw new ProtectionException("Ya existe un clan llamado " + clan.name() + ".");
    if (self == null && reservedNames.contains(key))
      throw new ProtectionException("Alguien está fundando ahora un clan con ese nombre.");
    return value;
  }

  /** Texto libre de los jugadores (motivos, condiciones) sin códigos de color. */
  public String cleanText(String value) {
    String clean =
        Objects.requireNonNullElse(value, "")
            .replaceAll("[§&{}<>]", "")
            .replaceAll("\\s+", " ")
            .trim();
    if (clean.length() > settings.maxText())
      throw new ProtectionException(
          "El texto no puede pasar de " + settings.maxText() + " caracteres.");
    return clean;
  }

  // ================================================================== fundar y disolver

  @Override
  public ItemStack createClanBlock(ProtectionBlock block, int amount) {
    return protections.createClanBlock(block, amount);
  }

  @Override
  public boolean isClanBlock(ItemStack item) {
    return protections.isClanBlock(item);
  }

  @Override
  public CompletableFuture<Clan> foundClan(
      UUID founder, String name, String color, CreateProtectionRequest territory) {
    return main(() -> found(founder, name, color, territory));
  }

  /** Crea el territorio principal y el clan. Si el clan no se guarda, se retira la protección. */
  CompletableFuture<Clan> found(
      UUID founder, String rawName, String color, CreateProtectionRequest territory) {
    String name = validName(rawName, null);
    if (color == null || !color.matches("#[0-9a-fA-F]{6}"))
      throw new ProtectionException("Color no válido.");
    if (!getPlayerClans(founder).isEmpty())
      throw new ProtectionException("Ya perteneces a un clan.");
    String key = normalized(name);
    reservedNames.add(key);
    var request =
        new CreateProtectionRequest(
            founder, territory.worldName(), territory.bounds(), name, territory.anchor());
    CompletableFuture<Clan> result = new CompletableFuture<>();
    protections
        .createProtection(request)
        .whenComplete(
            (home, failure) ->
                sync(
                    () -> {
                      if (failure != null) {
                        reservedNames.remove(key);
                        result.completeExceptionally(failure);
                        return;
                      }
                      long now = System.currentTimeMillis();
                      Clan clan =
                          new Clan(
                              UUID.randomUUID(),
                              name,
                              color.toUpperCase(Locale.ROOT),
                              founder,
                              home.id(),
                              now,
                              Map.of(founder, new ClanMember(founder, ClanRank.LEADER, now, true)),
                              Map.of(
                                  home.id(),
                                  new ClanTerritory(home.id(), founder, now, true, Set.of(), Set.of())),
                              Map.of(),
                              Map.of());
                      CompletableFuture<Clan> saved;
                      try {
                        saved = persist(null, clan);
                      } catch (RuntimeException error) {
                        saved = CompletableFuture.failedFuture(error);
                      }
                      saved.whenComplete(
                          (value, error) -> {
                            reservedNames.remove(key);
                            if (error == null) {
                              deleteRequests(r -> r.type() == ClanRequest.Type.INVITE && r.player().equals(founder));
                              result.complete(value);
                            } else {
                              protections.deleteProtection(home.id());
                              result.completeExceptionally(error);
                            }
                          });
                    }));
    return result;
  }

  @Override
  public CompletableFuture<Void> disbandClan(UUID clan, String reason) {
    return dissolve(clan, ClanDisbandEvent.Cause.PLUGIN, reason, null);
  }

  @Override
  public CompletableFuture<Void> disbandClan(UUID clan, String reason, boolean unpaid) {
    return dissolve(clan, unpaid ? ClanDisbandEvent.Cause.UNPAID : ClanDisbandEvent.Cause.PLUGIN, reason, null);
  }

  /**
   * Disuelve el clan: el territorio principal desaparece con su bloque, las protecciones aportadas
   * vuelven a sus dueños y la guerra en curso termina. Con {@code returnBlockTo}, el bloque de clan
   * vuelve a su inventario.
   */
  public CompletableFuture<Void> dissolve(
      UUID clanId, ClanDisbandEvent.Cause cause, String reason, Player returnBlockTo) {
    return main(
        () -> {
          Clan clan = required(clanId);
          var event = new ClanDisbandEvent(clan, cause, reason);
          Bukkit.getPluginManager().callEvent(event);
          if (event.isCancelled())
            throw new ProtectionException("Otro plugin ha impedido disolver el clan.");
          Protection home = clan.home() == null ? null : protections.getProtection(clan.home()).orElse(null);
          boolean copy = cause != ClanDisbandEvent.Cause.UNPAID && settings.backupMillis() > 0;
          return persist(clan, null)
              .thenApply(
                  ignored -> {
                    if (copy) backup(clan, reason, home);
                    requests.values().removeIf(r -> r.clan().equals(clanId));
                    wars.disbanded(clan);
                    for (ClanTerritory territory : clan.territories().values()) {
                      if (territory.home())
                        whenFree(territory.protection(), () -> protections.deleteProtection(territory.protection()))
                            .exceptionally(error -> log("No se pudo retirar el territorio principal de " + clan.name(), error));
                      else giveBack(clan, territory);
                    }
                    if (returnBlockTo != null && returnBlockTo.isOnline() && home != null && home.anchor() != null)
                      com.viciont.viciontprotections.commands.ProtectionCommand.giveItem(
                          returnBlockTo, createClanBlock(home.anchor().block(), 1));
                    String text =
                        "{texto}El clan "
                            + ClanMessages.paint(clan.name(), clan.color())
                            + " {texto}se ha disuelto"
                            + (reason == null || reason.isBlank() ? "." : ": {dato}" + reason + "{texto}.");
                    String kept =
                        copy
                            ? "{suave}Se guarda una copia {dato}" + settings.backupMillis() / 86_400_000L
                                + " días{suave}: " + name(clan.leader())
                                + " puede recuperarlo colocando un bloque de clan."
                            : null;
                    for (UUID member : clan.members().keySet()) {
                      Player online = Bukkit.getPlayer(member);
                      if (online == null) continue;
                      messages.send(online, text);
                      if (kept != null) messages.send(online, kept);
                    }
                    if (settings.flag("clanes.anunciar")) {
                      for (Player online : Bukkit.getOnlinePlayers())
                        if (!clan.isMember(online.getUniqueId())) messages.send(online, text);
                    }
                    return null;
                  });
        });
  }

  // ================================================================== copias de clanes disueltos

  private void backup(Clan clan, String reason, Protection home) {
    Clan snapshot =
        new Clan(clan.id(), clan.name(), clan.color(), clan.leader(), null, clan.foundedAt(), clan.members(),
            Map.of(), clan.permissions(), clan.flags());
    var copy =
        new ClanBackup(
            snapshot,
            System.currentTimeMillis(),
            reason,
            home == null ? "" : home.bounds().width() + " × " + home.bounds().depth());
    backups.put(clan.id(), copy);
    store.saveBackup(copy).exceptionally(error -> log("No se pudo guardar la copia del clan " + clan.name(), error));
  }

  /** Copias que ese jugador, como líder, puede recuperar todavía. */
  public List<ClanBackup> backupsOf(UUID leader) {
    long now = System.currentTimeMillis();
    return backups.values().stream()
        .filter(b -> b.clan().leader().equals(leader) && !b.expired(now, settings.backupMillis()))
        .sorted(Comparator.comparingLong(ClanBackup::dissolvedAt).reversed())
        .toList();
  }

  /**
   * Recupera un clan disuelto sobre el territorio de un bloque de clan nuevo: mismo ID (y por tanto
   * su historia de guerras), nombre, color, rangos, permisos y banderas. Vuelven los miembros que no
   * se han unido a otro clan; las protecciones aportadas no, porque ya volvieron a sus dueños.
   */
  CompletableFuture<Clan> restore(UUID leader, UUID backupId, CreateProtectionRequest territory) {
    ClanBackup copy = backups.get(backupId);
    if (copy == null || copy.expired(System.currentTimeMillis(), settings.backupMillis()))
      throw new ProtectionException("Esa copia ya no existe: se guardan " + settings.backupMillis() / 86_400_000L + " días.");
    Clan old = copy.clan();
    if (!old.leader().equals(leader)) throw new ProtectionException("Solo el líder del clan puede recuperarlo.");
    if (!getPlayerClans(leader).isEmpty()) throw new ProtectionException("Ya perteneces a un clan.");
    if (clans.containsKey(old.id())) throw new ProtectionException("Ese clan ya existe.");
    String name = validName(old.name(), old.id());
    String key = normalized(name);
    if (reservedNames.contains(key)) throw new ProtectionException("Alguien está fundando ahora un clan con ese nombre.");
    reservedNames.add(key);
    var request =
        new CreateProtectionRequest(leader, territory.worldName(), territory.bounds(), name, territory.anchor());
    CompletableFuture<Clan> result = new CompletableFuture<>();
    protections
        .createProtection(request)
        .whenComplete(
            (home, failure) ->
                sync(
                    () -> {
                      if (failure != null) {
                        reservedNames.remove(key);
                        result.completeExceptionally(failure);
                        return;
                      }
                      Map<UUID, ClanMember> members = new HashMap<>();
                      List<String> skipped = new ArrayList<>();
                      for (ClanMember member : old.members().values()) {
                        if (!member.player().equals(leader) && !getPlayerClans(member.player()).isEmpty()) {
                          skipped.add(name(member.player()));
                          continue;
                        }
                        members.put(member.player(), member.withActive(true));
                      }
                      Clan clan =
                          new Clan(old.id(), name, old.color(), leader, home.id(), old.foundedAt(), members,
                              Map.of(home.id(), new ClanTerritory(home.id(), leader, System.currentTimeMillis(), true, Set.of(), Set.of())),
                              old.permissions(), old.flags());
                      CompletableFuture<Clan> saved;
                      try {
                        saved = persist(null, clan);
                      } catch (RuntimeException error) {
                        saved = CompletableFuture.failedFuture(error);
                      }
                      saved.whenComplete(
                          (value, error) -> {
                            reservedNames.remove(key);
                            if (error != null) {
                              protections.deleteProtection(home.id());
                              result.completeExceptionally(error);
                              return;
                            }
                            backups.remove(old.id());
                            store.deleteBackup(old.id())
                                .exceptionally(e -> log("No se pudo borrar la copia del clan " + name, e));
                            deleteRequests(r -> r.type() == ClanRequest.Type.INVITE && members.containsKey(r.player()));
                            notice(value, "{dato}" + name(leader) + " {texto}ha recuperado el clan con sus rangos y permisos."
                                + (skipped.isEmpty() ? "" : " {suave}No vuelven porque ya están en otro clan: {dato}" + String.join("{suave}, {dato}", skipped) + "{suave}."));
                            result.complete(value);
                          });
                    }));
    return result;
  }

  /** Devuelve un territorio aportado a quien lo aportó con su acceso anterior. */
  private CompletableFuture<Protection> giveBack(Clan clan, ClanTerritory territory) {
    return whenFree(
            territory.protection(),
            () -> {
              Protection p = protections.getProtection(territory.protection()).orElse(null);
              if (p == null) return CompletableFuture.completedFuture(null);
              Map<String, String> flags = new HashMap<>(p.flags());
              settings.allowedFlags().forEach(flags::remove);
              return protections.setAccess(
                  p.id(),
                  territory.contributor(),
                  territory.previousOwners(),
                  territory.previousMembers(),
                  flags);
            })
        .whenComplete(
            (p, error) -> {
              if (error != null)
                log("No se pudo devolver el territorio " + territory.protection() + " del clan " + clan.name(), error);
              else if (p != null) {
                Player owner = Bukkit.getPlayer(territory.contributor());
                if (owner != null)
                  messages.send(
                      owner,
                      "{texto}Tu protección {dato}" + p.name() + " {texto}vuelve a ser solo tuya, con el acceso que tenía antes de aportarla.");
              }
            });
  }

  // ================================================================== miembros

  @Override
  public CompletableFuture<Void> invite(UUID clan, UUID player, ClanRank rank, UUID inviter) {
    return main(() -> createInvite(required(clan), player, rank, inviter).thenApply(r -> null));
  }

  /** Invitación desde el juego: comprueba la casilla y el rango de quien invita. */
  public CompletableFuture<ClanRequest> invite(Player actor, Clan clan, UUID target, ClanRank rank) {
    return main(
        () -> {
          ClanRank own = requireRank(clan, actor);
          requirePermission(clan, actor, "invitar");
          if (!own.isAbove(rank))
            throw new ProtectionException("Solo puedes invitar con un rango inferior al tuyo.");
          return createInvite(clan, target, rank, actor.getUniqueId());
        });
  }

  private CompletableFuture<ClanRequest> createInvite(
      Clan clan, UUID target, ClanRank rank, UUID inviter) {
    if (rank == ClanRank.LEADER) throw new ProtectionException("No se puede invitar como líder.");
    if (clan.isMember(target)) throw new ProtectionException("Ese jugador ya está en el clan.");
    if (!getPlayerClans(target).isEmpty())
      throw new ProtectionException("Ese jugador ya pertenece a un clan.");
    requireRoom(clan);
    long now = System.currentTimeMillis();
    deleteRequests(
        r -> r.type() == ClanRequest.Type.INVITE && r.clan().equals(clan.id()) && r.player().equals(target));
    var request =
        new ClanRequest(
            UUID.randomUUID(),
            ClanRequest.Type.INVITE,
            clan.id(),
            target,
            null,
            rank,
            inviter,
            now,
            now + settings.inviteMillis());
    requests.put(request.id(), request);
    return store.saveRequest(request).thenApply(ignored -> request);
  }

  public CompletableFuture<Clan> acceptInvite(Player player, Clan clan) {
    return main(
        () -> {
          UUID id = player.getUniqueId();
          ClanRequest invite =
              invitations(id).stream()
                  .filter(r -> r.clan().equals(clan.id()))
                  .findFirst()
                  .orElseThrow(() -> new ProtectionException("No tienes ninguna invitación de " + clan.name() + " (caducan a los " + settings.inviteMillis() / 60_000 + " minutos)."));
          if (!getPlayerClans(id).isEmpty())
            throw new ProtectionException("Ya perteneces a un clan; sal de él antes de aceptar.");
          requireRoom(required(clan.id()));
          return join(clan.id(), id, invite.rank(), invite.createdBy())
              .thenApply(
                  joined -> {
                    deleteRequests(r -> r.type() == ClanRequest.Type.INVITE && r.player().equals(id));
                    return joined;
                  });
        });
  }

  public CompletableFuture<Void> rejectInvite(Player player, Clan clan) {
    return main(
        () -> {
          UUID id = player.getUniqueId();
          var found = invitations(id).stream().filter(r -> r.clan().equals(clan.id())).toList();
          if (found.isEmpty()) throw new ProtectionException("No tienes ninguna invitación de " + clan.name() + ".");
          deleteRequests(r -> found.contains(r));
          for (ClanRequest request : found)
            if (request.createdBy() != null) {
              Player inviter = Bukkit.getPlayer(request.createdBy());
              if (inviter != null)
                messages.send(inviter, "{dato}" + player.getName() + " {texto}rechazó la invitación a " + ClanMessages.paint(clan.name(), clan.color()) + "{texto}.");
            }
          return CompletableFuture.completedFuture(null);
        });
  }

  private CompletableFuture<Clan> join(UUID clanId, UUID player, ClanRank rank, UUID actor) {
    Clan clan = required(clanId);
    if (clan.isMember(player)) throw new ProtectionException("Ese jugador ya está en el clan.");
    if (rank == ClanRank.LEADER) throw new ProtectionException("Para nombrar líder usa transferir.");
    member(clan, player, ClanMemberEvent.Type.JOIN, null, rank, actor);
    var members = new HashMap<>(clan.members());
    boolean first = getPlayerClans(player).isEmpty();
    members.put(player, new ClanMember(player, rank, System.currentTimeMillis(), first));
    return persist(clan, clan.withMembers(clan.leader(), members))
        .thenApply(
            next -> {
              notice(next, "{dato}" + name(player) + " {texto}se ha unido al clan como {dato}" + settings.rankName(rank) + "{texto}.");
              return next;
            });
  }

  @Override
  public CompletableFuture<Clan> addMember(UUID clan, UUID player, ClanRank rank) {
    return main(() -> join(clan, player, rank, null));
  }

  @Override
  public CompletableFuture<Clan> removeMember(UUID clan, UUID player) {
    return main(() -> remove(required(clan), player, ClanMemberEvent.Type.KICK, null));
  }

  public CompletableFuture<Clan> kick(Player actor, Clan clan, UUID target) {
    return main(
        () -> {
          ClanRank own = requireRank(clan, actor);
          requirePermission(clan, actor, "expulsar");
          ClanRank other = clan.rank(target).orElseThrow(() -> new ProtectionException("Ese jugador no está en el clan."));
          if (!own.isAbove(other))
            throw new ProtectionException("Solo puedes expulsar a miembros de rango inferior al tuyo.");
          return remove(clan, target, ClanMemberEvent.Type.KICK, actor.getUniqueId());
        });
  }

  public CompletableFuture<Clan> leave(Player player, Clan clan) {
    return main(() -> remove(clan, player.getUniqueId(), ClanMemberEvent.Type.LEAVE, player.getUniqueId()));
  }

  private CompletableFuture<Clan> remove(Clan clan, UUID player, ClanMemberEvent.Type type, UUID actor) {
    ClanRank rank = clan.rank(player).orElseThrow(() -> new ProtectionException("Ese jugador no está en el clan."));
    if (rank == ClanRank.LEADER)
      throw new ProtectionException(
          type == ClanMemberEvent.Type.LEAVE
              ? "El líder no puede salir: transfiere el liderazgo o disuelve el clan."
              : "No se puede expulsar al líder; transfiere antes el liderazgo.");
    member(clan, player, type, rank, null, actor);
    var members = new HashMap<>(clan.members());
    members.remove(player);
    return persist(clan, clan.withMembers(clan.leader(), members))
        .thenApply(
            next -> {
              if (type == ClanMemberEvent.Type.LEAVE) notice(next, "{dato}" + name(player) + " {texto}ha salido del clan.");
              else {
                notice(next, "{dato}" + name(player) + " {texto}ya no forma parte del clan.");
                Player online = Bukkit.getPlayer(player);
                if (online != null)
                  messages.send(online, "{texto}Ya no formas parte del clan " + ClanMessages.paint(clan.name(), clan.color()) + "{texto}.");
              }
              if (chat != null) chat.forget(player, clan.id());
              return next;
            });
  }

  @Override
  public CompletableFuture<Clan> setRank(UUID clan, UUID player, ClanRank rank) {
    return main(
        () -> {
          Clan value = required(clan);
          return rank == ClanRank.LEADER
              ? transfer(value, player, null)
              : changeRank(value, player, rank, null);
        });
  }

  public CompletableFuture<Clan> setRank(Player actor, Clan clan, UUID target, ClanRank rank) {
    return main(
        () -> {
          ClanRank own = requireRank(clan, actor);
          requirePermission(clan, actor, "rangos");
          ClanRank other = clan.rank(target).orElseThrow(() -> new ProtectionException("Ese jugador no está en el clan."));
          if (!own.isAbove(other) || !own.isAbove(rank))
            throw new ProtectionException("Solo puedes cambiar rangos inferiores al tuyo, y a un rango inferior al tuyo.");
          return changeRank(clan, target, rank, actor.getUniqueId());
        });
  }

  private CompletableFuture<Clan> changeRank(Clan clan, UUID player, ClanRank rank, UUID actor) {
    ClanMember current = clan.members().get(player);
    if (current == null) throw new ProtectionException("Ese jugador no está en el clan.");
    if (current.rank() == ClanRank.LEADER)
      throw new ProtectionException("El rango del líder solo cambia al transferir el liderazgo.");
    if (rank == ClanRank.LEADER) throw new ProtectionException("Para nombrar líder usa transferir.");
    if (current.rank() == rank)
      throw new ProtectionException(name(player) + " ya es " + settings.rankName(rank) + ".");
    member(clan, player, ClanMemberEvent.Type.RANK, current.rank(), rank, actor);
    var members = new HashMap<>(clan.members());
    members.put(player, current.withRank(rank));
    boolean up = rank.isAbove(current.rank());
    return persist(clan, clan.withMembers(clan.leader(), members))
        .thenApply(
            next -> {
              notice(next, "{dato}" + name(player) + (up ? " {texto}asciende a {dato}" : " {texto}pasa a {dato}") + settings.rankName(rank) + "{texto}.");
              return next;
            });
  }

  public CompletableFuture<Clan> transfer(Player actor, Clan clan, UUID target) {
    return main(
        () -> {
          if (!clan.leader().equals(actor.getUniqueId()))
            throw new ProtectionException("Solo el líder puede transferir el liderazgo.");
          return transfer(clan, target, actor.getUniqueId());
        });
  }

  private CompletableFuture<Clan> transfer(Clan clan, UUID target, UUID actor) {
    ClanMember next = clan.members().get(target);
    if (next == null) throw new ProtectionException("El nuevo líder debe ser miembro del clan.");
    if (target.equals(clan.leader())) throw new ProtectionException("Ya es el líder.");
    member(clan, target, ClanMemberEvent.Type.LEADER, next.rank(), ClanRank.LEADER, actor);
    var members = new HashMap<>(clan.members());
    members.put(target, next.withRank(ClanRank.LEADER));
    members.put(clan.leader(), clan.members().get(clan.leader()).withRank(ClanRank.CO_LEADER));
    return persist(clan, clan.withMembers(target, members))
        .thenApply(
            value -> {
              notice(value, "{dato}" + name(target) + " {texto}es el nuevo líder del clan; {dato}" + name(clan.leader()) + " {texto}pasa a " + settings.rankName(ClanRank.CO_LEADER) + ".");
              return value;
            });
  }

  /** Elige el clan que se muestra quien está en varios. */
  public CompletableFuture<Void> setActive(UUID player, Clan chosen) {
    return main(
        () -> {
          if (!chosen.isMember(player)) throw new ProtectionException("No perteneces a ese clan.");
          List<CompletableFuture<Clan>> changes = new ArrayList<>();
          for (Clan clan : getPlayerClans(player)) {
            boolean active = clan.id().equals(chosen.id());
            ClanMember member = clan.members().get(player);
            if (member.active() == active) continue;
            var members = new HashMap<>(clan.members());
            members.put(player, member.withActive(active));
            changes.add(persist(clan, clan.withMembers(clan.leader(), members)));
          }
          return CompletableFuture.allOf(changes.toArray(CompletableFuture[]::new));
        });
  }

  private void member(
      Clan clan, UUID player, ClanMemberEvent.Type type, ClanRank previous, ClanRank next, UUID actor) {
    var event = new ClanMemberEvent(clan, player, type, previous, next, actor);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) throw new ProtectionException("Otro plugin ha cancelado el cambio.");
  }

  // ================================================================== territorios

  /**
   * Aporta la protección donde está el jugador. Con la casilla «territorios» pasa al momento; si
   * no, queda una solicitud para el líder y los sublíderes. Devuelve null si quedó como solicitud.
   */
  public CompletableFuture<Clan> contribute(Player player, Clan clan, Protection protection) {
    return main(
        () -> {
          UUID id = player.getUniqueId();
          requireRank(clan, player);
          requirePermission(clan, player, "aportar");
          checkContribution(clan, protection, id);
          if (allows(clan, id, "territorios")) return addTerritory(clan, protection);
          deleteRequests(r -> r.type() == ClanRequest.Type.TERRITORY && protection.id().equals(r.protection()));
          long now = System.currentTimeMillis();
          var request =
              new ClanRequest(
                  UUID.randomUUID(),
                  ClanRequest.Type.TERRITORY,
                  clan.id(),
                  id,
                  protection.id(),
                  null,
                  id,
                  now,
                  now + settings.territoryRequestMillis());
          requests.put(request.id(), request);
          return store.saveRequest(request)
              .thenApply(
                  ignored -> {
                    for (UUID member : clan.members().keySet()) {
                      Player online = Bukkit.getPlayer(member);
                      if (online != null && allows(clan, member, "territorios"))
                        messages.components(
                            online,
                            true,
                            messages.component("{dato}" + player.getName() + " {texto}ofrece {dato}" + protection.name() + " {texto}como territorio. "),
                            messages.button("{acento}[Ver solicitudes]", "{texto}/clan solicitudes", "/clan solicitudes", true));
                    }
                    return null;
                  });
        });
  }

  private void checkContribution(Clan clan, Protection protection, UUID contributor) {
    if (!protection.primaryOwner().equals(contributor))
      throw new ProtectionException("Solo el creador de una protección puede aportarla.");
    if (isTerritory(protection.id()))
      throw new ProtectionException("Esa protección ya es territorio de un clan.");
    if (!clan.isMember(contributor))
      throw new ProtectionException("Quien la aporta debe seguir en el clan.");
  }

  public CompletableFuture<Clan> acceptTerritory(Player actor, Clan clan, ClanRequest request) {
    return main(
        () -> {
          requireRank(clan, actor);
          requirePermission(clan, actor, "territorios");
          deleteRequests(r -> r.id().equals(request.id()));
          Protection protection =
              protections.getProtection(request.protection())
                  .orElseThrow(() -> new ProtectionException("Esa protección ya no existe."));
          checkContribution(clan, protection, request.player());
          return addTerritory(clan, protection);
        });
  }

  public CompletableFuture<Void> rejectTerritory(Player actor, Clan clan, ClanRequest request) {
    return main(
        () -> {
          requireRank(clan, actor);
          requirePermission(clan, actor, "territorios");
          deleteRequests(r -> r.id().equals(request.id()));
          Player owner = Bukkit.getPlayer(request.player());
          if (owner != null)
            messages.send(owner, "{texto}El clan no ha aceptado tu protección como territorio.");
          return CompletableFuture.completedFuture(null);
        });
  }

  @Override
  public CompletableFuture<Clan> addTerritory(UUID clan, UUID protection) {
    return main(
        () -> {
          Clan value = required(clan);
          Protection p =
              protections.getProtection(protection)
                  .orElseThrow(() -> new ProtectionException("Esa protección no existe."));
          checkContribution(value, p, p.primaryOwner());
          return addTerritory(value, p);
        });
  }

  private CompletableFuture<Clan> addTerritory(Clan clan, Protection protection) {
    var territories = new HashMap<>(clan.territories());
    territories.put(
        protection.id(),
        new ClanTerritory(
            protection.id(),
            protection.primaryOwner(),
            System.currentTimeMillis(),
            false,
            protection.owners(),
            protection.members()));
    return persist(clan, clan.withTerritories(clan.home(), territories))
        .thenApply(
            next -> {
              notice(next, "{dato}" + name(protection.primaryOwner()) + " {texto}ha aportado {dato}" + protection.name() + " {texto}al territorio del clan.");
              return next;
            });
  }

  /** Devuelve un territorio aportado a su dueño. */
  public CompletableFuture<Clan> removeTerritory(Player actor, Clan clan, UUID protection) {
    return main(
        () -> {
          if (actor != null) {
            requireRank(clan, actor);
            requirePermission(clan, actor, "territorios");
          }
          ClanTerritory territory = clan.territories().get(protection);
          if (territory == null) throw new ProtectionException("Esa zona no es territorio del clan.");
          if (territory.home())
            throw new ProtectionException("El territorio principal no se puede devolver: es el del bloque de clan.");
          var territories = new HashMap<>(clan.territories());
          territories.remove(protection);
          return persist(clan, clan.withTerritories(clan.home(), territories))
              .thenCompose(next -> giveBack(clan, territory).thenApply(p -> next));
        });
  }

  /** Propietarios y miembros de la protección que no son del clan: perderían el acceso al aportarla. */
  public List<UUID> outsiders(Clan clan, Protection protection) {
    List<UUID> result = new ArrayList<>();
    for (UUID id : protection.owners()) if (!clan.isMember(id)) result.add(id);
    for (UUID id : protection.members()) if (!clan.isMember(id)) result.add(id);
    return result;
  }

  // ================================================================== ajustes

  public CompletableFuture<Clan> rename(Player actor, Clan clan, String name) {
    return main(
        () -> {
          if (actor != null) requirePermission(clan, actor, "ajustes");
          String value = validName(name, clan.id());
          return persist(clan, clan.withName(value))
              .thenApply(
                  next -> {
                    if (next.home() != null && protections.getProtection(next.home()).isPresent())
                      whenFree(next.home(), () -> protections.renameProtection(next.home(), value))
                          .exceptionally(error -> log("No se pudo renombrar el territorio principal", error));
                    notice(next, "{texto}El clan ahora se llama " + ClanMessages.paint(value, next.color()) + "{texto}.");
                    return next;
                  });
        });
  }

  public CompletableFuture<Clan> recolor(Player actor, Clan clan, String color) {
    return main(
        () -> {
          if (actor != null) requirePermission(clan, actor, "ajustes");
          String hex =
              settings.color(color)
                  .orElseThrow(() -> new ProtectionException("Usa un código HEX de 6 dígitos, como #B75FFF, o un color de la lista: " + String.join(", ", settings.colors().keySet()) + "."));
          return persist(clan, clan.withColor(hex))
              .thenApply(
                  next -> {
                    notice(next, "{texto}Nuevo color del clan: " + ClanMessages.paint(settings.colorName(hex), hex) + "{texto}.");
                    return next;
                  });
        });
  }

  public CompletableFuture<Clan> setFlag(Player actor, Clan clan, String flag, String value) {
    return main(
        () -> {
          if (actor != null) requirePermission(clan, actor, "banderas");
          String name = flag.toLowerCase(Locale.ROOT);
          if (!settings.allowedFlags().contains(name))
            throw new ProtectionException("El servidor no permite cambiar esa bandera. Permitidas: " + String.join(", ", settings.allowedFlags()) + ".");
          protections.guard().stateFlag(name);
          var flags = new HashMap<>(clan.flags());
          switch (value.toLowerCase(Locale.ROOT)) {
            case "permitir", "allow" -> flags.put(name, "ALLOW");
            case "denegar", "deny" -> flags.put(name, "DENY");
            case "restablecer", "reset" -> flags.remove(name);
            default -> throw new ProtectionException("Usa permitir, denegar o restablecer.");
          }
          return persist(clan, clan.withFlags(flags));
        });
  }

  /** El líder activa o quita una casilla a un rango. */
  public CompletableFuture<Clan> togglePermission(Player actor, Clan clan, ClanRank rank, String key) {
    return main(
        () -> {
          if (actor != null && !clan.leader().equals(actor.getUniqueId()))
            throw new ProtectionException("Solo el líder ajusta los permisos de los rangos.");
          if (rank == ClanRank.LEADER) throw new ProtectionException("El líder siempre tiene todos los permisos.");
          ClanPermission permission = permission(key).orElseThrow(() -> new ProtectionException("Esa casilla no existe."));
          if (settings.locked(key)) throw new ProtectionException("El servidor ha fijado esa casilla.");
          boolean now = !allows(clan, rank, key);
          boolean byDefault = settings.defaults(key, permission.defaults()).contains(rank);
          Map<ClanRank, Map<String, Boolean>> all = new EnumMap<>(ClanRank.class);
          clan.permissions().forEach((r, values) -> all.put(r, new HashMap<>(values)));
          var values = all.computeIfAbsent(rank, r -> new HashMap<>());
          if (now == byDefault) values.remove(key);
          else values.put(key, now);
          if (values.isEmpty()) all.remove(rank);
          return persist(clan, clan.withPermissions(all));
        });
  }

  // ================================================================== avisos y extensiones

  @Override
  public void broadcast(UUID clan, String message) {
    Clan value = clans.get(clan);
    if (value != null) notice(value, message);
  }

  /** Aviso en el chat del clan (texto de confianza con colores de la paleta). */
  public void notice(Clan clan, String message) {
    if (chat != null) chat.notice(clan, message);
  }

  @Override
  public CompletableFuture<ClanWar> setWarReward(UUID war, String reward) {
    return main(() -> wars.reward(war, reward));
  }

  @Override
  public void registerButton(Plugin plugin, ClanButton button) {
    extensions.button(plugin, button);
  }

  @Override
  public void registerStatusLine(Plugin plugin, String key, BiFunction<Clan, Player, String> line) {
    extensions.line(plugin, key, line);
  }

  @Override
  public void registerPermission(Plugin plugin, ClanPermission permission) {
    extensions.permission(plugin, permission);
  }

  @Override
  public void registerFoundingRequirement(Plugin plugin, FoundingRequirement requirement) {
    extensions.requirement(plugin, requirement);
  }

  @Override
  public void registerGuideTopic(Plugin plugin, String key, String title, List<String> lines) {
    extensions.topic(plugin, new ClanSettings.Topic(key, title, "✦", List.copyOf(lines)));
  }

  @Override
  public void unregisterAll(Plugin plugin) {
    extensions.unregister(plugin);
  }

  @EventHandler
  public void pluginDisabled(PluginDisableEvent event) {
    if (event.getPlugin() != plugin) extensions.unregister(event.getPlugin());
  }

  // ================================================================== sincronización con protecciones

  @EventHandler
  public void protectionChanged(ProtectionChangedEvent event) {
    Protection changed = event.getCurrent() != null ? event.getCurrent() : event.getPrevious();
    UUID clan = byTerritory.get(changed.id());
    if (clan != null) dirty.add(clan);
  }

  private void tick() {
    long now = System.currentTimeMillis();
    List<ClanRequest> expired = requests.values().stream().filter(r -> r.expired(now)).toList();
    if (!expired.isEmpty()) deleteRequests(expired::contains);
    if (wars != null) wars.tick(now);
    for (ClanBackup copy : List.copyOf(backups.values()))
      if (copy.expired(now, settings.backupMillis())) {
        backups.remove(copy.clan().id());
        store.deleteBackup(copy.clan().id());
      }
    for (UUID id : List.copyOf(dirty)) {
      Clan clan = clans.get(id);
      if (clan == null) {
        dirty.remove(id);
        continue;
      }
      if (pending.contains(id)) continue;
      if (sync(clan)) dirty.remove(id);
    }
  }

  /** Ajusta los accesos y banderas de cada territorio a los rangos del clan; true si ya coinciden. */
  private boolean sync(Clan clan) {
    boolean done = true;
    List<ClanTerritory> lost = new ArrayList<>();
    Set<UUID> owners = new HashSet<>(), members = new HashSet<>();
    for (ClanMember member : clan.members().values())
      if (member.rank() == ClanRank.CO_LEADER) owners.add(member.player());
      else if (member.rank() != ClanRank.LEADER) members.add(member.player());
    for (ClanTerritory territory : clan.territories().values()) {
      Protection p = protections.getProtection(territory.protection()).orElse(null);
      if (p == null) {
        lost.add(territory);
        continue;
      }
      if (protections.isPending(p.id())) {
        done = false;
        continue;
      }
      Map<String, String> flags = new HashMap<>(p.flags());
      settings.allowedFlags().forEach(flags::remove);
      flags.putAll(clan.flags());
      if (p.primaryOwner().equals(clan.leader())
          && p.owners().equals(owners)
          && p.members().equals(members)
          && p.flags().equals(flags)) {
        protections.reapply(p);
        continue;
      }
      done = false;
      protections
          .setAccess(p.id(), clan.leader(), owners, members, flags)
          .exceptionally(error -> log("No se pudo actualizar el territorio " + p.name() + " del clan " + clan.name(), error));
    }
    if (!lost.isEmpty()) {
      done = false;
      if (lost.stream().anyMatch(ClanTerritory::home))
        dissolve(clan.id(), ClanDisbandEvent.Cause.HOME_LOST, "su territorio principal ya no existe", null)
            .exceptionally(error -> log("No se pudo disolver el clan " + clan.name(), error));
      else {
        var territories = new HashMap<>(clan.territories());
        lost.forEach(t -> territories.remove(t.protection()));
        persist(clan, clan.withTerritories(clan.home(), territories))
            .exceptionally(error -> log("No se pudo retirar un territorio perdido de " + clan.name(), error));
      }
    }
    return done;
  }

  private String denyMessage(Protection protection) {
    UUID clan = byTerritory.get(protection.id());
    Clan value = clan == null ? null : clans.get(clan);
    if (value == null) return null;
    return messages.color(
        settings.prefix("publico")
            + "{error}Este territorio es del clan "
            + ClanMessages.paint(value.name(), value.color())
            + "{error}. No puedes hacer eso aquí.");
  }

  // ================================================================== persistencia

  private CompletableFuture<Clan> persist(Clan old, Clan next) {
    UUID id = old != null ? old.id() : next.id();
    if (!pending.add(id))
      throw new ProtectionException("Hay otra operación en curso para este clan. Espera un momento.");
    put(old, next);
    CompletableFuture<Void> write = next == null ? store.delete(id) : store.save(next);
    CompletableFuture<Clan> result = new CompletableFuture<>();
    write.whenComplete(
        (ignored, failure) -> {
          if (!plugin.isEnabled()) {
            result.completeExceptionally(
                new ProtectionException("El servidor se está apagando; el clan se recuperará al iniciar."));
            return;
          }
          Bukkit.getScheduler()
              .runTask(
                  plugin,
                  () -> {
                    pending.remove(id);
                    if (failure != null) {
                      put(next, old);
                      plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el clan " + id + "; se restauró su estado anterior.", failure);
                      result.completeExceptionally(
                          new ProtectionException("No se pudo guardar el cambio. El clan conserva su estado anterior."));
                      return;
                    }
                    if (next != null) dirty.add(id);
                    Bukkit.getPluginManager().callEvent(new ClanChangedEvent(old, next));
                    result.complete(next);
                  });
        });
    return result;
  }

  private void put(Clan remove, Clan add) {
    if (add != null) clans.put(add.id(), add);
    else if (remove != null) clans.remove(remove.id());
    reindex();
  }

  private void reindex() {
    Map<UUID, List<UUID>> players = new HashMap<>();
    Map<UUID, UUID> territories = new HashMap<>();
    for (Clan clan : clans.values()) {
      for (UUID member : clan.members().keySet())
        players.computeIfAbsent(member, k -> new ArrayList<>()).add(clan.id());
      for (UUID territory : clan.territories().keySet()) territories.put(territory, clan.id());
    }
    players.replaceAll((k, v) -> List.copyOf(v));
    byPlayer = Map.copyOf(players);
    byTerritory = Map.copyOf(territories);
  }

  private void deleteRequests(Predicate<ClanRequest> filter) {
    for (ClanRequest request : List.copyOf(requests.values()))
      if (filter.test(request)) {
        requests.remove(request.id());
        store.deleteRequest(request.id())
            .exceptionally(error -> log("No se pudo borrar una solicitud de clan", error));
      }
  }

  // ================================================================== utilidades

  /** Hay sitio para un miembro más según clans.yml (la administración puede superarlo). */
  private void requireRoom(Clan clan) {
    int max = settings.maxMembers();
    if (clan.members().size() >= max)
      throw new ProtectionException("El clan " + clan.name() + " está completo: " + max + " miembros como máximo.");
  }

  public Clan required(UUID id) {
    return getClan(id).orElseThrow(() -> new ProtectionException("Ese clan ya no existe."));
  }

  public ClanRank requireRank(Clan clan, Player player) {
    return clan.rank(player.getUniqueId())
        .orElseThrow(() -> new ProtectionException("No perteneces al clan " + clan.name() + "."));
  }

  public void requirePermission(Clan clan, Player player, String permission) {
    if (!allows(clan, player.getUniqueId(), permission))
      throw new ProtectionException(
          "Tu rango en el clan no permite eso"
              + permission(permission).map(p -> " (" + p.name().toLowerCase(Locale.ROOT) + ")").orElse("")
              + ".");
  }

  public static String name(UUID player) {
    String name = Bukkit.getOfflinePlayer(player).getName();
    return name == null ? player.toString().substring(0, 8) : name;
  }

  private <T> T log(String message, Throwable error) {
    plugin.getLogger().log(Level.WARNING, message + ": " + error.getMessage());
    return null;
  }

  private void sync(Runnable task) {
    if (Bukkit.isPrimaryThread()) task.run();
    else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
  }

  /** Espera a que la protección no tenga otra operación en curso antes de cambiarla. */
  private <T> CompletableFuture<T> whenFree(UUID protection, Supplier<CompletableFuture<T>> action) {
    return whenFree(protection, action, 0);
  }

  private <T> CompletableFuture<T> whenFree(UUID protection, Supplier<CompletableFuture<T>> action, int attempt) {
    if (!protections.isPending(protection) || attempt > 200) {
      try {
        return action.get();
      } catch (RuntimeException failure) {
        return CompletableFuture.failedFuture(failure);
      }
    }
    CompletableFuture<T> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () ->
                whenFree(protection, action, attempt + 1)
                    .whenComplete((value, error) -> {
                      if (error == null) result.complete(value);
                      else result.completeExceptionally(error);
                    }),
            2);
    return result;
  }

  <T> CompletableFuture<T> main(Supplier<CompletableFuture<T>> action) {
    if (!plugin.isEnabled())
      return CompletableFuture.failedFuture(new ProtectionException("El plugin no está disponible."));
    if (Bukkit.isPrimaryThread()) {
      try {
        return action.get();
      } catch (RuntimeException failure) {
        return CompletableFuture.failedFuture(failure);
      }
    }
    CompletableFuture<T> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () ->
                main(action)
                    .whenComplete(
                        (value, error) -> {
                          if (error == null) result.complete(value);
                          else result.completeExceptionally(error);
                        }));
    return result;
  }

  ClanStore store() {
    return store;
  }

  /** Para ClanWars: guarda un clan modificado por la propia lógica del plugin. */
  CompletableFuture<Clan> save(Clan old, Clan next) {
    return persist(old, next);
  }
}
