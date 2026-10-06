package com.viciont.viciontprotections.managers;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.api.event.*;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.integration.WorldGuardBridge;
import com.viciont.viciontprotections.models.Protection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectionManager implements ViciontProtectionsApi {
  private final JavaPlugin plugin;
  private final DatabaseManager store;
  private final WorldGuardBridge guard;
  private final ProtectionItems items;
  private final Map<UUID, Protection> protections = new ConcurrentHashMap<>();
  private final Set<UUID> pending = new HashSet<>();

  private record AnchorKey(String world, int x, int y, int z) {}

  private final Map<AnchorKey, UUID> anchors = new HashMap<>();

  public ProtectionManager(JavaPlugin plugin, DatabaseManager store, WorldGuardBridge guard)
      throws SQLException {
    this.plugin = plugin;
    this.store = store;
    this.guard = guard;
    this.items = new ProtectionItems(plugin);
    for (Protection protection : store.loadAll()) put(protection);
    for (World world : Bukkit.getWorlds()) guard.reconcile(world, protections.values());
  }

  public WorldGuardBridge guard() {
    return guard;
  }

  public boolean isPending(UUID id) {
    return pending.contains(id);
  }

  public void worldLoaded(World world) {
    guard.reconcile(world, protections.values());
  }

  public ItemStack createProtectionBlock(ProtectionBlock block, int amount) {
    requireMain();
    return items.create(block, amount);
  }

  public Optional<ProtectionBlock> readProtectionBlock(ItemStack item) {
    requireMain();
    return items.read(item);
  }

  public Optional<Protection> getProtection(UUID id) {
    return Optional.ofNullable(protections.get(id));
  }

  public Optional<Protection> getProtectionAt(Location location) {
    requireMain();
    return guard.at(location).stream()
        .map(protections::get)
        .filter(Objects::nonNull)
        .min(Comparator.comparing(Protection::createdAt));
  }

  public List<Protection> getProtections() {
    return protections.values().stream()
        .sorted(
            Comparator.comparing(Protection::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Protection::id))
        .toList();
  }

  public List<Protection> getProtections(UUID player) {
    return getProtections().stream().filter(p -> p.canAccess(player)).toList();
  }

  public boolean canAccess(UUID id, UUID player) {
    return getProtection(id).map(p -> p.canAccess(player)).orElse(false);
  }

  private AnchorKey key(Protection p) {
    Anchor a = p.anchor();
    return a == null ? null : new AnchorKey(p.worldName(), a.x(), a.y(), a.z());
  }

  private void put(Protection p) {
    protections.put(p.id(), p);
    if (key(p) != null) anchors.put(key(p), p.id());
  }

  private void remove(UUID id) {
    Protection p = protections.remove(id);
    if (p != null && key(p) != null) anchors.remove(key(p), id);
  }

  public Optional<Protection> anchorAt(Location location) {
    UUID id =
        anchors.get(
            new AnchorKey(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()));
    return id == null ? Optional.empty() : getProtection(id);
  }

  public Protection resolve(String selector) {
    String value = selector.startsWith("#") ? selector.substring(1) : selector;
    List<Protection> matches =
        getProtections().stream()
            .filter(
                p ->
                    p.id().toString().equalsIgnoreCase(value)
                        || p.shortId().equalsIgnoreCase(value)
                        || p.name().equalsIgnoreCase(selector))
            .toList();
    if (matches.size() != 1)
      throw new ProtectionException(
          matches.isEmpty()
              ? "No se encontró esa protección."
              : "Hay varios nombres iguales; usa el identificador # de la lista.");
    return matches.getFirst();
  }

  public static String validName(String name) {
    String value =
        Normalizer.normalize(Objects.requireNonNullElse(name, ""), Normalizer.Form.NFKC)
            .trim()
            .replaceAll(" +", " ");
    if (!value.matches("[\\p{L}\\p{N} ._#-]{1,48}"))
      throw new ProtectionException(
          "El nombre debe tener entre 1 y 48 letras, números, espacios, puntos, guiones o #.");
    return value;
  }

  public CompletableFuture<Protection> createProtection(CreateProtectionRequest request) {
    return main(
        () -> {
          guard.validate(request, null);
          UUID id = UUID.randomUUID();
          if (request.anchor() != null
              && !request
                  .bounds()
                  .contains(request.anchor().x(), request.anchor().y(), request.anchor().z()))
            throw new ProtectionException("El bloque protector debe estar dentro de la región.");
          String name =
              request.name() == null || request.name().isBlank()
                  ? "Protección #" + id.toString().substring(0, 8)
                  : validName(request.name());
          Protection protection =
              new Protection(
                  id,
                  name,
                  request.worldName(),
                  request.bounds(),
                  request.primaryOwner(),
                  Set.of(),
                  Set.of(),
                  Map.of(),
                  request.anchor(),
                  System.currentTimeMillis());
          return persist(null, protection);
        });
  }

  public CompletableFuture<Protection> renameProtection(UUID id, String name) {
    return change(id, p -> p.withName(validName(name)));
  }

  public CompletableFuture<Protection> addMember(UUID id, UUID target) {
    return change(
        id,
        p -> {
          if (p.canAccess(target))
            throw new ProtectionException("Ese jugador ya tiene acceso a la protección.");
          var members = new HashSet<>(p.members());
          members.add(target);
          return p.withRoles(p.primaryOwner(), p.owners(), members);
        });
  }

  public CompletableFuture<Protection> removeMember(UUID id, UUID target) {
    return change(
        id,
        p -> {
          if (!p.members().contains(target))
            throw new ProtectionException(
                "Ese jugador no es miembro. Los propietarios se gestionan por separado.");
          var members = new HashSet<>(p.members());
          members.remove(target);
          return p.withRoles(p.primaryOwner(), p.owners(), members);
        });
  }

  public CompletableFuture<Protection> addOwner(UUID id, UUID target) {
    return change(
        id,
        p -> {
          if (p.isOwner(target)) throw new ProtectionException("Ese jugador ya es propietario.");
          var owners = new HashSet<>(p.owners());
          var members = new HashSet<>(p.members());
          owners.add(target);
          members.remove(target);
          return p.withRoles(p.primaryOwner(), owners, members);
        });
  }

  public CompletableFuture<Protection> removeOwner(UUID id, UUID target) {
    return change(
        id,
        p -> {
          if (p.primaryOwner().equals(target))
            throw new ProtectionException("No puedes eliminar al creador de la protección.");
          if (!p.owners().contains(target))
            throw new ProtectionException("Ese jugador no es un propietario añadido.");
          var owners = new HashSet<>(p.owners());
          owners.remove(target);
          return p.withRoles(p.primaryOwner(), owners, p.members());
        });
  }

  public CompletableFuture<Protection> transferOwnership(UUID id, UUID target) {
    return change(
        id,
        p -> {
          var owners = new HashSet<>(p.owners());
          var members = new HashSet<>(p.members());
          owners.remove(target);
          members.remove(target);
          owners.add(p.primaryOwner());
          owners.remove(target);
          return p.withRoles(target, owners, members);
        });
  }

  public CompletableFuture<Protection> setFlag(UUID id, String flag, String value) {
    return change(
        id,
        p -> {
          String name = guard.stateFlag(flag).getName();
          var flags = new HashMap<>(p.flags());
          switch (value.toLowerCase(Locale.ROOT)) {
            case "permitir", "allow" -> flags.put(name, "ALLOW");
            case "denegar", "deny" -> flags.put(name, "DENY");
            case "restablecer", "reset" -> flags.remove(name);
            default -> throw new ProtectionException("Usa permitir, denegar o restablecer.");
          }
          return p.withFlags(flags);
        });
  }

  public CompletableFuture<Void> deleteProtection(UUID id) {
    return main(() -> persist(required(id), null).thenApply(p -> null));
  }

  private CompletableFuture<Protection> change(UUID id, UnaryOperator<Protection> change) {
    return main(
        () -> {
          Protection old = required(id);
          return persist(old, change.apply(old));
        });
  }

  private Protection required(UUID id) {
    return getProtection(id)
        .orElseThrow(() -> new ProtectionException("La protección ya no existe."));
  }

  private CompletableFuture<Protection> persist(Protection old, Protection next) {
    UUID id = old == null ? next.id() : old.id();
    if (!pending.add(id))
      throw new ProtectionException(
          "Hay una operación en curso para esta protección. Espera un momento.");
    try {
      var event = new ProtectionChangingEvent(old, next);
      Bukkit.getPluginManager().callEvent(event);
      if (event.isCancelled())
        throw new ProtectionException("Otro plugin ha cancelado la operación.");
      if (old == null)
        guard.validate(
            new CreateProtectionRequest(
                next.primaryOwner(), next.worldName(), next.bounds(), next.name(), next.anchor()),
            null);
      if (next != null) {
        guard.apply(next);
        put(next);
      }
    } catch (RuntimeException failure) {
      pending.remove(id);
      throw failure;
    }
    CompletableFuture<Protection> result = new CompletableFuture<>();
    CompletableFuture<Void> write = next == null ? store.delete(id) : store.save(next);
    write.whenComplete(
        (ignored, failure) -> {
          if (!plugin.isEnabled()) {
            result.completeExceptionally(
                new ProtectionException(
                    "El servidor se está apagando; el estado se recuperará al iniciar."));
            return;
          }
          Bukkit.getScheduler()
              .runTask(
                  plugin,
                  () -> {
                    pending.remove(id);
                    try {
                      if (failure != null) {
                        if (old == null) {
                          guard.remove(next);
                          remove(id);
                        } else {
                          guard.apply(old);
                          put(old);
                        }
                        plugin
                            .getLogger()
                            .severe(
                                "No se pudo guardar la protección "
                                    + id
                                    + ". Se restauró su estado anterior. Causa: "
                                    + failure.getClass().getSimpleName());
                        result.completeExceptionally(
                            new ProtectionException(
                                "No se pudo guardar el cambio. La protección conserva su estado"
                                    + " anterior."));
                        return;
                      }
                      if (next == null) {
                        guard.remove(old);
                        remove(id);
                        removeAnchor(old);
                      }
                      Bukkit.getPluginManager().callEvent(new ProtectionChangedEvent(old, next));
                      result.complete(next);
                    } catch (RuntimeException synchronizationFailure) {
                      plugin
                          .getLogger()
                          .log(
                              java.util.logging.Level.SEVERE,
                              "No se pudo sincronizar la protección " + id + " con WorldGuard.",
                              synchronizationFailure);
                      result.completeExceptionally(
                          new ProtectionException(
                              "No se pudo sincronizar la protección. Revisa la consola antes de"
                                  + " continuar."));
                    }
                  });
        });
    return result;
  }

  private void removeAnchor(Protection old) {
    Anchor anchor = old.anchor();
    World world = Bukkit.getWorld(old.worldName());
    if (anchor != null && world != null) {
      var block = world.getBlockAt(anchor.x(), anchor.y(), anchor.z());
      if (block.getType() == anchor.block().material()) block.setType(Material.AIR, false);
    }
  }

  private <T> CompletableFuture<T> main(Supplier<CompletableFuture<T>> action) {
    if (!plugin.isEnabled())
      return CompletableFuture.failedFuture(
          new ProtectionException("El plugin no está disponible."));
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

  private static void requireMain() {
    if (!Bukkit.isPrimaryThread())
      throw new IllegalStateException("Esta consulta requiere el hilo principal de Bukkit.");
  }
}
