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
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WorldGuard protege la región; este listener gestiona exclusivamente el bloque ancla y la
 * presentación.
 */
public final class ProtectionListener implements Listener {
  /** Lo que los clanes cambian de este listener: los bloques y los avisos de sus territorios. */
  public interface Territories {
    /** true si la protección es territorio de un clan y ya se atendió la rotura de su bloque. */
    boolean anchorBroken(Player player, Protection protection);

    /** true si los avisos de entrada o salida ya se mostraron. */
    boolean crossed(Player player, Protection previous, Protection next);
  }

  private final JavaPlugin plugin;
  private Territories territories;
  private final ProtectionManager manager;
  private final Messages messages;
  private final Boundaries boundaries;
  private final ProtectionDialogs dialogs;
  private final Map<BlockPlaceEvent, CreateProtectionRequest> placements = new WeakHashMap<>();
  private final Map<UUID, Protection> current = new HashMap<>();

  public ProtectionListener(
      JavaPlugin plugin,
      ProtectionManager manager,
      Messages messages,
      Boundaries boundaries,
      ProtectionDialogs dialogs) {
    this.plugin = plugin;
    this.manager = manager;
    this.messages = messages;
    this.boundaries = boundaries;
    this.dialogs = dialogs;
  }

  public void setTerritories(Territories territories) {
    this.territories = territories;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void validatePlace(BlockPlaceEvent event) {
    if (anchor(event.getBlockPlaced())) {
      event.setCancelled(true);
      messages.fail(event.getPlayer(), "Ahí está el bloque protector de una protección.");
      return;
    }
    if (manager.isClanBlock(event.getItemInHand())) return;
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
                Player player = event.getPlayer();
                messages.notice(player, "avisos.creada", "name", protection.name());
                update(player);
                // Como en 1.1.1: se invita a nombrarla nada más colocarla.
                if (!dialogs.form(player, protection, "nombrar", null))
                  messages.components(
                      player,
                      false,
                      Messages.button(
                          "{acento}[✎ Ponle nombre]",
                          "{texto}Escribe el nombre tras el comando",
                          "/pr @" + protection.id() + " nombre ",
                          false));
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
    if (territories != null && territories.anchorBroken(player, protection)) return;
    if (!AccessPolicy.allows(player, protection, AccessPolicy.Action.DELETE)) {
      messages.fail(player, "Solo el creador o la administración pueden retirar este bloque.");
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
                messages.send(
                    player,
                    "Retiraste la protección {dato}"
                        + protection.name()
                        + (player.getGameMode() == GameMode.CREATIVE
                            ? "{texto}."
                            : "{texto}; el bloque volvió a tu inventario."));
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

  private boolean protectionItem(ItemStack item) {
    return item != null && manager.readProtectionBlock(item).isPresent();
  }

  private boolean anyProtectionItem(ItemStack... items) {
    for (ItemStack item : items) if (protectionItem(item)) return true;
    return false;
  }

  // Solo mira los huecos de entrada (no el resultado), así se puede seguir comprando un bloque protector
  private boolean protectionInput(InventoryView view) {
    Inventory top = view.getTopInventory();
    for (int slot = 0; slot < top.getSize(); slot++)
      if (view.getSlotType(slot) != InventoryType.SlotType.RESULT && protectionItem(top.getItem(slot)))
        return true;
    return false;
  }

  // Los bloques protectores no sirven de ingrediente: si no, uno de netherite daría 9 lingotes. Se tapan la
  // mesa de crafteo, el crafter, el yunque, la herrería, la piedra de afilar, el cortapiedras, el telar, la
  // mesa de cartografía, los aldeanos y los hornos
  @EventHandler(priority = EventPriority.HIGHEST)
  public void prepareCraft(PrepareItemCraftEvent event) {
    if (anyProtectionItem(event.getInventory().getMatrix())) event.getInventory().setResult(null);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void prepareResult(PrepareInventoryResultEvent event) {
    if (protectionInput(event.getView())) event.setResult(null);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void takeResult(InventoryClickEvent event) {
    if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
    // Los hornos se tapan con smelt y burn; aquí solo se sacaría lo ya fundido
    if (event.getView().getTopInventory() instanceof FurnaceInventory) return;
    if (protectionInput(event.getView())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void crafter(CrafterCraftEvent event) {
    if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
        && anyProtectionItem(crafter.getInventory().getContents())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void smelt(FurnaceSmeltEvent event) {
    if (protectionItem(event.getSource())) event.setCancelled(true);
  }

  // Tampoco arde como combustible ni gasta combustible intentando fundir un bloque protector
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void burn(FurnaceBurnEvent event) {
    if (protectionItem(event.getFuel())
        || event.getBlock().getState() instanceof org.bukkit.block.Furnace furnace
            && protectionItem(furnace.getInventory().getSmelting())) event.setCancelled(true);
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
    if (territories == null || !territories.crossed(player, previous, next)) {
      if (previous != null && next == null) messages.left(player, previous.name());
      if (next != null)
        messages.entered(player, next.name(), ProtectionDialogs.ownerName(next.primaryOwner()));
    }
    Bukkit.getPluginManager().callEvent(new ProtectionCrossedEvent(player, previous, next));
  }
}
