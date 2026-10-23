package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.api.event.clan.ClanFoundEvent;
import com.viciont.viciontprotections.commands.ProtectionCommand;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.ui.ClanViews;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fundación con el bloque de clan. Al colocarlo queda una fundación pendiente: el bloque se ve en
 * el mundo pero aún no protege nada. Con nombre y color (diálogo o /clan fundar) se cobran los
 * requisitos de otros plugins, como el depósito, y se crea el clan. Si no se completa a tiempo, si
 * el jugador sale o si no puede pagar, el bloque vuelve a su inventario.
 *
 * <p>Si quien lo coloca ya es líder de un clan, el bloque es un cambio de bloque de clan: al
 * confirmarlo, el territorio principal se mueve al bloque nuevo con su tamaño y el antiguo vuelve
 * al inventario del líder, sin disolver el clan.
 */
public final class ClanFounding implements Listener {
  /** @param clan el clan cuyo bloque se cambia; null si es una fundación */
  public record Draft(
      UUID player,
      Location block,
      ProtectionBlock spec,
      Bounds bounds,
      ItemStack item,
      long expiresAt,
      UUID clan) {}

  private final JavaPlugin plugin;
  private final ClanManager manager;
  private final ClanMessages messages;
  private ClanViews views;
  private final Map<UUID, Draft> drafts = new HashMap<>();
  private final Map<BlockPlaceEvent, Draft> placements = new WeakHashMap<>();
  private final Set<UUID> busy = new HashSet<>();

  public ClanFounding(JavaPlugin plugin, ClanManager manager) {
    this.plugin = plugin;
    this.manager = manager;
    this.messages = manager.messages();
    Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 40, 40);
  }

  public void connect(ClanViews views) {
    this.views = views;
  }

  public Optional<Draft> draft(UUID player) {
    return Optional.ofNullable(drafts.get(player));
  }

  public List<String> requirements(Player player) {
    List<String> lines = new ArrayList<>();
    for (var entry : manager.extensions().requirements()) {
      try {
        String line = entry.value().describe(player);
        if (line != null && !line.isBlank()) lines.add(line);
      } catch (RuntimeException failure) {
        plugin.getLogger().warning("Requisito de " + entry.plugin().getName() + " falló al describirse: " + failure);
      }
    }
    return lines;
  }

  // ------------------------------------------------------------------ colocar

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void place(BlockPlaceEvent event) {
    if (isDraft(event.getBlockPlaced())) {
      event.setCancelled(true);
      return;
    }
    ItemStack item = event.getItemInHand();
    if (!manager.isClanBlock(item)) return;
    Player player = event.getPlayer();
    try {
      if (!manager.settings().enabled()) throw new ProtectionException("Los clanes están desactivados en este servidor.");
      if (!player.hasPermission("viciontprotections.clan.create"))
        throw new ProtectionException("No tienes permiso para fundar clanes.");
      List<Clan> clans = manager.getPlayerClans(player.getUniqueId());
      Clan leading =
          manager.getPlayerClan(player.getUniqueId())
              .filter(c -> c.leader().equals(player.getUniqueId()))
              .or(() -> clans.stream().filter(c -> c.leader().equals(player.getUniqueId())).findFirst())
              .orElse(null);
      if (!clans.isEmpty() && leading == null)
        throw new ProtectionException(
            "Ya perteneces a un clan: solo su líder puede colocar un bloque de clan para cambiar el suyo.");
      if (drafts.containsKey(player.getUniqueId()))
        throw new ProtectionException("Ya tienes un bloque de clan pendiente: confírmalo o rompe ese bloque.");
      ProtectionBlock spec =
          manager.protections().readProtectionBlock(item).orElseThrow(() -> new ProtectionException("Bloque de clan no válido."));
      int maximum = plugin.getConfig().getInt("protections.max-block-size", 4096);
      if (spec.width() > maximum || spec.depth() > maximum)
        throw new ProtectionException("El tamaño máximo configurado es " + maximum + " bloques.");
      Location at = event.getBlockPlaced().getLocation();
      World world = at.getWorld();
      Bounds bounds =
          Bounds.centered(at.getBlockX(), at.getBlockY(), at.getBlockZ(), spec.width(), spec.depth(), spec.height(),
              world.getMinHeight(), world.getMaxHeight());
      String ignored = null;
      if (leading != null) {
        Protection home =
            leading.home() == null ? null : manager.protections().getProtection(leading.home()).orElse(null);
        if (home == null) throw new ProtectionException("Tu clan no tiene territorio principal que cambiar.");
        if (!home.worldName().equals(world.getName()))
          throw new ProtectionException(
              "Coloca el nuevo bloque de clan en el mismo mundo que el territorio principal (" + home.worldName() + ").");
        ignored = home.regionId();
      }
      manager.protections().guard().validate(request(player.getUniqueId(), "Clan", at, spec, bounds), player, ignored);
      ItemStack single = item.clone();
      single.setAmount(1);
      placements.put(
          event,
          new Draft(
              player.getUniqueId(),
              at,
              spec,
              bounds,
              single,
              System.currentTimeMillis() + manager.settings().foundingMillis(),
              leading == null ? null : leading.id()));
    } catch (RuntimeException failure) {
      event.setCancelled(true);
      messages.error(player, failure);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void placed(BlockPlaceEvent event) {
    Draft draft = placements.remove(event);
    if (draft == null || event.isCancelled()) return;
    drafts.put(draft.player(), draft);
    Player player = event.getPlayer();
    if (draft.clan() != null) {
      Clan clan = manager.getClan(draft.clan()).orElse(null);
      if (clan != null) {
        messages.send(
            player,
            "{texto}Bloque de clan nuevo colocado. Confirma el cambio en {dato}"
                + manager.settings().foundingMillis() / 60_000
                + " minutos {texto}o rompe el bloque para cancelarlo.");
        views.confirmReplace(player, clan, draft);
        return;
      }
    }
    if (!manager.backupsOf(player.getUniqueId()).isEmpty()) {
      messages.send(
          player,
          "{texto}Bloque de clan colocado. Puedes {dato}fundar un clan nuevo {texto}o {dato}recuperar uno que disolviste{texto}.");
      views.foundChoice(player, null);
      return;
    }
    messages.send(
        player,
        "{texto}Bloque de clan colocado. Elige el {dato}nombre {texto}y el {dato}color {texto}de tu clan para fundarlo. Tienes {dato}"
            + manager.settings().foundingMillis() / 60_000
            + " minutos{texto}; si no, el bloque vuelve a tu inventario.");
    views.found(player, null);
  }

  private static CreateProtectionRequest request(UUID owner, String name, Location at, ProtectionBlock spec, Bounds bounds) {
    return new CreateProtectionRequest(
        owner, at.getWorld().getName(), bounds, name, new Anchor(at.getBlockX(), at.getBlockY(), at.getBlockZ(), spec));
  }

  // ------------------------------------------------------------------ fundar

  /** Valida, cobra los requisitos y funda. Si algo falla tras cobrar, se devuelve todo. */
  public CompletableFuture<Clan> found(Player player, String color, String rawName) {
    UUID id = player.getUniqueId();
    Draft draft = drafts.get(id);
    if (draft == null)
      throw new ProtectionException("Coloca un bloque de clan sin estar en ningún clan para fundar uno.");
    if (draft.clan() != null)
      throw new ProtectionException("Ese bloque es para cambiar el de tu clan: confírmalo con /clan bloque confirmar.");
    if (busy.contains(id)) throw new ProtectionException("Tu clan se está fundando; espera un momento.");
    if (!manager.getPlayerClans(id).isEmpty()) throw new ProtectionException("Ya perteneces a un clan.");
    String name = manager.validName(rawName, null);
    String hex =
        manager.settings().color(color)
            .orElseThrow(() -> new ProtectionException("Elige uno de estos colores: " + String.join(", ", manager.settings().colors().keySet()) + "."));
    var event = new ClanFoundEvent(player, name, hex);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled())
      throw new ProtectionException(event.getCancelMessage() != null ? event.getCancelMessage() : "Otro plugin ha impedido fundar el clan.");
    busy.add(id);
    List<FoundingRequirement> charged = new ArrayList<>();
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    for (var entry : manager.extensions().requirements()) {
      FoundingRequirement requirement = entry.value();
      chain =
          chain.thenCompose(
              ignored ->
                  onMain(() -> requirement.charge(player, name))
                      .thenRun(() -> charged.add(requirement)));
    }
    CompletableFuture<Clan> result = new CompletableFuture<>();
    chain
        .thenCompose(ignored -> onMain(() -> manager.found(id, name, hex, request(id, name, draft.block(), draft.spec(), draft.bounds()))))
        .whenComplete(
            (clan, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          busy.remove(id);
                          if (failure == null) {
                            drafts.remove(id);
                            if (manager.settings().flag("clanes.anunciar"))
                              messages.broadcast(
                                  ClanMessages.Theme.PUBLIC,
                                  "{dato}" + player.getName() + " {texto}ha fundado el clan " + ClanMessages.paint(clan.name(), clan.color()) + "{texto}.");
                            result.complete(clan);
                            return;
                          }
                          Collections.reverse(charged);
                          for (FoundingRequirement requirement : charged)
                            try {
                              requirement.refund(player, name);
                            } catch (RuntimeException refund) {
                              plugin.getLogger().severe("No se pudo devolver un requisito de fundación a " + player.getName() + ": " + refund);
                            }
                          cancel(id, true);
                          result.completeExceptionally(failure);
                        }));
    return result;
  }

  /**
   * Recupera un clan disuelto con el bloque que el jugador acaba de colocar, sin requisitos de
   * fundación: no es un clan nuevo. Si falla, el bloque sigue colocado para fundar o reintentar.
   */
  public CompletableFuture<Clan> restore(Player player, UUID backup) {
    UUID id = player.getUniqueId();
    Draft draft = drafts.get(id);
    if (draft == null || draft.clan() != null)
      throw new ProtectionException("Coloca un bloque de clan para recuperar tu clan: será su nuevo territorio principal.");
    if (busy.contains(id)) throw new ProtectionException("Tu clan se está recuperando; espera un momento.");
    busy.add(id);
    CompletableFuture<Clan> result = new CompletableFuture<>();
    onMain(() -> manager.restore(id, backup, request(id, "Clan", draft.block(), draft.spec(), draft.bounds())))
        .whenComplete(
            (clan, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          busy.remove(id);
                          if (failure != null) {
                            result.completeExceptionally(failure);
                            return;
                          }
                          drafts.remove(id);
                          if (manager.settings().flag("clanes.anunciar"))
                            messages.broadcast(
                                ClanMessages.Theme.PUBLIC,
                                "{dato}" + player.getName() + " {texto}ha recuperado el clan " + ClanMessages.paint(clan.name(), clan.color()) + "{texto}.");
                          result.complete(clan);
                        }));
    return result;
  }

  /**
   * Cambia el bloque de clan por el que el líder acaba de colocar: el territorio principal pasa a
   * la zona del bloque nuevo y el bloque antiguo vuelve a su inventario.
   */
  public CompletableFuture<Clan> replace(Player player) {
    UUID id = player.getUniqueId();
    Draft draft = drafts.get(id);
    if (draft == null || draft.clan() == null)
      throw new ProtectionException(
          "Primero coloca el nuevo bloque de clan donde quieras el centro del territorio principal.");
    if (busy.contains(id)) throw new ProtectionException("El cambio ya está en marcha; espera un momento.");
    Clan clan = manager.required(draft.clan());
    if (!clan.leader().equals(id)) throw new ProtectionException("Solo el líder puede cambiar el bloque de clan.");
    Protection home =
        clan.home() == null ? null : manager.protections().getProtection(clan.home()).orElse(null);
    if (home == null) throw new ProtectionException("Tu clan no tiene territorio principal que cambiar.");
    Anchor anchor =
        new Anchor(draft.block().getBlockX(), draft.block().getBlockY(), draft.block().getBlockZ(), draft.spec());
    busy.add(id);
    CompletableFuture<Clan> result = new CompletableFuture<>();
    manager
        .protections()
        .relocate(home.id(), draft.bounds(), anchor, player)
        .whenComplete(
            (moved, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          busy.remove(id);
                          if (failure != null) {
                            cancel(id, true);
                            result.completeExceptionally(failure);
                            return;
                          }
                          drafts.remove(id);
                          Anchor old = home.anchor();
                          World world = Bukkit.getWorld(home.worldName());
                          if (old != null && world != null) {
                            Block block = world.getBlockAt(old.x(), old.y(), old.z());
                            if (block.getType() == old.block().material()) block.setType(Material.AIR, false);
                            ProtectionCommand.giveItem(player, manager.createClanBlock(old.block(), 1));
                          }
                          Clan current = manager.getClan(clan.id()).orElse(clan);
                          manager.notice(
                              current,
                              "{dato}" + player.getName() + " {texto}ha cambiado el bloque de clan: el territorio principal ahora mide {dato}"
                                  + draft.spec().dimensions() + "{texto}.");
                          result.complete(current);
                        }));
    return result;
  }

  private <T> CompletableFuture<T> onMain(java.util.function.Supplier<CompletableFuture<T>> action) {
    return manager.main(action);
  }

  // ------------------------------------------------------------------ cancelar

  /** Retira el bloque pendiente y lo devuelve al jugador. */
  public void cancel(UUID player, boolean notify) {
    Draft draft = drafts.remove(player);
    if (draft == null) return;
    Block block = draft.block().getBlock();
    if (block.getType() == draft.spec().material()) block.setType(Material.AIR, false);
    Player online = Bukkit.getPlayer(player);
    if (online != null) {
      ProtectionCommand.giveItem(online, draft.item());
      if (notify) messages.send(online, "{texto}El bloque de clan ha vuelto a tu inventario.");
    } else block.getWorld().dropItemNaturally(draft.block(), draft.item());
  }

  public void cancelAll() {
    for (UUID player : List.copyOf(drafts.keySet())) cancel(player, false);
  }

  private void expire() {
    long now = System.currentTimeMillis();
    for (Draft draft : List.copyOf(drafts.values()))
      if (now >= draft.expiresAt() && !busy.contains(draft.player())) {
        Player online = Bukkit.getPlayer(draft.player());
        if (online != null)
          messages.send(
              online,
              draft.clan() == null
                  ? "{texto}Se acabó el tiempo para fundar el clan."
                  : "{texto}Se acabó el tiempo para cambiar el bloque de clan; el de siempre sigue en su sitio.");
        cancel(draft.player(), true);
      }
  }

  private boolean isDraft(Block block) {
    for (Draft draft : drafts.values())
      if (draft.block().getBlock().equals(block)) return true;
    return false;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakDraft(BlockBreakEvent event) {
    for (Draft draft : List.copyOf(drafts.values())) {
      if (!draft.block().getBlock().equals(event.getBlock())) continue;
      event.setCancelled(true);
      if (busy.contains(draft.player())) return;
      if (draft.player().equals(event.getPlayer().getUniqueId())) cancel(draft.player(), true);
      else messages.fail(event.getPlayer(), "Ese bloque de clan está a punto de fundar un clan.");
      return;
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void explode(EntityExplodeEvent event) {
    event.blockList().removeIf(this::isDraft);
  }

  @EventHandler(ignoreCancelled = true)
  public void explode(BlockExplodeEvent event) {
    event.blockList().removeIf(this::isDraft);
  }

  @EventHandler(ignoreCancelled = true)
  public void piston(BlockPistonExtendEvent event) {
    if (event.getBlocks().stream().anyMatch(this::isDraft)) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void piston(BlockPistonRetractEvent event) {
    if (event.getBlocks().stream().anyMatch(this::isDraft)) event.setCancelled(true);
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    if (!busy.contains(event.getPlayer().getUniqueId())) cancel(event.getPlayer().getUniqueId(), false);
  }
}
