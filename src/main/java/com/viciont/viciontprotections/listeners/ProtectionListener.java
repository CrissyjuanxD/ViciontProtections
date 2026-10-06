package com.viciont.viciontprotections.listeners;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.api.event.ProtectionCrossedEvent;
import com.viciont.viciontprotections.commands.ProtectionCommand;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.ui.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WorldGuard protege la región; este listener gestiona exclusivamente el bloque ancla y la
 * presentación.
 */
public final class ProtectionListener implements Listener {
  private final JavaPlugin plugin;
  private final ProtectionManager manager;
  private final Messages messages;
  private final Boundaries boundaries;
  private final Map<BlockPlaceEvent, CreateProtectionRequest> placements = new WeakHashMap<>();
  private final Map<UUID, Protection> current = new HashMap<>();

  public ProtectionListener(
      JavaPlugin plugin, ProtectionManager manager, Messages messages, Boundaries boundaries) {
    this.plugin = plugin;
    this.manager = manager;
    this.messages = messages;
    this.boundaries = boundaries;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void validatePlace(BlockPlaceEvent event) {
    if (anchor(event.getBlockPlaced())) {
      event.setCancelled(true);
      messages.send(event.getPlayer(), "Retira primero el bloque protector.");
      return;
    }
    var spec = manager.readProtectionBlock(event.getItemInHand());
    if (spec.isEmpty()) return;
    try {
      Player player = event.getPlayer();
      if (!player.hasPermission("viciontprotections.user.create"))
        throw new ProtectionException("No tienes permiso para colocar bloques protectores.");
      int limit = plugin.getConfig().getInt("protections.max-per-player", 20);
      if (!player.hasPermission("viciontprotections.admin.manage")
          && manager.getProtections().stream()
                  .filter(p -> p.primaryOwner().equals(player.getUniqueId()))
                  .count()
              >= limit)
        throw new ProtectionException("Has alcanzado el límite de " + limit + " protecciones.");
      Location at = event.getBlockPlaced().getLocation();
      ProtectionBlock block = spec.get();
      int maximum = plugin.getConfig().getInt("protections.max-block-size", 4096);
      if (block.width() > maximum || block.depth() > maximum)
        throw new ProtectionException("El tamaño máximo configurado es " + maximum + " bloques.");
      World world = at.getWorld();
      Bounds bounds =
          Bounds.centered(
              at.getBlockX(),
              at.getBlockY(),
              at.getBlockZ(),
              block.width(),
              block.depth(),
              block.height(),
              world.getMinHeight(),
              world.getMaxHeight());
      var request =
          new CreateProtectionRequest(
              player.getUniqueId(),
              world.getName(),
              bounds,
              null,
              new Anchor(at.getBlockX(), at.getBlockY(), at.getBlockZ(), block));
      manager.guard().validate(request, player);
      placements.put(event, request);
    } catch (RuntimeException failure) {
      event.setCancelled(true);
      messages.error(event.getPlayer(), failure);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void placed(BlockPlaceEvent event) {
    CreateProtectionRequest request = placements.remove(event);
    if (request == null || event.isCancelled()) return;
    ItemStack refund = event.getItemInHand().clone();
    refund.setAmount(1);
    manager
        .createProtection(request)
        .whenComplete(
            (protection, error) -> {
              if (error != null) {
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          event.getBlockReplacedState().update(true, false);
                          if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
                            ProtectionCommand.giveItem(event.getPlayer(), refund);
                          messages.error(event.getPlayer(), error);
                        });
              } else {
                messages.send(
                    event.getPlayer(),
                    "&#E6CCFFCreada &#F1B9DE"
                        + protection.name()
                        + "&#E6CCFF. Usa /pr nombre <nombre> o /proteccion.");
                update(event.getPlayer());
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakAnchor(BlockBreakEvent event) {
    var value = manager.anchorAt(event.getBlock().getLocation());
    if (value.isEmpty()) return;
    event.setCancelled(true);
    Protection protection = value.get();
    Player player = event.getPlayer();
    if (!AccessPolicy.allows(player, protection, AccessPolicy.Action.DELETE)) {
      messages.send(
          player, "&#F1B9DESolo el creador o un administrador puede retirar este bloque.");
      return;
    }
    manager
        .deleteProtection(protection.id())
        .whenComplete(
            (ignored, error) -> {
              if (error != null) messages.error(player, error);
              else {
                if (player.getGameMode() != GameMode.CREATIVE)
                  ProtectionCommand.giveItem(
                      player, manager.createProtectionBlock(protection.anchor().block(), 1));
                messages.send(player, "&#E6CCFFProtección retirada.");
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interactAnchor(PlayerInteractEvent event) {
    if (event.getClickedBlock() != null
        && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
        && anchor(event.getClickedBlock())) {
      event.setUseInteractedBlock(Event.Result.DENY);
      event.setUseItemInHand(Event.Result.DENY);
    }
  }

  private boolean anchor(org.bukkit.block.Block block) {
    return manager.anchorAt(block.getLocation()).isPresent();
  }

  @EventHandler(ignoreCancelled = true)
  public void explode(EntityExplodeEvent event) {
    event.blockList().removeIf(this::anchor);
  }

  @EventHandler(ignoreCancelled = true)
  public void explode(BlockExplodeEvent event) {
    event.blockList().removeIf(this::anchor);
  }

  @EventHandler(ignoreCancelled = true)
  public void piston(BlockPistonExtendEvent event) {
    if (event.getBlocks().stream().anyMatch(this::anchor)) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void piston(BlockPistonRetractEvent event) {
    if (event.getBlocks().stream().anyMatch(this::anchor)) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void burn(BlockBurnEvent event) {
    if (anchor(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void fade(BlockFadeEvent event) {
    if (anchor(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void physics(BlockPhysicsEvent event) {
    if (anchor(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void flow(BlockFromToEvent event) {
    if (anchor(event.getToBlock())) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void entityChange(EntityChangeBlockEvent event) {
    if (anchor(event.getBlock())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void move(PlayerMoveEvent event) {
    Location from = event.getFrom(), to = event.getTo();
    if (to == null) return;
    if (from.getWorld() != to.getWorld()
        || from.getBlockX() != to.getBlockX()
        || from.getBlockY() != to.getBlockY()
        || from.getBlockZ() != to.getBlockZ()) updateAt(event.getPlayer(), to);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void teleport(PlayerTeleportEvent event) {
    if (event.getTo() != null) updateAt(event.getPlayer(), event.getTo());
  }

  @EventHandler
  public void join(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> update(event.getPlayer()));
  }

  @EventHandler
  public void respawn(PlayerRespawnEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> update(event.getPlayer()));
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    current.remove(event.getPlayer().getUniqueId());
    boundaries.forget(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void world(WorldLoadEvent event) {
    manager.worldLoaded(event.getWorld());
  }

  private void update(Player player) {
    if (player.isOnline()) updateAt(player, player.getLocation());
  }

  private void updateAt(Player player, Location location) {
    Protection next = manager.getProtectionAt(location).orElse(null),
        previous = current.get(player.getUniqueId());
    if (Objects.equals(previous == null ? null : previous.id(), next == null ? null : next.id()))
      return;
    if (next == null) current.remove(player.getUniqueId());
    else current.put(player.getUniqueId(), next);
    if (previous != null) messages.send(player, messages.text("left", "name", previous.name()));
    if (next != null)
      messages.entered(player, next.name(), ProtectionDialogs.ownerName(next.primaryOwner()));
    Bukkit.getPluginManager().callEvent(new ProtectionCrossedEvent(player, previous, next));
  }
}
